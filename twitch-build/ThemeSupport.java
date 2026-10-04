package io.github.bakwudo.uyu.extension.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.Map;

import io.github.bakwudo.uyu.extension.Utils;

/**
 * Shared theme handling for Kizu-owned UI and the Twitch activity surface.
 *
 * Normal mode always follows Twitch's resolved background colors. AMOLED mode keeps that
 * theme selection intact but replaces neutral Twitch background surfaces with pure black,
 * including window chrome and Kizu settings/pickers.
 */
public final class ThemeSupport {
    public static final int AMOLED_BLACK = Color.BLACK;
    private static final int FALLBACK_LIGHT = 0xFFF7F7F8;
    private static final int FALLBACK_DARK = 0xFF0E0E10;
    private static final int FALLBACK_SURFACE_LIGHT = Color.WHITE;
    private static final int FALLBACK_SURFACE_DARK = 0xFF18181B;
    private static final int SECONDARY_ALPHA = 0x99;

    private static final WeakHashMap<View, Drawable> ORIGINAL_BACKGROUNDS = new WeakHashMap<>();
    private static final WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> APPLY_QUEUED = new WeakHashMap<>();

    private ThemeSupport() {}

    public static boolean isAmoled() {
        try {
            return Settings.AMOLED_THEME.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int backgroundColor(Context context) {
        if (isAmoled()) return AMOLED_BLACK;
        return twitchColor(context, "background_body",
                isSystemDark(context) ? FALLBACK_DARK : FALLBACK_LIGHT);
    }

    public static int surfaceColor(Context context) {
        if (isAmoled()) return AMOLED_BLACK;
        return twitchColor(context, "background_base",
                isSystemDark(context) ? FALLBACK_SURFACE_DARK : FALLBACK_SURFACE_LIGHT);
    }

    public static int alternateBackgroundColor(Context context) {
        if (isAmoled()) return AMOLED_BLACK;
        return twitchColor(context, "background_alt", backgroundColor(context));
    }

    public static int primaryTextColor(Context context) {
        if (isAmoled()) return Color.WHITE;
        int resolved = twitchColor(context, "text_base", isDark(context) ? Color.WHITE : Color.BLACK);
        return resolved == 0 ? (isDark(context) ? Color.WHITE : Color.BLACK) : resolved;
    }

    public static int secondaryTextColor(Context context) {
        if (isAmoled()) return 0xFFB3B3B3;
        int resolved = twitchColor(context, "text_alt_2",
                twitchColor(context, "text_alt", isDark(context) ? 0xFFB3B3B3 : 0xFF666666));
        return resolved == 0 ? (isDark(context) ? 0xFFB3B3B3 : 0xFF666666) : resolved;
    }

    public static int dividerColor(Context context) {
        int base = primaryTextColor(context);
        return Color.argb(isDark(context) ? 0x26 : 0x1F,
                Color.red(base), Color.green(base), Color.blue(base));
    }

    public static boolean isDark(Context context) {
        if (isAmoled()) return true;
        int body = twitchColor(context, "background_body",
                isSystemDark(context) ? FALLBACK_DARK : FALLBACK_LIGHT);
        return luminance(body) < 128d;
    }

    public static void apply(Activity activity) {
        if (activity == null || activity.isFinishing() ||
                (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;

        Context context = activity;
        boolean amoled = isAmoled();
        int body = amoled ? AMOLED_BLACK : twitchColor(context, "background_body",
                isSystemDark(context) ? FALLBACK_DARK : FALLBACK_LIGHT);

        try {
            Window window = activity.getWindow();
            window.setStatusBarColor(body);
            window.setNavigationBarColor(body);

            View decor = window.getDecorView();
            decor.setBackgroundColor(body);
            updateSystemBarAppearance(decor, body);

            if (amoled) {
                normalizeNeutralBackgrounds(decor, context);
                watch(decor, activity);
            } else {
                restoreOriginalBackgrounds();
                unwatch(decor);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void applySettingsView(View root) {
        if (root == null) return;
        try {
            Context context = root.getContext();
            root.setBackgroundColor(backgroundColor(context));
            styleSettingsTree(root, context);
            if (isAmoled()) {
                normalizeNeutralBackgrounds(root, context);
            } else {
                restoreOriginalBackgrounds();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void styleSettingsTree(View root, Context context) {
        if (root instanceof TextView) {
            TextView text = (TextView) root;
            int id = text.getId();
            boolean summary = false;
            if (id != View.NO_ID) {
                try {
                    String name = context.getResources().getResourceEntryName(id);
                    summary = name != null && name.toLowerCase(java.util.Locale.ROOT).contains("summary");
                } catch (Throwable ignored) {
                }
            }
            text.setTextColor(summary ? secondaryTextColor(context) : primaryTextColor(context));
        }

        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            styleSettingsTree(child, context);
        }
    }

    private static void normalizeNeutralBackgrounds(View root, Context context) {
        int body = twitchColor(context, "background_body",
                isSystemDark(context) ? FALLBACK_DARK : FALLBACK_LIGHT);
        int surface = twitchColor(context, "background_base",
                isSystemDark(context) ? FALLBACK_SURFACE_DARK : FALLBACK_SURFACE_LIGHT);
        int alternate = twitchColor(context, "background_alt", surface);

        normalize(root, body, surface, alternate);
    }

    private static void normalize(View view, int body, int surface, int alternate) {
        if (view instanceof ViewGroup) {
            Drawable background = view.getBackground();
            Integer color = drawableColor(background);
            if (color != null && isNeutralThemeColor(color, body, surface, alternate)) {
                if (!ORIGINAL_BACKGROUNDS.containsKey(view)) {
                    ORIGINAL_BACKGROUNDS.put(view, background);
                }
                if (color != AMOLED_BLACK) view.setBackgroundColor(AMOLED_BLACK);
            }

            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                normalize(group.getChildAt(i), body, surface, alternate);
            }
        }
    }

    private static boolean isNeutralThemeColor(int color, int body, int surface, int alternate) {
        int opaque = color | 0xFF000000;
        if (opaque == (body | 0xFF000000) ||
                opaque == (surface | 0xFF000000) ||
                opaque == (alternate | 0xFF000000)) return true;

        // Known neutral Twitch/Material surfaces used by 31.3.1. Only neutral grays/blacks
        // are eligible, so accent/button backgrounds are left untouched.
        int r = Color.red(opaque);
        int g = Color.green(opaque);
        int b = Color.blue(opaque);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max - min <= 4 && (max <= 55 || min >= 245);
    }

    private static Integer drawableColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) {
            return ((ColorDrawable) drawable).getColor();
        }
        if (Build.VERSION.SDK_INT >= 29 && drawable instanceof GradientDrawable) {
            try {
                ColorStateList list = ((GradientDrawable) drawable).getColor();
                if (list != null) return list.getDefaultColor();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void restoreOriginalBackgrounds() {
        synchronized (ORIGINAL_BACKGROUNDS) {
            for (Map.Entry<View, Drawable> entry : ORIGINAL_BACKGROUNDS.entrySet()) {
                View view = entry.getKey();
                if (view != null && view.getBackground() instanceof ColorDrawable &&
                        ((ColorDrawable) view.getBackground()).getColor() == AMOLED_BLACK) {
                    try {
                        view.setBackground(entry.getValue());
                    } catch (Throwable ignored) {
                    }
                }
            }
            ORIGINAL_BACKGROUNDS.clear();
        }
    }

    private static int twitchColor(Context context, String name, int fallback) {
        if (context == null) return fallback;
        try {
            int id = Utils.getResourceId(context, name, "color");
            if (id != 0) return context.getColor(id);
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static boolean isSystemDark(Context context) {
        if (context == null) return false;
        try {
            int night = context.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double luminance(int color) {
        return 0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color);
    }

    private static void updateSystemBarAppearance(View decor, int background) {
        int flags = decor.getSystemUiVisibility();
        boolean dark = luminance(background) < 128d;
        if (dark) {
            if (Build.VERSION.SDK_INT >= 23) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private static void watch(View decor, Activity activity) {
        synchronized (LISTENERS) {
            if (LISTENERS.containsKey(decor)) return;

            WeakReference<Activity> ref = new WeakReference<>(activity);
            ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
                if (!isAmoled()) return;
                if (Boolean.TRUE.equals(APPLY_QUEUED.get(decor))) return;
                APPLY_QUEUED.put(decor, Boolean.TRUE);
                decor.postDelayed(() -> {
                    APPLY_QUEUED.remove(decor);
                    Activity current = ref.get();
                    if (current != null) apply(current);
                }, 100L);
            };

            LISTENERS.put(decor, listener);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
    }

    private static void unwatch(View decor) {
        synchronized (LISTENERS) {
            ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(decor);
            if (listener != null) {
                try {
                    decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                } catch (Throwable ignored) {
                }
            }
            APPLY_QUEUED.remove(decor);
        }
    }
}
