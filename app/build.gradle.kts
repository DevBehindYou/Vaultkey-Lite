plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.vaultkey.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vaultkey.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures { compose = true }
    // No composeOptions.kotlinCompilerExtensionVersion block needed — the
    // Compose Compiler Gradle plugin (applied above) picks a compiler that
    // always matches the Kotlin version automatically.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Populated from environment variables so the real keystore/passwords
    // never touch source control — see .github/workflows/android-release.yml,
    // which decodes the keystore and exports these before invoking Gradle.
    // Locally (no env vars set), this config is simply skipped and release
    // builds fall back to the debug signing config, so `gradle assembleRelease`
    // still works for local testing without ever needing real secrets.
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
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(project(":vault-core"))
    implementation(project(":keyboard"))   // so Settings can deep-link into IME setup
    implementation(project(":autofill"))   // so Settings can deep-link into Autofill setup

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.navigation.compose)
    implementation(libs.biometric)
    debugImplementation(libs.compose.ui.tooling)
}
