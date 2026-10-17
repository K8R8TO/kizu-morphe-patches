package io.github.bakwudo.uyu.patches.twitch.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

internal const val EXTENSION_PACKAGE = "Lapp/morphe/extension"

internal val sharedExtensionPatch = bytecodePatch {
    extendWith("extensions/twitch.mpe")

    execute {
        TwitchApplicationOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/Utils;->setContext(Landroid/content/Context;)V",
        )
        // Start composer discovery and warm native/global autocomplete data at app startup.
        TwitchApplicationOnCreateFingerprint.method.addInstruction(
            1,
            "invoke-static/range { p0 .. p0 }, Lapp/morphe/extension/twitch/emotes/EmoteAutocomplete;->startActivityDiscovery(Landroid/content/Context;)V",
        )
    }
}
