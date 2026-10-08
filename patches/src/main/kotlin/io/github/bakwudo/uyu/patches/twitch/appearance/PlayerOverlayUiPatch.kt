package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.EXTENSION_PACKAGE
import io.github.bakwudo.uyu.patches.util.addInstructionsAtControlFlowLabel

private const val SUPPORT_CLASS = "$EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"

internal val playerOverlayUiPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        // Exact Twitch 31.3.1 Lout.j is the ComposeView resolved from
        // create_clip_button_compose_view (0x7f0b05f0).
        PlayerOverlayConstructorFingerprint.method.apply {
            val returnIndices = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }
            if (returnIndices.size != 1) {
                throw PatchException("Player overlay constructor must have exactly one return.")
            }

            addInstructionsAtControlFlowLabel(
                returnIndices.single(),
                """
                    iget-object v0, p0, Lout;->j:Landroidx/compose/ui/platform/ComposeView;
                    invoke-static {v0}, $SUPPORT_CLASS->bind(Landroid/view/View;)V
                """,
            )
        }

        // The player also creates create_clip_text_button (0x7f0b05f2) separately
        // in Ld040.<init>. Bind the exact ComposeView after its null check setup.
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
                while (j >= 0 && i - j <= 16) {
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

                for (k in i + 1 until minOf(i + 5, instructions.size)) {
                    val next = instructions[k]
                    if (next.opcode == Opcode.IF_EQZ &&
                        next is OneRegisterInstruction &&
                        next.registerA == register
                    ) {
                        targetIndex = k
                        targetRegister = register
                        break
                    }
                }

                if (targetIndex >= 0) break
            }

            if (targetIndex < 0) {
                throw PatchException("Could not locate create_clip_text_button ComposeView null-check.")
            }

            addInstructionsAtControlFlowLabel(
                targetIndex,
                "invoke-static {v$targetRegister}, $SUPPORT_CLASS->bindTextClipButton(Landroid/view/View;)V",
            )
        }
    }
}
