package app.morphe.extension.appearance;

import android.view.View;

import app.morphe.extension.settings.Settings;

@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private PlayerOverlaySupport() {
    }

    /**
     * Twitch 31.3.1's Lout; player overlay keeps the Create Clip controls as
     * concrete views. Binding those exact instances avoids relying on a view-tree
     * scan, which can miss controls managed by the overlay lifecycle.
     */
    public static void bind(View createClipComposeView, View createClipImageView) {
        try {
            if (createClipComposeView != null) {
                HiddenView.attach(
                        createClipComposeView,
                        view -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(),
                        false);
            }
            if (createClipImageView != null) {
                HiddenView.attach(
                        createClipImageView,
                        view -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(),
                        false);
            }
        } catch (Throwable ignored) {
        }
    }
}
