// app/build.gradle.kts — App Build Configuration
// This is the most important config file. It defines:
//   - What kind of app this is (Android app)
//   - What version of Android it supports
//   - What libraries/dependencies it needs (Firebase, etc.)

plugins {
    // "This is an Android application" (not a library)
    id("com.android.application")

    // "We're writing in Kotlin"
    id("org.jetbrains.kotlin.android")

    // "Connect this app to Firebase"
    id("com.google.gms.google-services")

    // Kotlin annotation processing — needed by Room database and Glide
    id("com.google.devtools.ksp")
}

android {
    // Our app's unique ID — like a fingerprint for the Play Store
    namespace = "com.fallguard.app"
    compileSdk = 35  // We compile against Android 15 APIs

    defaultConfig {
        applicationId = "com.fallguard.app"
        minSdk = 26       // Minimum Android version: 8.0 (for notification channels)
        targetSdk = 35    // Target Android version: 15 (latest)
        versionCode = 1   // Internal version number (increment for updates)
        versionName = "1.0"  // Human-readable version shown to users
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Don't shrink code for now (makes debugging easier)
        }
    }

    // Use Java 17 features
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Use Java 17 for Kotlin too
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // === Android Core Libraries ===
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // === Firebase ===
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-messaging")

    // === Room Database — local storage for fall event history ===
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")       // Kotlin coroutines support
    ksp("androidx.room:room-compiler:$roomVersion")              // Annotation processor

    // === Lifecycle — LiveData for auto-refreshing UI ===
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    // === Glide — loads images/video thumbnails from URLs ===
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")

    // === ExoPlayer (Media3) — in-app video playback ===
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // === DrawerLayout — navigation drawer ===
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
}
