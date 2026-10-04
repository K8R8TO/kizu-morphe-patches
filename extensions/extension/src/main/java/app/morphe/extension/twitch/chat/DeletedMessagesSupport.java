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
import java.util.Locale;

/**
 * Applies PurpleTV-compatible deleted-message presentation without touching normal chat rows.
 */
public final class DeletedMessagesSupport {
    private DeletedMessagesSupport() {
    }

    public static boolean useEnhancedStyle() {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return false;
            return !"default".equals(normalizeStyle());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean resolveAccess(boolean original) {
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) return original;
            return "default".equals(normalizeStyle()) ? original : true;
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
            return message;
        } catch (Throwable ignored) {
            return message;
        }
    }

    private static String normalizeStyle() {
        String style = Settings.CHAT_DELETED_MESSAGES_STYLE.get();
        if (style == null) return "strikethrough";
        style = style.trim().toLowerCase(Locale.ROOT);
        if ("default".equals(style)
                || "mod".equals(style)
                || "strikethrough".equals(style)
                || "grey".equals(style)) {
            return style;
        }
        return "strikethrough";
    }

    private static Spanned createStrikethrough(Spanned message) {
        SpannableStringBuilder builder = withoutDeletedSpan(message);
        int start = findMessageStart(builder);
        if (start < builder.length()) {
            if (builder.getSpans(start, builder.length(), StrikethroughSpan.class).length == 0) {
                builder.setSpan(
                        new StrikethroughSpan(),
                        start,
                        builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
        return SpannedString.valueOf(builder);
    }

    private static Spanned createGrey(Spanned message) {
        SpannableStringBuilder builder = withoutDeletedSpan(message);
        ForegroundColorSpan[] colors =
                builder.getSpans(0, builder.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : colors) {
            builder.removeSpan(span);
        }
        if (builder.length() > 0) {
            builder.setSpan(
                    new ForegroundColorSpan(Color.GRAY),
                    0,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return SpannedString.valueOf(builder);
    }

    private static SpannableStringBuilder withoutDeletedSpan(Spanned message) {
        SpannableStringBuilder builder = new SpannableStringBuilder(message);
        ClickableSpan[] clickable =
                builder.getSpans(0, builder.length(), ClickableSpan.class);
        for (ClickableSpan span : clickable) {
            if (looksLikeDeletedSpan(span)) {
                builder.removeSpan(span);
            }
        }
        return builder;
    }

    private static boolean looksLikeDeletedSpan(Object span) {
        if (span == null) return false;
        for (Class<?> current = span.getClass();
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                for (Field field : current.getDeclaredFields()) {
                    if (field.getType() == SpannedString.class) return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static int findMessageStart(Spanned message) {
        try {
            Class<?> usernameClass = Class.forName(
                    "tv.twitch.android.shared.chat.messages.span.ClickableUsernameSpan"
            );
            Object[] spans = message.getSpans(0, message.length(), usernameClass);
            if (spans != null && spans.length > 0) {
                int end = message.getSpanEnd(spans[0]);
                int after = end + 2;
                if (after <= message.length()
                        && ": ".contentEquals(message.subSequence(end, after))) {
                    return after;
                }
                return end;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}
