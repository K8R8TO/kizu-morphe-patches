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
 * Hides Twitch's Stories shelf without touching Home navigation.
 *
 * The important safety rule is that we only hide a view that looks like the
 * short, wide Stories shelf itself. Navigation, scrolling, paging and
 * full-screen containers are explicitly rejected.
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
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

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
        // 1. Exact visible "Stories" label. This remains the primary detector.
        for (View view : views) {
            if (!isStoriesLabel(view)) continue;

            View current = view;
            for (int depth = 0; depth < 7 && current.getParent() instanceof View; depth++) {
                current = (View) current.getParent();

                if (current == root) break;
                if (isUnsafeContainer(current, root)) continue;

                // Only accept a real shelf, not a navigation/header/content wrapper.
                if (looksLikeShelf(current, root) && !containsNavigationLabels(current, 5)) {
                    return current;
                }
            }
        }

        // 2. Twitch/PurpleTV-style class/resource fallback for Compose/custom views.
        // The iOS implementation uses StoryViewerListCollapsibleView; Android builds
        // may expose equivalent StoryViewer/Stories class names or story-specific ids.
        for (View view : views) {
            if (!isStoryTargetView(view)) continue;

            View current = view;
            for (int depth = 0; depth < 7 && current.getParent() instanceof View; depth++) {
                current = (View) current.getParent();

                if (current == root) break;
                if (isUnsafeContainer(current, root)) continue;

                if (looksLikeShelf(current, root) && !containsNavigationLabels(current, 5)) {
                    return current;
                }
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

    private static boolean isStoryTargetView(View view) {
        String cls = view.getClass().getName().toLowerCase(Locale.ROOT);
        String resource = resourceName(view);
        if (resource == null) resource = "";

        return cls.contains("storyviewerlistcollapsible")
                || cls.contains("storyviewer")
                || (cls.contains("stories") && !cls.contains("avatar"))
                || resource.contains("story_viewer")
                || resource.contains("storyviewer")
                || resource.contains("stories_shelf")
                || resource.equals("stories");
    }

    private static boolean looksLikeShelf(View view, View root) {
        if (!(view instanceof ViewGroup)) return false;

        int width = view.getWidth();
        int height = view.getHeight();
        int rootWidth = root == null ? 0 : root.getWidth();
        int rootHeight = root == null ? 0 : root.getHeight();

        if (width <= 0 || height <= 0) return false;

        // Wide and short: characteristic of the Stories shelf.
        if (rootWidth > 0 && width < (rootWidth * 3) / 5) return false;

        int minHeight = dp(view, 80);
        if (height < minHeight) return false;

        if (rootHeight > 0 && height >= (rootHeight * 3) / 5) return false;
        if (rootWidth > 0 && height >= Math.max(dp(view, 360), rootWidth / 2)) return false;

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
                || name.contains("nestedscroll")
                || name.contains("viewpager")
                || name.contains("bottomnavigation")
                || name.contains("tablayout")
                || name.contains("toolbar")
                || name.contains("appbar")
                || name.contains("navigation")) {
            return true;
        }

        int rootHeight = root == null ? 0 : root.getHeight();
        if (rootHeight > 0 && view.getHeight() >= (rootHeight * 3) / 5) return true;

        return false;
    }

    private static boolean containsNavigationLabels(View root, int maxDepth) {
        return containsNavigationLabels(root, 0, maxDepth);
    }

    private static boolean containsNavigationLabels(View view, int depth, int maxDepth) {
        if (view == null || depth > maxDepth) return false;

        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (isNavigationLabel(text)) return true;
        }

        CharSequence description = view.getContentDescription();
        if (isNavigationLabel(description)) return true;

        if (!(view instanceof ViewGroup)) return false;

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsNavigationLabels(group.getChildAt(i), depth + 1, maxDepth)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNavigationLabel(CharSequence value) {
        if (value == null) return false;
        String text = value.toString().trim();
        return "following".equalsIgnoreCase(text)
                || "live".equalsIgnoreCase(text)
                || "clips".equalsIgnoreCase(text)
                || "home".equalsIgnoreCase(text)
                || "browse".equalsIgnoreCase(text)
                || "search".equalsIgnoreCase(text);
    }

    private static String resourceName(View view) {
        try {
            if (view.getId() == View.NO_ID) return null;
            return view.getResources().getResourceEntryName(view.getId()).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
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
