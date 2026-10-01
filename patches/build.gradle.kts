group = "app.morphe"

patches {
    about {
        name = "Kizu Morphe Patches"
        description = "Morphe patches for Kizu/Boost Reddit."
        source = "https://github.com/K8R8TO/kizu-morphe-patches"
        author = "K8R8TO"
        contact = ""
        website = "https://github.com/K8R8TO/kizu-morphe-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch metadata from the generated .mpp."
        dependsOn(build)
        workingDir(project.projectDir)
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}
