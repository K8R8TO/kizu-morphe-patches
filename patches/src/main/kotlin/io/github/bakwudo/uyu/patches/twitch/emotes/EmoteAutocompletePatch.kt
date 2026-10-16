package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val RESTRICTION_CLASS = "Ldlf;"
private const val BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmoteAutocomplete;"
private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"

internal val thirdPartyEmoteAutocompletePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        // Keep the tested native trigger/start adjustments, but do not hook Twitch's private
        // autocomplete provider (the old Lr6f hook was the failing dependency).
        val restrictionDef = classDefByOrNull(RESTRICTION_CLASS)
            ?: throw PatchException("Kizu autocomplete: Twitch 31.3.1 restriction class Ldlf; was not found.")
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
        if (openImpl.instructions.isEmpty()) {
            throw PatchException("Kizu autocomplete: Ldlf.i0 has no instructions.")
        }
        openMethod.addInstructionsWithLabels(
            0,
            """
                iget v0, p0, $RESTRICTION_CLASS->B:I
                if-nez v0, :kizu_native_open
                invoke-static {p1}, $BRIDGE->shouldOpenAutocomplete(Ljava/lang/CharSequence;)Z
                move-result v0
                if-eqz v0, :kizu_native_open
                # Kizu owns eligible emote searches; suppress Twitch's competing vertical popup.
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
            ExternalLabel("kizu_native_open", openImpl.instructions.first()),
        )

        val startImpl = startMethod.implementation
            ?: throw PatchException("Kizu autocomplete: Ldlf.j0 has no implementation.")
        if (startImpl.instructions.isEmpty()) {
            throw PatchException("Kizu autocomplete: Ldlf.j0 has no instructions.")
        }
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
