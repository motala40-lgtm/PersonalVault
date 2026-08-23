plugins {
    // Bumped from 8.4.0 for Google Play's Aug 31, 2026 target-API-level requirement (needs
    // compileSdk/targetSdk 36) — AGP 8.10.0 is the first release whose own release notes
    // confirm official support for API level 36.
    id("com.android.application") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
