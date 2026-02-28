// build.gradle.kts (Root Level) — Master Build Configuration
// This file declares the build tools/plugins used across the ENTIRE project.
// Think of it as saying: "Here are the tools we have available"

plugins {
    // Android build tool — converts our code into an Android app
    id("com.android.application") version "8.7.3" apply false

    // Kotlin language support for Android
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false

    // Google Services plugin — connects our app to Firebase
    id("com.google.gms.google-services") version "4.4.4" apply false
}
