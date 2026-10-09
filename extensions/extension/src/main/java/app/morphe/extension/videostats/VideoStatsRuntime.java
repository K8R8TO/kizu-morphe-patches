package app.morphe.extension.videostats;

import android.util.Log;
import android.view.View;
import app.morphe.extension.settings.Settings;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Locale;

/** Reads the same VideoStats model and factory used by Twitch's own video-statistics UI. */
public final class VideoStatsRuntime {
    private static final String TAG = "KizuVideoStats";
    private static volatile WeakReference<Object> playerContext = new WeakReference<>(null);
    private static volatile Object latestStats;
    private static volatile boolean loggedFailure;

    private VideoStatsRuntime() {}

    public static void bindPlayerContext(Object candidate) {
        if (candidate == null) return;
        Class<?> type = candidate.getClass();
        while (type != null) {
            if ("sl2".equals(type.getName())) {
                Object previous = playerContext.get();
                if (previous != candidate) {
                    latestStats = null;
                    loggedFailure = false;
                }
                playerContext = new WeakReference<>(candidate);
                return;
            }
            type = type.getSuperclass();
        }
    }

    public static boolean enabled() {
        try {
            return Settings.SHOW_VIDEO_STATS_BUTTON.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Ask Twitch's own factory for a fresh model. This runs only while the custom stats panel
     * is open, so the feature does not poll the player in the background.
     */
    public static boolean refreshFromPlayer(View displayView) {
        Object controller = playerContext.get();
        if (controller == null || displayView == null) return latestStats != null;
        try {
            Object player = invokeNoArgs(controller, "x2");
            Object presenter = invokeNoArgs(controller, "R0");
            Object quality = invokeNoArgs(controller, "s1");
            if (player == null || presenter == null) return latestStats != null;

            ClassLoader loader = controller.getClass().getClassLoader();
            Class<?> playerType = Class.forName("ska", true, loader);
            Class<?> presenterType = Class.forName("lvt", true, loader);
            if (!playerType.isInstance(player) || !presenterType.isInstance(presenter)) {
                return latestStats != null;
            }

            Class<?> factoryType = Class.forName("u480", true, loader);
            Method factory = factoryType.getDeclaredMethod(
                    "a", playerType, presenterType, View.class, String.class);
            factory.setAccessible(true);
            Object fresh = factory.invoke(null, player, presenter, displayView,
                    quality == null ? "" : String.valueOf(quality));
            if (fresh != null) {
                latestStats = fresh;
                loggedFailure = false;
                return true;
            }
        } catch (Throwable error) {
            if (!loggedFailure) {
                loggedFailure = true;
                Log.w(TAG, "Twitch's VideoStats factory is not ready yet.", error);
            }
        }
        return latestStats != null;
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Object read(Object stats, String getter) {
        if (stats == null) return null;
        try {
            Method method = stats.getClass().getMethod(getter);
            method.setAccessible(true);
            return method.invoke(stats);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String text(Object stats, String getter) {
        Object value = read(stats, getter);
        if (value == null) return "Unknown";
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? "Unknown" : result;
    }

    private static long number(Object stats, String getter) {
        Object value = read(stats, getter);
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }

    public static String snapshot() {
        Object stats = latestStats;
        if (stats == null) {
            return "Waiting for playback statistics…\n\nStart a stream and reopen this panel.";
        }

        long bitrate = number(stats, "getBitrateEstimate");
        String bitrateText = bitrate < 0 ? "Unknown"
                : String.format(Locale.US, "%.2f Mbps", bitrate / 1_000_000.0d);
        long buffer = number(stats, "getBufferSize");
        String bufferText = buffer < 0 ? "Unknown" : buffer + " sec";
        long latency = number(stats, "getBroadcasterToViewerLatency");
        String latencyText = latency < 0 ? "Unknown" : Long.toString(latency);
        long dropped = number(stats, "getDroppedFrames");

        return "Quality: " + text(stats, "getSelectedQuality")
                + "\nResolution: " + text(stats, "getVideoResolution")
                + "\nDisplay Resolution: " + text(stats, "getDisplayResolution")
                + "\nBitrate: " + bitrateText
                + "\nBuffer: " + bufferText
                + "\nLatency: " + latencyText
                + "\nDropped Frames: " + (dropped < 0 ? "Unknown" : Long.toString(dropped))
                + "\nCodecs: " + text(stats, "getCodecs")
                + "\nProtocol: " + text(stats, "getProtocol");
    }
}
