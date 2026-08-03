# Deployment

You don't need Android Studio or the Flutter SDK installed locally for any
of this — **this project isn't Flutter** (see the note at the very bottom of
this file), and the two GitHub Actions workflows in `.github/workflows/`
handle the entire build/sign/release pipeline on GitHub's own runners.

## What's automated

| Workflow | Trigger | Does |
|---|---|---|
| `android-ci.yml` | every push, every PR | Unit tests, lint, builds a debug APK, uploads it as a workflow artifact. No secrets needed — works the moment you push this repo to GitHub. |
| `android-release.yml` | pushing a tag like `v0.1.0` | Builds a **signed** release AAB + APK, attaches them to a GitHub Release, and (if configured) uploads the AAB straight to the Play Console's internal testing track. |

Download any workflow run's artifacts from the repo's **Actions** tab, or —
for tagged releases — from the **Releases** page.

## One-time setup before your first tagged release

### 1. Generate a signing keystore

You need a JDK locally for this one command (or run it in a throwaway
Codespace/Cloud Shell if you don't have a JDK either — `keytool` ships with
every JDK):

```
keytool -genkey -v -keystore vaultkey-release.jks -keyalg RSA \
  -keysize 2048 -validity 10000 -alias vaultkey
```

It'll ask for a keystore password, your name/org (cosmetic, goes in the
cert), and a key password. **Back this file up somewhere safe outside
GitHub** — if you lose it, you can never publish an update to the same Play
Store listing again; Google can't reset this for you.

### 2. Base64-encode it and add GitHub secrets

```
base64 -w0 vaultkey-release.jks > vaultkey-release.b64   # Linux
base64 -i vaultkey-release.jks | pbcopy                  # macOS, copies to clipboard
```

In your GitHub repo: **Settings → Secrets and variables → Actions → New
repository secret**, add all four:

| Secret name | Value |
|---|---|
| `VAULTKEY_KEYSTORE_BASE64` | contents of the base64 file/clipboard above |
| `VAULTKEY_KEYSTORE_PASSWORD` | the keystore password you chose |
| `VAULTKEY_KEY_ALIAS` | `vaultkey` (or whatever `-alias` you used) |
| `VAULTKEY_KEY_PASSWORD` | the key password you chose |

Without these four, `android-release.yml` fails fast with a clear error
message rather than silently producing an unsigned build.

### 3. (Optional) Play Store auto-upload

Only needed if you want tagged releases to land in the Play Console
automatically, rather than uploading the AAB by hand each time.

1. In [Google Cloud Console](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com), enable the **Google Play Android Developer API** for a project.
2. Create a **service account** in that project (IAM & Admin → Service accounts). Don't grant it any GCP roles.
3. Generate a **JSON key** for that service account and download it.
4. In the [Play Console](https://play.google.com/console), under **Users and permissions**, invite that service account's email and grant it at least *Release to testing tracks* permission for this app.
5. Add the entire downloaded JSON as a GitHub secret named `PLAY_SERVICE_ACCOUNT_JSON`.

With that secret set, every tagged release also uploads to the **internal
testing track** as a `draft` (so nothing goes live without you reviewing it
in the Play Console first). Change `track:`/`status:` in
`android-release.yml` once you're ready to progress to closed/open testing
or production.

### 4. First app listing in the Play Console

The very first version of any app **must** be uploaded manually through the
Play Console web UI once (Google requires this to establish the app
listing, store graphics, content rating questionnaire, data-safety form,
etc.) — after that first manual upload, the automation above can take over
for subsequent versions.

## Versioning

Bump both fields in `app/build.gradle.kts` before tagging a release:

```kotlin
versionCode = 2      // must strictly increase every single release, no exceptions
versionName = "0.2"  // the human-readable version users see
```

Then tag and push — the tag name (e.g. `v0.2.0`) doesn't have to match
`versionName`, but keeping them aligned avoids confusion later.

## Local builds (if you ever do get a JDK + Android SDK on a machine)

No Gradle wrapper is committed to this repo (see `PHASES.md` — it was
authored in a sandbox with no local Gradle to generate the wrapper jar).
Install Gradle 8.9 yourself (e.g. via [sdkman](https://sdkman.io/):
`sdk install gradle 8.9`) and run `gradle assembleDebug` from the project
root — same command the CI workflow runs.

## iOS

There is currently **no iOS project in this repository** — everything here
is native Android (Kotlin + Gradle). This isn't an oversight to fix with a
CI workflow; the entire architecture (a system-default custom keyboard, the
Android Autofill Framework, Android Keystore) has no equivalent that a CI
pipeline could "just build" from this codebase. See `IOS_NOTES.md` for what
an iOS version would actually require.
