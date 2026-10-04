package app.morphe.extension.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

import io.github.bakwudo.uyu.extension.Utils;

/**
 * Helpers shared by the uyu settings screen and its preferences.
 */
final class SettingsUi {
    private SettingsUi() {
    }

    static int dp(Context context, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    /**
     * Twitch's own page background, so the screen matches light and dark mode.
     */
    static int backgroundColor(Context context) {
        return ThemeSupport.backgroundColor(context);
    }

    static boolean isDark(Context context) {
        return ThemeSupport.isDark(context);
    }

    /**
     * A platform dialog in the light or dark style of Twitch's current theme. Twitch's themes
     * are AppCompat themes, which do not style platform dialogs.
     */
    @SuppressWarnings("deprecation")
    static AlertDialog.Builder dialog(Context context) {
        return new AlertDialog.Builder(context, isDark(context)
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }
}
