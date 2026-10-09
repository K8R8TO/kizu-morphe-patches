package io.github.bakwudo.uyu.patches.twitch.playergestures

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/** Replace only Twitch's player_wrapper tag with Kizu's intercepting RelativeLayout subclass. */
internal val playerGesturesResourcePatch = resourcePatch {
    execute {
        document("res/layout/player_view.xml").use { doc ->
            val matches = mutableListOf<Element>()
            val layouts = doc.getElementsByTagName("RelativeLayout")
            for (index in 0 until layouts.length) {
                val element = layouts.item(index) as? Element ?: continue
                val id = element.getAttributeNode("android:id")?.textContent ?: continue
                if (id.endsWith("/player_wrapper") || id == "player_wrapper") matches.add(element)
            }
            if (matches.size != 1) {
                throw PatchException("Player gestures: expected exactly one RelativeLayout with id player_wrapper; found ${matches.size}.")
            }
            val original = matches.single()
            val replacement = doc.createElement("app.morphe.extension.player.GesturePlayerWrapper")
            for (index in 0 until original.attributes.length) {
                val attribute = original.attributes.item(index)
                replacement.setAttribute(attribute.nodeName, attribute.nodeValue)
            }
            while (original.hasChildNodes()) replacement.appendChild(original.firstChild)
            original.parentNode.replaceChild(replacement, original)
        }
    }
}
