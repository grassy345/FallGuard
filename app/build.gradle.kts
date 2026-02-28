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
    // These are the basic building blocks every Android app needs

    implementation("androidx.core:core-ktx:1.15.0")          // Kotlin extensions for Android
    implementation("androidx.appcompat:appcompat:1.7.0")      // Backward compatibility
    implementation("com.google.android.material:material:1.12.0")  // Material Design UI components

    // === Firebase ===
    // Firebase BOM (Bill of Materials) ensures all Firebase libraries use compatible versions
    // Think of BOM as a "package deal" — it keeps all Firebase versions in sync
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))

    // Firebase Analytics — basic usage tracking (required by Firebase)
    implementation("com.google.firebase:firebase-analytics")

    // Firebase Realtime Database — this is how our app talks to the Python backend
    implementation("com.google.firebase:firebase-database")

    // Firebase Cloud Messaging — for push notifications (used in Feature 2)
    implementation("com.google.firebase:firebase-messaging")
}
