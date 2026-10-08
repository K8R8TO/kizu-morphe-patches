package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
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

private const val SUPPORT_CLASS = "$EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"

internal val playerOverlayUiPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        // Exact Twitch 31.3.1 Lout.j:
        // create_clip_button_compose_view (resource 0x7f0b05f0).
        PlayerOverlayConstructorFingerprint.method.apply {
            val returnIndex = instructions.indexOfFirst { it.opcode == Opcode.RETURN_VOID }
            if (returnIndex < 0) {
                throw PatchException("Twitch 31.3.1 Lout constructor has no RETURN_VOID.")
            }

            addInstructions(
                returnIndex,
                """
                    iget-object v0, p0, Lout;->j:Landroidx/compose/ui/platform/ComposeView;
                    invoke-static {v0}, $SUPPORT_CLASS->bind(Landroid/view/View;)V;
                    iget-object v0, p0, Lout;->k:Landroid/widget/ImageView;
                    iget-object v1, p0, Lout;->q:Landroidx/mediarouter/app/MediaRouteButton;
                    invoke-static {v0, v1}, $SUPPORT_CLASS->bindPlayerControls(Landroid/view/View;Landroid/view/View;)V
                """,
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
