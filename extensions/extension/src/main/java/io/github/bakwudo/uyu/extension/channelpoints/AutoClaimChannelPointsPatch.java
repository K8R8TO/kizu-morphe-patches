package io.github.bakwudo.uyu.extension.channelpoints;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import io.github.bakwudo.uyu.extension.settings.Settings;

public final class AutoClaimChannelPointsPatch {
    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final long RETRY_DELAY_MS = 3_000L;
    private static final String CHAT_MODE_METADATA_CLASS =
            "tv.twitch.android.shared.one.chat.pub.ChatModeMetadata";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static String lastClaimId;
    private static long lastClaimTime;

    private static volatile Object activeProvider;
    private static volatile Object activeModel;
    private static boolean polling;

    private static final Runnable POLL = new Runnable() {
        @Override
        public void run() {
            Object provider = activeProvider;
            Object model = activeModel;
            if (provider == null || model == null || !Settings.AUTO_CLAIM_CHANNEL_POINTS.get()) {
                synchronized (AutoClaimChannelPointsPatch.class) {
                    if (provider == activeProvider) {
                        polling = false;
                    }
                }
                return;
            }

            checkCurrentClaim(provider, model);

            synchronized (AutoClaimChannelPointsPatch.class) {
                if (!polling || activeProvider != provider || activeModel != model) {
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
     * Called from the existing CommunityPointsModel update hook. The exact live model object
     * is retained, so polling never has to reflectively search Twitch's provider fields.
     */
    public static synchronized void startPolling(Object provider, Object model) {
        if (provider == null || model == null) {
            return;
        }

        activeProvider = provider;
        activeModel = model;
        if (polling) {
            return;
        }

        polling = true;
        MAIN.removeCallbacks(POLL);
        MAIN.postDelayed(POLL, POLL_INTERVAL_MS);
    }

    private static void checkCurrentClaim(Object provider, Object model) {
        try {
            Method getClaim = model.getClass().getDeclaredMethod("getClaim");
            getClaim.setAccessible(true);
            Object claim = getClaim.invoke(model);
            if (claim == null) {
                return;
            }

            String claimId = findClaimId(claim);
            if (claimId == null || !shouldClaim(claimId)) {
                return;
            }

            Method claimMethod = findClaimMethod(provider.getClass());
            if (claimMethod == null) {
                return;
            }

            claimMethod.setAccessible(true);
            claimMethod.invoke(provider, claimId, null);
        } catch (Throwable ignored) {
        }
    }

    private static String findClaimId(Object claim) throws IllegalAccessException {
        for (Field field : claim.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }

            field.setAccessible(true);
            Object value = field.get(claim);
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        }
        return null;
    }

    private static Method findClaimMethod(Class<?> providerClass) {
        for (Method method : providerClass.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 &&
                    params[0] == String.class &&
                    CHAT_MODE_METADATA_CLASS.equals(params[1].getName()) &&
                    method.getReturnType() == void.class) {
                return method;
            }
        }
        return null;
    }
}