package io.github.bakwudo.uyu.patches.twitch.channelpoints

import io.github.bakwudo.uyu.patches.twitch.settings.setPatchIncluded
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch
import app.morphe.patcher.patch.bytecodePatch

/**
 * Enables the existing extension-side channel-points watcher. It deliberately does not hook
 * Twitch's points balance/state; the extension only clicks Twitch's own visible claim control.
 */
internal val autoClaimChannelPointsPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch, sharedExtensionPatch)

    execute {
        setPatchIncluded("autoClaimChannelPoints")
    }
}
