package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.EXTENSION_PACKAGE

private const val SUPPORT_CLASS = "$EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"

/**
 * Twitch 31.3.1 exposes the player clip control through Compose state rather than an
 * ImageView button. PurpleTV's implementation confirmed that the correct behavior boundary
 * is the ClipButtonUiState.isClipButtonVisible value.
 */
internal val playerOverlayUiPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        val method = ClipButtonUiStateConstructorFingerprint.method
        val instructions = method.instructions

        val visibleStore = instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.IPUT_BOOLEAN &&
                instruction is TwoRegisterInstruction &&
                instruction is ReferenceInstruction &&
                (instruction.reference as? FieldReference)?.let { field ->
                    field.definingClass == "Lnra;" &&
                        field.name == "a" &&
                        field.type == "Z"
                } == true
        }

        if (visibleStore.size != 1) {
            throw PatchException("Twitch 31.3.1 ClipButtonUiState must store isClipButtonVisible exactly once.")
        }

        val register = (visibleStore.single().value as TwoRegisterInstruction).registerA

        method.addInstructions(
            visibleStore.single().index,
            "invoke-static { v$register }, $SUPPORT_CLASS->normalizeClipButtonVisible(Z)Z\n" +
                "move-result v$register",
        )
    }
}
