package app.morphe.extension;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

import app.morphe.extension.twitch.emotes.EmoteSupport;
import app.morphe.extension.twitch.emotes.EmotePickerBridge;
import io.github.bakwudo.uyu.extension.settings.Settings;

public final class Utils {
    private static final String TAG = "kizu";
    private static final long CLAIM_POLL_INTERVAL_MS = 3_000L;
    private static final long CLAIM_VERIFY_WINDOW_MS = 2_500L;
    private static final long CLAIM_VERIFY_STEP_MS = 200L;
    private static final long CLAIM_SUCCESS_COOLDOWN_MS = 60_000L;
    private static final int CLAIM_REWARD_POINTS = 50;
    private static final int STATUS_DURATION_MS = 2_000;

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
    private static final WeakHashMap<View, View> CLAIM_BALANCE_VIEWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> CLAIM_VERIFY_DEADLINE = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> CLAIM_VERIFYING = new WeakHashMap<>();
    private static final WeakHashMap<Activity, TextView> STATUS_MESSAGES = new WeakHashMap<>();

    private static volatile long lastVerifiedClaimAt;
    private static volatile boolean claimWatcherStarted;

    private static final Runnable CLAIM_WATCHER = new Runnable() {
        @Override public void run() {
            try {
                if (Settings.AUTO_CLAIM_CHANNEL_POINTS.get()) {
                    Activity activity = currentActivity;
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                        if (SystemClock.elapsedRealtime() - lastVerifiedClaimAt >= CLAIM_SUCCESS_COOLDOWN_MS) {
                            scanForClaimButton(activity.getWindow().getDecorView());
                        }
                    }
                } else {
                    synchronized (CLAIM_LAST_CLICK) {
                        CLAIM_LAST_CLICK.clear();
                    }
                    synchronized (CLAIM_VERIFYING) {
                        CLAIM_BASELINE_POINTS.clear();
                        CLAIM_BALANCE_VIEWS.clear();
                        CLAIM_VERIFY_DEADLINE.clear();
                        CLAIM_VERIFYING.clear();
                    }
                    lastVerifiedClaimAt = 0L;
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
                    synchronized (STATUS_MESSAGES) {
                        STATUS_MESSAGES.remove(activity);
                    }
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
     * channel-points bonus control. The +50 verification baseline is captured BEFORE the click.
     */
    private static void scanForClaimButton(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE || !root.isShown()) return;

        if (isClaimControl(root)) {
            boolean eligible = root.isEnabled() && root.isClickable();
            boolean verifying;
            synchronized (CLAIM_VERIFYING) {
                verifying = Boolean.TRUE.equals(CLAIM_VERIFYING.get(root));
            }

            if (eligible && !verifying) {
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
                    PointBalanceCandidate baselineCandidate = findPointBalanceCandidate(root);

                    try {
                        if (root.performClick()) {
                            startClaimVerification(root, baselineCandidate);
                            Log.d(TAG, "auto-clicked visible +50 channel-points bonus; verifying balance");
                        } else {
                            synchronized (CLAIM_LAST_CLICK) {
                                CLAIM_LAST_CLICK.remove(root);
                            }
                            showClaimStatus("Channel Points +50 claim failed");
                        }
                    } catch (Throwable ignored) {
                        synchronized (CLAIM_LAST_CLICK) {
                            CLAIM_LAST_CLICK.remove(root);
                        }
                        showClaimStatus("Channel Points +50 claim failed");
                    }
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

    private static void startClaimVerification(final View claimedView, PointBalanceCandidate baselineCandidate) {
        final long deadline = SystemClock.elapsedRealtime() + CLAIM_VERIFY_WINDOW_MS;

        Integer baseline = baselineCandidate == null ? null : baselineCandidate.value;
        View balanceView = baselineCandidate == null ? null : baselineCandidate.sourceView;

        synchronized (CLAIM_VERIFYING) {
            CLAIM_BASELINE_POINTS.put(claimedView, baseline);
            CLAIM_BALANCE_VIEWS.put(claimedView, balanceView);
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
        View balanceView;
        Long deadline;

        synchronized (CLAIM_VERIFYING) {
            if (!Boolean.TRUE.equals(CLAIM_VERIFYING.get(claimedView))) return;
            baseline = CLAIM_BASELINE_POINTS.get(claimedView);
            balanceView = CLAIM_BALANCE_VIEWS.get(claimedView);
            deadline = CLAIM_VERIFY_DEADLINE.get(claimedView);
        }

        if (baseline != null) {
            Integer current = readPointBalanceView(balanceView);

            if (current == null) {
                PointBalanceCandidate candidate = findPointBalanceCandidate(claimedView);
                if (candidate == null) {
                    Activity activity = currentActivity;
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                        candidate = findPointBalanceCandidate(activity.getWindow().getDecorView());
                    }
                }
                if (candidate != null) current = candidate.value;
            }

            if (current != null) {
                int delta = current - baseline;
                // Allow a simultaneous +10 watch-time award while still requiring the +50 bonus.
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
            CLAIM_BALANCE_VIEWS.remove(claimedView);
            CLAIM_VERIFY_DEADLINE.remove(claimedView);
        }

        if (success) {
            lastVerifiedClaimAt = SystemClock.elapsedRealtime();
            showClaimStatus("Channel Points +50 claimed");
            dismissClaimControl(claimedView);
            Log.d(TAG, "verified +50 channel-points claim; observed balance delta +" + delta);
        } else {
            showClaimStatus("Channel Points +50 claim failed");
            Log.d(TAG, "could not verify the expected +50 balance change");
        }
    }

    /**
     * After the balance confirms +50, hide both the matched claim view and its nearest
     * clickable ancestor so a child-only indicator cannot remain visible.
     */
    private static void dismissClaimControl(View claimedView) {
        if (claimedView == null) return;

        try {
            View target = findNearestClickableAncestor(claimedView);

            claimedView.setVisibility(View.GONE);
            if (target != null && target != claimedView) {
                target.setVisibility(View.GONE);
            }

            View parent = target == null ? claimedView : target;
            while (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
                parent.refreshDrawableState();
                parent.invalidate();
                parent.requestLayout();
            }
        } catch (Throwable ignored) {
        }
    }

    private static View findNearestClickableAncestor(View view) {
        View current = view;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable() && current.isEnabled()) return current;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return null;
    }

    /**
     * Uses an in-app overlay instead of Toast so the status is visible even when Twitch's
     * activity/notification handling suppresses ordinary Toast rendering.
     */
    private static void showClaimStatus(final String message) {
        Activity activity = currentActivity;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Runnable show = new Runnable() {
            @Override public void run() {
                try {
                    View decorView = activity.getWindow().getDecorView();
                    if (!(decorView instanceof FrameLayout)) return;

                    FrameLayout decor = (FrameLayout) decorView;
                    TextView previous;
                    synchronized (STATUS_MESSAGES) {
                        previous = STATUS_MESSAGES.get(activity);
                    }
                    if (previous != null) {
                        try { decor.removeView(previous); } catch (Throwable ignored) {}
                    }

                    TextView status = new TextView(activity);
                    status.setText(message);
                    status.setTextColor(Color.WHITE);
                    status.setTextSize(14f);
                    status.setGravity(Gravity.CENTER);
                    status.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));

                    GradientDrawable background = new GradientDrawable();
                    background.setColor(0xEE222222);
                    background.setCornerRadius(dp(activity, 10));
                    status.setBackground(background);
                    status.setElevation(dp(activity, 8));

                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                    lp.bottomMargin = dp(activity, 72);
                    lp.leftMargin = dp(activity, 16);
                    lp.rightMargin = dp(activity, 16);

                    decor.addView(status, lp);
                    synchronized (STATUS_MESSAGES) {
                        STATUS_MESSAGES.put(activity, status);
                    }

                    MAIN.postDelayed(new Runnable() {
                        @Override public void run() {
                            try {
                                synchronized (STATUS_MESSAGES) {
                                    if (STATUS_MESSAGES.get(activity) == status) {
                                        STATUS_MESSAGES.remove(activity);
                                    }
                                }
                                decor.removeView(status);
                            } catch (Throwable ignored) {
                            }
                        }
                    }, STATUS_DURATION_MS);
                } catch (Throwable ignored) {
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) show.run();
        else MAIN.post(show);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Integer readPointBalanceView(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;

        if (view instanceof TextView) {
            PointBalanceCandidate[] best = new PointBalanceCandidate[1];
            considerPointText(((TextView) view).getText(), best, view);
            return best[0] == null ? null : best[0].value;
        }

        PointBalanceCandidate[] best = new PointBalanceCandidate[1];
        considerPointText(view.getContentDescription(), best, view);
        return best[0] == null ? null : best[0].value;
    }

    private static PointBalanceCandidate findPointBalanceCandidate(View claimView) {
        if (claimView == null) return null;

        PointBalanceCandidate best = null;
        View ancestor = claimView;

        for (int depth = 0; depth <= 6 && ancestor != null; depth++) {
            PointBalanceCandidate candidate = findBestPointBalanceCandidate(ancestor, 260);
            if (candidate != null && (best == null || candidate.score > best.score ||
                    (candidate.score == best.score && candidate.value > best.value))) {
                best = candidate;
            }

            if (candidate != null && candidate.score >= 100) {
                return candidate;
            }

            if (!(ancestor.getParent() instanceof View)) break;
            ancestor = (View) ancestor.getParent();
        }

        return best;
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
            considerPointText(((TextView) view).getText(), best, view);
        }
        considerPointText(view.getContentDescription(), best, view);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && visited[0] < maxNodes; i++) {
                scanBalanceCandidates(group.getChildAt(i), best, visited, maxNodes);
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private static String resourceName(View view) {
        if (view == null || view.getId() == View.NO_ID || context == null) return "";
        try {
            return context.getResources().getResourceEntryName(view.getId()).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void considerPointText(
            CharSequence sequence,
            PointBalanceCandidate[] best,
            View sourceView
    ) {
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

            String resource = resourceName(sourceView);
            if (resource.contains("channel") || resource.contains("point")) score += 60;
            if (resource.contains("community")) score += 30;

            if (!hasPointContext && value <= CLAIM_REWARD_POINTS) continue;

            if (best[0] == null || score > best[0].score ||
                    (score == best[0].score && value > best[0].value)) {
                best[0] = new PointBalanceCandidate(value, score, sourceView);
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
        final View sourceView;

        PointBalanceCandidate(int value, int score, View sourceView) {
            this.value = value;
            this.score = score;
            this.sourceView = sourceView;
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

    public static Context getContext() {
        return context;
    }

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
