package io.github.bakwudo.uyu.extension.appearance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.bakwudo.uyu.extension.Utils;
import app.morphe.extension.settings.Settings;

/**
 * Tier-1 Home/navigation controls for Twitch 31.3.1.
 *
 * All resource IDs referenced here were verified against the supplied Twitch 31.3.1 APKM.
 * This class reuses the existing stable BaseViewDelegate hook instead of adding another global
 * bytecode hook.
 */
@SuppressWarnings("unused")
public final class HomeCleanupSupport {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_DEPTH = 16;
    private static final Map<View, Boolean> FORCED_VISIBLE = new WeakHashMap<>();
    private static final Map<ViewGroup, Boolean> SCROLL_WATCHED = new WeakHashMap<>();

    private HomeCleanupSupport() {
    }

    public static boolean disableLinkDisclaimer() {
        return Settings.DISABLE_LINK_DISCLAIMER.get();
    }

    public static void onViewCreated(View root) {
        try {
            // Exact Twitch 31.3.1 resource IDs for global/navigation/player controls.
            attachId(root, "create_button",
                    v -> Settings.HIDE_CREATE_BUTTON.get(), false);
            attachId(root, "following_nav_rail_create_button",
                    v -> Settings.HIDE_CREATE_BUTTON.get(), false);
            attachId(root, "cast_button",
                    v -> Settings.HIDE_CAST_BUTTON.get(), false);
            attachId(root, "create_clip_button_compose_view",
                    v -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(), false);
            attachId(root, "create_clip_text_button",
                    v -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(), false);
            attachId(root, "share_stream_button",
                    v -> Settings.HIDE_PLAYER_LIVE_SHARE_BUTTON.get(), false);

            forceVisibleId(root, "open_search_bar_text_view_container");
            forceVisibleId(root, "open_search_bar_text_view");
            forceVisibleId(root, "search_button");

            attachId(root, "recommended",
                    v -> Settings.HIDE_RECOMMENDATIONS.get(), false);
            attachId(root, "recommended_channels_list_title",
                    v -> Settings.HIDE_RECOMMENDATIONS.get(), false);
            attachId(root, "resume_auto_scroll_root",
                    v -> Settings.HIDE_RESUME_WATCHING.get(), false);

            if (!isFollowingRoot(root)) return;

            ViewGroup recycler = findFollowingRecycler(root);
            if (recycler == null) return;

            attachId(recycler, "leaderboards_container",
                    v -> Settings.HIDE_HOME_LEADERBOARDS.get(), false);
            attachId(recycler, "following_tab_turbo_button",
                    v -> Settings.HIDE_TURBO_UPSELL.get(), false);
            attachId(recycler, "turbo_upsell_container",
                    v -> Settings.HIDE_TURBO_UPSELL.get(), false);

            watchFollowingScroll(recycler);
            scanFollowingRecycler(recycler);
            scheduleRecyclerScan(recycler, 100);
            scheduleRecyclerScan(recycler, 500);
            scheduleRecyclerScan(recycler, 1200);
        } catch (Throwable t) {
            Utils.logError("Failed to prepare Home tier-1 controls", t);
        }
    }

    private static void watchFollowingScroll(final ViewGroup recycler) {
        if (SCROLL_WATCHED.put(recycler, Boolean.TRUE) != null) return;

        ViewTreeObserver observer = recycler.getViewTreeObserver();
        observer.addOnScrollChangedListener(() -> scheduleRecyclerScan(recycler, 100));
    }

    private static void scheduleRecyclerScan(final ViewGroup recycler, long delay) {
        MAIN.postDelayed(() -> {
            try {
                if (recycler.isAttachedToWindow()) scanFollowingRecycler(recycler);
            } catch (Throwable t) {
                Utils.logError("Following tier-1 scan failed", t);
            }
        }, delay);
    }

