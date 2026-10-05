package io.github.bakwudo.uyu.patches.twitch.appearance

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bakwudo.uyu.patches.twitch.appearance.BaseViewDelegateConstructorFingerprint
import io.github.bakwudo.uyu.patches.twitch.settings.setPatchIncluded
import io.github.bakwudo.uyu.patches.twitch.settings.settingsPatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.EXTENSION_PACKAGE
import io.github.bakwudo.uyu.patches.util.addInstructionsAtControlFlowLabel
import io.github.bakwudo.uyu.patches.util.thisRegister
import io.github.bakwudo.uyu.patches.util.writesRegister

private const val EXTENSION_CLASS = "$EXTENSION_PACKAGE/appearance/HomeCleanupSupport;"

internal val homeCleanupPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(settingsPatch)

    execute {
        setPatchIncluded("homeCleanup")
        BaseViewDelegateConstructorFingerprint.method.apply {
            val viewRegister = thisRegister + 2
            if (writesRegister(viewRegister)) return@apply

            val returnIndices = instructions.indices.filter {
                instructions[it].opcode == Opcode.RETURN_VOID
            }
            returnIndices.asReversed().forEach { returnIndex ->
                addInstructionsAtControlFlowLabel(
                    returnIndex,
                    "invoke-static/range { p2 .. p2 }, $EXTENSION_CLASS->onViewCreated(Landroid/view/View;)V",
                )
            }
        }
    }
}
