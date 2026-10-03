package io.github.bakwudo.uyu.extension.channelpoints;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Objects;
import io.github.bakwudo.uyu.extension.settings.Settings;

public final class AutoClaimChannelPointsPatch {
    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final long RETRY_DELAY_MS = 3_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String lastClaimId;
    private static long lastClaimTime;
    private static volatile Object activeProvider;
    private static volatile String pendingClaimId;
    private static boolean polling;

    private static final Runnable POLL = new Runnable() {
        @Override public void run() {
            Object provider = activeProvider;
            if (provider == null || !Settings.AUTO_CLAIM_CHANNEL_POINTS.get()) {
                synchronized (AutoClaimChannelPointsPatch.class) { polling = false; }
                return;
            }
            try { invokeGeneratedPoll(provider); } catch (Throwable ignored) {}
            synchronized (AutoClaimChannelPointsPatch.class) {
                if (polling && activeProvider == provider) {
                    MAIN.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        }
    };

    private AutoClaimChannelPointsPatch() {}

    public static synchronized boolean shouldClaim(String claimId) {
        if (!Settings.AUTO_CLAIM_CHANNEL_POINTS.get() || claimId == null || claimId.isEmpty()) return false;
        long now = SystemClock.elapsedRealtime();
        if (Objects.equals(claimId, lastClaimId) && now - lastClaimTime < RETRY_DELAY_MS) return false;
        lastClaimId = claimId;
        lastClaimTime = now;
        return true;
    }

    public static synchronized boolean retryAllowed(String claimId) {
        return Settings.AUTO_CLAIM_CHANNEL_POINTS.get() && claimId != null && !claimId.isEmpty();
    }

    public static synchronized void startPolling(Object provider) {
        if (provider == null || !Settings.AUTO_CLAIM_CHANNEL_POINTS.get()) return;
        activeProvider = provider;
        pendingClaimId = null;
        if (polling) return;
        polling = true;
        MAIN.removeCallbacks(POLL);
        MAIN.post(POLL);
    }

    public static synchronized void startPolling(Object provider, String claimId) {
        startPolling(provider);
    }

    public static synchronized void stopPolling() {
        activeProvider = null;
        pendingClaimId = null;
        polling = false;
        MAIN.removeCallbacks(POLL);
    }

    private static void invokeGeneratedPoll(Object provider) throws Exception {
        Class<?> providerClass = provider.getClass();
        java.lang.reflect.Method method = providerClass.getMethod("kizuPollClaim", providerClass);
        method.invoke(null, provider);
    }

    private static void invokeGeneratedRetry(Object provider, String claimId) throws Exception {
        Class<?> providerClass = provider.getClass();
        java.lang.reflect.Method method = providerClass.getMethod("kizuRetryClaim", providerClass, String.class);
        method.invoke(null, provider, claimId);
    }
}
