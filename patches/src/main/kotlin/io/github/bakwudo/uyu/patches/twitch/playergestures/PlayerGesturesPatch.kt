package io.github.bakwudo.uyu.patches.twitch.playergestures

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Hooks both verified Twitch 31.3.1 player hierarchies:
 * - player_view.xml: older RelativeLayout player wrapper
 * - theatre_coordinator(.xml / _land.xml): full-screen theatre root used in landscape
 */
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
                throw PatchException(
                    "Player gestures: expected one RelativeLayout with id player_wrapper; found ${matches.size}.",
                )
            }
            replaceTag(
                doc,
                matches.single(),
                "app.morphe.extension.player.GesturePlayerWrapper",
            )
        }

        // Fullscreen theatre uses a different hierarchy. Its root must intercept the swipes;
        // replacing only player_view.xml misses this layout and leaves Twitch's swipe-to-collapse
        // handler in control.
        listOf(
            "res/layout/theatre_coordinator.xml",
            "res/layout/theatre_coordinator_land.xml",
        ).forEach { layoutPath ->
            document(layoutPath).use { doc ->
                val root = doc.documentElement
                val id = root.getAttributeNode("android:id")?.textContent ?: ""
                if (!id.endsWith("/theatre_root_container") && id != "theatre_root_container") {
                    throw PatchException(
                        "Player gestures: $layoutPath root no longer has the verified theatre_root_container id.",
                    )
                }
                if (root.tagName != "androidx.constraintlayout.widget.ConstraintLayout") {
                    throw PatchException(
                        "Player gestures: $layoutPath root is not the verified ConstraintLayout.",
                    )
                }
                replaceTag(doc, root, "app.morphe.extension.player.GestureTheatreRoot")
            }
        }
    }
}

private fun replaceTag(
    doc: org.w3c.dom.Document,
    original: Element,
    replacementName: String,
) {
    val replacement = doc.createElement(replacementName)
    for (index in 0 until original.attributes.length) {
        val attribute = original.attributes.item(index)
        replacement.setAttribute(attribute.nodeName, attribute.nodeValue)
    }
    while (original.hasChildNodes()) replacement.appendChild(original.firstChild)
    original.parentNode?.replaceChild(replacement, original)
}
