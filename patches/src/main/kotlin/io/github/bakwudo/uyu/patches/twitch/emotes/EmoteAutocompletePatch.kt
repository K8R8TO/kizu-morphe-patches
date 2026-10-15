package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val AUTOCOMPLETE_CLASS = "Lr6f;"
private const val RESTRICTION_CLASS = "Ldlf;"
private const val BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmoteAutocomplete;"
private const val STRING = "Ljava/lang/String;"
private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"
private const val LIST = "Ljava/util/List;"

internal val thirdPartyEmoteAutocompletePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val autocompleteDef = classDefByOrNull(AUTOCOMPLETE_CLASS)
            ?: throw PatchException("Kizu autocomplete: Twitch 31.3.1 class Lr6f; was not found.")
        val autocompleteClass = mutableClassDefBy(autocompleteDef)
        val builder = autocompleteClass.methods.singleOrNull { method ->
            method.name == "H" &&
                method.returnType == LIST &&
                method.parameterTypes.map { it.toString() } == listOf(STRING)
        } ?: throw PatchException(
            "Kizu autocomplete: expected unique Lr6f;->H(Ljava/lang/String;)Ljava/util/List; builder.",
        )
        val implementation = builder.implementation
            ?: throw PatchException("Kizu autocomplete: native autocomplete builder has no implementation.")

        // Save the original composer text before Twitch mutates p1 for its own filtering.
        builder.addInstructions(
            0,
            "invoke-static {p1}, $BRIDGE->captureQuery(Ljava/lang/String;)V",
        )

        // Add third-party native suggestion objects to every native return path.
        val returnIndices = implementation.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_OBJECT) index else null
        }.asReversed()
        if (returnIndices.isEmpty()) {
            throw PatchException("Kizu autocomplete: native autocomplete builder has no object return.")
        }
        for (returnIndex in returnIndices) {
            val returnRegister =
                (implementation.instructions.elementAt(returnIndex) as OneRegisterInstruction).registerA
            builder.addInstructions(
                returnIndex,
                """
                    move-object/from16 v0, v$returnRegister
                    move-object/from16 v1, p0
                    invoke-static {v0, v1}, $BRIDGE->augmentNativeSuggestions(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
                    move-result-object v0
                    move-object/16 v$returnRegister, v0
                """.trimIndent(),
            )
        }

        // Keep the tested bare-token trigger/start hooks.
        val restrictionDef = classDefByOrNull(RESTRICTION_CLASS)
            ?: throw PatchException("Kizu autocomplete: Twitch restriction class Ldlf; was not found.")
        val restrictionClass = mutableClassDefBy(restrictionDef)
        val openMethod = restrictionClass.methods.singleOrNull { method ->
            method.name == "i0" &&
                method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf(CHAR_SEQUENCE)
        } ?: throw PatchException("Kizu autocomplete: expected unique Ldlf;->i0(CharSequence):boolean.")
        val startMethod = restrictionClass.methods.singleOrNull { method ->
            method.name == "j0" &&
                method.returnType == "I" &&
                method.parameterTypes.map { it.toString() } == listOf(CHAR_SEQUENCE)
        } ?: throw PatchException("Kizu autocomplete: expected unique Ldlf;->j0(CharSequence):int.")

        val openImpl = openMethod.implementation
            ?: throw PatchException("Kizu autocomplete: Ldlf.i0 has no implementation.")
        openMethod.addInstructionsWithLabels(
            0,
            """
                iget v0, p0, $RESTRICTION_CLASS->B:I
                if-nez v0, :kizu_native_open
                invoke-static {p1}, $BRIDGE->shouldOpenAutocomplete(Ljava/lang/CharSequence;)Z
                move-result v0
                if-eqz v0, :kizu_native_open
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
            ExternalLabel("kizu_native_open", openImpl.instructions.first()),
        )

        val startImpl = startMethod.implementation
            ?: throw PatchException("Kizu autocomplete: Ldlf.j0 has no implementation.")
        startMethod.addInstructionsWithLabels(
            0,
            """
                iget v0, p0, $RESTRICTION_CLASS->B:I
                if-nez v0, :kizu_native_start
                invoke-static {p1}, $BRIDGE->getBareAutocompleteStart(Ljava/lang/CharSequence;)I
                move-result v0
                if-ltz v0, :kizu_native_start
                return v0
            """.trimIndent(),
            ExternalLabel("kizu_native_start", startImpl.instructions.first()),
        )
    }
}
