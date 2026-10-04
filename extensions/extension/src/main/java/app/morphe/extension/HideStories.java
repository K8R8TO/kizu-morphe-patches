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
 * Hides only the Stories shelf/item, never the page-level feed container.
 *
 * Twitch's Home/Following hierarchy can vary between native and Compose-backed
 * layouts, so detection uses the visible Stories label first and a constrained
 * size/resource fallback second. A candidate must look like a real shelf before
 * it is hidden; full-screen/root/list containers are never hidden.
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
        if (shelf == null || shelf == root || isUnsafeContainer(shelf, root)) return;

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
        // Prefer a real visible Stories label. The shelf may not itself be a
        // RecyclerView/LazyRow on newer Twitch builds, so choose the nearest
        // ancestor that looks like a horizontal shelf instead.
        for (View view : views) {
            if (!isStoriesLabel(view)) continue;

            View current = view;
            for (int depth = 0; depth < 8 && current.getParent() instanceof View; depth++) {
                current = (View) current.getParent();
                if (current == root) break;

                if (looksLikeShelf(current, root) && !isUnsafeContainer(current, root)) {
                    return current;
                }
            }
        }

        // Fallback for builds that expose a story-specific Android resource id
        // but do not expose the visible label as a TextView.
        for (View view : views) {
            String resource = resourceName(view);
            if (resource == null) continue;

            String normalized = resource.toLowerCase(Locale.ROOT);
            if (!normalized.contains("stor")) continue;

            if (looksLikeShelf(view, root) && !isUnsafeContainer(view, root)) {
                return view;
            }
        }

        return null;
    }

    private static boolean isStoriesLabel(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (isStoriesText(text)) return true;
        }
        return isStoriesText(view.getContentDescription());
    }

    private static boolean isStoriesText(CharSequence value) {
        if (value == null) return false;
        String text = value.toString().trim();
        return "stories".equalsIgnoreCase(text)
                || "twitch stories".equalsIgnoreCase(text);
    }

    private static boolean looksLikeShelf(View view, View root) {
        if (!(view instanceof ViewGroup)) return false;

        int width = view.getWidth();
        int height = view.getHeight();
        int rootWidth = root == null ? 0 : root.getWidth();
        int rootHeight = root == null ? 0 : root.getHeight();

        if (width <= 0 || height <= 0) return false;

        // Stories is a wide, relatively short horizontal shelf.
        if (width < 180 || height < 40) return false;
        if (rootWidth > 0 && width < rootWidth / 3) return false;
        if (rootHeight > 0 && height >= (rootHeight * 3) / 4) return false;
        if (rootWidth > 0 && height >= Math.max(300, rootWidth / 2)) return false;

        ViewGroup group = (ViewGroup) view;
        return group.getChildCount() >= 2;
    }

    private static boolean isUnsafeContainer(View view, View root) {
        if (view == null || view == root) return true;

        String name = view.getClass().getName().toLowerCase(Locale.ROOT);
        if (name.contains("recyclerview")
                || name.contains("lazycolumn")
                || name.contains("lazyrow")
                || name.contains("scrollview")
                || name.contains("viewpager")) {
            return true;
        }

        int rootHeight = root == null ? 0 : root.getHeight();
        if (rootHeight > 0 && view.getHeight() >= (rootHeight * 3) / 4) return true;

        return false;
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
