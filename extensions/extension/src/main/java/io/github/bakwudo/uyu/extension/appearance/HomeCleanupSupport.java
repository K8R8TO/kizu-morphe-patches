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
import io.github.bakwudo.uyu.extension.settings.Settings;

@SuppressWarnings("unused")
public final class HomeCleanupSupport {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_DEPTH = 12;
    private static final Map<View, Boolean> FORCED_VISIBLE = new WeakHashMap<>();

    private HomeCleanupSupport() {
    }

    public static void onViewCreated(View root) {
        try {
            skipLinkDisclaimer(root);

            forceVisibleId(root, "open_search_bar_text_view_container");
            forceVisibleId(root, "open_search_bar_text_view");
            attachId(root, "create_button", v -> Settings.HIDE_CREATE_BUTTON.get(), false);
            attachId(root, "following_nav_rail_create_button", v -> Settings.HIDE_CREATE_BUTTON.get(), false);
            attachId(root, "bottom_nav_create_button", v -> Settings.HIDE_CREATE_BUTTON.get(), false);
            attachId(root, "cast_button", v -> Settings.HIDE_CAST_BUTTON.get(), false);
            attachId(root, "create_clip_button_compose_view",
                    v -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(), false);
            attachId(root, "create_clip_text_button",
                    v -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(), false);
            attachId(root, "share_stream_button",
                    v -> Settings.HIDE_PLAYER_LIVE_SHARE_BUTTON.get(), false);

            if (!isFollowingRoot(root)) return;

            attachId(root, "following_tab_turbo_button", v -> Settings.HIDE_TURBO_UPSELL.get(), false);
            attachId(root, "turbo_upsell_container", v -> Settings.HIDE_TURBO_UPSELL.get(), false);
            attachId(root, "leaderboards_container",
                    v -> Settings.HIDE_HOME_LEADERBOARDS.get(), false);
            attachId(root, "resume_auto_scroll_root",
                    v -> Settings.HIDE_RESUME_WATCHING.get(), false);
            attachId(root, "following_drawer_section_offline_channels",
                    v -> Settings.HIDE_OFFLINE_CHANNELS.get(), false);

            // Twitch's Following feed is populated asynchronously. These retries cover
            // sections inserted after the delegate itself was constructed.
            scheduleScan(root, 150);
            scheduleScan(root, 600);
            scheduleScan(root, 1500);
        } catch (Throwable t) {
            Utils.logError("Failed to prepare Home cleanup", t);
        }
    }

    private static void scheduleScan(final View root, long delay) {
        MAIN.postDelayed(() -> {
            try {
                if (root.isAttachedToWindow() && isFollowingRoot(root)) scan(root, 0);
            } catch (Throwable t) {
                Utils.logError("Home cleanup scan failed", t);
            }
        }, delay);
    }

    private static void scan(View view, int depth) {
        if (depth > MAX_DEPTH) return;

        if (view instanceof TextView) {
            String text = String.valueOf(((TextView) view).getText()).trim().toLowerCase();
            if (!text.isEmpty()) {
                if (Settings.HIDE_FEATURED_CLIPS.get() && containsAny(text, "featured clips")) {
                    hideSection(view);
                } else if (Settings.HIDE_RECOMMENDATIONS.get()
                        && containsAny(text, "recommended channels", "recommended for you", "recommendations")) {
                    hideSection(view);
                } else if (Settings.HIDE_UPCOMING_STREAMS.get()
                        && containsAny(text, "upcoming streams", "upcoming events")) {
                    hideSection(view);
                } else if (Settings.HIDE_GAME_SECTION.get()
                        && isGameHeading(text)) {
                    hideSection(view);
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (Settings.FULL_FOLLOWED_CARDS.get()) enlargeFollowedCard(group);
            for (int i = 0; i < group.getChildCount(); i++) {
                scan(group.getChildAt(i), depth + 1);
            }
        }
    }

    private static void enlargeFollowedCard(ViewGroup group) {
        int followedId = Utils.getResourceId(group.getContext(), "followed_user", "id");
        if (followedId == 0 || group.findViewById(followedId) == null) return;
        int height = (int) (112 * group.getResources().getDisplayMetrics().density + 0.5f);
        if (group.getMinimumHeight() < height) group.setMinimumHeight(height);
    }

    private static void skipLinkDisclaimer(View root) {
        if (!Settings.DISABLE_LINK_DISCLAIMER.get()) return;
        View dialog = findId(root, "browser_link_disclaimer");
        if (dialog == null) return;
        View button = findId(root, "continue_button");
        if (button != null) {
            button.post(() -> {
                try {
                    if (button.isShown()) button.performClick();
                } catch (Throwable t) {
                    Utils.logError("Failed to skip link disclaimer", t);
                }
            });
        }
    }

    private static void forceVisibleId(View root, String name) {
        if (!Settings.FORCE_SEARCH_BUTTON.get()) return;
        View view = findId(root, name);
        if (view == null || FORCED_VISIBLE.put(view, Boolean.TRUE) != null) return;
        ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (view.getVisibility() != View.VISIBLE) view.setVisibility(View.VISIBLE);
                return true;
            }
        });
    }

    private static boolean isFollowingRoot(View root) {
        return findId(root, "following_header") != null
                || findId(root, "following_tab_header_item") != null
                || findId(root, "following_tab_turbo_button") != null
                || findId(root, "discovery_feed_following_page") != null;
    }

    private static boolean isGameHeading(String text) {
        return "games".equals(text) || "game".equals(text)
                || text.startsWith("games ") || text.startsWith("game ");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static void hideSection(View title) {
        View current = title;
        for (int i = 0; i < 5 && current.getParent() instanceof ViewGroup; i++) {
            ViewGroup parent = (ViewGroup) current.getParent();
            if (parent.getChildCount() >= 2 && hasRecyclerChild(parent)) {
                HiddenView.attach(parent, v -> true, true);
                return;
            }
            current = parent;
        }
        HiddenView.attach(title, v -> true, true);
    }

    private static boolean hasRecyclerChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getClass().getName().contains("RecyclerView")) return true;
        }
        return false;
    }

    private static void attachId(View root, String name, HiddenView.Condition condition, boolean restore) {
        View view = findId(root, name);
        if (view != null) HiddenView.attach(view, condition, restore);
    }

    private static View findId(View root, String name) {
        Context context = root.getContext();
        int id = Utils.getResourceId(context, name, "id");
        return id == 0 ? null : root.findViewById(id);
    }
}
