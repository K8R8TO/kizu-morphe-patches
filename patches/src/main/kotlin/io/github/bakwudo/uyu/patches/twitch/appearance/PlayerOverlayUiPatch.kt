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

private const val SUPPORT_CLASS = "$" + "EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"
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
        val instructions = method.instructions

        val constMatches = instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.CONST &&
                instruction is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
                instruction.narrowLiteral == CREATE_CLIP_BUTTON_RESOURCE_ID
        }
        if (constMatches.size != 1) {
            throw PatchException("Twitch 31.3.1 Create Clip resource lookup must match exactly once.")
        }

        val resourceRegister = (constMatches.single().value as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("Create Clip resource constant has no destination register.")

        val findViewMatches = instructions.withIndex().filter { (index, instruction) ->
            index > constMatches.single().index &&
                instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                instruction is FiveRegisterInstruction &&
                instruction.registerCount == 2 &&
                listOf(instruction.registerC, instruction.registerD).contains(resourceRegister) &&
                instruction is ReferenceInstruction &&
                (instruction.reference as? MethodReference)?.let { reference ->
                    reference.definingClass == "Landroid/view/View;" &&
                        reference.name == "findViewById" &&
                        reference.returnType == "Landroid/view/View;" &&
                        reference.parameterTypes.map { it.toString() } == listOf("I")
                } == true
        }
        if (findViewMatches.size != 1) {
            throw PatchException("Twitch 31.3.1 Create Clip findViewById lookup must match exactly once.")
        }

        val findViewIndex = findViewMatches.single().index
        val resultInstruction = instructions.getOrNull(findViewIndex + 1) as? OneRegisterInstruction
        if (instructions.getOrNull(findViewIndex + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT ||
            resultInstruction == null) {
            throw PatchException("Create Clip findViewById must be immediately followed by move-result-object.")
        }

        method.addInstructions(
            findViewIndex + 2,
            "invoke-static { v" + resultInstruction.registerA + " }, " + SUPPORT_CLASS +
                "->bindClip(Landroid/view/View;)V",
        )
    }
}
