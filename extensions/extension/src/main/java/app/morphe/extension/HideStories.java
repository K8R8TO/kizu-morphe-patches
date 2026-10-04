package app.morphe.extension;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

/**
 * Hides only the individual feed item containing Twitch's visible Stories label.
 *
 * It never hides a RecyclerView/Lazy list itself, because those can contain the entire Home feed.
 * This keeps the patch isolated from Twitch's page container and prevents a blank Home screen.
 */
public final class HideStories {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long[] RETRY_DELAYS_MS = {
            100L, 300L, 750L, 1500L, 3000L, 5000L, 8000L, 12000L, 16000L, 20000L
    };
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

        // Twitch builds the Home feed lazily. A one-shot scan can run before the
        // Stories shelf exists, so retry for the first few seconds after every
        // activity start/resume.
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
        if (root == null || root.getWidth() <= 0) return;

        List<View> views = new ArrayList<>();
        collectVisibleViews(root, views);

        for (View view : views) {
            if (!isStoriesLabel(view)) continue;

            View shelf = findFeedItem(view);
            if (shelf == null || shelf == root) continue;

            try {
                synchronized (HIDDEN) {
                    HIDDEN.put(shelf, Boolean.TRUE);
                }
                shelf.setVisibility(View.GONE);
                requestLayout(shelf);
            } catch (Throwable ignored) {
            }
            return;
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

    private static void collectVisibleViews(View view, List<View> out) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isShown()) return;
        out.add(view);

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectVisibleViews(group.getChildAt(i), out);
        }
    }

    /**
     * Returns the direct child of the first list container above the Stories label.
     * The list container itself is deliberately never hidden.
     */
    private static View findFeedItem(View label) {
        View current = label;
        for (int depth = 0; depth < 10 && current.getParent() instanceof View; depth++) {
            View parent = (View) current.getParent();
            if (isListContainer(parent)) {
                return current;
            }
            if (parent == parent.getRootView()) return null;
            current = parent;
        }
        return null;
    }

    private static boolean isStoriesLabel(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (isExactStoriesText(text)) return true;
        }
        return isExactStoriesText(view.getContentDescription());
    }

    private static boolean isExactStoriesText(CharSequence value) {
        if (value == null) return false;
        String text = value.toString().trim();
        return "stories".equalsIgnoreCase(text)
                || "twitch stories".equalsIgnoreCase(text);
    }

    private static boolean isListContainer(View view) {
        String name = view.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("recyclerview")
                || name.contains("lazycolumn")
                || name.contains("lazyrow");
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
