package io.github.bakwudo.uyu.patches.twitch.channelpoints

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import io.github.bakwudo.uyu.patches.twitch.shared.Constants
import io.github.bakwudo.uyu.patches.twitch.shared.reference

@Suppress("unused")
val autoClaimChannelPointsPatch = bytecodePatch(
    name = "Auto-claim bonus channel points",
    description = "Claims available bonus rewards in live playback.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITCH)
    execute {
        val points = resolvePointsHooks()
        val player = resolvePlayerHooks()
        val claimBridge = pointsBridge(points)
        val configureBridge = playerConfigurationBridge(player)
        val stateBridge = playerStateBridge(player)
        mutableClassDefBy(points.update.definingClass).methods.single { it.reference == points.update.reference }
            .addInstruction(points.updateIndex + 1,
                "invoke-static {v${points.providerRegister}, v${points.modelRegister}}, $claimBridge")
        mutableClassDefBy(player.player.type).methods.single { it.reference == player.configure.reference }
            .addInstruction(0, "invoke-static/range {p0 .. p2}, $configureBridge")
        mutableClassDefBy(player.player.type).methods.single { it.reference == player.state.reference }
            .addInstruction(0, "invoke-static/range {p0 .. p1}, $stateBridge")
        mutableClassDefBy(player.player.type).methods.single { it.reference == player.release.reference }
            .addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, Lapp/morphe/extension/channelpoints/ChannelPoints;->release(Ljava/lang/Object;)V",
            )
    }
}
