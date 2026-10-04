group = "app.morphe"

patches {
    about {
        name = "Kizu Twitch Patches"
        description = "Twitch Android patches for Kizu enhancements."
        source = "https://github.com/K8R8TO/kizu-morphe-patches.git"
        author = "Kizu"
        contact = "https://github.com/K8R8TO"
        website = "https://github.com/K8R8TO/kizu-morphe-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

val releaseBundleName = "patches-${project.version}.mpp"
val releaseBundleFile = layout.buildDirectory.file("libs/$releaseBundleName")
val releaseBundleDirectory = layout.buildDirectory.dir("release")

val copyFinishedReleaseBundle = tasks.register("copyFinishedReleaseBundle") {
    description = "Copies the finished Morphe bundle to an immutable release location."
    doLast {
        val source = releaseBundleFile.get().asFile
        val destinationDirectory = releaseBundleDirectory.get().asFile
        check(source.isFile) { "Finished Morphe bundle was not produced: $source" }
        destinationDirectory.mkdirs()
        destinationDirectory.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.extension == "mpp") existing.delete()
        }
        source.copyTo(destinationDirectory.resolve(releaseBundleName), overwrite = true)
    }
}

tasks.named("buildAndroid") {
    finalizedBy(copyFinishedReleaseBundle)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
