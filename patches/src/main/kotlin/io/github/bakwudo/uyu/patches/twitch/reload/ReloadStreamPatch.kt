package io.github.bakwudo.uyu.patches.twitch.reload

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import io.github.bakwudo.uyu.patches.twitch.shared.TwitchApplicationOnCreateFingerprint

/**
 * Installs Reload Stream as an internal part of Twitch Enhancement.
 * Deliberately not a separate bytecodePatch: Morphe must expose one Twitch bundle.
 */
internal fun BytecodePatchContext.installReloadStream() {
    TwitchApplicationOnCreateFingerprint.method.apply {
        instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }
            .asReversed().forEach { index ->
                addInstructions(index,
                    "invoke-static/range {p0 .. p0}, Lapp/morphe/extension/reload/ReloadRuntime;->initialize(Landroid/app/Application;)V")
            }
    }
}
