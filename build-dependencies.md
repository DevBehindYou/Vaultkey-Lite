# Gradle dependencies by module

## vault-core/build.gradle.kts
```kotlin
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")
    // No networking library anywhere in this module or its dependents — enforce
    // this with a CI check that greps for retrofit/okhttp/ktor imports and fails
    // the build if any appear in vault-core, keyboard, or autofill.
}
```

## keyboard/build.gradle.kts
```kotlin
dependencies {
    implementation(project(":vault-core"))
    // + the forked HeliBoard module, imported as a git submodule or module source copy
    // per the licensing note in 01-research-open-source-keyboard.md (this module is
    // the GPL-3.0 boundary — do not let vault-core depend back on it).
}
```

## autofill/build.gradle.kts
```kotlin
dependencies {
    implementation(project(":vault-core"))
    // AutofillService + inline suggestions are platform APIs (android.service.autofill,
    // android.widget.inline) — no extra artifact needed beyond compileSdk 30+.
}
```

## AndroidManifest.xml additions (keyboard module)
```xml
<service
    android:name=".ime.PrivateKeyboardIME"
    android:permission="android.permission.BIND_INPUT_METHOD"
    android:exported="true">
    <intent-filter><action android:name="android.view.InputMethod"/></intent-filter>
    <meta-data android:name="android.view.im" android:resource="@xml/method"/>
</service>
```

## AndroidManifest.xml additions (autofill module)
```xml
<service
    android:name=".autofill.VaultAutofillService"
    android:permission="android.permission.BIND_AUTOFILL_SERVICE"
    android:exported="true">
    <intent-filter><action android:name="android.service.autofill.AutofillService"/></intent-filter>
</service>
```

## Deliberately absent from every module's manifest
`android.permission.INTERNET` — its absence is the auditable proof that no
component in this app can reach the network, mentioned in the architecture doc's
threat model.
