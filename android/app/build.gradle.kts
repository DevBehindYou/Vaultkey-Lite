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
    // vault-core: VaultKeyGraph, used directly by MainActivity's MethodChannel handler.
    // keyboard: FlutterVaultIME (in this module) imports CredentialSuggestionInjector
    //   and CredentialChip from it directly — see PHASES.md's Phase 6 notes on why
    //   the IME service class itself lives here rather than in :keyboard.
    // autofill: no compile-time dependency needed, but included so `flutter build apk`
    //   produces one APK containing the autofill service too.
    implementation(project(":vault-core"))
    implementation(project(":keyboard"))
    implementation(project(":autofill"))
    implementation(libs.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
