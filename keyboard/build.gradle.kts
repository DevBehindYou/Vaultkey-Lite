plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vaultkey.ime"
    compileSdk = 34

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":vault-core"))
    implementation(libs.kotlinx.coroutines.android)

    // PHASE 2b: add the forked HeliBoard module here (as an included build or
    // a copied module source set) and swap SimpleVaultIME's onCreateInputView
    // for the fork's keyboard view, per FORK_NOTES.md in this module.
}
