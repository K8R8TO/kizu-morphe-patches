package app.morphe.extension;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

/**
 * Removes Twitch's Stories shelf from the UI when enabled.
 *
 * This is deliberately view-only. It does not touch Twitch's story data or navigation models.
 * The shelf is loaded asynchronously, so the scan is retried after activity start/resume.
 */
public final class HideStories {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long[] RETRY_DELAYS_MS = {0L, 250L, 750L, 1500L, 3000L, 6000L};
    private static final WeakHashMap<View, Boolean> HIDDEN = new WeakHashMap<>();

    private HideStories() {}

    public static void onActivityStarted(Activity activity) {
        schedule(activity);
    }

    public static void onActivityResumed(Activity activity) {
        schedule(activity);
    }

    private static void schedule(final Activity activity) {
        if (activity == null) return;
        for (long delay : RETRY_DELAYS_MS) {
            MAIN.postDelayed(() -> {
                try {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        apply(activity);
                    }
                } catch (Throwable ignored) {
                }
            }, delay);
        }
    }

    private static void apply(Activity activity) {
        if (!Settings.HIDE_STORIES.get()) {
            restore();
            return;
        }

        View root = activity.getWindow().getDecorView();
        if (root == null) return;

        List<View> candidates = new ArrayList<>();
        collect(root, candidates);

        for (View view : candidates) {
            View shelf = findStoryShelf(view);
            if (shelf != null && shelf != root && shelf.getVisibility() == View.VISIBLE) {
                try {
                    HIDDEN.put(shelf, Boolean.TRUE);
                    shelf.setVisibility(View.GONE);
                    requestLayout(shelf);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void restore() {
        synchronized (HIDDEN) {
            for (View view : new ArrayList<>(HIDDEN.keySet())) {
                try {
                    if (view != null && view.getVisibility() == View.GONE) {
                        view.setVisibility(View.VISIBLE);
                        requestLayout(view);
                    }
                } catch (Throwable ignored) {
                }
            }
            HIDDEN.clear();
        }
    }

    private static void collect(View view, List<View> out) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isShown()) return;
        out.add(view);
        if (!(view instanceof ViewGroup)) return;

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collect(group.getChildAt(i), out);
        }
    }

    /**
     * Prefer the actual feed-item/container that owns the Stories label. If Twitch exposes a
     * story-specific resource name, use that only when the view is large enough to be a shelf.
     */
    private static View findStoryShelf(View view) {
        if (hasStoriesLabel(view)) {
            View recyclerChild = nearestRecyclerChild(view);
            if (recyclerChild != null) return recyclerChild;

            View parent = view;
            for (int i = 0; i < 5 && parent.getParent() instanceof View; i++) {
                parent = (View) parent.getParent();
                if (isLargeEnough(parent, view.getRootView())) {
                    return parent;
                }
            }
        }

        String resource = resourceName(view);
        if (resource != null && resource.toLowerCase(java.util.Locale.ROOT).contains("stor")) {
            String cls = view.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (!cls.contains("imageview") && isLargeEnough(view, view.getRootView())) {
                return view;
            }
        }

        return null;
    }

    private static boolean hasStoriesLabel(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (isStoriesText(text)) return true;
        }

        CharSequence description = view.getContentDescription();
        if (isStoriesText(description)) return true;

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasStoriesLabel(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static boolean isStoriesText(CharSequence value) {
        if (value == null) return false;
        String text = value.toString().trim();
        return "stories".equalsIgnoreCase(text)
                || "twitch stories".equalsIgnoreCase(text)
                || text.toLowerCase(java.util.Locale.ROOT).contains("stories");
    }

    private static View nearestRecyclerChild(View view) {
        View current = view;
        for (int i = 0; i < 8 && current != null; i++) {
            if (!(current.getParent() instanceof ViewGroup)) return null;
            ViewGroup parent = (ViewGroup) current.getParent();
            String name = parent.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("recyclerview") || name.contains("lazycolumn")) {
                return current;
            }
            current = parent;
        }
        return null;
    }

    private static boolean isLargeEnough(View view, View root) {
        if (view == null) return false;
        int rootWidth = root == null ? 0 : root.getWidth();
        int width = view.getWidth();
        int height = view.getHeight();
        return width >= 180 && height >= 40 && (rootWidth <= 0 || width >= rootWidth / 4);
    }

    private static String resourceName(View view) {
        try {
            if (view.getId() == View.NO_ID) return null;
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void requestLayout(View view) {
        try {
            View current = view;
            for (int i = 0; i < 8 && current != null; i++) {
                current.requestLayout();
                current.invalidate();
                if (!(current.getParent() instanceof View)) break;
                current = (View) current.getParent();
            }
        } catch (Throwable ignored) {
        }
    }
}
