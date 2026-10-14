package app.morphe.extension.twitch.chat;

import android.os.SystemClock;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.widget.TextView;

import io.github.bakwudo.uyu.extension.settings.Settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeletedMessagesSupport {
    private static final String DIAGNOSTIC_TAG = "KizuDeletedStyle";
    private static final AtomicBoolean ENTRY_TOAST_SHOWN = new AtomicBoolean(false);
    private static final AtomicBoolean OUTCOME_TOAST_SHOWN = new AtomicBoolean(false);
    // Leave headroom for roughly 10,000 messages arriving in a ten-minute rapid-chat window.
    private static final int ORIGINAL_CACHE_LIMIT = 12_000;
    private static final long ORIGINAL_CACHE_TTL_MS = 15L * 60L * 1000L;
    private static final Object ORIGINAL_CACHE_LOCK = new Object();
    private static long lastOriginalCachePruneAt;
    private static final Map<String, CachedOriginal> ORIGINAL_MESSAGES =
            new LinkedHashMap<String, CachedOriginal>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedOriginal> eldest) {
                    return size() > ORIGINAL_CACHE_LIMIT;
                }
            };

    private static final Map<Class<?>, Field> MESSAGE_ID_FIELDS = new java.util.WeakHashMap<>();
    private static final Map<Class<?>, Boolean> MISSING_MESSAGE_ID_FIELDS = new java.util.WeakHashMap<>();

    private static final class CachedOriginal {
        final SpannedString text;
        final long capturedAt;

        CachedOriginal(SpannedString text, long capturedAt) {
            this.text = text;
            this.capturedAt = capturedAt;
        }
    }

    /** Marker retained through EmoteSupport's text rebuild so later emotes inherit Gray mode. */
    public static final class DeletedStyleMarker {
        private DeletedStyleMarker() { }
    }

    private DeletedMessagesSupport() {
    }

    /**
     * Called from the existing chat-row bind hook after Twitch sets the row text. Keep a bounded
     * copy of recent message rows so a later rapid-chat deletion can still show the original even
     * when Twitch replaces the deleted span's original-message field with its placeholder.
     * This method is deliberately failure-contained because it runs on Twitch's RecyclerView path.
     */
    public static void onRowBound(Object messageModel, TextView textView) {
        if (textView == null) return;
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return;
            String messageId = readMessageId(messageModel);
            if (messageId == null || messageId.isEmpty()) return;

            CharSequence current = textView.getText();
            if (current == null || current.length() == 0) return;

            if (containsDeletedPlaceholder(current)) {
                Spanned recovered = current instanceof Spanned
                        ? restoreDeletedMessageFromRow((Spanned) current)
                        : null;
                if (recovered != null && recovered.length() > 0) {
                    textView.setText(recovered, TextView.BufferType.SPANNABLE);
                    return;
                }
                SpannedString cached = findCachedOriginal(messageId, SystemClock.uptimeMillis());
                if (cached != null && cached.length() > 0) {
                    SpannableStringBuilder restored = new SpannableStringBuilder(cached);
                    int bodyStart = findMessageBodyStart(restored);
                    applyDeletedStyle(restored, bodyStart, restored.length());
                    textView.setText(SpannedString.valueOf(restored), TextView.BufferType.SPANNABLE);
                }
                return;
            }

            SpannedString snapshot = new SpannedString(current);
            long now = SystemClock.uptimeMillis();
            synchronized (ORIGINAL_CACHE_LOCK) {
                ORIGINAL_MESSAGES.put(messageId, new CachedOriginal(snapshot, now));
                // Expired entries can otherwise occupy the LRU until enough new messages arrive.
                if (ORIGINAL_MESSAGES.size() > 256
                        && now - lastOriginalCachePruneAt >= 60_000L) {
                    lastOriginalCachePruneAt = now;
                    java.util.Iterator<Map.Entry<String, CachedOriginal>> iterator =
                            ORIGINAL_MESSAGES.entrySet().iterator();
                    while (iterator.hasNext()) {
                        if (now - iterator.next().getValue().capturedAt > ORIGINAL_CACHE_TTL_MS) {
                            iterator.remove();
                        }
                    }
                }
            }
        } catch (Throwable failure) {
            Log.w(DIAGNOSTIC_TAG, "could not cache or restore a chat row", failure);
        }
    }

    private static SpannedString findCachedOriginal(String messageId, long now) {
        synchronized (ORIGINAL_CACHE_LOCK) {
            CachedOriginal cached = ORIGINAL_MESSAGES.get(messageId);
            if (cached == null) return null;
            if (now - cached.capturedAt > ORIGINAL_CACHE_TTL_MS) {
                ORIGINAL_MESSAGES.remove(messageId);
                return null;
            }
            return cached.text;
        }
    }

    private static boolean containsDeletedPlaceholder(CharSequence text) {
        final String marker = "<message deleted>";
        String value = text.toString();
        for (int start = 0; start + marker.length() <= value.length(); start++) {
            if (value.regionMatches(true, start, marker, 0, marker.length())) return true;
        }
        return false;
    }

    private static String readMessageId(Object model) {
        if (model == null) return null;
        Field field = null;
        try {
            Class<?> type = model.getClass();
            synchronized (MESSAGE_ID_FIELDS) {
                field = MESSAGE_ID_FIELDS.get(type);
                if (field == null && !MISSING_MESSAGE_ID_FIELDS.containsKey(type)) {
                    for (Class<?> current = type; current != null && current != Object.class;
                         current = current.getSuperclass()) {
                        try {
                            field = current.getDeclaredField("messageId");
                            field.setAccessible(true);
                            MESSAGE_ID_FIELDS.put(type, field);
                            break;
                        } catch (NoSuchFieldException ignored) {
                        }
                    }
                    if (field == null) MISSING_MESSAGE_ID_FIELDS.put(type, Boolean.TRUE);
                }
            }
        } catch (Throwable ignored) {
        }

        if (field != null) {
            try {
                Object value = field.get(model);
                if (value != null) {
                    String id = String.valueOf(value);
                    if (!id.isEmpty()) return id;
                }
            } catch (Throwable ignored) {
                // R8 may rename the backing field or deny reflective access; the data-class
                // toString still includes the stable messageId label used by our fingerprint.
            }
        }
        return readMessageIdFromDataClassString(model);
    }

    private static String readMessageIdFromDataClassString(Object model) {
        try {
            String value = String.valueOf(model);
            int marker = value.indexOf("MessageRecyclerItem(messageId=");
            int start = marker >= 0
                    ? marker + "MessageRecyclerItem(messageId=".length()
                    : value.indexOf("messageId=");
            if (start < 0) return null;

            int end = value.indexOf(", sourceChannelId=", start);
            if (end < 0) end = value.indexOf(',', start);
            if (end < 0) return null;

            String id = value.substring(start, end).trim();
            return id.isEmpty() || "null".equals(id) ? null : id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Styles the original message at Twitch's deleted-message factory boundary.
     * Returning null leaves Twitch's native deleted-message handling untouched.
     */
    public static boolean resolveAccess(boolean original) {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return original;
            return true;
        } catch (Throwable ignored) {
            return original;
        }
    }

    /**
     * Mirrors PurpleTV's hook at ChatMessageSpanFactoryV2's deleted-message factory call:
     * Mod falls through to Twitch's factory with moderator access, while the other modes
     * style the original rich message and skip the factory's deleted placeholder.
     */
    public static Spanned styleDeletedMessageFromFactory(Spanned message) {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get() || message == null || message.length() == 0) {
                return null;
            }
            if (containsDeletedPlaceholder(message)) return null;

            String style = normalizeStyle();
            if ("mod".equals(style)) return null;

            SpannableStringBuilder builder = new SpannableStringBuilder(message);
            int bodyStart = findMessageBodyStart(builder);
            if ("strikethrough".equals(style)) {
                if (builder.getSpans(0, builder.length(), StrikethroughSpan.class).length > 0) {
                    return message;
                }
                if (bodyStart < builder.length()) {
                    builder.setSpan(new StrikethroughSpan(), bodyStart, builder.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if ("grey".equals(style)) {
                for (ForegroundColorSpan span : builder.getSpans(
                        0, builder.length(), ForegroundColorSpan.class)) {
                    builder.removeSpan(span);
                }
                grayInlineDrawables(builder);
                builder.setSpan(new ForegroundColorSpan(Color.GRAY), 0, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new DeletedStyleMarker(), 0, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                return null;
            }
            return SpannedString.valueOf(builder);
        } catch (Throwable failure) {
            Log.e(DIAGNOSTIC_TAG, "could not style original deleted message", failure);
            return null;
        }
    }

    public static boolean isGreyDeletedMessage(CharSequence text) {
        if (!(text instanceof Spanned)) return false;
        Spanned spanned = (Spanned) text;
        return spanned.getSpans(0, spanned.length(), DeletedStyleMarker.class).length > 0;
    }

    /** Apply a per-drawable grayscale filter; unlike mutating the shared bitmap, this is row-local. */
    public static void applyGrayFilter(Drawable drawable) {
        if (drawable == null) return;
        try {
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0f);
            drawable.setColorFilter(new ColorMatrixColorFilter(matrix));
        } catch (Throwable ignored) {
        }
    }

    private static void grayInlineDrawables(Spanned text) {
        try {
            for (DynamicDrawableSpan span : text.getSpans(
                    0, text.length(), DynamicDrawableSpan.class)) {
                applyGrayFilter(span.getDrawable());
            }
        } catch (Throwable ignored) {
        }
    }

    /** Restore the original rich-text body from Twitch's deleted-message span. */
    public static Spanned restoreDeletedMessageFromRow(Spanned message) {
        if (message == null || message.length() == 0) return null;
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return null;
            for (ClickableSpan deletedSpan : message.getSpans(0, message.length(), ClickableSpan.class)) {
                if (!hasSpannedStringField(deletedSpan)) continue;
                int start = message.getSpanStart(deletedSpan), end = message.getSpanEnd(deletedSpan);
                if (start < 0 || end <= start || end > message.length()) continue;
                SpannedString original = findOriginalMessage(deletedSpan);
                if (original == null || original.length() == 0 || original.toString().toLowerCase(java.util.Locale.ROOT).contains("<message deleted>")) continue;
                SpannedString body = stripDuplicateChatterHeader(SpannedString.valueOf(message), deletedSpan, start, original);
                android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder(message);
                builder.replace(start, end, body);
                builder.removeSpan(deletedSpan);
                applyDeletedStyle(builder, start, start + body.length());
                return SpannedString.valueOf(builder);
            }
        } catch (Throwable failure) {
            Log.w(DIAGNOSTIC_TAG, "could not restore original deleted chat message", failure);
        }
        return null;
    }

    private static int findMessageBodyStart(Spanned text) {
        try {
            for (ClickableSpan span : text.getSpans(0, text.length(), ClickableSpan.class)) {
                if (!"ClickableUsernameSpan".equals(span.getClass().getSimpleName())) continue;
                int end = text.getSpanEnd(span);
                if (end < 0 || end > text.length()) continue;
                if (end + 2 <= text.length() && ": ".contentEquals(text.subSequence(end, end + 2))) {
                    return end + 2;
                }
                return end;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void applyDeletedStyle(SpannableStringBuilder text, int start, int end) {
        if (start < 0 || end <= start || end > text.length()) return;
        String style = normalizeStyle();

        if ("strikethrough".equals(style)) {
            start = findMessageBodyStart(text);
            if (start >= end) return;
            for (StrikethroughSpan span : text.getSpans(start, end, StrikethroughSpan.class)) {
                int spanStart = text.getSpanStart(span);
                int spanEnd = text.getSpanEnd(span);
                if (spanStart >= start && spanEnd <= end) text.removeSpan(span);
            }
            text.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("grey".equals(style)) {
            for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class)) {
                text.removeSpan(span);
            }
            grayInlineDrawables(text);
            text.setSpan(new ForegroundColorSpan(Color.GRAY), 0, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (text.getSpans(0, text.length(), DeletedStyleMarker.class).length == 0) {
                text.setSpan(new DeletedStyleMarker(), 0, text.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static String normalizeStyle() {
        try {
            String style = Settings.CHAT_DELETED_MESSAGES_STYLE.get();
            if (style == null) return "mod";
            style = style.trim().toLowerCase(java.util.Locale.ROOT);
            if ("default".equals(style)) return "mod";
            if ("gray".equals(style)) return "grey";
            if ("strikethrough".equals(style) || "grey".equals(style) || "mod".equals(style)) {
                return style;
            }
        } catch (Throwable ignored) {
        }
        return "mod";
    }

    private static boolean hasSpannedStringField(Object candidate) {
        try {
            for (Class<?> type = candidate.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) if (field.getType() == SpannedString.class) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static SpannedString findOriginalMessage(ClickableSpan deletedSpan) {
        try {
            Class<?> type = deletedSpan.getClass();
            while (type != null) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (field.getType() != SpannedString.class) continue;
                    field.setAccessible(true);
                    Object value = field.get(deletedSpan);
                    if (value instanceof SpannedString) {
                        return (SpannedString) value;
                    }
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static SpannedString stripDuplicateChatterHeader(
            SpannedString message,
            ClickableSpan deletedSpan,
            int spanStart,
            SpannedString original
    ) {
        try {
            int lineStart = 0;
            for (int i = spanStart - 1; i >= 0; i--) {
                if (message.charAt(i) == 10) {
                    lineStart = i + 1;
                    break;
                }
            }
            if (spanStart <= lineStart) return original;

            String outerText = message.toString();
            String originalText = original.toString();

            int delimiter = outerText.lastIndexOf(": ", spanStart - 1);
            if (delimiter >= lineStart) {
                int headerEnd = delimiter + 2;
                String fullHeader = outerText.substring(lineStart, headerEnd);
                if (!fullHeader.isEmpty() && originalText.startsWith(fullHeader)) {
                    return deletePrefix(original, fullHeader.length());
                }
            }

            ClickableSpan[] spans =
                    message.getSpans(lineStart, spanStart, ClickableSpan.class);

            ClickableSpan candidate = null;
            int candidateEnd = -1;
            for (ClickableSpan span : spans) {
                if (span == deletedSpan) continue;
                int start = message.getSpanStart(span);
                int end = message.getSpanEnd(span);
                if (start < lineStart || end <= start || end > spanStart) continue;
                if (end <= candidateEnd) continue;

                if (end + 2 <= spanStart
                        && ": ".contentEquals(message.subSequence(end, end + 2))) {
                    candidate = span;
                    candidateEnd = end;
                }
            }

            if (candidate != null) {
                int usernameStart = message.getSpanStart(candidate);
                int usernameEnd = message.getSpanEnd(candidate);
                if (usernameStart >= lineStart
                        && usernameEnd > usernameStart
                        && usernameEnd <= spanStart) {
                    String usernamePrefix =
                            message.subSequence(usernameStart, usernameEnd).toString() + ": ";
                    if (originalText.startsWith(usernamePrefix)) {
                        return deletePrefix(original, usernamePrefix.length());
                    }
                }
            }

            String before = outerText.substring(lineStart, spanStart);
            int previous = before.lastIndexOf(": ");
            if (previous >= 0) {
                String tail = before.substring(previous + 2);
                String tailPrefix = tail + ": ";
                if (!tail.isEmpty() && originalText.startsWith(tailPrefix)) {
                    return deletePrefix(original, tailPrefix.length());
                }
            }
        } catch (Throwable ignored) {
        }

        return original;
    }

    private static SpannedString deletePrefix(SpannedString original, int length) {
        if (length <= 0 || length > original.length()) return original;
        SpannableStringBuilder builder = new SpannableStringBuilder(original);
        builder.delete(0, length);
        return SpannedString.valueOf(builder);
    }
}

