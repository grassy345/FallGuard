// settings.gradle.kts — Project Settings
// This file tells Gradle: "Here's our project name and where to download libraries from"

pluginManagement {
    repositories {
        google()          // Google's library repository (for Android & Firebase)
        mavenCentral()    // The main public library repository
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// This is the name of our project!
rootProject.name = "FallGuard"

// This tells Gradle our app code is in the "app" folder
include(":app")
