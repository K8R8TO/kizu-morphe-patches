package app.morphe.extension.twitch.chat;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;

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

    /**
     * PurpleTV-style handling: apply the selected appearance to the original Spanned message
     * at the deletion factory call site. Returning null preserves Twitch's native result.
     */
    public static Spanned styleDeletedMessage(Spanned message) {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get() || message == null) return null;

            String style = normalizeStyle();
            if ("mod".equals(style)) return null;
            if (message.length() == 0) return message;

            SpannableStringBuilder builder = new SpannableStringBuilder(message);
            if ("grey".equals(style)) {
                ForegroundColorSpan[] colors =
                        builder.getSpans(0, builder.length(), ForegroundColorSpan.class);
                for (ForegroundColorSpan color : colors) {
                    if (color.getForegroundColor() == Color.GRAY) return message;
                }
                for (ForegroundColorSpan color : colors) builder.removeSpan(color);
                builder.setSpan(new ForegroundColorSpan(Color.GRAY), 0, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("strikethrough".equals(style)) {
                StrikethroughSpan[] existing =
                        builder.getSpans(0, builder.length(), StrikethroughSpan.class);
                if (existing.length > 0) return message;

                int start = 0;
                ClickableSpan[] spans =
                        builder.getSpans(0, builder.length(), ClickableSpan.class);
                for (ClickableSpan span : spans) {
                    if (!"ClickableUsernameSpan".equals(span.getClass().getSimpleName())) continue;
                    int end = builder.getSpanEnd(span);
                    if (end <= 0 || end > builder.length()) continue;
                    start = end;
                    if (start + 2 <= builder.length()
                            && ": ".contentEquals(builder.subSequence(start, start + 2))) {
                        start += 2;
                    }
                    break;
                }
                if (start < builder.length()) {
                    builder.setSpan(new StrikethroughSpan(), start, builder.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return SpannedString.valueOf(builder);
        } catch (Throwable failure) {
            android.util.Log.e("KizuDeletedStyle", "could not style original deleted message", failure);
            return null;
        }
    }

    private static String normalizeStyle() {
        try {
            String style = Settings.CHAT_DELETED_MESSAGES_STYLE.get();
            if (style == null) return "mod";
            style = style.trim().toLowerCase(Locale.ROOT);
            if ("strikethrough".equals(style) || "grey".equals(style) || "mod".equals(style)) {
                return style;
            }
            if ("default".equals(style)) return "mod";
        } catch (Throwable ignored) {
        }
        return "mod";
    }
}
