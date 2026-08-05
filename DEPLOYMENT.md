# Deployment

You don't need the Flutter SDK, Android Studio, or a local Android SDK for
any of this — the two GitHub Actions workflows in `.github/workflows/`
handle the entire build/sign/release pipeline on GitHub's own runners.

## What's automated

| Workflow | Trigger | Does |
|---|---|---|
| `android-ci.yml` | every push, every PR | `flutter analyze`, `flutter test`, native `vault-core` unit tests, builds a debug APK, uploads it as a workflow artifact. No secrets needed. |
| `android-release.yml` | pushing a tag like `v0.1.0` | Builds a **signed** release AAB + APK, attaches them to a GitHub Release, and (if configured) uploads the AAB to the Play Console's internal testing track. |

Download any workflow run's artifacts from the repo's **Actions** tab, or —
for tagged releases — from the **Releases** page.

## The Gradle-wrapper bootstrap step (read this once)

Normally, `flutter create` generates a real Gradle wrapper (`android/gradlew`,
`gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) as part of scaffolding a
new Flutter project. This repo's `android/` folder was hand-assembled in an
environment with no Flutter SDK installed and no network access to run that
command — so there's no committed wrapper, and `gradle-wrapper.jar` is a
compiled binary that can't be faked by hand.

Both workflows solve this the same way: before building anything, they run
`flutter create --platforms=android --org com.vaultkey /tmp/wrapper_bootstrap`
in a scratch directory (using the real Flutter SDK the workflow just
installed), then copy **only** the four wrapper files out of it into this
repo's `android/` folder. Nothing else from that scratch project is used —
our hand-written `settings.gradle.kts`, `build.gradle.kts`,
`app/build.gradle.kts`, and `MainActivity.kt` are untouched. This means every
CI run is building with a genuine, correct wrapper, not a guessed one.

**If you ever get Flutter installed locally**, you can do this once yourself
instead and commit the result, which removes the bootstrap step from CI
entirely:
```
flutter create --platforms=android --org com.vaultkey /tmp/wrapper_bootstrap
cp /tmp/wrapper_bootstrap/android/gradlew* android/
cp -r /tmp/wrapper_bootstrap/android/gradle/wrapper android/gradle/
```

## One-time setup before your first tagged release

### 1. Generate a signing keystore
```
keytool -genkey -v -keystore vaultkey-release.jks -keyalg RSA \
  -keysize 2048 -validity 10000 -alias vaultkey
```
`keytool` ships with any JDK — you don't need the rest of the Android
toolchain just for this command. **Back this file up somewhere outside
GitHub** — lose it, and you can never update the same Play Store listing
again.

### 2. Base64-encode it and add GitHub secrets
```
base64 -w0 vaultkey-release.jks > vaultkey-release.b64   # Linux
base64 -i vaultkey-release.jks | pbcopy                  # macOS
```
In **Settings → Secrets and variables → Actions**, add:

| Secret name | Value |
|---|---|
| `VAULTKEY_KEYSTORE_BASE64` | contents of the base64 file/clipboard above |
| `VAULTKEY_KEYSTORE_PASSWORD` | the keystore password you chose |
| `VAULTKEY_KEY_ALIAS` | `vaultkey` (or whatever `-alias` you used) |
| `VAULTKEY_KEY_PASSWORD` | the key password you chose |

These four are read as environment variables by `android/app/build.gradle.kts`
(same pattern as before the Flutter migration — see its `hasReleaseSigningEnv`
check). Without them, `android-release.yml` fails fast with a clear error.

### 3. (Optional) Play Store auto-upload
1. Enable the **Google Play Android Developer API** for a Google Cloud project.
2. Create a **service account** in that project (no GCP roles needed).
3. Download its **JSON key**.
4. In the [Play Console](https://play.google.com/console), invite that service account's email under **Users and permissions** with *Release to testing tracks* permission.
5. Add the whole JSON as a GitHub secret named `PLAY_SERVICE_ACCOUNT_JSON`.

With that set, tagged releases upload to the **internal testing track** as
a `draft`. Change `track:`/`status:` in `android-release.yml` to progress
further once you're ready.

### 4. First Play Console listing
Google requires the very first version of any app to be uploaded manually
through the Play Console web UI once (to set up the listing, store
graphics, content rating, data-safety form). After that, the automation
above handles subsequent versions.

## Versioning
Bump in `pubspec.yaml`'s `version:` line (Flutter maps this to Android's
`versionName`+`versionCode` automatically — `1.2.3+4` means versionName
`1.2.3`, versionCode `4`):
```yaml
version: 0.2.0+2
```
`versionCode` (`+2` above) must strictly increase every release.

## Local builds (if you ever do get Flutter installed on a machine)
```
flutter pub get
flutter run              # debug, on a connected device/emulator
flutter build apk        # debug APK
flutter build appbundle --release   # signed release AAB (needs the 4 env vars set locally too)
```

## iOS
Still no iOS project in this repo — see `IOS_NOTES.md`. Moving the vault UI
to Flutter makes a future iOS build meaningfully easier (the `lib/` Dart UI
is already cross-platform), but the keyboard and Autofill equivalents still
need to be written as native Swift extensions either way.
