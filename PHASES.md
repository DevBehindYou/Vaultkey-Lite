# Build phases — status

Read `README.md` first for how to open this in Android Studio. This file
tracks what's actually done versus stubbed, phase by phase.

## Phase 1 — Vault core (crypto + data) — ✅ done
- `CryptoManager` — Keystore-backed AES-256-GCM wrap/unwrap (generic).
- `PasswordKeyDerivation` — PBKDF2-HMAC-SHA256 (210k iterations) master-password path.
- `BiometricUnlock` — Keystore key with `setUserAuthenticationRequired(true)`, invalidated on re-enrollment.
- `FieldCipher` — per-field AES-GCM using the live session key (no Keystore round-trip per field).
- `VaultMetadataStore` — salts/wrapped-key blobs in plain SharedPreferences (safe — see its class doc for why).
- `VaultSession` — the one place holding the unlocked key in memory; auto-locks after 30s idle (hardcoded — see "Known gaps" below).
- `Entities` / `VaultDatabase` / `CredentialDao` — Room on SQLCipher.
- `CredentialRepository` — the single read/write API every other module uses.
- `VaultKeyGraph` — process-wide singleton wiring the above together.

## Phase 2 — Keyboard — ✅ walking skeleton done, ⏳ production fork pending
- `SimpleVaultIME` — real, compilable, minimal keyboard with a working
  suggestion strip. Good enough to run on a device/emulator and confirm the
  whole suggestion → tap → insert flow works.
- `CredentialSuggestionInjector` — the reusable glue; already written against
  the final `CredentialRepository` API, so Phase 2b doesn't need to touch it.
- **Not done:** swapping in the actual HeliBoard fork for production-quality
  typing. Step-by-step instructions are in `keyboard/FORK_NOTES.md` — this
  step needs to happen on a machine with network access, since this
  environment can't clone from GitHub.

## Phase 3 — Autofill service — ✅ done
- `VaultAutofillService` — real `FillResponse`/`Dataset` building against
  `CredentialRepository.findForWebDomain`, gated on the vault actually being
  unlocked first (never prompts for the master password itself).
- **Stubbed on purpose:** inline suggestions (Android 11+, rendering inside
  the keyboard strip instead of the dropdown) and `onSaveRequest`'s "save
  this new login?" prompt — both called out in code comments with the exact
  API to reach for next.

## Phase 4 — Vault app UI — ✅ done
- `VaultKeyApplication`, `MainActivity` (now a `FragmentActivity`, required by
  `BiometricPrompt`), `VaultKeyNavHost`.
- Screens: `UnlockScreen` (first-run + everyday unlock + biometric prompt
  wired up for real), `VaultListScreen`, `AddEditCredentialScreen` (two
  explicit optional match fields — web domain and app package — rather than
  guessing which one a single field means), `SettingsScreen` (real intents
  into system IME settings and Autofill settings, real biometric-enrollment
  toggle).
- `BiometricPromptHelper` — wraps `androidx.biometric.BiometricPrompt` around
  `VaultSession`'s Cipher-based methods.
- Theme/typography tokens copied from `03-ui-ux-mockup.html` so the built app
  and the design mockup share one palette.
- Manifest, adaptive launcher icon (vector, no PNGs needed), strings, base
  XML theme, proguard/consumer-proguard stubs, root `gradle.properties`.

## Fixed this session (after a full manual re-read/code review)
- **Biometric prompts could fail/crash on devices with no biometric hardware
  or nothing enrolled** — `BiometricPromptHelper.isAvailable()` now gates
  every place a biometric enroll/unlock prompt is offered (`UnlockScreen`,
  `SettingsScreen`).
- **Toggling shift on the keyboard cleared the visible suggestion chip** —
  `SimpleVaultIME` was calling `setInputView(onCreateInputView())` on every
  shift press, rebuilding the suggestion strip empty along with the keys.
  Fixed by splitting the view into a `keysContainer` that shift rebuilds and
  a separate `suggestionStrip` that it never touches.
- **No Gradle/Kotlin/Compose version verification** — the original version
  numbers were written from memory. Re-checked against current official
  compatibility tables and updated to a verified-compatible set: AGP 8.7.2 +
  Gradle 8.9 + Kotlin 2.0.21 + KSP 2.0.21-1.0.28, plus migrating Compose
  setup to the current Compose Compiler Gradle plugin model (Kotlin 2.0+
  moved off the old `composeOptions.kotlinCompilerExtensionVersion`).
- **No automated tests** — added JVM unit tests for `PasswordKeyDerivation`
  and `FieldCipher` (both pure `javax.crypto`, no Android framework
  dependency, so they run without an emulator) covering round-trip
  correctness and wrong-key/wrong-password rejection. `CryptoManager` and
  `BiometricUnlock` still have no tests — both need `AndroidKeyStore`, which
  means an instrumented test (real device/emulator) or Robolectric, neither
  of which fit a plain JVM unit test.
- **No CI** — added `.github/workflows/android-ci.yml` (build + test on
  every push/PR) and `android-release.yml` (signed AAB on tag push, with
  optional Play Store upload). Since no Gradle wrapper could be generated in
  this sandbox (no local Gradle to run `gradle wrapper`), both workflows use
  `gradle/actions/setup-gradle` to install Gradle 8.9 directly rather than
  relying on a committed `./gradlew`.

