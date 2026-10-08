package io.github.bakwudo.uyu.patches.twitch.reload

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch
import app.morphe.patcher.patch.PatchException

internal val nativeReloadPatch = bytecodePatch {
    dependsOn(sharedExtensionPatch, nativeReloadResources)
    execute {
        val methods = mutableListOf<Method>()
        classDefForEach { definition -> methods.addAll(definition.methods) }
        val ui = resolveNativeReloadUi(methods)
        val player = resolveNativeReloadPlayer(methods, ui)
        val views = resolveNativeReloadViews(methods, player)
        val owner = classDefBy(player.ownerType)
        if (owner.fields.any { it.name == "reloadReleased" } || owner.methods.any {
            it.name in setOf("reloadIdentity", "reloadNativeStream", "releaseReload")
        }) throw PatchException("Reload stream: native player bridge already exists.")
        installNativeReloadPlayer(player, ui)
        installNativeReloadUi(ui, player)
        installNativeReloadViews(views, player)
        installNativeReloadControllerOwner(methods, views)
    }
}
