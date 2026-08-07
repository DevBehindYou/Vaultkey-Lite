// This is Flutter's standard settings.gradle.kts template (current
// dev.flutter.flutter-plugin-loader model), extended with three include()
// lines for our own pre-existing native modules. Nothing else here is
// custom — if `flutter create` ever needs to regenerate this file, only the
// three extra include() lines and their plugin-version pins below need to
// be re-added.
pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        file("local.properties").inputStream().use { properties.load(it) }
        val flutterSdkPath = properties.getProperty("flutter.sdk")
        require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
        flutterSdkPath
    }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    // Same verified-compatible versions as before the Flutter migration —
    // see PHASES.md for why these three were checked against official
    // compatibility tables rather than guessed.
    id("com.android.application") version "8.7.2" apply false
    // Declared here (with the version) and applied WITHOUT a version in the
    // library modules. When the Flutter plugin loader is active it puts AGP on
    // the classpath with an "unknown version"; a module re-requesting AGP *with*
    // a version (the old `alias(libs.plugins.android.library)`) then fails with
    // "already on the classpath with an unknown version". Applying by bare id
    // resolves against this declaration instead. Same pattern the app uses.
    id("com.android.library") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // The Compose compiler plugin was dropped with the Flutter migration — no
    // module in this build applies Compose anymore (the archived
    // legacy_compose_ui module is not included below).
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

include(":app")

// --- our pre-existing native modules, unchanged by the Flutter migration ---
include(":vault-core")
include(":keyboard")
include(":autofill")
