package io.github.bakwudo.uyu.patches.twitch.enhancement

import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.ads.blockAdsPatch
import io.github.bakwudo.uyu.patches.twitch.channelpoints.autoClaimChannelPointsPatch
import io.github.bakwudo.uyu.patches.twitch.appearance.hidePromotionsPatch
import io.github.bakwudo.uyu.patches.twitch.appearance.linkDisclaimerPatch
import io.github.bakwudo.uyu.patches.twitch.appearance.playerOverlayUiPatch
import io.github.bakwudo.uyu.patches.twitch.chat.showDeletedMessagesPatch
import io.github.bakwudo.uyu.patches.twitch.emotes.thirdPartyEmotesPatch
import io.github.bakwudo.uyu.patches.twitch.emotes.thirdPartyEmotePickerPatch
import io.github.bakwudo.uyu.patches.twitch.emotes.thirdPartyEmotePickerUrlPatch
import io.github.bakwudo.uyu.patches.twitch.login.fixLoginPatch
import io.github.bakwudo.uyu.patches.twitch.navigation.defaultHomeTabPatch
import io.github.bakwudo.uyu.patches.twitch.notifications.fixNotificationsPatch
import io.github.bakwudo.uyu.patches.twitch.privacy.privacyPatch
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

@Suppress("unused")
val twitchEnhancementPatch = bytecodePatch(
    name = "Twitch Enhancement",
    description = "Kizu Twitch enhancements: third-party emotes in chat and the native Twitch emote menu, animated emote playback, live ad blocking, appearance controls, selectable deleted-message display styles, privacy controls, Hide Stories, targeted player controls, link-disclaimer bypass, and patched-app compatibility.",
) {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(
        settingsPatch,
        defaultHomeTabPatch,
        fixLoginPatch,
        fixNotificationsPatch,
        blockAdsPatch,
        autoClaimChannelPointsPatch,
        hidePromotionsPatch,
        playerOverlayUiPatch,
        linkDisclaimerPatch,
        showDeletedMessagesPatch,
        thirdPartyEmotesPatch,
        thirdPartyEmotePickerPatch,
        thirdPartyEmotePickerUrlPatch,
        privacyPatch,
    )
}
