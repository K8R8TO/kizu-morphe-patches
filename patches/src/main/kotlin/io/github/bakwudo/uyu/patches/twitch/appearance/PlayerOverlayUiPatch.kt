package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.EXTENSION_PACKAGE

private const val SUPPORT_CLASS = "$EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"
private const val CREATE_CLIP_BUTTON_RESOURCE_ID = 0x7f0b05f0

/**
 * Twitch 31.3.1's player overlay constructor looks up the Create Clip ComposeView
 * by create_clip_button_compose_view. This patch only binds that actual view.
 */
internal val playerOverlayUiPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        val method = PlayerOverlayCreateClipFingerprint.method

        // Bind the actual overlay root. The helper identifies both Twitch Create Clip
        // controls by resource entry name, and re-applies GONE after visibility refreshes.
        method.addInstructions(
            0,
            "invoke-static/range { p1 .. p1 }, " + SUPPORT_CLASS +
                "->bindPlayerOverlay(Landroid/view/View;)V",
        )
    }
}
