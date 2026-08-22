plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.personalvault"
    compileSdk = 35

    defaultConfig {
        // The public Google Play identity — permanent once published, and deliberately NOT
        // "com.example.*" (which Play rejects). The internal `namespace` above stays as the
        // original package so no source files need moving; only this outward-facing ID changes.
        applicationId = "com.newlifetech.easyarchive"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.0"
    }

    signingConfigs {
        // Fixed, checked-in debug key (harmless to commit — debug keys carry no real
        // security value) so every CI build is signed identically. Without this, Gradle's
        // auto-generated debug key differs on every fresh GitHub Actions runner, which is
        // exactly why every previous debug APK required Uninstall before reinstalling.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        create("release") {
            // Populated by the GitHub Actions workflow from repo secrets — never hardcode
            // real values here, since this file is public in the repo.
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Only actually signs when the KEYSTORE_PATH env var is present (CI with secrets
            // configured); a plain local `gradle bundleRelease` without it just won't sign,
            // rather than crashing the build.
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    // Lets us detect when the WHOLE app (not just one screen) goes to background,
    // so the vault can re-lock itself instead of staying unlocked forever.
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room database (local storage on device)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coil for showing images from local file/content uris
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Background trash cleanup
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Fingerprint / biometric lock
    implementation("androidx.biometric:biometric:1.1.0")

    // Encrypted SharedPreferences for storing the PIN safely
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
