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
import java.util.Locale;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.settings.Settings;

/**
 * Removes Twitch's Stories shelf from the UI when enabled.
 *
 * This deliberately uses a single tree walk per retry. The previous implementation recursively
 * searched every candidate subtree, which could rescan the whole Twitch view tree many times.
 */
public final class HideStories {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long[] RETRY_DELAYS_MS = {0L, 250L, 750L, 1500L, 3000L, 6000L};
    private static final WeakHashMap<View, Boolean> HIDDEN =
            new WeakHashMap<>();

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
        if (root == null || root.getWidth() <= 0) return;

        List<View> views = new ArrayList<>();
        collectVisibleViews(root, views);

        View shelf = findStoriesShelf(views, root);
        if (shelf == null || shelf == root) return;

        try {
            synchronized (HIDDEN) {
                HIDDEN.put(shelf, Boolean.TRUE);
            }
            shelf.setVisibility(View.GONE);
            requestLayout(shelf);
        } catch (Throwable ignored) {
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

    private static View findStoriesShelf(List<View> views, View root) {
        // First prefer an actual "Stories" label. Climb only through a small number of parents
        // and only accept a container that looks like a horizontal feed shelf.
        for (View view : views) {
            if (!isStoriesLabel(view)) continue;

            View current = view;
            for (int depth = 0; depth < 6 && current.getParent() instanceof View; depth++) {
                current = (View) current.getParent();

                if (isRecyclerView(current) && current instanceof ViewGroup) {
                    return current;
                }

                if (looksLikeShelf(current, root)) {
                    return current;
                }
            }
        }

        // Fallback: some Twitch builds may expose a story-specific resource/container name
        // without a visible text label.
        for (View view : views) {
            String resource = resourceName(view);
            if (resource == null) continue;
            String normalized = resource.toLowerCase(Locale.ROOT);
            if (!normalized.contains("stor")) continue;

            if (looksLikeShelf(view, root)) {
                return view;
            }
        }

        return null;
    }

    private static boolean isStoriesLabel(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (containsStories(text)) return true;
        }

        return containsStories(view.getContentDescription());
    }

    private static boolean containsStories(CharSequence value) {
        if (value == null) return false;
        String text = value.toString().trim();
        return "stories".equalsIgnoreCase(text)
                || "twitch stories".equalsIgnoreCase(text);
    }

    private static boolean isRecyclerView(View view) {
        String name = view.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("recyclerview")
                || name.contains("lazycolumn")
                || name.contains("lazyrow");
    }

    private static boolean looksLikeShelf(View view, View root) {
        if (!(view instanceof ViewGroup)) return false;

        int width = view.getWidth();
        int height = view.getHeight();
        int rootWidth = root == null ? 0 : root.getWidth();

        if (width < 180 || height < 40) return false;
        if (rootWidth > 0 && width < rootWidth / 3) return false;

        // Avoid selecting the whole screen or an arbitrary parent container.
        if (root != null && view == root) return false;
        return height < Math.max(240, rootWidth / 2);
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
