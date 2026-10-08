package io.github.bakwudo.uyu.patches.twitch.reload

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import io.github.bakwudo.uyu.patches.util.addInstructionsAtControlFlowLabel

internal fun MutableMethod.insertAtReturn(index: Int, smali: String) {
    addInstructionsAtControlFlowLabel(index, smali)
}
