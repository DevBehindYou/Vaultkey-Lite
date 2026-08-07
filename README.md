# VaultKey — full project (Flutter UI + a thin native Android core)

**Update:** per direct request, this now goes as far into Flutter as an
Android app of this kind can go — the vault UI *and* the keyboard's visuals
are both Dart/Flutter. What's left in Kotlin is the unavoidable minimum:
registering the keyboard and Autofill service with Android (system APIs
with no Dart equivalent, for any Flutter app), and the encryption/database
layer (working, security-critical code with no reason to rewrite). See the
table below, and `PHASES.md`'s Phase 6 section for the full story —
including a real performance/memory tradeoff that came with pushing the
keyboard this far. The old Compose UI is archived, not deleted, in
`legacy_compose_ui/app-compose-reference/`.

**Nothing here has been compiled** — this sandbox still has no Flutter SDK,
Android SDK, or network access — so this is a careful hand-written draft that
needs a real `flutter pub get` / CI run to catch any typos or API mismatches.

## What Flutter does and doesn't cover here

| Layer | Language | Why |
|---|---|---|
| Vault UI (unlock, list, add login, settings) | **Dart/Flutter** (`lib/`) | One shared UI codebase, ready for iOS later. |
| Keyboard visuals + logic (keys, suggestion strip, all styling) | **Dart/Flutter** (`lib/keyboard/`) | Rendered inside a Flutter engine hosted by a thin native shell — see below. |
| Keyboard's OS registration | **Kotlin** (`android/app/.../FlutterVaultIME.kt`, ~150 lines) | `InputMethodService` is an Android-only system class; no Flutter app, by anyone, can implement it in Dart. This file is the entire native footprint of the keyboard. |
| Autofill service | **Kotlin** (`android/autofill`) | Same reasoning as above — `AutofillService` is Android-only, and it has no UI of its own to move to Flutter regardless (the OS renders the suggestion dropdown itself). |
| Encryption/database | **Kotlin** (`android/vault-core`) | Security-critical, unchanged, working code — no reason to rewrite it. Both Flutter engines talk to it over their own `MethodChannel`. |

This is genuinely as far into Flutter as this app can go — see
`PHASES.md`'s Phase 6 section for exactly why, and for the real
performance/memory tradeoff that came with pushing the keyboard this far
(a second Flutter engine now runs inside the keyboard process).

## Project layout

```
vaultkey/
├── pubspec.yaml, lib/
│   ├── main.dart, screens/, services/  — Flutter/Dart: the vault app UI
│   └── keyboard/                       — Flutter/Dart: the keyboard UI (separate entrypoint, keyboardMain())
├── android/
│   ├── app/                      — FlutterFragmentActivity (vault UI host) + FlutterVaultIME (keyboard host) — both Flutter hosts live here, see INTEGRATION.md for why
│   ├── vault-core/                — encryption, Room+SQLCipher database, repository (unchanged)
│   ├── keyboard/                  — CredentialSuggestionInjector + CredentialChip only — pure logic, no UI, no registered service (SimpleVaultIME.kt kept here unregistered, for reference)
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
