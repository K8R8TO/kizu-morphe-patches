package io.github.bakwudo.uyu.patches.twitch.reload

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.w3c.dom.Element
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal var nativeMuteLabel = 0L
internal var nativeReloadIcon = 0
internal var nativeMuteButton = 0
internal var nativePlaybackContainer = 0

private fun parseResourceXml(text: String) =
    DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(InputSource(StringReader(text)))

internal val nativeReloadResources = resourcePatch {
    execute {
        val document = parseResourceXml(get("res/values/public.xml").readText())
        val nodes = document.getElementsByTagName("public")
        val symbols = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        fun id(type: String, name: String): Long {
            val matches = symbols.filter { it.getAttribute("type") == type && it.getAttribute("name") == name }
            if (matches.size != 1) throw PatchException("Reload stream: missing or ambiguous $type/$name.")
            return matches.single().getAttribute("id").removePrefix("0x").toLong(16)
        }
        nativeMuteLabel = id("string", "mute_button_text")
        nativeReloadIcon = id("drawable", "ic_refresh_white_24dp").toInt()
        nativeMuteButton = id("id", "mute_button").toInt()
        nativePlaybackContainer = id("id", "playback_view_container").toInt()

        // A small, self-contained bar-chart icon for the custom Video Stats button.
        val statsIcon = get("res/drawable/kizu_video_stats.xml")
        statsIcon.parentFile?.mkdirs()
        statsIcon.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M3,3h4v18H3zM10,8h4v13h-4zM17,13h4v8h-4z" />
</vector>
""",
        )

        for ((name, constrained) in listOf("bottom_player_overlay_controls" to true,
            "bottom_player_control_overlay_widget" to false)) {
            val file = get("res/layout/$name.xml")
            val layout = parseResourceXml(file.readText())
            insertNativeReloadLayout(layout, constrained)
            file.outputStream().use { output ->
                TransformerFactory.newInstance().newTransformer().transform(DOMSource(layout), StreamResult(output))
            }
        }
    }
}
