package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val AUTOCOMPLETE_CLASS = "Lr6f;"
private const val RESTRICTION_CLASS = "Ldlf;"
private const val URL_UTIL_CLASS = "Ltv/twitch/android/util/EmoteUrlUtil;"
private const val BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmoteAutocomplete;"
private const val STRING = "Ljava/lang/String;"
private const val CONTEXT = "Landroid/content/Context;"
private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"
private const val LIST = "Ljava/util/List;"

internal val thirdPartyEmoteAutocompletePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        // Lr6f;->h is the backing LinkedHashMap consumed by H(String) in Twitch 31.3.1.
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
        if (builder.implementation == null) {
            throw PatchException("Kizu autocomplete: native H(String) builder has no implementation.")
        }
        builder.addInstructions(
            0,
            "invoke-static {p0}, $BRIDGE->updateNativeProvider(Ljava/lang/Object;)V",
        )

        // H() passes Lwof.b() (the first model string, the synthetic emote ID) to this URL resolver.
        val urlClassDef = classDefByOrNull(URL_UTIL_CLASS)
            ?: throw PatchException("Kizu autocomplete: Twitch EmoteUrlUtil class was not found.")
        val urlMethod = urlClassDef.methods.singleOrNull { method ->
            method.name == "c" &&
                method.returnType == STRING &&
                method.parameterTypes.map { it.toString() } == listOf(CONTEXT, STRING)
        } ?: throw PatchException(
            "Kizu autocomplete: expected unique EmoteUrlUtil.c(Context,String):String in Twitch 31.3.1.",
        )
        val mutableUrlClass = mutableClassDefBy(urlClassDef)
        val mutableUrlMethod = mutableUrlClass.methods.single {
            it.name == urlMethod.name &&
                it.returnType == urlMethod.returnType &&
                it.parameterTypes == urlMethod.parameterTypes
        }
        val urlImplementation = mutableUrlMethod.implementation
            ?: throw PatchException("Kizu autocomplete: EmoteUrlUtil.c has no implementation.")
        mutableUrlMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {p1}, $BRIDGE->getAutocompleteUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :kizu_native_url
                return-object v0
            """.trimIndent(),
            ExternalLabel("kizu_native_url", urlImplementation.instructions.first()),
        )

        // Match Canic's bare-token autocomplete hooks. Methods and B:I are verified in the target DEX.
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
