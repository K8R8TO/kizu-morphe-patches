package app.morphe.extension;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import app.morphe.extension.twitch.emotes.EmoteSupport;

public final class Utils {
    private static final String TAG = "kizu";
    @SuppressLint("StaticFieldLeak")
    private static volatile Context context;
    private static volatile Activity currentActivity;
    private static volatile Application registeredApplication;
    private static volatile String lastThemeActivityClass;
    private static volatile int lastThemeActivityNight = -1;
    private static volatile int lastSystemNight = -1;
    private static volatile boolean themeRepairScheduled;

    private static final Application.ActivityLifecycleCallbacks ACTIVITY_CALLBACKS =
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {}
                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityResumed(Activity activity) {
                    currentActivity = activity;
                    stabilizeTheme(activity);
                }
                @Override public void onActivityPaused(Activity activity) {
                    if (currentActivity == activity) currentActivity = null;
                }
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
     * Twitch's Default/System theme can occasionally restore a newly-resumed Activity with
     * the opposite night configuration even though the device mode has not changed. Do not
     * force a light or dark mode here; simply recreate the same Activity once when its
     * configuration unexpectedly flips. Explicit Twitch theme changes still settle normally
     * after the recreation.
     */
    private static void stabilizeTheme(Activity activity) {
        try {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

            int activityNight = activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            int systemNight = android.content.res.Resources.getSystem().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

            String activityClass = activity.getClass().getName();
            if (activityClass.equals(lastThemeActivityClass)
                    && lastSystemNight == systemNight
                    && lastThemeActivityNight != -1
                    && lastThemeActivityNight != activityNight
                    && !themeRepairScheduled) {
                themeRepairScheduled = true;
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            activity.recreate();
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        themeRepairScheduled = false;
                    }
                }, 60L);
            }

            lastThemeActivityClass = activityClass;
            lastThemeActivityNight = activityNight;
            lastSystemNight = systemNight;
        } catch (Throwable ignored) {
        }
    }

    public static Context getContext() {
        return context;
    }

    /** Returns the currently resumed Twitch Activity, even when the extension only has an application context. */
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
