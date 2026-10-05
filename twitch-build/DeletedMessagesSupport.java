package app.morphe.extension.twitch.chat;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;

import io.github.bakwudo.uyu.extension.settings.Settings;

import java.util.Locale;

/**
 * Early deleted-message recovery support for Twitch 31.3.1.
 *
 * Twitch stores the real deleted message as a SpannedString inside its
 * ClickableSpan. The bytecode patch substitutes that stored message before
 * Twitch's placeholder formatter runs. This class only decides whether recovery
 * is enabled and applies the requested visual style to the recovered text.
 */
public final class DeletedMessagesSupport {
    private DeletedMessagesSupport() {
    }

    public static boolean shouldRecover() {
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

    /**
     * Called on Twitch's stored original SpannedString before Twitch builds its
     * deleted-message placeholder. Returning the same object for Mod/default
     * keeps Twitch's native recovered-message presentation.
     */
    public static SpannedString prepareRecovered(SpannedString original) {
        try {
            if (original == null || !Settings.CHAT_DELETED_MESSAGES.get()) return original;

            String style = normalizeStyle();
            if ("strikethrough".equals(style)) {
                SpannableStringBuilder builder = new SpannableStringBuilder(original);
                if (builder.length() > 0) {
                    builder.setSpan(
                            new StrikethroughSpan(),
                            0,
                            builder.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                return SpannedString.valueOf(builder);
            }

            if ("grey".equals(style)) {
                SpannableStringBuilder builder = new SpannableStringBuilder(original);
                if (builder.length() > 0) {
                    ForegroundColorSpan[] colors =
                            builder.getSpans(0, builder.length(), ForegroundColorSpan.class);
                    for (ForegroundColorSpan color : colors) {
                        builder.removeSpan(color);
                    }
                    builder.setSpan(
                            new ForegroundColorSpan(Color.GRAY),
                            0,
                            builder.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                return SpannedString.valueOf(builder);
            }

            // Mod/default: Twitch's own formatter receives the original message.
            return original;
        } catch (Throwable ignored) {
            return original;
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
}