    private static void scanFollowingRecycler(ViewGroup recycler) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            scanSectionText(recycler.getChildAt(i), 0);
        }
    }

    private static void scanSectionText(View view, int depth) {
        if (depth > MAX_DEPTH) return;

        if (view instanceof TextView) {
            String text = String.valueOf(((TextView) view).getText())
                    .trim().toLowerCase(java.util.Locale.ROOT);

            if (!text.isEmpty()) {
                if (Settings.HIDE_FEATURED_CLIPS.get()
                        && containsAny(text, "featured clips")) {
                    hideSectionItem(view);
                } else if (Settings.HIDE_RECOMMENDATIONS.get()
                        && containsAny(text, "recommended for you",
                        "recommended channels", "recommended live channels",
                        "recommendations")) {
                    hideSectionItem(view);
                } else if (Settings.HIDE_RESUME_WATCHING.get()
                        && containsAny(text, "resume watching", "continue watching")) {
                    hideSectionItem(view);
                } else if (Settings.HIDE_OFFLINE_CHANNELS.get()
                        && containsAny(text, "offline channels")) {
                    hideSectionItem(view);
                } else if (Settings.HIDE_UPCOMING_STREAMS.get()
                        && containsAny(text, "upcoming streams", "upcoming events")) {
                    hideSectionItem(view);
                } else if (Settings.HIDE_GAME_SECTION.get()
                        && isGameHeading(text)) {
                    hideSectionItem(view);
                } else if (Settings.HIDE_HOME_LEADERBOARDS.get()
                        && containsAny(text, "leaderboards")) {
                    hideSectionItem(view);
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanSectionText(group.getChildAt(i), depth + 1);
            }
        }
    }

    private static void hideSectionItem(View title) {
        ViewGroup recycler = findRecyclerAncestor(title);
        if (recycler == null) {
            HiddenView.attach(title, v -> true, true);
            return;
        }

        View sectionItem = findDirectRecyclerChild(title, recycler);
        if (sectionItem == null) {
            HiddenView.attach(title, v -> true, true);
            return;
        }

        int start = recycler.indexOfChild(sectionItem);
        if (start < 0) {
            HiddenView.attach(title, v -> true, true);
            return;
        }

        for (int i = start; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            if (i > start && isFollowingSectionHeader(child)) break;
            HiddenView.attach(child, v -> true, true);
        }
    }

    private static boolean isFollowingSectionHeader(View view) {
        return findId(view, "following_tab_section_header") != null;
    }

    private static ViewGroup findFollowingRecycler(View root) {
        View view = findId(root, "following_list_recycler_view");
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    private static boolean isFollowingRoot(View root) {
        return findId(root, "following_list_recycler_view") != null
                || findId(root, "following_tab_section_header") != null
                || findId(root, "following_tab_turbo_button") != null;
    }

    private static ViewGroup findRecyclerAncestor(View view) {
        View current = view;
        for (int i = 0; i < MAX_DEPTH && current.getParent() instanceof ViewGroup; i++) {
            ViewGroup parent = (ViewGroup) current.getParent();
            if (parent.getClass().getName().contains("RecyclerView")) return parent;
            current = parent;
        }
        return null;
    }

    private static View findDirectRecyclerChild(View view, ViewGroup recycler) {
        View current = view;
        for (int i = 0; i < MAX_DEPTH && current.getParent() instanceof ViewGroup; i++) {
            if (current.getParent() == recycler) return current;
            current = (View) current.getParent();
        }
        return null;
    }

    private static boolean isGameHeading(String text) {
        return "games".equals(text)
                || "game".equals(text)
                || "categories".equals(text)
                || "followed categories".equals(text);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static void attachId(View root, String name,
                                 HiddenView.Condition condition, boolean restore) {
        View view = findId(root, name);
        if (view != null) HiddenView.attach(view, condition, restore);
    }

    private static void forceVisibleId(View root, String name) {
        View view = findId(root, name);
        if (view == null || FORCED_VISIBLE.put(view, Boolean.TRUE) != null) return;

        ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (Settings.FORCE_SEARCH_BUTTON.get()
                        && view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE);
                }
                return true;
            }
        });
    }

    private static View findId(View root, String name) {
        Context context = root.getContext();
        int id = Utils.getResourceId(context, name, "id");
        return id == 0 ? null : root.findViewById(id);
    }
}
