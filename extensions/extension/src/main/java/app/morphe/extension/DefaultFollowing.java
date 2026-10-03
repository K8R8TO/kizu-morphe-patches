package app.morphe.extension;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * Selects Twitch's existing Following destination once when an Activity first opens.
 * This deliberately uses only the native view hierarchy and never changes Twitch navigation state.
 */
public final class DefaultFollowing {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, Boolean> SCHEDULED = new WeakHashMap<>();
    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_MS = 500L;

    private DefaultFollowing() {}

    public static void onActivityStarted(Activity activity) {
        if (activity == null) return;
        synchronized (ATTEMPTED) {
            if (SCHEDULED.containsKey(activity)) return;
            SCHEDULED.put(activity, Boolean.TRUE);
        }
        attempt(activity, 0);
    }

    private static void attempt(final Activity activity, final int attempt) {
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    View root = activity.getWindow().getDecorView();
                    if (selectFollowing(root)) return;
                    if (attempt + 1 < MAX_ATTEMPTS) {
                        attempt(activity, attempt + 1);
                    }
                } catch (Throwable ignored) {
                    if (attempt + 1 < MAX_ATTEMPTS) {
                        attempt(activity, attempt + 1);
                    }
                }
            }
        }, attempt == 0 ? 900L : RETRY_MS);
    }

    private static boolean selectFollowing(View root) {
        if (!(root instanceof ViewGroup)) return false;
        int height = root.getHeight();
        if (height <= 0) return false;
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        return findFollowing((ViewGroup) root, height, rootLocation[1]);
    }

    private static boolean findFollowing(ViewGroup group, int rootHeight, int rootTopOnScreen) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE || !child.isShown()) continue;

            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                CharSequence text = textView.getText();
                CharSequence description = child.getContentDescription();
                boolean isFollowing = (text != null && "following".equalsIgnoreCase(text.toString().trim()))
                        || (description != null && "following".equalsIgnoreCase(description.toString().trim()));

                if (isFollowing && isInLowerNavigation(child, rootHeight, rootTopOnScreen)) {
                    if (isSelected(child)) return true;
                    View target = nearestClickable(child);
                    try {
                        if (target != null && target.isEnabled() && target.performClick()) return true;
                        if (child.isEnabled() && child.performClick()) return true;
                    } catch (Throwable ignored) {}
                }
            }

            if (child instanceof ViewGroup && findFollowing((ViewGroup) child, rootHeight, rootTopOnScreen)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInLowerNavigation(View view, int rootHeight, int rootTopOnScreen) {
        try {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int centerY = location[1] - rootTopOnScreen + (view.getHeight() / 2);
            return centerY >= (rootHeight * 55) / 100;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSelected(View view) {
        View current = view;
        for (int i = 0; i < 5 && current != null; i++) {
            if (current.isSelected()) return true;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return false;
    }

    private static View nearestClickable(View view) {
        View current = view;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable()) return current;
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return null;
    }
}
