# 🛡️ FallGuard — Elderly Fall Detection Alert App

An Android application that alerts caregivers when an elderly person falls. Part of the **Elderly Fall Detection System using Computer Vision and AI** major project.

## How It Works

```
Camera (Python + MediaPipe) → detects fall → writes to Firebase → triggers Android app → ALARM! 🚨
```

1. A **Python script** (running on a laptop/Raspberry Pi) uses a camera and MediaPipe computer vision to detect falls
2. When a fall is detected, the script writes the alert to **Firebase Realtime Database**
3. This Android app runs a **24/7 background service** that listens for Firebase changes
4. When `FALL_DETECTED` is received, the app triggers an alarm on the caregiver's phone

## Related Repository

This Android app is the **caregiver client**. The **camera-side Python backend** that detects falls using MediaPipe computer vision lives here:

🔗 **[fall_detection_project](https://github.com/grassy345/fall_detection_project)** — Python script that runs on a laptop/Raspberry Pi, detects falls via camera, and writes alerts to Firebase.

## Features

| Status | Feature | Description |
|--------|---------|-------------|
| ✅ | Firebase Connection | Real-time listener on Firebase RTDB |
| ✅ | Background Service | Foreground service survives app close, phone reboot |
| ✅ | Battery Exemption | Requests Android not to kill the service |
| ✅ | Boot Auto-Start | Service restarts automatically when phone reboots |
| 🔲 | FCM Push Notifications | Wake phone even on silent/DND mode |
| 🔲 | Full Screen Alarm | Loud alarm with full screen takeover |
| 🔲 | Login Page | Firebase Authentication for caregivers |
| 🔲 | Dashboard | Fall event history, monitoring status |
| 🔲 | Settings | Notification toggle, alarm sound, about |

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** API 26 (Android 8.0)
- **Target SDK:** API 35 (Android 15)
- **Backend:** Firebase Realtime Database, Firebase Cloud Messaging
- **Build System:** Gradle 8.9 with Kotlin DSL

## Project Structure

```
Android application/
├── build.gradle.kts                    # Root build config (plugin versions)
├── settings.gradle.kts                 # Project name + repositories
├── gradle.properties                   # Gradle settings
├── gradlew.bat / gradlew              # Build commands (Windows / Mac-Linux)
├── gradle/wrapper/                     # Auto-downloads correct Gradle version
└── app/
    ├── build.gradle.kts               # App dependencies (Firebase, AndroidX)
    ├── google-services.json           # ⚠️ NOT in repo — see setup below
    └── src/main/
        ├── AndroidManifest.xml        # Permissions, services, receivers
        ├── java/com/fallguard/app/
        │   ├── MainActivity.kt        # Main screen, starts service
        │   ├── FallDetectionService.kt # 🔥 Core: background Firebase listener
        │   └── BootReceiver.kt        # Auto-starts service on phone reboot
        └── res/
            ├── layout/activity_main.xml
            ├── drawable/              # App icon vectors
            ├── mipmap-anydpi-v26/     # Adaptive icon definition
            └── values/                # Colors, strings, themes
```

## Prerequisites

Before setting up, install the following:

| Tool | Version | Download |
|------|---------|----------|
| **Java JDK** | 17 or higher | [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) |
| **Android SDK** | Command-line tools | [Android SDK](https://developer.android.com/studio#command-line-tools-only) |

### Android SDK Setup

1. Download and extract Android SDK command-line tools to a folder (e.g., `C:\AndroidSDK`)

2. Set environment variables:
   ```
   ANDROID_HOME = C:\AndroidSDK
   ```
   Add to PATH:
   ```
   %ANDROID_HOME%\cmdline-tools\latest\bin
   %ANDROID_HOME%\platform-tools
   ```

3. Accept licenses and install required packages:
   ```bash
   sdkmanager --licenses
   sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
   ```

## Setup Instructions

### 1. Clone the repository

```bash
git clone https://github.com/grassy345/FallGuard.git
cd FallGuard
```

### 2. Set up Firebase

1. Go to [Firebase Console](https://console.firebase.google.com/) and create a project (or get access to the existing one)
2. Go to **Project Settings** → **Your apps** → **Add Android app**
3. Package name: `com.fallguard.app`
4. Download `google-services.json` and place it in the `app/` folder
5. Enable **Realtime Database** (Build → Realtime Database → Create Database)

> ⚠️ **IMPORTANT:** `google-services.json` contains API keys and is NOT committed to the repo. Each developer must download their own from Firebase Console.

### 3. Build the app

```bash
# Windows
gradlew.bat assembleDebug

# Mac/Linux
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### 4. Install on your phone

```bash
# Connect phone via USB with USB Debugging enabled, then:
adb install app/build/outputs/apk/debug/app-debug.apk

# To reinstall (overwrite existing):
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. Verify it works

1. Open FallGuard → Grant notification + battery permissions
2. Check notification bar → "FallGuard Active" should appear
3. In Firebase Console → Realtime Database, add/update data at `/fall_alert`:
   ```json
   {
     "fall_status": "FALL_DETECTED",
     "timestamp": "2026-02-28 17:00:00",
     "acknowledged": false
   }
   ```
4. Monitor logs: `adb logcat -s FallDetectionService`

## Firebase Database Structure

```json
{
  "fall_alert": {
    "fall_status": "FALL_DETECTED",    // or "SUSPICIOUS"
    "timestamp": "2026-02-28 10:30:00",
    "acknowledged": false
  }
}
```

### Python Backend — Writing to Firebase

Use `update()` to write all fields at once (triggers only one listener callback):

```python
from firebase_admin import db

ref = db.reference("fall_alert")
ref.update({
    "fall_status": "FALL_DETECTED",
    "timestamp": "2026-02-28 10:30:00",
    "acknowledged": False
})
```

## Debugging Tips

| Command | Purpose |
|---------|---------|
| `adb devices` | Check if phone is connected |
| `adb logcat -s FallDetectionService` | View service logs |
| `adb logcat \| findstr fallguard` | Search all logs for FallGuard |
| `adb shell dumpsys activity services \| findstr fallguard` | Check if service is running |

## Future Roadmap

- Video thumbnail of fall events
- Multiple camera support
- Multiple caregiver accounts
- Two-way audio communication

## License

This project is part of a college Major Project (S6). All rights reserved.
