package io.github.bakwudo.uyu.extension.appearance;

import android.view.View;

import io.github.bakwudo.uyu.extension.settings.Settings;

@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private PlayerOverlaySupport() {
    }

    public static void bindClip(View createClipButton) {
        try {
            if (createClipButton != null) {
                HiddenView.attach(
                        createClipButton,
                        view -> Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get(),
                        false
                );
            }
        } catch (Throwable ignored) {
        }
    }
}
