package io.github.bakwudo.uyu.extension.channelpoints;

import android.os.SystemClock;

import java.util.Objects;

import io.github.bakwudo.uyu.extension.settings.Settings;

public final class AutoClaimChannelPointsPatch {
    private static final long RETRY_DELAY_MS = 30_000L;

    private static String lastClaimId;
    private static long lastClaimTime;

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
}
