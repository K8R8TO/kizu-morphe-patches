package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val URL_UTIL_CLASS = "Ltv/twitch/android/util/EmoteUrlUtil;"
private const val PICKER_BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmotePickerBridge;"
private const val STRING = "Ljava/lang/String;"
private const val CONTEXT = "Landroid/content/Context;"
private const val ANIMATED_UTIL_CLASS = "Ltv/twitch/android/shared/emotes/utils/AnimatedEmotesUrlUtil;"

internal val thirdPartyEmotePickerUrlPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        val classDef = classDefByOrNull(URL_UTIL_CLASS)
            ?: throw PatchException("Kizu emotes: Twitch EmoteUrlUtil was not found.")

        val method = classDef.methods.singleOrNull { candidate ->
            candidate.name == "b" &&
                candidate.returnType == STRING &&
                candidate.parameterTypes.map { it.toString() } ==
                    listOf(STRING, STRING)
        } ?: throw PatchException(
            "Kizu emotes: Twitch 31.3.1 EmoteUrlUtil.b(String,String) was not found.",
        )

        val mutable = mutableClassDefBy(classDef)
        val target = mutable.methods.first {
            it.name == method.name &&
                it.returnType == method.returnType &&
                it.parameterTypes == method.parameterTypes
        }

        target.addInstructions(
            0,
            """
                invoke-static {p0, p1}, $PICKER_BRIDGE->getEmoteUrl(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :kizu_emote_url_fallback
                return-object v0
                :kizu_emote_url_fallback
            """.trimIndent(),
        )
    }

    // The native picker uses AnimatedEmotesUrlUtil for models marked ANIMATED.
    // Its URL builder is separate from tv.twitch.android.util.EmoteUrlUtil, so
    // hook that path directly and replace only the URL while preserving the
    // native EmoteUrlDetails/animation machinery.
    execute {
        val classDef = classDefByOrNull(ANIMATED_UTIL_CLASS)
            ?: throw PatchException("Kizu emotes: Twitch AnimatedEmotesUrlUtil was not found.")

        val method = classDef.methods.singleOrNull { candidate ->
            val parameters = candidate.parameterTypes.map { it.toString() }
            candidate.returnType.startsWith("Ltv/twitch/android/shared/emotes/utils/AnimatedEmotesUrlUtil$") &&
                candidate.returnType.endsWith("EmoteUrlDetails;") &&
                parameters.size == 4 &&
                parameters[0] == CONTEXT &&
                parameters[1] == STRING &&
                parameters[2] == "F" &&
                parameters[3].contains("EmoteUrlAnimationSetting")
        } ?: throw PatchException(
            "Kizu emotes: AnimatedEmotesUrlUtil URL-builder method was not found.",
        )

        val createIndex = method.instructions.indexOfFirst { instruction ->
            if (instruction.opcode != Opcode.NEW_INSTANCE) return@indexOfFirst false
            val reference = (instruction as? ReferenceInstruction)?.reference as? TypeReference
            reference?.type == method.returnType
        }
        if (createIndex < 0) {
            throw PatchException(
                "Kizu emotes: AnimatedEmotesUrlUtil has no EmoteUrlDetails allocation.",
            )
        }

        val mutable = mutableClassDefBy(classDef)
        val target = mutable.methods.singleOrNull {
            it.name == method.name &&
                it.returnType == method.returnType &&
                it.parameterTypes == method.parameterTypes
        } ?: throw PatchException(
            "Kizu emotes: AnimatedEmotesUrlUtil method disappeared during patching.",
        )

        target.addInstructions(
            createIndex,
            """
                invoke-static {p2, p4}, Lapp/morphe/extension/twitch/emotes/EmotePickerBridge;->getAnimatedPickerEmoteUrl(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v1
                if-eqz v1, :kizu_picker_animated_url_fallback
                move-object p1, v1
                :kizu_picker_animated_url_fallback
            """.trimIndent(),
        )
    }
}
