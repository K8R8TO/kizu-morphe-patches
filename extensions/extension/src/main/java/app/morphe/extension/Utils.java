package app.morphe.extension;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.WeakHashMap;

import app.morphe.extension.twitch.emotes.EmoteSupport;
import app.morphe.extension.twitch.emotes.EmotePickerBridge;
import io.github.bakwudo.uyu.extension.settings.Settings;

public final class Utils {
    private static final String TAG = "kizu";
    private static final long CLAIM_POLL_INTERVAL_MS = 3_000L;

    @SuppressLint("StaticFieldLeak")
    private static volatile Context context;
    private static volatile Activity currentActivity;
    private static volatile Application registeredApplication;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Long> CLAIM_LAST_CLICK = new WeakHashMap<>();
    private static volatile boolean claimWatcherStarted;

    private static final Runnable CLAIM_WATCHER = new Runnable() {
        @Override public void run() {
            try {
                if (Settings.AUTO_CLAIM_CHANNEL_POINTS.get()) {
                    Activity activity = currentActivity;
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                        scanForClaimButton(activity.getWindow().getDecorView());
                    }
                } else {
                    synchronized (CLAIM_LAST_CLICK) {
                        CLAIM_LAST_CLICK.clear();
                    }
                }
            } catch (Throwable ignored) {
            }

            MAIN.postDelayed(this, CLAIM_POLL_INTERVAL_MS);
        }
    };

    private static final Application.ActivityLifecycleCallbacks ACTIVITY_CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {}

                @Override public void onActivityStarted(Activity activity) {
                    currentActivity = activity;
                    try { EmotePickerBridge.ensureComposerButton(); } catch (Throwable ignored) {}
                }

                @Override public void onActivityResumed(Activity activity) {
                    currentActivity = activity;
                    try { EmotePickerBridge.ensureComposerButton(); } catch (Throwable ignored) {}
                }

                @Override public void onActivityPaused(Activity activity) {}

                @Override public void onActivityStopped(Activity activity) {}

                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                @Override public void onActivityDestroyed(Activity activity) {
                    if (currentActivity == activity) currentActivity = null;
                }
            };

    private Utils() {}

    public static void setContext(Context appContext) {
        context = appContext;
        io.github.bakwudo.uyu.extension.Utils.setContext(appContext);
        EmoteSupport.init(appContext);

        if (!claimWatcherStarted) {
            claimWatcherStarted = true;
            MAIN.removeCallbacks(CLAIM_WATCHER);
            MAIN.post(CLAIM_WATCHER);
        }

        try {
            Context applicationContext = appContext == null ? null : appContext.getApplicationContext();
            if (applicationContext instanceof Application) {
                Application application = (Application) applicationContext;
                if (registeredApplication != application) {
                    if (registeredApplication != null) {
                        registeredApplication.unregisterActivityLifecycleCallbacks(ACTIVITY_CALLBACKS);
                    }
                    registeredApplication = application;
                    application.registerActivityLifecycleCallbacks(ACTIVITY_CALLBACKS);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Scans only the currently visible Twitch Activity and presses Twitch's own visible
     * channel-points claim control. This does not modify CommunityPointsModel or call a
     * separate claim API, so the Twitch chat/model lifecycle stays untouched.
     */
    private static void scanForClaimButton(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE || !root.isShown()) return;

        if (isClaimControl(root)) {
            boolean eligible = root.isEnabled() && root.isClickable();
            if (eligible) {
                long now = SystemClock.elapsedRealtime();
                boolean shouldClick;
                synchronized (CLAIM_LAST_CLICK) {
                    Long previous = CLAIM_LAST_CLICK.get(root);
                    shouldClick = previous == null || now - previous >= CLAIM_POLL_INTERVAL_MS;
                    if (shouldClick) {
                        CLAIM_LAST_CLICK.put(root, now);
                    }
                }

                if (shouldClick) {
                    try {
                        if (root.performClick()) {
                            Log.d(TAG, "auto-claimed visible channel-points bonus");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                synchronized (CLAIM_LAST_CLICK) {
                    CLAIM_LAST_CLICK.remove(root);
                }
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanForClaimButton(group.getChildAt(i));
            }
        }
    }

    private static boolean isClaimControl(View view) {
        CharSequence text = null;
        CharSequence description = view.getContentDescription();

        if (view instanceof android.widget.TextView) {
            text = ((android.widget.TextView) view).getText();
        }

        String value = ((text == null ? "" : text.toString()) + " " +
                (description == null ? "" : description.toString()))
                .toLowerCase(java.util.Locale.ROOT);

        return value.contains("claim") &&
                (value.contains("bonus") || value.contains("channel point"));
    }

    public static Context getContext() {
        return context;
    }

    /** Returns the currently active Twitch Activity, even when the extension only has an application context. */
    public static Activity getCurrentActivity() {
        Activity activity = currentActivity;
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) return activity;
        return findActivity(context);
    }

    @SuppressLint("DiscouragedApi")
    public static int getResourceId(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    public static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity activity) return activity;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static void logInfo(String message) {
        Log.i(TAG, message);
    }

    public static void logError(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }
}
