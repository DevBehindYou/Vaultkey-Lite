allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Flutter expects the Android build outputs under <repo>/build (one level above
// android/), NOT the default android/build — `flutter build apk` looks for
// build/app/outputs/flutter-apk/app-*.apk there. `flutter create` normally
// generates this redirection; it was missing from the hand-authored file, which
// is why assembleDebug succeeded but the tool "couldn't find" the .apk.
val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
