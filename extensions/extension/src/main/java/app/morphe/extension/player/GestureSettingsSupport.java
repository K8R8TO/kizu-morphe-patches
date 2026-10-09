package app.morphe.extension.player;

import android.content.res.Configuration;
import android.view.View;

import app.morphe.extension.settings.Settings;

/** Runtime guard for Twitch's parent-level landscape swipe-to-portrait handler. */
public final class GestureSettingsSupport {
    private GestureSettingsSupport() {
    }

    /**
     * Return true only when collapse prevention is enabled and this Twitch container is in
     * landscape. Called per touch event so toggling the Kizu setting does not require a rebuild.
     */
    public static boolean shouldSuppressNativeLandscapeSwipe(View container) {
        if (container == null) return false;
        try {
            return Settings.DISABLE_LANDSCAPE_SWIPE_TO_PORTRAIT.get()
                    && container.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
