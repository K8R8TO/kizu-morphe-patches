package io.github.bakwudo.uyu.extension.channelpoints;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.Objects;

import io.github.bakwudo.uyu.extension.settings.Settings;

public final class AutoClaimChannelPointsPatch {
    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final long RETRY_DELAY_MS = 3_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static String lastClaimId;
    private static long lastClaimTime;

    private static volatile Object activeProvider;
    private static boolean polling;

    private static final Runnable POLL = new Runnable() {
        @Override
        public void run() {
            Object provider = activeProvider;
            if (provider == null || !Settings.AUTO_CLAIM_CHANNEL_POINTS.get()) {
                synchronized (AutoClaimChannelPointsPatch.class) {
                    if (provider == activeProvider) {
                        polling = false;
                    }
                }
                return;
            }

            checkCurrentClaim(provider);

            synchronized (AutoClaimChannelPointsPatch.class) {
                if (!polling || activeProvider != provider) {
                    return;
                }
                MAIN.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    private AutoClaimChannelPointsPatch() {
    }

    public static synchronized boolean shouldClaim(String claimId) {
        if (!Settings.AUTO_CLAIM_CHANNEL_POINTS.get() || claimId == null || claimId.isEmpty()) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (Objects.equals(claimId, lastClaimId) && now - lastClaimTime < RETRY_DELAY_MS) {
            return false;
        }

        lastClaimId = claimId;
        lastClaimTime = now;
        return true;
    }

    /**
     * Called whenever Twitch updates the provider's current CommunityPointsModel.
     * The provider is retained so the poller can read the latest model directly even
     * when Twitch does not emit another model-update callback when the bonus appears.
     */
    public static synchronized void startPolling(Object provider) {
        if (provider == null) {
            return;
        }

        activeProvider = provider;
        if (polling) {
            return;
        }

        polling = true;
        MAIN.removeCallbacks(POLL);
        MAIN.postDelayed(POLL, POLL_INTERVAL_MS);
    }

    /**
     * Called when Twitch creates a new channel-chat connection. This prevents a poller
     * from carrying a stale provider across a stream/channel transition.
     */
    public static synchronized void reset() {
        activeProvider = null;
        polling = false;
        lastClaimId = null;
        lastClaimTime = 0L;
        MAIN.removeCallbacks(POLL);
    }

    private static void checkCurrentClaim(Object provider) {
        try {
            Method method = provider.getClass().getDeclaredMethod(
                    "kizuAutoClaimCurrent", provider.getClass()
            );
            method.setAccessible(true);
            method.invoke(null, provider);
        } catch (Throwable ignored) {
        }
    }
}
