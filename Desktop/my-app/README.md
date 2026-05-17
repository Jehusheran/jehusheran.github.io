# Namma‑Nala (Kotlin Android)

Namma‑Nala is a **Canal Health Monitor** app that lets farmers act as “Canal Guards”.
They can report breaches/leaks with **photo + GPS**, post **water-reached** updates for villages, track **maintenance schedules**, and log **silt alerts**.

## Features (matches your problem statement)

- **Breach Report**
  - Take a photo
  - Capture GPS (Fused Location Provider)
  - App shows **distance to nearest milestone** (success criterion)
  - Submit (stored offline in Room DB)

- **Water Status (Crowdsourced Feed)**
  - Post “Water reached [Village]”
  - Feed shows **timestamp of last update** (success criterion)
  - “Update” button refreshes the timestamp

- **Maintenance Tracker**
  - Mock schedule data (simulation hint)

- **Silt Alert**
  - Log silt-heavy stretches (stored offline)

- **Canal Map (Low bandwidth)**
  - Lightweight overlay screen (Compose `Canvas`) with primary + secondary canal paths
  - No map tiles / no API key required (optimized for low bandwidth)

## How to run

- **Open in Android Studio**: open this folder as a Gradle project.
- **Build from terminal** (Windows PowerShell):

```bash
gradle :app:assembleDebug
```

- **APK output**:
  - `C:\Users\Admin\AppData\Local\Temp\namma-nala-build\_app\outputs\apk\debug\app-debug.apk`

## Firebase Authentication (email & password)

1. In [Firebase Console](https://console.firebase.google.com/), create a project (or use an existing one) and add an **Android app** with package name **`com.nammanala`**.
2. Download **`google-services.json`** and **replace** the file at `app/google-services.json` in this repo (the bundled file is only a structural placeholder).
3. In Firebase Console go to **Build → Authentication → Sign-in method** and enable **Email/Password**.
4. Run the app: you’ll see **Sign in / Create account**. After sign-in, the main Namma‑Nala home screen appears; use **Sign out** in the top bar to leave the session.

## Customize milestones / villages

Edit `app/src/main/java/com/nammanala/MainActivity.kt` and update `NearestLocator.milestones` with your real milestone or village coordinates.

