package io.github.bakwudo.uyu.patches.twitch.navigation

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH

/**
 * Uses Twitch's native home-page resolver for the Following choice.
 *
 * Kizu already supports Following, Live and Clips. The native resolver cleanly exposes
 * Following in Twitch 31.3.1, so Live/Clips retain the existing UI fallback rather than
 * silently losing those settings.
 */
internal val defaultHomeTabPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)

    execute {
        DefaultHomePageFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lapp/morphe/extension/DefaultFollowing;->useNativeFollowing()Z
                if-eqz v0, :kizu_default_home_original
                sget-object v0, $FOLLOWING_PAGE_TYPE->INSTANCE:$FOLLOWING_PAGE_TYPE
                return-object v0
                :kizu_default_home_original
            """,
        )
    }
}
