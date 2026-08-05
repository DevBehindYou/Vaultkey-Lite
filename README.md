# VaultKey — full project (Flutter UI + native Android core)

**Update:** the vault UI has been rewritten in Flutter/Dart, per your
request. The keyboard and Autofill service stay native Kotlin/Android — see
`IOS_NOTES.md` and the note below for exactly why those two can't be Flutter.
The old all-native-Android version (Compose UI) is archived, not deleted, in
`legacy_compose_ui/app-compose-reference/`.

**Nothing here has been compiled** — this sandbox still has no Flutter SDK,
Android SDK, or network access — so this is a careful hand-written draft that
needs a real `flutter pub get` / CI run to catch any typos or API mismatches.

## What Flutter does and doesn't cover here

| Layer | Language | Why |
|---|---|---|
| Vault UI (unlock, list, add login, settings) | **Dart/Flutter** (`lib/`) | What you asked to move to Flutter — one shared UI codebase, ready for iOS later. |
| Keyboard (`SimpleVaultIME`) | **Kotlin** (`android/keyboard`) | `InputMethodService` is an Android-only system API; Flutter cannot implement a system keyboard. |
| Autofill service | **Kotlin** (`android/autofill`) | Same reasoning — `AutofillService` is Android-only. |
| Encryption/database | **Kotlin** (`android/vault-core`) | No reason to rewrite working, security-critical code — Flutter's Dart side talks to it over a `MethodChannel`. |

The Dart UI and the native Kotlin core talk to each other through a single
`MethodChannel` (`com.vaultkey.app/vault`), implemented in
`android/app/src/main/kotlin/com/vaultkey/app/MainActivity.kt` on the native
side and `lib/services/vault_channel.dart` on the Dart side. See
`INTEGRATION.md` for the full list of channel methods and
`DATA_FLOW.md` for sequence diagrams.

## Project layout

```
vaultkey/
├── pubspec.yaml, lib/            — Flutter/Dart: the vault UI
├── android/
│   ├── app/                      — Flutter's Android host (FlutterFragmentActivity + MethodChannel bridge)
│   ├── vault-core/                — encryption, Room+SQLCipher database, repository (unchanged)
│   ├── keyboard/                  — SimpleVaultIME + suggestion-injector glue (unchanged, just restyled — see PHASES.md)
│   └── autofill/                  — VaultAutofillService (unchanged)
└── legacy_compose_ui/
    └── app-compose-reference/     — the pre-Flutter, all-native Compose UI, kept for reference/rollback
```

## Building (no Flutter SDK or Android Studio needed locally)

Same as before the Flutter migration — the two GitHub Actions workflows in
`.github/workflows/` do the whole build:

- **`android-ci.yml`** — every push/PR: `flutter analyze`, `flutter test`,
  native `vault-core` unit tests, debug APK build.
- **`android-release.yml`** — on a version tag: signed release AAB + APK,
  attached to a GitHub Release, optional Play Store auto-upload.

Both workflows install the Flutter SDK on GitHub's runner via
`subosito/flutter-action` — you never need it locally either, unless you
want to iterate faster than pushing commits.

**One important detail:** this repo has no committed Gradle wrapper under
`android/` (it was written in a sandbox with no Flutter SDK to generate the
real wrapper jar via `flutter create`). Both workflows bootstrap a real one
from a scratch `flutter create` project before building — see the comment
in either workflow file, and `DEPLOYMENT.md`, for why this is the honest
solution rather than a hand-authored binary.

## Reading order

1. `01-research-open-source-keyboard.md` — which keyboard to fork for production typing, and why.
2. `02-architecture.md` — full system design, data model, encryption, threat model.
3. `DATA_FLOW.md` — sequence diagrams for every core flow.
4. `UX_UI_DESIGN.md` — screen inventory, components, states, accessibility, mobile-optimization review.
5. `INTEGRATION.md` — how the Flutter UI, the three native modules, and the MethodChannel bridge fit together.
6. `03-ui-ux-mockup.html` (in the outputs folder, not this zip) — open in a browser.
7. `PHASES.md` — what's built, what's stubbed, what changed in the Flutter migration, what to do next.
8. `DEPLOYMENT.md` — CI/CD, keystore + Play Store setup, versioning, the Gradle-wrapper bootstrap.
9. `IOS_NOTES.md` — why iOS still needs its own native extension work even with Flutter in the picture.
10. `keyboard/FORK_NOTES.md` — how to swap `SimpleVaultIME` for a real HeliBoard fork.
