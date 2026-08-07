plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vaultkey.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Export the Room schema JSON to a version-controlled folder so every schema
// change is reviewable in git and so Room can auto-verify migrations in tests.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.biometric)
    testImplementation(libs.junit)

    // Intentionally absent: retrofit / okhttp / ktor / any HTTP client.
    // See the CI note in build-dependencies.md — this module must never
    // gain a networking dependency.
}
