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
                    v -> app.morphe.extension.settings.Settings.HIDE_RESUME_WATCHING.get(), false);

        } catch (Throwable t) {
            Utils.logError("Failed to prepare Home tier-1 controls", t);
        }
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
