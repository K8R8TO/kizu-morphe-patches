package app.morphe.extension.twitch.chat;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;

import io.github.bakwudo.uyu.extension.settings.Settings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies selectable deleted-message presentation.
 *
 * Twitch's deleted-message span stores the original message in a private
 * SpannedString field while displaying "<message deleted>". For the Mod style
 * we leave Twitch's native clickable spoiler untouched. For the visual styles
 * we replace that placeholder with the stored original text first, then style
 * only the recovered message range.
 */
public final class DeletedMessagesSupport {
    private DeletedMessagesSupport() {
    }

    public static boolean useEnhancedStyle() {
        try {
            return Settings.CHAT_DELETED_MESSAGES.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean resolveAccess(boolean original) {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return original;
            return true;
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static Spanned format(Spanned message) {
        try {
            if (message == null || !Settings.CHAT_DELETED_MESSAGES.get()) return message;

            String style = normalizeStyle();
            if ("strikethrough".equals(style)) {
                return createStrikethrough(message);
            }
            if ("grey".equals(style)) {
                return createGrey(message);
            }

            // Mod is Twitch's native recovered-message behaviour. Keep it exactly as-is.
            return message;
        } catch (Throwable ignored) {
            return message;
        }
    }

    private static String normalizeStyle() {
        String style = Settings.CHAT_DELETED_MESSAGES_STYLE.get();
        if (style == null) return "mod";
        style = style.trim().toLowerCase(Locale.ROOT);

        // "default" was exposed by an earlier build and is intentionally kept as
        // a compatibility alias for Mod, which has the same behaviour.
        if ("default".equals(style)) return "mod";
        if ("mod".equals(style)
                || "strikethrough".equals(style)
                || "grey".equals(style)) {
            return style;
        }
        return "mod";
    }

    private static Spanned createStrikethrough(Spanned message) {
        SpannableStringBuilder builder = new SpannableStringBuilder(message);
        List<int[]> ranges = restoreOriginalMessages(builder);
        if (ranges.isEmpty()) return message;

        for (int[] range : ranges) {
            if (range[0] < range[1]) {
                builder.setSpan(
                        new StrikethroughSpan(),
                        range[0],
                        range[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
        return SpannedString.valueOf(builder);
    }

    private static Spanned createGrey(Spanned message) {
        SpannableStringBuilder builder = new SpannableStringBuilder(message);
        List<int[]> ranges = restoreOriginalMessages(builder);
        if (ranges.isEmpty()) return message;

        for (int[] range : ranges) {
            if (range[0] < range[1]) {
                builder.setSpan(
                        new ForegroundColorSpan(Color.GRAY),
                        range[0],
                        range[1],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
        return SpannedString.valueOf(builder);
    }

    private static List<int[]> restoreOriginalMessages(SpannableStringBuilder builder) {
        List<DeletedSpanData> candidates = new ArrayList<>();

        ClickableSpan[] clickable =
                builder.getSpans(0, builder.length(), ClickableSpan.class);
        for (ClickableSpan span : clickable) {
            Field originalField = findOriginalMessageField(span);
            if (originalField == null) continue;

            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            if (start < 0 || end <= start) continue;

            try {
                originalField.setAccessible(true);
                Object value = originalField.get(span);
                if (!(value instanceof SpannedString)) continue;

                SpannedString original = (SpannedString) value;
                if (original.length() == 0) continue;

                // The stored original can already contain the chatter prefix.
                // The formatter result also keeps that prefix before the deleted
                // span, so inserting it verbatim would produce "kz: kz: message".
                original = stripDuplicatePrefix(builder, start, original);
                if (original.length() == 0) continue;

                candidates.add(new DeletedSpanData(span, start, end, original));
            } catch (Throwable ignored) {
            }
        }

        // Replace from right to left so multiple deleted spans cannot invalidate
        // the character offsets we collected above.
        candidates.sort((left, right) -> Integer.compare(right.start, left.start));

        List<int[]> ranges = new ArrayList<>();
        for (DeletedSpanData candidate : candidates) {
            try {
                int start = candidate.start;
                int end = candidate.end;
                builder.replace(start, end, candidate.original);
                builder.removeSpan(candidate.span);
                ranges.add(new int[]{start, start + candidate.original.length()});
            } catch (Throwable ignored) {
            }
        }

        ranges.sort((left, right) -> Integer.compare(left[0], right[0]));
        return ranges;
    }

    private static SpannedString stripDuplicatePrefix(
            SpannableStringBuilder builder,
            int spanStart,
            SpannedString original
    ) {
        try {
            int lineStart = 0;
            for (int i = spanStart - 1; i >= 0; i--) {
                if (builder.charAt(i) == '\n') {
                    lineStart = i + 1;
                    break;
                }
            }

            if (spanStart <= lineStart) return original;

            String before = builder.subSequence(lineStart, spanStart).toString();
            String originalText = original.toString().trim();
            if (before.isEmpty() || originalText.isEmpty()) return original;

            // Twitch can include accessibility/badge text in the stored original,
            // e.g. "predictions rvxuf: message", while the visible formatter already
            // renders the badge and "rvxuf: " before the deleted span.
            //
            // Recover the visible username from the text immediately before the span:
            // "... rvxuf: ". Then remove everything through that exact username and
            // delimiter from the stored original. This prevents badge/role text and
            // the username from being duplicated in visual styles.
            int delimiter = before.lastIndexOf(": ");
            if (delimiter < 0) return original;

            String visiblePrefix = before.substring(0, delimiter).trim();
            if (visiblePrefix.isEmpty()) return original;

            int usernameStart = visiblePrefix.lastIndexOf(' ');
            String username = usernameStart >= 0
                    ? visiblePrefix.substring(usernameStart + 1)
                    : visiblePrefix;

            if (username.isEmpty()) return original;

            String lowerOriginal = originalText.toLowerCase(Locale.ROOT);
            String lowerUsername = username.toLowerCase(Locale.ROOT);

            int usernameIndex = lowerOriginal.indexOf(lowerUsername);
            while (usernameIndex >= 0) {
                int afterUsername = usernameIndex + username.length();
                if (afterUsername < originalText.length()) {
                    int colon = afterUsername;
                    while (colon < originalText.length()
                            && Character.isWhitespace(originalText.charAt(colon))) {
                        colon++;
                    }
                    if (colon < originalText.length() && originalText.charAt(colon) == ':') {
                        colon++;
                        while (colon < originalText.length()
                                && Character.isWhitespace(originalText.charAt(colon))) {
                            colon++;
                        }

                        SpannableStringBuilder cleaned = new SpannableStringBuilder(original);
                        cleaned.delete(0, colon);
                        return SpannedString.valueOf(cleaned);
                    }
                }

                usernameIndex = lowerOriginal.indexOf(lowerUsername, usernameIndex + 1);
            }
        } catch (Throwable ignored) {
        }
        return original;
    }

    private static Field findOriginalMessageField(Object span) {
        if (span == null) return null;

        for (Class<?> current = span.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                for (Field field : current.getDeclaredFields()) {
                    if (field.getType() == SpannedString.class) {
                        return field;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final class DeletedSpanData {
        final ClickableSpan span;
        final int start;
        final int end;
        final SpannedString original;

        DeletedSpanData(ClickableSpan span, int start, int end, SpannedString original) {
            this.span = span;
            this.start = start;
            this.end = end;
            this.original = original;
        }
    }
}
