# 🛡️ FallGuard — Elderly Fall Detection Alert App

An Android application that alerts caregivers in real-time when an elderly person falls. Part of the **Elderly Fall Detection System using Computer Vision and AI** major project.

## How It Works

```
Camera (Python + MediaPipe) → detects fall → writes to Firebase → triggers Android app → ALARM! 🚨
```

1. A **Python script** (running on a laptop/Raspberry Pi) uses a camera and MediaPipe computer vision to detect falls
2. When a fall is detected, the script writes the alert to **Firebase Realtime Database** and uploads a video clip to **Cloudinary**
3. This Android app runs a **24/7 background service** that listens for Firebase changes
4. When `FALL_DETECTED` or `SUSPICIOUS` is received, the app triggers a **full-screen alarm** on the caregiver's phone
5. Fall events are saved locally and displayed as **cards with video thumbnails** in the dashboard

## Related Repository

This Android app is the **caregiver client**. The **camera-side Python backend** that detects falls using MediaPipe computer vision lives here:

🔗 **[fall_detection_project](https://github.com/grassy345/fall_detection_project)** — Python script that runs on a laptop/Raspberry Pi, detects falls via camera, and writes alerts to Firebase.

## Features

| Status | Feature | Description |
|--------|---------|-------------|
| ✅ | **Firebase Connection** | Real-time listener on Firebase RTDB for fall alerts |
| ✅ | **Background Service** | Foreground service survives app close, phone sleep, and reboot |
| ✅ | **Battery Exemption** | Requests Android not to kill the service |
| ✅ | **Boot Auto-Start** | Service restarts automatically when phone reboots |
| ✅ | **Full-Screen Alarm** | Loud alarm with screen wake, DND bypass, and vibration |
| ✅ | **Login / Register** | Firebase Authentication with email + password |
| ✅ | **Dashboard** | Fall event history as cards with video thumbnails |
| ✅ | **Navigation Drawer** | Settings, GitHub link, and logout via slide-out menu |
| ✅ | **Video Player** | In-app video playback of fall clips (ExoPlayer/Media3) |
| ✅ | **Video Download** | Save fall clips to device storage |
| ✅ | **Settings** | Custom alarm tone, video save location, about section |
| ✅ | **Full History** | View all past fall events beyond the latest 10 |
| ✅ | **Notification Tap** | Tapping the alert notification opens the dashboard and highlights the matching card |
| ✅ | **Acknowledge** | Caregiver can acknowledge alerts to stop the alarm and reset Firebase |

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Language** | Kotlin |
| **Min SDK** | API 26 (Android 8.0 Oreo) |
| **Target SDK** | API 35 (Android 15) |
| **Backend** | Firebase Realtime Database, Firebase Authentication, Firebase Cloud Messaging |
| **Local Storage** | Room Database (SQLite) with LiveData |
| **Video** | ExoPlayer (Media3) for playback, Glide for thumbnails |
| **UI** | Material Design 3, DrawerLayout, RecyclerView |
| **Build System** | Gradle 8.9 with Kotlin DSL + KSP |

## Project Structure

```
Android application/
├── build.gradle.kts                        # Root build config (plugin versions)
├── settings.gradle.kts                     # Project name + repositories
├── gradle.properties                       # Gradle settings
├── gradlew.bat / gradlew                   # Build commands (Windows / Mac-Linux)
└── app/
    ├── build.gradle.kts                    # App dependencies
    ├── google-services.json                # ⚠️ NOT in repo — see setup below
    └── src/main/
        ├── AndroidManifest.xml             # Permissions, activities, services, receivers
        └── java/com/fallguard/app/
            ├── MainActivity.kt             # Dashboard with drawer, cards, and event list
            ├── LoginActivity.kt            # Firebase Auth login/register screen
            ├── AlarmActivity.kt            # Full-screen alarm with sound and vibration
            ├── FallDetectionService.kt      # 🔥 Core: 24/7 background Firebase listener
            ├── SettingsActivity.kt          # Alarm tone, save location, about
            ├── FullHistoryActivity.kt       # View all past fall events
            ├── VideoPlayerActivity.kt       # In-app video player (ExoPlayer)
            ├── FallEvent.kt                 # Room entity — fall event data model
            ├── FallEventDao.kt              # Room DAO — database queries
            ├── FallDatabase.kt              # Room database singleton
            ├── FallEventAdapter.kt          # RecyclerView adapter for event cards
            ├── BootReceiver.kt              # Auto-starts service on phone reboot
            └── MyFirebaseMessagingService.kt # FCM push notification handler
        └── res/
            ├── layout/
            │   ├── activity_main.xml        # Dashboard with toolbar, drawer, cards
            │   ├── activity_settings.xml    # Settings screen
            │   ├── activity_full_history.xml # Full history screen
            │   ├── activity_video_player.xml # Video player screen
            │   ├── item_fall_event.xml      # Individual card layout
            │   └── nav_drawer_header.xml    # Drawer header with app icon
            ├── menu/
            │   ├── toolbar_menu.xml         # AppBar settings icon
            │   ├── nav_drawer_menu.xml      # Drawer navigation items
            │   └── video_player_menu.xml    # Video player save icon
            ├── drawable/                     # App icon vectors
            ├── mipmap-anydpi-v26/           # Adaptive icon definition
            └── values/
                ├── colors.xml               # Color palette
                ├── strings.xml              # App strings
                └── themes.xml               # App themes (FallGuard, DarkStatusBar, Alarm)
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
5. Enable the following Firebase services:
   - **Realtime Database** (Build → Realtime Database → Create Database)
   - **Authentication** (Build → Authentication → Email/Password)
   - **Cloud Messaging** (Build → Cloud Messaging)

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

1. Open FallGuard → Log in or register with email + password
2. Grant all requested permissions (notifications, battery optimization, overlay)
3. Check notification bar → "FallGuard Active" should appear
4. In Firebase Console → Realtime Database, add/update data at `/fall_alert`:
   ```json
   {
     "fall_status": "FALL_DETECTED",
     "timestamp": "05-03-2026 18:30:00",
     "acknowledged": false
   }
   ```
5. The alarm should trigger on the phone with full-screen alarm activity
6. After acknowledging, a card should appear on the dashboard

## Firebase Database Structure

```json
{
  "fall_alert": {
    "fall_status": "FALL_DETECTED",
    "timestamp": "05-03-2026 18:30:00",
    "acknowledged": false,
    "clip_url": "https://res.cloudinary.com/.../fall_clip.mp4"
  }
}
```

| Field | Type | Values | Description |
|-------|------|--------|-------------|
| `fall_status` | String | `FALL_DETECTED`, `SUSPICIOUS`, `NORMAL` | Current detection state |
| `timestamp` | String | `DD-MM-YYYY HH:mm:ss` | When the event was detected |
| `acknowledged` | Boolean | `true` / `false` | Whether caregiver acknowledged the alert |
| `clip_url` | String | Cloudinary URL | Video clip of the fall event (added after upload) |

### Python Backend — Writing to Firebase

Use `update()` to write all fields at once (triggers only one listener callback):

```python
from firebase_admin import db

ref = db.reference("fall_alert")
ref.update({
    "fall_status": "FALL_DETECTED",
    "timestamp": "05-03-2026 18:30:00",
    "acknowledged": False
})
```

## App Permissions

| Permission | Why It's Needed |
|------------|----------------|
| `INTERNET` | Firebase communication |
| `FOREGROUND_SERVICE` | 24/7 background monitoring |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on phone reboot |
| `POST_NOTIFICATIONS` | Show alert and monitoring notifications |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Android from killing the service |
| `WAKE_LOCK` | Keep CPU awake during alert processing |
| `USE_FULL_SCREEN_INTENT` | Show alarm over lock screen |
| `ACCESS_NOTIFICATION_POLICY` | Override Do Not Disturb for emergencies |
| `SYSTEM_ALERT_WINDOW` | Display alarm overlay on Android 10+ |

## Debugging Tips

| Command | Purpose |
|---------|---------|
| `adb devices` | Check if phone is connected |
| `adb logcat -s FallDetectionService` | View service logs |
| `adb logcat -s AlarmActivity` | View alarm logs |
| `adb logcat -s MainActivity` | View dashboard logs |
| `adb logcat \| findstr fallguard` | Search all logs for FallGuard |
| `adb shell dumpsys activity services \| findstr fallguard` | Check if service is running |

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Production-ready client version |
| `dev` | Developer version with additional debug features (e.g., long-press card deletion) |

## Future Roadmap

- Multiple camera support
- Multiple caregiver accounts
- Two-way audio communication
- Fall event statistics and analytics
- Emergency SOS contact integration

## License

This project is part of a college Major Project (S6). All rights reserved.
