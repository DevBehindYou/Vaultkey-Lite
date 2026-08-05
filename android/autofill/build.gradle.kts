plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vaultkey.autofill"
    compileSdk = 34

    defaultConfig { minSdk = 26 } // android.service.autofill.AutofillService requires API 26

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":vault-core"))
    implementation(libs.kotlinx.coroutines.android)
}
