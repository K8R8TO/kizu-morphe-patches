package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val AUTOCOMPLETE_CLASS = "Ldefpackage/r6f;"
private const val BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmoteAutocomplete;"

internal val thirdPartyEmoteAutocompletePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val classDef = classDefByOrNull(AUTOCOMPLETE_CLASS)
            ?: throw PatchException("Kizu emotes: Twitch 31.3.1 native autocomplete class was not found.")
        val autocompleteClass = mutableClassDefBy(classDef)
        val method = autocompleteClass.methods.singleOrNull { candidate ->
            candidate.name == "H" && candidate.returnType == "Ljava/util/List;" &&
                candidate.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;")
        } ?: throw PatchException("Kizu emotes: expected Twitch 31.3.1 autocomplete builder H(String): List.")
        val implementation = method.implementation
            ?: throw PatchException("Kizu emotes: native autocomplete builder has no implementation.")
        val returnIndices = implementation.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_OBJECT) index else null
        }.asReversed()
        if (returnIndices.isEmpty()) throw PatchException("Kizu emotes: native autocomplete builder has no object return.")
        for (returnIndex in returnIndices) {
            val returnRegister = (implementation.instructions.elementAt(returnIndex) as OneRegisterInstruction).registerA
            method.addInstructions(
                returnIndex,
                """
                    move-object/from16 v0, v$returnRegister
                    move-object/from16 v1, p1
                    invoke-static { v0, v1 }, $BRIDGE->augmentNativeSuggestions(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;
                    move-result-object v0
                    move-object/16 v$returnRegister, v0
                """,
            )
        }
    }
}
