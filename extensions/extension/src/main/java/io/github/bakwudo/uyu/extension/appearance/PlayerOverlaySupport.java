package io.github.bakwudo.uyu.extension.appearance;

import android.widget.ImageView;

import androidx.compose.ui.platform.ComposeView;
import androidx.mediarouter.app.MediaRouteButton;

import io.github.bakwudo.uyu.extension.settings.Settings;

@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private PlayerOverlaySupport() {
    }

    public static void bind(ComposeView createClipButton,
                            ImageView shareButton,
                            MediaRouteButton castButton) {
        try {
            if (createClipButton != null) {
                HiddenView.attach(createClipButton,
                        view -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(), false);
            }
            if (shareButton != null) {
                HiddenView.attach(shareButton,
                        view -> Settings.HIDE_PLAYER_LIVE_SHARE_BUTTON.get(), false);
            }
            if (castButton != null) {
                HiddenView.attach(castButton,
                        view -> Settings.HIDE_CAST_BUTTON.get(), false);
            }
        } catch (Throwable ignored) {
        }
    }
}