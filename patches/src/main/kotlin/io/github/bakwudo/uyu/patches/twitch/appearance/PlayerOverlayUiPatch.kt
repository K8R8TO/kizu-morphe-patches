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

private const val SUPPORT_CLASS = "\$EXTENSION_PACKAGE/appearance/PlayerOverlaySupport;"

internal val playerOverlayUiPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        // Exact Twitch 31.3.1 field assignments, verified against the supplied APKM:
        // j = Create Clip, k = player Share/Live Share, q = Cast.
        // Bind immediately after each exact iput-object. This is deliberately the only
        // Share/Cast injection path: the previous semantic return hooks caused stream-open
        // runtime verification failures on the real Twitch method control-flow graph.
        PlayerOverlayConstructorFingerprint.method.apply {
            fun findFieldAssignment(fieldName: String, fieldType: String): Pair<Int, Int> {
                val matches = instructions.withIndex().filter { (_, instruction) ->
                    if (instruction.opcode != Opcode.IPUT_OBJECT || instruction !is ReferenceInstruction) return@filter false
                    val reference = instruction.reference as? FieldReference ?: return@filter false
                    reference.definingClass == "Lout;" &&
                        reference.name == fieldName &&
                        reference.type == fieldType
                }
                if (matches.size != 1) {
                    throw PatchException(
                        "Expected exactly one Twitch 31.3.1 Lout.\$fieldName assignment, found ${matches.size}.",
                    )
                }
                val assignment = matches.single()
                val register = (assignment.value as? TwoRegisterInstruction)?.registerA
                    ?: throw PatchException("Could not read the Lout.\$fieldName source register.")
                return assignment.index to register
            }

            val targets = listOf(
                Triple("j", "Landroidx/compose/ui/platform/ComposeView;", "bind"),
                Triple("k", "Landroid/widget/ImageView;", "bindLiveShareButton"),
                Triple("q", "Landroidx/mediarouter/app/MediaRouteButton;", "bindCastButton"),
            ).map { (fieldName, fieldType, methodName) ->
                val (index, register) = findFieldAssignment(fieldName, fieldType)
                Triple(index, register, methodName)
            }.sortedByDescending { it.first }

            targets.forEach { (index, register, methodName) ->
                addInstructions(
                    index + 1,
                    "invoke-static/range { v\$register .. v\$register }, " +
                        "\$SUPPORT_CLASS->\$methodName(Landroid/view/View;)V",
                )
            }
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
                "invoke-static/range { v\$targetRegister .. v\$targetRegister }, " +
                    "\$SUPPORT_CLASS->bindTextClipButton(Landroid/view/View;)V",
            )
        }
    }
}
