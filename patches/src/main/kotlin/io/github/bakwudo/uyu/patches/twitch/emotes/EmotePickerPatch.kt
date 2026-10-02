package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch

private const val PICKER_BRIDGE = "Lapp/morphe/extension/twitch/emotes/EmotePickerBridge;"

internal val thirdPartyEmotePickerPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch)

    execute {
        // Observe only the native picker-open event. Twitch's native emote models and URL
        // renderer are deliberately left untouched.
        EmotePickerOpenFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $PICKER_BRIDGE->onPickerOpened(Ljava/lang/Object;)V",
        )
    }
}
