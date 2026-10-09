package app.morphe.extension.reload;

import android.app.Application;
import android.content.SharedPreferences;
import app.morphe.extension.settings.Setting;
import app.morphe.extension.settings.Settings;
import app.morphe.extension.videostats.NativeVideoStatsViews;

public final class ReloadRuntime {
    private static volatile boolean initialized;
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER =
            (preferences, key) -> {
                if (Settings.SHOW_REFRESH_BUTTON.key.equals(key)) refresh();
                if (Settings.SHOW_VIDEO_STATS_BUTTON.key.equals(key)) {
                    NativeVideoStatsViews.refreshPreferences();
                }
            };

    private ReloadRuntime() {}

    public static synchronized void initialize(Application application) {
        if (initialized) return;
        initialized = true;
        Setting.addChangeListener(LISTENER);
        NativeReloadBridge.update(Settings.SHOW_REFRESH_BUTTON.get());
    }

    public static boolean enabled() {
        return Settings.SHOW_REFRESH_BUTTON.get();
    }

    public static void refresh() {
        NativeReloadBridge.update(enabled());
        NativeReloadViews.refreshPreferences();
        NativeVideoStatsViews.refreshPreferences();
    }
}
