plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Must be applied after the Android and Kotlin plugins, per Flutter's
    // own migration guide (see DEPLOYMENT.md for the source link).
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.vaultkey.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    defaultConfig {
        applicationId = "com.vaultkey.app"
        // vault-core/keyboard/autofill all declare minSdk 26 (Autofill
        // Framework's floor) — keep this in lockstep with those.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // Same env-var-driven release signing pattern as before the Flutter
    // migration, just relocated — see DEPLOYMENT.md for the one-time setup.
    val hasReleaseSigningEnv = listOf(
        "VAULTKEY_KEYSTORE_PATH", "VAULTKEY_KEYSTORE_PASSWORD",
        "VAULTKEY_KEY_ALIAS", "VAULTKEY_KEY_PASSWORD"
    ).all { System.getenv(it) != null }

    signingConfigs {
        if (hasReleaseSigningEnv) {
            create("release") {
                storeFile = file(System.getenv("VAULTKEY_KEYSTORE_PATH")!!)
                storePassword = System.getenv("VAULTKEY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VAULTKEY_KEY_ALIAS")
                keyPassword = System.getenv("VAULTKEY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigningEnv) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug") // local `flutter build apk --release` still works without secrets
            }
        }
    }
}

flutter {
    // Path from this file up to the folder containing pubspec.yaml.
    source = "../.."
}

dependencies {
    // The platform channel handler in MainActivity.kt talks to VaultKeyGraph,
    // which lives in vault-core. keyboard/autofill don't need a compile-time
    // dependency here — they're separate system-bound components installed
    // alongside this app, not called into directly from Dart/Kotlin here —
    // but including them means `flutter build apk` produces one APK
    // containing all four components together, which is what you want
    // installed on a device (app + keyboard + autofill service in one place).
    implementation(project(":vault-core"))
    implementation(project(":keyboard"))
    implementation(project(":autofill"))
    implementation(libs.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
