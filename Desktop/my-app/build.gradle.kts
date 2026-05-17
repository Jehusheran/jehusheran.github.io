import java.io.File

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// Root build file. Module build logic lives in :app.

// Keep build outputs out of OneDrive-synced folders to reduce file-lock issues on Windows.
allprojects {
    val home = System.getProperty("user.home")
    val base = File(home, "AppData/Local/Temp/namma-nala-build")
    buildDir = File(base, project.path.replace(":", "_"))
}

