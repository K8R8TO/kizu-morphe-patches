package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val INPUT_CLASS = "Lcom/twitchrn/emoteinput/TwitchEmoteInputView;"
private const val INPUT_CONTEXT = "Lcom/facebook/react/uimanager/ThemedReactContext;"
private const val BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmoteAutocomplete;"

internal val thirdPartyEmoteAutocompletePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val classDef = classDefByOrNull(INPUT_CLASS)
            ?: throw PatchException("Kizu emotes: Twitch 31.3.1 emote-input view was not found.")
        val inputClass = mutableClassDefBy(classDef)
        val constructor = inputClass.methods.singleOrNull { method ->
            method.name == "<init>" && method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } == listOf(INPUT_CONTEXT)
        } ?: throw PatchException("Kizu emotes: expected TwitchEmoteInputView(ThemedReactContext).")
        val returnIndex = constructor.implementation?.instructions?.indexOfLast { it.opcode == Opcode.RETURN_VOID } ?: -1
        if (returnIndex < 0) throw PatchException("Kizu emotes: emote-input constructor has no return-void.")
        constructor.addInstructions(
            returnIndex,
            "invoke-static/range { p0 .. p0 }, $BRIDGE->install(Ljava/lang/Object;)V",
        )
    }
}

