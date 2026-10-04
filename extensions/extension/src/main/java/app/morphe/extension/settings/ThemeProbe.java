package app.morphe.extension.settings;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;
import android.widget.TextView;
import android.app.AlertDialog;
import android.util.TypedValue;
import android.view.View;

public final class ThemeProbe {
    private static final String TAG = "KizuTheme";
    private ThemeProbe() {}

    public static String buildReport(Context context) {
        StringBuilder out = new StringBuilder();
        try {
            Activity activity = findActivity(context);
            Context app = context == null ? null : context.getApplicationContext();
            out.append("user_theme: ").append(userTheme(context)).append("\n");
            out.append("ThemeManager (Kizu context): ").append(night(context)).append("\n");
            out.append("ThemeManager (Activity): ").append(activity == null ? "n/a" : night(activity)).append("\n");
            out.append("ThemeManager (Application): ").append(app == null ? "n/a" : night(app)).append("\n");
            out.append("uiMode (Kizu context): ").append(ui(context)).append("\n");
            out.append("uiMode (Activity): ").append(activity == null ? "n/a" : ui(activity)).append("\n");
            out.append("uiMode (Application): ").append(app == null ? "n/a" : ui(app)).append("\n");
            out.append("isLightTheme (Kizu): ").append(attr(context, android.R.attr.isLightTheme)).append("\n");
            out.append("isLightTheme (Activity): ").append(activity == null ? "n/a" : attr(activity, android.R.attr.isLightTheme)).append("\n");
            out.append("Kizu context: ").append(context == null ? "null" : context.getClass().getName()).append("\n");
            out.append("Activity context: ").append(activity == null ? "n/a" : activity.getClass().getName()).append("\n");
            out.append("Theme object (Kizu): ").append(context == null ? "n/a" : Integer.toHexString(System.identityHashCode(context.getTheme()))).append("\n");
            out.append("Theme object (Activity): ").append(activity == null ? "n/a" : Integer.toHexString(System.identityHashCode(activity.getTheme()))).append("\n");
        } catch (Throwable t) {
            out.append("Probe error: ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        }
        return out.toString();
    }

    public static void show(Context context) {
        if (context == null) return;
        final String report = buildReport(context);
        new AlertDialog.Builder(context)
                .setTitle("Theme Diagnostics")
                .setMessage(report)
                .setPositiveButton("Close", null)
                .setNeutralButton("Copy", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Kizu Theme Diagnostics", report));
                })
                .show();
    }

    public static void report(String where, View view) {
        if (view == null) {
            Log.i(TAG, where + ": view=null");
            return;
        }
        report(where, view.getContext());
    }

    public static void report(String where, Context context) {
        try {
            Activity activity = findActivity(context);
            Context app = context == null ? null : context.getApplicationContext();
            Log.i(TAG, where + " user_theme=" + userTheme(context)
                    + " night(ctx)=" + night(context)
                    + " night(activity)=" + (activity == null ? "n/a" : night(activity))
                    + " night(app)=" + (app == null ? "n/a" : night(app))
                    + " uiMode(ctx)=" + ui(context)
                    + " uiMode(activity)=" + (activity == null ? "n/a" : ui(activity))
                    + " uiMode(app)=" + (app == null ? "n/a" : ui(app))
                    + " isLight(ctx)=" + attr(context, android.R.attr.isLightTheme)
                    + " isLight(activity)=" + (activity == null ? "n/a" : attr(activity, android.R.attr.isLightTheme))
                    + " theme(ctx)=" + Integer.toHexString(System.identityHashCode(context.getTheme()))
                    + " theme(activity)=" + (activity == null ? "n/a" : Integer.toHexString(System.identityHashCode(activity.getTheme())))
                    + " context=" + context.getClass().getName());
        } catch (Throwable t) {
            Log.e(TAG, where + ": probe failed", t);
        }
    }

    private static Activity findActivity(Context c) {
        int guard = 0;
        while (c != null && guard++ < 16) {
            if (c instanceof Activity) return (Activity) c;
            if (c instanceof ContextWrapper) c = ((ContextWrapper) c).getBaseContext();
            else break;
        }
        return null;
    }

    private static String userTheme(Context c) {
        try {
            SharedPreferences p = android.preference.PreferenceManager.getDefaultSharedPreferences(c);
            return p.getString("user_theme", "ABSENT");
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    private static String night(Context c) {
        try {
            Class<?> manager = Class.forName("tv.twitch.android.app.core.ThemeManager");
            java.lang.reflect.Field f = manager.getDeclaredField("Companion");
            f.setAccessible(true);
            Object companion = f.get(null);
            java.lang.reflect.Method m = companion.getClass().getMethod("isNightModeEnabled", Context.class);
            return String.valueOf(m.invoke(companion, c));
        } catch (Throwable t) {
            return "ERR:" + t.getClass().getSimpleName();
        }
    }

    private static String ui(Context c) {
        if (c == null) return "n/a";
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES ? "NIGHT"
                : mode == Configuration.UI_MODE_NIGHT_NO ? "DAY" : "UNDEFINED";
    }

    private static String attr(Context c, int id) {
        TypedValue v = new TypedValue();
        return c.getTheme().resolveAttribute(id, v, true)
                ? (v.type == TypedValue.TYPE_INT_BOOLEAN ? String.valueOf(v.data != 0)
                : "0x" + Integer.toHexString(v.data))
                : "unresolved";
    }
}