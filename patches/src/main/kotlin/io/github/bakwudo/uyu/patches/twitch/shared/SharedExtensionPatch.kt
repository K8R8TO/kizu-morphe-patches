package io.github.bakwudo.uyu.patches.twitch.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_PACKAGE = "Lapp/morphe/extension"

internal val sharedExtensionPatch = bytecodePatch {
    extendWith("extensions/twitch.mpe")

    execute {
        TwitchApplicationOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/Utils;->setContext(Landroid/content/Context;)V",
        )

        val returns = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }
        returns.asReversed().forEach { index ->
            TwitchApplicationOnCreateFingerprint.method.addInstruction(
                index,
                "invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/settings/ThemeSync;->init(Landroid/content/Context;)V",
            )
        }
    }
}
