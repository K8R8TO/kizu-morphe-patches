package app.morphe.extension.twitch.chat;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.ClickableSpan;

import io.github.bakwudo.uyu.extension.settings.Settings;

import java.util.Locale;

public final class DeletedMessagesSupport {
    private DeletedMessagesSupport() {
    }

    public static boolean resolveAccess(boolean original) {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return original;
            return true;
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static boolean shouldApplyVisualStyle() {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return false;
            String style = normalizeStyle();
            return "strikethrough".equals(style) || "grey".equals(style);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Apply styles only after Twitch finishes rebuilding the returned Spanned row. */
    public static Spanned applyVisualStyleToResult(Spanned formatted) {
        if (formatted == null) return null;
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get() || "mod".equals(normalizeStyle())) {
                return formatted;
            }
            SpannableStringBuilder builder = new SpannableStringBuilder(formatted);
            applyStyle(builder, 0, builder.length());
            return SpannedString.valueOf(builder);
        } catch (Throwable ignored) {
            return formatted;
        }
    }

    private static void applyStyle(SpannableStringBuilder builder, int start, int end) {
        if (start < 0 || end <= start || end > builder.length()) return;

        String style = normalizeStyle();
        if ("strikethrough".equals(style)) {
            builder.setSpan(new StrikethroughSpan(), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("grey".equals(style)) {
            ForegroundColorSpan[] colors =
                    builder.getSpans(start, end, ForegroundColorSpan.class);
            for (ForegroundColorSpan color : colors) builder.removeSpan(color);
            builder.setSpan(new ForegroundColorSpan(Color.GRAY), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static String normalizeStyle() {
        try {
            String style = Settings.CHAT_DELETED_MESSAGES_STYLE.get();
            if (style == null) return "mod";
            style = style.trim().toLowerCase(Locale.ROOT);
            if ("default".equals(style)) return "mod";
            if ("mod".equals(style)
                    || "strikethrough".equals(style)
                    || "grey".equals(style)) {
                return style;
            }
        } catch (Throwable ignored) {
        }
        return "mod";
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
                if (message.charAt(i) == '\n') {
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
