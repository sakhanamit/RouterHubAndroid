/**
 * Build configuration for the RouterHub Android application module (`:app`).
 *
 * Configures Gradle plugins, Android SDK and build settings,
 * signing configurations, build types, and project dependencies.
 */

// Gradle Plugins
plugins {
    // Android Application plugin enables building an Android application APK/bundle
    id("com.android.application")
    // Kotlin Android plugin provides Kotlin language support for Android
    id("org.jetbrains.kotlin.android")
    // Compose compiler plugin for Jetpack Compose support
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Unique package namespace used for generated R and BuildConfig classes
    namespace = "com.routerhub"
    // Compile SDK version against which the application is built
    compileSdk = 34

    defaultConfig {
        // Unique identifier for the application on Google Play and Android devices
        applicationId = "com.routerhub"
        // Minimum Android OS version required to run this app (API 26: Android 8.0 Oreo)
        minSdk = 26
        // Target Android OS version tested and optimized against (API 34: Android 14)
        targetSdk = 34
        // Internal version number used for update management
        versionCode = 1
        // User-facing version string
        versionName = "1.0"
    }

    signingConfigs {
        // Keystore configuration for signing release builds
        // Note: For production environments, consider reading credentials from environment variables or gradle.properties
        create("release") {
            storeFile = file("routerhub-release.jks")
            storePassword = "routerhub123"
            keyAlias = "routerhub"
            keyPassword = "routerhub123"
        }
    }

    buildTypes {
        release {
            // Apply release signing config to sign production APKs/AABs
            signingConfig = signingConfigs.getByName("release")
            // Code shrinking, obfuscation, and optimization with R8 (currently disabled)
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        // Enable Jetpack Compose UI toolkit support
        compose = true
    }

    compileOptions {
        // Java language level compatibility for compiling Java/Kotlin bytecode
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // JVM bytecode target version for Kotlin compiler
        jvmTarget = "17"
    }
}

dependencies {
    // Compose Bill of Materials (BOM) to align Compose library versions
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Jetpack Compose & UI dependencies
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX Security for encrypted key-value storage (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Kotlin Coroutines for asynchronous background execution on Android
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
