extension {
    name = "extensions/twitch.mpe"
}

android {
    namespace = "app.morphe.extension"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    // Twitch 31.3.1 provides ConstraintLayout at runtime. This compile-only reference lets
    // GestureTheatreRoot subclass that existing widget without bundling a duplicate library.
    compileOnly("androidx.constraintlayout:constraintlayout:2.2.1")
}
