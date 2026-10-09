package io.github.bakwudo.uyu.patches.twitch.videostats

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

/**
 * Retains the verified Twitch 31.3.1 player controller so the custom stats popup can query
 * Twitch's own VideoStats factory while it is open. This avoids inventing or approximating metrics.
 */
internal val videoStatsPatch = bytecodePatch {
    dependsOn(sharedExtensionPatch)

    execute {
        val controller = mutableClassDefBy("Lsl2;")
        val expectedParameters = listOf(
            "Lqd;",
            "Lcs1;",
            "Lhhc;",
            "Lb3d;",
            "Loot;",
            "Last;",
            "Lu380;",
            "Landroid/content/Context;",
            "Ljava/lang/String;",
        )
        val constructors = controller.methods.filter { method ->
            method.name == "<init>" &&
                method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } == expectedParameters
        }
        if (constructors.size != 1) {
            throw PatchException(
                "Video Stats: expected exactly one verified Lsl2 player-controller constructor; found ${constructors.size}.",
            )
        }

        constructors.single().apply {
            val exits = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_VOID }
            if (exits.isEmpty()) {
                throw PatchException("Video Stats: verified Lsl2 constructor has no return-void exit.")
            }
            exits.asReversed().forEach { index ->
                addInstructions(
                    index,
                    "invoke-static/range { p0 .. p0 }, " +
                        "Lapp/morphe/extension/videostats/VideoStatsRuntime;->bindPlayerContext(Ljava/lang/Object;)V",
                )
            }
        }
    }
}
