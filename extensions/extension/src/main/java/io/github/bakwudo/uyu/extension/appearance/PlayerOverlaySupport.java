package io.github.bakwudo.uyu.extension.appearance;

import io.github.bakwudo.uyu.extension.settings.Settings;

@SuppressWarnings("unused")
public final class PlayerOverlaySupport {
    private PlayerOverlaySupport() {
    }

    /**
     * Keeps Twitch's ClipButtonUiState visibility value unchanged when the setting is off,
     * and forces isClipButtonVisible=false when the Kizu setting is enabled.
     */
    public static boolean normalizeClipButtonVisible(boolean visible) {
        try {
            return Settings.HIDE_PLAYER_CREATE_CLIP_BUTTON.get() ? false : visible;
        } catch (Throwable ignored) {
            return visible;
        }
    }
}
