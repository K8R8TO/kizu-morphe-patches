package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val URL_UTIL_CLASS = "Ltv/twitch/android/util/EmoteUrlUtil;"
private const val PICKER_BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmotePickerBridge;"
private const val STRING = "Ljava/lang/String;"

internal val thirdPartyEmotePickerUrlPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val classDef = classDefByOrNull(URL_UTIL_CLASS)
            ?: throw PatchException("Kizu emotes: Twitch EmoteUrlUtil was not found.")

        // Twitch 31.3.1 does contain generateEmoteUrl, but its parameter list
        // is not the String,float signature used by the old donor patch.
        // Match the actual URL generator by its stable String input/output
        // contract and preserve the remaining parameters.
        val method = classDef.methods.singleOrNull { candidate ->
            candidate.name == "generateEmoteUrl" &&
                candidate.returnType == STRING &&
                candidate.parameterTypes.firstOrNull()?.toString() == STRING
        } ?: throw PatchException(
            "Kizu emotes: could not uniquely identify Twitch EmoteUrlUtil.generateEmoteUrl(String,...).",
        )

        val mutable = mutableClassDefBy(classDef)
        val target = mutable.methods.single {
            it.name == method.name &&
                it.returnType == method.returnType &&
                it.parameterTypes == method.parameterTypes
        }

        // Use the first register after the parameter registers for the bridge
        // result, so this remains valid if Twitch adds/removes URL parameters.
        val resultRegister = "p" + method.parameterTypes.size

        target.addInstructions(
            0,
            """
                invoke-static {p0}, $PICKER_BRIDGE->getEmoteUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object $resultRegister
                if-eqz $resultRegister, :kizu_emote_url_fallback
                return-object $resultRegister
                :kizu_emote_url_fallback
            """.trimIndent(),
        )
    }
}
