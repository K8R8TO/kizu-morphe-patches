package io.github.bakwudo.uyu.patches.twitch.emotes

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import io.github.bakwudo.uyu.patches.twitch.shared.Constants.COMPATIBILITY_TWITCH
import io.github.bakwudo.uyu.patches.twitch.shared.sharedExtensionPatch
import io.github.bakwudo.uyu.patches.twitch.shared.references
import io.github.bakwudo.uyu.patches.twitch.shared.isInstance
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

private const val PREVIEW = "Lapp/morphe/extension/twitch/emotes/NativeEmotePreview;"
private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private var previewResourceIds = emptyList<Int>()

private fun Element.androidId(): String = getAttributeNS(ANDROID_NAMESPACE, "id")
    .ifEmpty { getAttribute("android:id") }

private fun parseEmoteResourceXml(source: String) = DocumentBuilderFactory.newInstance()
    .apply { isNamespaceAware = true }
    .newDocumentBuilder()
    .parse(InputSource(StringReader(source)))

private val emotePreviewResources = resourcePatch {
    execute {
        val public = parseEmoteResourceXml(get("res/values/public.xml").readText())
        val nodes = public.getElementsByTagName("public")
        val entries = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        previewResourceIds = listOf(
            "layout" to "emote_card_dialog",
            "id" to "emote_card_loaded_content",
            "id" to "emote_icon",
            "id" to "emote_name",
            "id" to "emote_desc",
            "dimen" to "default_margin",
        ).map { (type, name) ->
            entries.filter { it.getAttribute("type") == type && it.getAttribute("name") == name }
                .singleOrNull()?.getAttribute("id")?.removePrefix("0x")?.toLong(16)?.toInt()
                ?: throw PatchException("Kizu emotes: Twitch emote-card resource $type/$name was not unique.")
        }

        val layout = parseEmoteResourceXml(get("res/layout/emote_card_dialog.xml").readText())
        val contentNodes = layout.getElementsByTagName("*")
        val content = (0 until contentNodes.length).mapNotNull { contentNodes.item(it) as? Element }
            .singleOrNull { it.androidId().endsWith("/emote_card_loaded_content") }
            ?: throw PatchException("Kizu emotes: Twitch emote-card layout hierarchy changed.")
        for (id in listOf("emote_icon", "emote_name", "emote_desc")) {
            val found = (0 until contentNodes.length).mapNotNull { contentNodes.item(it) as? Element }
                .any { it.androidId().endsWith("/$id") }
            if (!found) {
                throw PatchException("Kizu emotes: Twitch emote-card is missing $id.")
            }
        }
    }
}

internal val thirdPartyEmotePreviewPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_TWITCH)
    dependsOn(sharedExtensionPatch, emotePreviewResources)

    execute {
        val fragment = classDefBy("Lcom/google/android/material/bottomsheet/BottomSheetDialogFragment;")
        val factory = fragment.methods.filter {
            it.name == "onCreateDialog" &&
                it.isInstance(listOf("Landroid/os/Bundle;"), "Landroid/app/Dialog;")
        }.singleOrNull() ?: throw PatchException("Kizu emotes: native Twitch bottom-sheet factory was not unique.")
        val constructor = factory.references().filterIsInstance<com.android.tools.smali.dexlib2.iface.reference.MethodReference>()
            .filter { it.name == "<init>" &&
                it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;", "I") &&
                it.returnType == "V" }
            .singleOrNull() ?: throw PatchException("Kizu emotes: native Twitch bottom-sheet constructor was not unique.")
        val declaration = classDefBy(constructor.definingClass).methods.filter {
            it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) ==
                constructor.parameterTypes.map(CharSequence::toString) && it.returnType == constructor.returnType
        }
            .singleOrNull() ?: throw PatchException("Kizu emotes: native bottom-sheet constructor declaration was not found.")
        if (!AccessFlags.PUBLIC.isSet(declaration.accessFlags) || previewResourceIds.size != 6) {
            throw PatchException("Kizu emotes: native preview bridge contract changed.")
        }

        val preview = mutableClassDefBy(PREVIEW)
        fun replace(name: String, locals: Int, body: String) {
            val original = preview.methods.filter { it.name == name && AccessFlags.STATIC.isSet(it.accessFlags) }
                .singleOrNull() ?: throw PatchException("Kizu emotes: preview bridge $name was not unique.")
            val replacement = ImmutableMethod(
                original.definingClass,
                original.name,
                original.parameters,
                original.returnType,
                original.accessFlags,
                original.annotations,
                original.hiddenApiRestrictions,
                MutableMethodImplementation(locals),
            ).toMutable()
            replacement.addInstructions(0, body)
            preview.methods.remove(original)
            preview.methods.add(replacement)
        }

        replace("createDialog", 3, """
            new-instance v0, ${constructor.definingClass}
            const/4 v1, 0x0
            invoke-direct {v0, p0, v1}, $constructor
            return-object v0
        """.trimIndent())
        replace("resourceIds", 6, previewResourceIds.mapIndexed { index, id -> "const v$index, $id" }
            .joinToString("\n") + """

            filled-new-array/range {v0 .. v5}, [I
            move-result-object v0
            return-object v0
        """.trimIndent())
    }
}

