package io.github.bakwudo.uyu.patches.twitch.enhancement

import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.emotes.thirdPartyEmotesPatch
import io.github.bakwudo.uyu.patches.twitch.shared.COMPATIBILITY_TWITCH

@Suppress("unused")
val twitchEnhancementPatch = bytecodePatch(
    name = "Twitch Enhancement",
    description = "Kizu Twitch Enhancement: third-party BTTV, FFZ, and 7TV emotes in chat.",
) {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(thirdPartyEmotesPatch)
}
