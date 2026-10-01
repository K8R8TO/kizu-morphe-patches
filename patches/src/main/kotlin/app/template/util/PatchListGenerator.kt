package app.morphe.util

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.net.URLClassLoader
import java.util.jar.Manifest

typealias PackageName = String
typealias VersionName = String

internal fun main() {
    val buildDir = File("build/libs")
    val patchFiles = buildDir.listFiles { file ->
        file.isFile &&
            file.name.endsWith(".mpp") &&
            !file.name.contains("javadoc") &&
            !file.name.contains("sources")
    }?.sortedBy { it.name }
        ?: error("Could not list $buildDir")

    val patchFile = patchFiles.firstOrNull()
        ?: error("No .mpp patch bundle found in $buildDir")

    val loadedPatches = loadPatchesFromJar(setOf(patchFile))
    val patchClassLoader = URLClassLoader(arrayOf(patchFile.toURI().toURL()))

    patchClassLoader.getResources("META-INF/MANIFEST.MF").asSequence().forEach { manifestUrl ->
        Manifest(manifestUrl.openStream()).use { manifest ->
            manifest.mainAttributes.getValue("Version")?.let { version ->
                generatePatchList(version, loadedPatches)
            }
        }
    }

    if (loadedPatches.isEmpty()) {
        error("Patch bundle loaded successfully, but contains zero patches.")
    }
}

@Suppress("DEPRECATION")
private fun generatePatchList(version: String, patches: Set<Patch<*>>) {
    val listJson = File("../../patches-list.json")

    val patchesMap = patches.sortedBy { it.name }.map {
        JsonPatch(
            it.name!!,
            it.description,
            it.use,
            it.dependencies.map { dependency -> dependency.javaClass.simpleName },
            it.compatiblePackages?.associate { (packageName, versions) -> packageName to versions },
            it.options.values.map { option ->
                JsonPatch.Option(
                    option.key,
                    option.title,
                    option.description,
                    option.required,
                    option.type.toString(),
                    option.default,
                    option.values,
                )
            },
        )
    }

    val gson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    val jsonObject = JsonObject()
    jsonObject.addProperty("version", "v$version")
    jsonObject.add("patches", gson.toJsonTree(patchesMap))

    listJson.writeText(gson.toJson(jsonObject))
}

@Suppress("unused")
private class JsonPatch(
    val name: String? = null,
    val description: String? = null,
    val use: Boolean = true,
    val dependencies: List<String>,
    val compatiblePackages: Map<PackageName, Set<VersionName>?>? = null,
    val options: List<Option>,
) {
    class Option(
        val key: String,
        val title: String?,
        val description: String?,
        val required: Boolean,
        val type: String,
        val default: Any?,
        val values: Map<String, Any?>?,
    )
}
