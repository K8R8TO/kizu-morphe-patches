package io.github.bakwudo.uyu.patches.twitch.reload

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bakwudo.uyu.patches.twitch.shared.TwitchApplicationOnCreateFingerprint
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

@Suppress("unused")
internal val reloadStreamPatch = bytecodePatch(
    name = "Reload stream",
    description = "Adds a single-tap reload button immediately to the left of the player mute button.",
    default = true,
) {
    dependsOn(sharedExtensionPatch, nativeReloadPatch)
    execute {
        TwitchApplicationOnCreateFingerprint.method.apply {
            instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }
                .asReversed().forEach { index ->
                    addInstructions(index,
                        "invoke-static/range {p0 .. p0}, Lapp/morphe/extension/reload/ReloadRuntime;->initialize(Landroid/app/Application;)V")
                }
        }
    }
}
