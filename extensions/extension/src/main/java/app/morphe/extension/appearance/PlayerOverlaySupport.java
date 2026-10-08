package app.morphe.extension.appearance;

import android.view.View;

import app.morphe.extension.settings.Settings;

@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private PlayerOverlaySupport() {
    }

    /** Hide the verified Twitch 31.3.1 player Create Clip ComposeView. */
    public static void bind(View createClipComposeView) {
        try {
            if (createClipComposeView != null) {
                HiddenView.attach(
                        createClipComposeView,
                        view -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(),
                        false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Hide the verified Twitch 31.3.1 player Share and Cast controls. */
    public static void bindPlayerControls(View shareButton, View castButton) {
        try {
            if (shareButton != null) {
                HiddenView.attach(
                        shareButton,
                        view -> Settings.HIDE_PLAYER_LIVE_SHARE_BUTTON.get(),
                        false);
            }
            if (castButton != null) {
                HiddenView.attach(
                        castButton,
                        view -> Settings.HIDE_CAST_BUTTON.get(),
                        false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Hide the separately-created verified create_clip_text_button ComposeView. */
    public static void bindTextClipButton(View createClipTextButton) {
        try {
            if (createClipTextButton != null) {
                HiddenView.attach(
                        createClipTextButton,
                        view -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(),
                        false);
            }
        } catch (Throwable ignored) {
        }
    }
}
