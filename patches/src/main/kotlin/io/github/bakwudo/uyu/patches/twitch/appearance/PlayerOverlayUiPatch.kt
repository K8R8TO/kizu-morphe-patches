package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.EXTENSION_PACKAGE

private const val SUPPORT_CLASS = "$EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"

/**
 * Twitch 31.3.1's player overlay constructor receives the overlay root View
 * as p2. Bind that actual root so the helper can hide both Create Clip controls.
 */
internal val playerOverlayUiPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        val method = PlayerOverlayCreateClipFingerprint.method

        // p0 = Lout; instance, p1 = Context, p2 = player overlay View.
        method.addInstructions(
            0,
            "invoke-static/range { p2 .. p2 }, " + SUPPORT_CLASS +
                "->bindPlayerOverlay(Landroid/view/View;)V",
        )
    }
}