## Known gaps — deliberately left for you to decide, not oversights
- **Tapping a credential row in VaultListScreen does nothing** — there's no
  view/edit-existing-credential screen yet, only add-new. Real gap, not
  intentional; see `UX_UI_DESIGN.md`'s screen inventory.
- **No "show password" toggle** anywhere a password is entered or displayed.
- **Auto-lock duration** is hardcoded to 30 seconds in `VaultSession`'s
  constructor default rather than a Settings-screen slider — small, mechanical
  change once you decide the right default and UI for it.
- **Argon2id** was not substituted for PBKDF2 — PBKDF2-HMAC-SHA256 at 210k
  iterations is still an acceptable, audited choice, but Argon2id is stronger
  against GPU/ASIC attacks if you want to add a maintained Argon2 binding later.
- **Room migrations** — `fallbackToDestructiveMigration()` is set, meaning a
  schema change during development wipes the local DB. Replace with real
  `Migration` objects before this ever ships with real user data.
- **Display typeface** — the mockup's geometric display font isn't bundled
  as an actual font resource yet; `Type.kt` uses platform-default fonts at
  matching weights/sizes. See `UX_UI_DESIGN.md`.
- **Accessibility pass** — icon-only buttons need an explicit audit for
  `contentDescription` coverage; keyboard key touch targets are 46dp,
  slightly under the 48dp recommendation. See `UX_UI_DESIGN.md`.
- **Inline autofill suggestions** (Android 11+, rendering inside the
  keyboard strip instead of the dropdown) and `onSaveRequest`'s "save this
  new login?" prompt are both stubbed with the exact API to reach for next
  in code comments.

## Suggested next session
1. Open in Android Studio, let Gradle sync, fix whatever surfaces (this was
   hand-written without a compiler in the loop — see the caveat in every
   earlier message).
2. Run on an emulator: create a vault, add a credential with a package name
   matching some installed app, switch the system keyboard to VaultKey, and
   confirm the suggestion chip appears and inserts text.
3. Then decide: invest in Phase 2b (real HeliBoard fork) or keep iterating on
   Phase 3's inline-suggestions/save-prompt stubs first.

## Phase 5 — Flutter UI migration — done this session
The vault UI (previously Jetpack Compose in the `app` Gradle module) has
been rewritten in Flutter/Dart, per direct request. What changed:

- **Archived, not deleted:** the old Compose UI now lives at
  `legacy_compose_ui/app-compose-reference/` — full Gradle module, untouched,
  in case anything there needs to be referenced or restored.
- **New Flutter app at the repo root:** `pubspec.yaml` + `lib/` — four
  screens (`UnlockScreen`, `VaultListScreen`, `AddEditScreen`,
  `SettingsScreen`), a `VaultChannel` service class wrapping a single
  `MethodChannel`, and `theme.dart` carrying over the exact same color
  tokens as the mockup and the old Compose theme.
- **`android/` restructured to Flutter's expected layout:** `vault-core`,
  `keyboard`, and `autofill` moved under `android/` unchanged; a brand-new
  `android/app` hosts `MainActivity` (now a `FlutterFragmentActivity`,
  still `FragmentActivity`-based for the same `BiometricPrompt` reason as
  before) plus the `MethodChannel` handler that's the only bridge between
  Dart and `VaultKeyGraph`.
- **New repository method:** `CredentialRepository.getById()` /
  `CredentialDao.getById()` — needed so the Dart detail dialog can fetch one
  credential's plaintext without pulling the whole list.
- **CI rewritten** to use `flutter build apk`/`flutter build appbundle`
  instead of raw `gradle` tasks — and since the Flutter tool needs a real
  Gradle wrapper (which this project still doesn't have committed — no
  Flutter SDK in this sandbox to generate one), both workflows now bootstrap
  a genuine wrapper from a scratch `flutter create` project before building.
  This is arguably a bigger sandbox-honesty caveat than anything before it —
  see DEPLOYMENT.md.

**What did NOT change:** every native module's actual logic —
`VaultSession`, `CryptoManager`, `FieldCipher`, `CredentialSuggestionInjector`,
`VaultAutofillService` — is byte-for-byte the same as before this migration.
Only the UI layer and the Gradle project layout moved.

## Keyboard visual restyle — done this session
Direct feedback: the keyboard "looked worst, not like Google keyboard."
`SimpleVaultIME` was rebuilt with a Gboard-style light theme: rounded key
backgrounds (`GradientDrawable` + `StateListDrawable` for a real pressed
state, no XML drawables needed), proper 48dp key height (this also happens
to close the touch-target accessibility gap flagged in `UX_UI_DESIGN.md`),
a staggered middle row, haptic feedback on every key, a language label on
the spacebar, and a pill-shaped, icon-prefixed suggestion chip instead of a
flat rectangle. Still a Phase 2a proof-of-concept, not production typing —
see `keyboard/FORK_NOTES.md` for the real HeliBoard fork plan, which this
restyle doesn't change.

## Biggest remaining risk, stated plainly
The `android/app` Gradle files (`settings.gradle.kts`, `build.gradle.kts`,
the Flutter plugin wiring) were hand-authored against current, verified
Flutter documentation/templates — but normally `flutter create` generates
these, and this session had no Flutter SDK to actually run that command and
diff against. If CI fails on the Flutter-specific plugin wiring (as opposed
to anything in `vault-core`/`keyboard`/`autofill`, which are unchanged and
already had a full review pass), that's the most likely place — start
there, and the error message from `flutter build apk` will usually name the
exact line.
