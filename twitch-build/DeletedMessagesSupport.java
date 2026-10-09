package app.morphe.extension.twitch.chat;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;

import io.github.bakwudo.uyu.extension.Utils;
import io.github.bakwudo.uyu.extension.settings.Settings;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeletedMessagesSupport {
    private static final String DIAGNOSTIC_TAG = "KizuDeletedStyle";
    private static final AtomicBoolean ENTRY_TOAST_SHOWN = new AtomicBoolean(false);
    private static final AtomicBoolean OUTCOME_TOAST_SHOWN = new AtomicBoolean(false);

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

    public static SpannedString recoverDeletedMessage(
            SpannedString message,
            Object[] spans,
            Class<?> deletedSpanClass
    ) {
        String rawStyle = readRawStyleForDiagnostics();
        boolean enabled = readEnabledForDiagnostics();
        reportEntryOnce(
                "helper entered; enabled=" + enabled
                        + "; styleSetting=" + rawStyle
                        + "; spanCount=" + (spans == null ? -1 : spans.length)
                        + "; expectedClass=" + className(deletedSpanClass)
        );

        if (message == null || spans == null || spans.length == 0 || deletedSpanClass == null) return null;
        try {
            if (!Settings.CHAT_DELETED_MESSAGES.get()) {
                reportOutcomeOnce("recovery disabled by CHAT_DELETED_MESSAGES setting");
                return null;
            }

            String possibleMismatch = null;
            for (Object candidate : spans) {
                if (candidate == null) continue;
                boolean exactClass = deletedSpanClass.isInstance(candidate);
                if (!exactClass) {
                    if (candidate instanceof ClickableSpan && hasSpannedStringField(candidate)) {
                        possibleMismatch = "possible deleted span class mismatch; actual="
                                + className(candidate.getClass())
                                + "; expected=" + className(deletedSpanClass)
                                + "; styleSetting=" + rawStyle;
                    }
                    continue;
                }
                if (!(candidate instanceof ClickableSpan)) {
                    reportOutcomeOnce("target class matched but is not a ClickableSpan: "
                            + className(candidate.getClass()));
                    continue;
                }

                ClickableSpan deletedSpan = (ClickableSpan) candidate;
                int spanStart = message.getSpanStart(deletedSpan);
                int spanEnd = message.getSpanEnd(deletedSpan);
                if (spanStart < 0 || spanEnd <= spanStart || spanEnd > message.length()) {
                    reportOutcomeOnce("target class matched but range is invalid; start=" + spanStart
                            + "; end=" + spanEnd + "; messageLength=" + message.length());
                    continue;
                }

                SpannedString original = findOriginalMessage(deletedSpan);
                if (original == null || original.length() == 0) {
                    reportOutcomeOnce("target class matched but original SpannedString was not found");
                    continue;
                }

                SpannableStringBuilder builder = new SpannableStringBuilder(message);
                SpannedString recovered = stripDuplicateChatterHeader(
                        message,
                        deletedSpan,
                        spanStart,
                        original
                );
                if (recovered.length() == 0) {
                    reportOutcomeOnce("target class matched; recovered text was empty");
                    continue;
                }

                builder.replace(spanStart, spanEnd, recovered);
                builder.removeSpan(deletedSpan);

                int recoveredEnd = spanStart + recovered.length();
                String normalizedStyle = normalizeStyle();
                applyStyle(builder, spanStart, recoveredEnd);
                SpannedString result = SpannedString.valueOf(builder);
                int checkEnd = Math.min(result.length(), recoveredEnd);
                int strikeCount = checkEnd > spanStart
                        ? result.getSpans(spanStart, checkEnd, StrikethroughSpan.class).length : 0;
                int greyCount = checkEnd > spanStart
                        ? result.getSpans(spanStart, checkEnd, ForegroundColorSpan.class).length : 0;
                reportOutcomeOnce(
                        "recovered; style=" + normalizedStyle
                                + "; range=" + spanStart + "-" + recoveredEnd
                                + "; resultStrikethroughSpans=" + strikeCount
                                + "; resultForegroundSpans=" + greyCount
                );
                return result;
            }

            if (possibleMismatch != null) {
                reportOutcomeOnce(possibleMismatch);
            }
        } catch (Throwable failure) {
            Log.e(DIAGNOSTIC_TAG, "exception in recoverDeletedMessage", failure);
            reportOutcomeOnce("recovery threw " + failure.getClass().getSimpleName()
                    + ": " + String.valueOf(failure.getMessage()));
        }
        return null;
    }

    private static boolean hasSpannedStringField(Object candidate) {
        try {
            Class<?> type = candidate.getClass();
            while (type != null) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (field.getType() == SpannedString.class) return true;
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String className(Class<?> type) {
        return type == null ? "null" : type.getName();
    }

    private static String readRawStyleForDiagnostics() {
        try {
            String value = Settings.CHAT_DELETED_MESSAGES_STYLE.get();
            return value == null ? "null" : value;
        } catch (Throwable failure) {
            return "<error:" + failure.getClass().getSimpleName() + ">";
        }
    }

    private static boolean readEnabledForDiagnostics() {
        try {
            return Settings.CHAT_DELETED_MESSAGES.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void reportEntryOnce(String message) {
        if (!ENTRY_TOAST_SHOWN.compareAndSet(false, true)) return;
        Log.i(DIAGNOSTIC_TAG, "ENTRY: " + message);
        showDiagnosticToast(summarizeEntry(message));
    }

    private static void reportOutcomeOnce(String message) {
        if (!OUTCOME_TOAST_SHOWN.compareAndSet(false, true)) return;
        Log.i(DIAGNOSTIC_TAG, "OUTCOME: " + message);
        showDiagnosticToast(summarizeOutcome(message));
    }

    // Keep on-screen diagnostics brief enough to read. Full details remain in Logcat.
    private static String summarizeEntry(String message) {
        String enabled = valueAfter(message, "enabled=", ";");
        String style = valueAfter(message, "styleSetting=", ";");
        String count = valueAfter(message, "spanCount=", ";");
        return "Kizu D1: on=" + ("true".equals(enabled) ? "1" : "false".equals(enabled) ? "0" : "?")
                + " style=" + compactStyle(style)
                + " spans=" + (count == null ? "?" : count);
    }

    private static String summarizeOutcome(String message) {
        if (message.startsWith("recovered;")) {
            String style = valueAfter(message, "style=", ";");
            String strike = valueAfter(message, "resultStrikethroughSpans=", ";");
            String grey = valueAfter(message, "resultForegroundSpans=", null);
            return "Kizu D2: OK style=" + compactStyle(style)
                    + " strike=" + (strike == null ? "?" : strike)
                    + " grey=" + (grey == null ? "?" : grey);
        }
        if (message.startsWith("possible deleted span class mismatch")) return "Kizu D2: CLASS MISMATCH";
        if (message.startsWith("target class matched but range is invalid")) return "Kizu D2: BAD RANGE";
        if (message.startsWith("target class matched but original SpannedString was not found")) return "Kizu D2: NO ORIGINAL";
        if (message.startsWith("target class matched; recovered text was empty")) return "Kizu D2: EMPTY TEXT";
        if (message.startsWith("recovery disabled")) return "Kizu D2: FEATURE OFF";
        if (message.startsWith("target class matched but is not a ClickableSpan")) return "Kizu D2: BAD SPAN";
        if (message.startsWith("recovery threw")) return "Kizu D2: ERROR";
        return "Kizu D2: NO MATCH";
    }

    private static String compactStyle(String style) {
        if (style == null) return "?";
        String value = style.trim().toLowerCase(Locale.ROOT);
        if ("strikethrough".equals(value)) return "st";
        if ("grey".equals(value)) return "gy";
        if ("mod".equals(value) || "default".equals(value)) return "mod";
        return "?";
    }

    private static String valueAfter(String source, String key, String terminator) {
        int start = source.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = terminator == null ? source.length() : source.indexOf(terminator, start);
        if (end < 0) end = source.length();
        return source.substring(start, end).trim();
    }

    private static void showDiagnosticToast(String message) {
        try {
            Context context = Utils.getContext();
            if (context == null) return;
            new Handler(Looper.getMainLooper()).post(
                    () -> Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            );
        } catch (Throwable failure) {
            Log.w(DIAGNOSTIC_TAG, "could not show diagnostic toast", failure);
        }
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
            if ("strikethrough".equals(style)
                    || "grey".equals(style)
                    || "mod".equals(style)) return style;
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
