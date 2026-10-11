package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.EXTENSION_PACKAGE

private const val SUPPORT_CLASS = "$EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"

internal val playerOverlayUiPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        // Exact Twitch 31.3.1 player-header binding. The visible Share/Cast controls are
        // Lqot.r/Lqot.e, and Llrx.v() reapplies their visibility after Lout is constructed.
        // Bind the exact visible views at every verified field load so HiddenView can enforce
        // the setting after Twitch's own visibility updates.
        PlayerOverlayHeaderControlsFingerprint.method.apply {
            val targets = instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.IGET_OBJECT || instruction !is ReferenceInstruction) return@filter false
                val reference = instruction.reference as? FieldReference ?: return@filter false
                reference.definingClass == "Lqot;" &&
                    ((reference.name == "r" && reference.type == "Landroid/widget/ImageView;") ||
                        (reference.name == "e" && reference.type == "Landroidx/mediarouter/app/MediaRouteButton;"))
            }.map { (index, instruction) ->
                val register = (instruction as TwoRegisterInstruction).registerA
                val reference = (instruction as ReferenceInstruction).reference as FieldReference
                Triple(index, register, if (reference.name == "r") "bindLiveShareButton" else "bindCastButton")
            }.sortedByDescending { it.first }

            if (targets.isEmpty()) {
                throw PatchException("Verified Lqot Share/Cast field loads were not found in Llrx.v().")
            }

            targets.forEach { (index, register, methodName) ->
                addInstructions(
                    index + 1,
                    "invoke-static/range { v$register .. v$register }, " +
                        "$SUPPORT_CLASS->$methodName(Landroid/view/View;)V",
                )
            }
        }

        // Exact Twitch 31.3.1 Lout Create Clip binding remains resource/field verified.
        PlayerOverlayConstructorFingerprint.method.apply {
            val matches = instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.IPUT_OBJECT || instruction !is ReferenceInstruction) return@filter false
                val reference = instruction.reference as? FieldReference ?: return@filter false
                reference.definingClass == "Lout;" &&
                    reference.name == "j" &&
                    reference.type == "Landroidx/compose/ui/platform/ComposeView;"
            }
            if (matches.size != 1) {
                throw PatchException("Expected exactly one verified Lout.j Create Clip assignment.")
            }
            val index = matches.single().index
            addInstructions(
                index + 1,
                "move-object/from16 v0, p0\n" +
                    "iget-object v0, v0, Lout;->j:Landroidx/compose/ui/platform/ComposeView;\n" +
                    "invoke-static {v0}, $SUPPORT_CLASS->bind(Landroid/view/View;)V",
            )
        }

        // Exact Twitch 31.3.1 Ld040.<init> lookup:
        // create_clip_text_button (resource 0x7f0b05f2) -> ComposeView v21 -> if-eqz v21.
        // v21 cannot be used with the 35c invoke form, so /range is mandatory.
        PlayerClipTextButtonFingerprint.method.apply {
            var targetIndex = -1
            var targetRegister = -1

            for (i in instructions.indices) {
                val instruction = instructions[i]
                if (instruction.opcode != Opcode.CHECK_CAST ||
                    instruction !is ReferenceInstruction ||
                    (instruction.reference as? TypeReference)?.type !=
                        "Landroidx/compose/ui/platform/ComposeView;" ||
                    instruction !is OneRegisterInstruction
                ) {
                    continue
                }

                val register = instruction.registerA
                var followsClipLookup = false
                var j = i - 1
                while (j >= 0 && i - j <= 8) {
                    val previous = instructions[j]
                    if (previous.opcode == Opcode.CONST &&
                        previous is com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction &&
                        previous.narrowLiteral == 0x7f0b05f2
                    ) {
                        followsClipLookup = true
                        break
                    }
                    j--
                }

                if (!followsClipLookup) continue

                if (i + 1 < instructions.size) {
                    val next = instructions[i + 1]
                    if (next.opcode == Opcode.IF_EQZ &&
                        next is OneRegisterInstruction &&
                        next.registerA == register
                    ) {
                        targetIndex = i + 1
                        targetRegister = register
                        break
                    }
                }
            }

            if (targetIndex < 0) {
                throw PatchException(
                    "Could not locate verified create_clip_text_button ComposeView null-check.",
                )
            }

            addInstructions(
                targetIndex,
                "invoke-static/range { v$targetRegister .. v$targetRegister }, " +
                    "$SUPPORT_CLASS->bindTextClipButton(Landroid/view/View;)V",
            )
        }
    }
}
