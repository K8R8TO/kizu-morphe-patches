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
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.twitch.emotes.EmoteSupport;
import app.morphe.extension.twitch.emotes.EmotePickerBridge;
import io.github.bakwudo.uyu.extension.settings.Settings;

public final class Utils {
    private static final String TAG = "kizu";
    private static final long CLAIM_POLL_INTERVAL_MS = 3_000L;
    private static final long CLAIM_VERIFY_WINDOW_MS = 2_200L;
    private static final long CLAIM_VERIFY_STEP_MS = 250L;
    private static final int CLAIM_REWARD_POINTS = 50;

    private static final Pattern POINT_NUMBER_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:[\\s,]\\d{3})+|\\d+(?:\\.\\d+)?\\s*[kKmM]?)(?!\\d)"
    );

    @SuppressLint("StaticFieldLeak")
    private static volatile Context context;
    private static volatile Activity currentActivity;
    private static volatile Application registeredApplication;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Long> CLAIM_LAST_CLICK = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> CLAIM_BASELINE_POINTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> CLAIM_VERIFY_DEADLINE = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> CLAIM_VERIFYING = new WeakHashMap<>();
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
                    synchronized (CLAIM_VERIFYING) {
                        CLAIM_BASELINE_POINTS.clear();
                        CLAIM_VERIFY_DEADLINE.clear();
                        CLAIM_VERIFYING.clear();
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
     * channel-points bonus control. The verifier below uses the displayed points balance,
     * not the click return value, to decide whether the +50 reward was actually received.
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
                            Integer baseline = findPointBalance(root);
                            if (baseline == null) {
                                Activity activity = currentActivity;
                                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                                    baseline = findPointBalance(activity.getWindow().getDecorView());
                                }
                            }
                            startClaimVerification(root, baseline);
                            Log.d(TAG, "auto-claimed visible channel-points bonus; verifying +50 balance change");
                        } else {
                            synchronized (CLAIM_LAST_CLICK) {
                                CLAIM_LAST_CLICK.remove(root);
                            }
                            showClaimStatus("Channel Points +50 claim failed");
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

    private static void startClaimVerification(final View claimedView, Integer baseline) {
        final long deadline = SystemClock.elapsedRealtime() + CLAIM_VERIFY_WINDOW_MS;

        synchronized (CLAIM_VERIFYING) {
            CLAIM_BASELINE_POINTS.put(claimedView, baseline);
            CLAIM_VERIFY_DEADLINE.put(claimedView, deadline);
            CLAIM_VERIFYING.put(claimedView, true);
        }

        MAIN.post(new Runnable() {
            @Override public void run() {
                verifyClaim(claimedView);
            }
        });
    }

    private static void verifyClaim(final View claimedView) {
        Integer baseline;
        Long deadline;
        synchronized (CLAIM_VERIFYING) {
            if (!Boolean.TRUE.equals(CLAIM_VERIFYING.get(claimedView))) return;
            baseline = CLAIM_BASELINE_POINTS.get(claimedView);
            deadline = CLAIM_VERIFY_DEADLINE.get(claimedView);
        }

        if (baseline != null) {
            Integer current = findPointBalance(claimedView);
            if (current == null) {
                Activity activity = currentActivity;
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    current = findPointBalance(activity.getWindow().getDecorView());
                }
            }

            if (current != null) {
                int delta = current - baseline;
                if (delta >= CLAIM_REWARD_POINTS && delta <= CLAIM_REWARD_POINTS + 20) {
                    finishClaimVerification(claimedView, true, delta);
                    return;
                }
            }
        }

        if (deadline != null && SystemClock.elapsedRealtime() < deadline) {
            MAIN.postDelayed(new Runnable() {
                @Override public void run() {
                    verifyClaim(claimedView);
                }
            }, CLAIM_VERIFY_STEP_MS);
            return;
        }

        finishClaimVerification(claimedView, false, 0);
    }

    private static void finishClaimVerification(View claimedView, boolean success, int delta) {
        synchronized (CLAIM_VERIFYING) {
            CLAIM_VERIFYING.remove(claimedView);
            CLAIM_BASELINE_POINTS.remove(claimedView);
            CLAIM_VERIFY_DEADLINE.remove(claimedView);
        }

        if (success) {
            showClaimStatus("Channel Points +50 claimed");
            dismissClaimControl(claimedView);
            Log.d(TAG, "verified channel-points claim; observed balance delta +" + delta);
        } else {
            showClaimStatus("Channel Points +50 claim failed");
            Log.d(TAG, "could not verify the expected +50 balance change");
        }
    }

    /**
     * The bonus control is hidden only after the displayed Channel Points balance confirms
     * the reward. This avoids fabricating a successful claim by blindly hiding the control.
     */
    private static void dismissClaimControl(View claimedView) {
        if (claimedView == null) return;

        try {
            claimedView.setVisibility(View.GONE);
            refreshClaimUi(claimedView);
        } catch (Throwable ignored) {
        }
    }

    private static void showClaimStatus(String message) {
        try {
            Context appContext = context;
            if (appContext != null) {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void refreshClaimUi(View view) {
        if (view == null) return;

        View current = view;
        while (current != null) {
            try {
                current.refreshDrawableState();
                current.invalidate();
                current.requestLayout();
            } catch (Throwable ignored) {
            }

            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
    }

    private static boolean isClaimControl(View view) {
        CharSequence text = null;
        CharSequence description = view.getContentDescription();

        if (view instanceof TextView) {
            text = ((TextView) view).getText();
        }

        String value = ((text == null ? "" : text.toString()) + " " +
                (description == null ? "" : description.toString()))
                .toLowerCase(Locale.ROOT);

        return value.contains("claim") &&
                (value.contains("bonus") || value.contains("channel point"));
    }

    private static Integer findPointBalance(View claimView) {
        if (claimView == null) return null;

        PointBalanceCandidate best = null;
        View ancestor = claimView;

        for (int depth = 0; depth <= 5 && ancestor != null; depth++) {
            PointBalanceCandidate candidate = findBestPointBalanceCandidate(ancestor, 220);
            if (candidate != null && (best == null || candidate.score > best.score ||
                    (candidate.score == best.score && candidate.value > best.value))) {
                best = candidate;
            }

            if (candidate != null && candidate.score >= 80) {
                return candidate.value;
            }

            if (!(ancestor.getParent() instanceof View)) break;
            ancestor = (View) ancestor.getParent();
        }

        return best == null ? null : best.value;
    }

    private static PointBalanceCandidate findBestPointBalanceCandidate(View root, int maxNodes) {
        if (root == null) return null;

        PointBalanceCandidate[] best = new PointBalanceCandidate[1];
        int[] visited = new int[] {0};
        scanBalanceCandidates(root, best, visited, maxNodes);
        return best[0];
    }

    private static void scanBalanceCandidates(
            View view,
            PointBalanceCandidate[] best,
            int[] visited,
            int maxNodes
    ) {
        if (view == null || visited[0] >= maxNodes || view.getVisibility() != View.VISIBLE) return;
        visited[0]++;

        if (view instanceof TextView) {
            considerPointText(((TextView) view).getText(), best);
        }
        considerPointText(view.getContentDescription(), best);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && visited[0] < maxNodes; i++) {
                scanBalanceCandidates(group.getChildAt(i), best, visited, maxNodes);
            }
        }
    }

    private static void considerPointText(CharSequence sequence, PointBalanceCandidate[] best) {
        if (sequence == null) return;

        String text = sequence.toString().trim();
        if (text.isEmpty()) return;

        String lower = text.toLowerCase(Locale.ROOT);
        Matcher matcher = POINT_NUMBER_PATTERN.matcher(text);

        while (matcher.find()) {
            Integer value = parsePointNumber(matcher.group());
            if (value == null || value < 0) continue;

            boolean hasPointContext = lower.contains("point");
            boolean isClaimContext = lower.contains("claim") || lower.contains("bonus");

            int score = 0;
            if (hasPointContext) score += 80;
            if (text.matches("\\s*[0-9][0-9,\\s]*\\s*")) score += 20;
            if (value > CLAIM_REWARD_POINTS) score += 5;
            if (isClaimContext) score -= 100;

            if (!hasPointContext && value <= CLAIM_REWARD_POINTS) continue;
            if (best[0] == null || score > best[0].score ||
                    (score == best[0].score && value > best[0].value)) {
                best[0] = new PointBalanceCandidate(value, score);
            }
        }
    }

    private static Integer parsePointNumber(String raw) {
        if (raw == null) return null;

        String token = raw.trim().replace("\\u00A0", "");
        if (token.isEmpty()) return null;

        char suffix = token.charAt(token.length() - 1);
        double multiplier = 1.0;
        if (suffix == 'k' || suffix == 'K') {
            multiplier = 1_000.0;
            token = token.substring(0, token.length() - 1).trim();
        } else if (suffix == 'm' || suffix == 'M') {
            multiplier = 1_000_000.0;
            token = token.substring(0, token.length() - 1).trim();
        }

        token = token.replace(",", "").replace(" ", "");

        try {
            double value = Double.parseDouble(token) * multiplier;
            if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            return (int) Math.round(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class PointBalanceCandidate {
        final int value;
        final int score;

        PointBalanceCandidate(int value, int score) {
            this.value = value;
            this.score = score;
        }
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
