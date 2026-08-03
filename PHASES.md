# Build phases — status

Read `README.md` first for how to open this in Android Studio. This file
tracks what's actually done versus stubbed, phase by phase.

## Phase 1 — Vault core (crypto + data) — ✅ done
- `CryptoManager` — Keystore-backed AES-256-GCM wrap/unwrap; now used by
  `VaultSession` to device-bind the password-unlock envelope.
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

## Fixed in the bug-audit pass (crypto + integration correctness)
These address defects that would have surfaced on the first real Gradle
build/run (still not compiler-verified here — run CI to confirm):
- **SQLCipher would not compile** — code imported `net.sqlcipher.database.SupportFactory`
  (the *old*, deprecated `android-database-sqlcipher` API) while depending on
  the current `net.zetetic:sqlcipher-android`. Switched to
  `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` and added the
  required `System.loadLibrary("sqlcipher")` load. `VaultSession.activate()`
  now hands SQLCipher a private copy of the key, since the factory zeroes the
  passphrase after opening the DB.
- **Vault force-locked 30s after unlock, during active use** — `notifyUserActivity()`
  existed but nothing ever called it. Now wired to `MainActivity.onUserInteraction()`,
  every keystroke in `SimpleVaultIME`, and each autofill fill request; default
  idle timeout raised 30s → 5 min; `activate/lock/state` made thread-safe so
  background auto-lock can't tear down the DB mid-query.
- **Autofill silently missed common domains** — web-domain matches were stored
  verbatim but looked up normalized. `CredentialRepository.addCredential()` now
  normalizes `WEB_DOMAIN` values on write with the same rule the lookup uses.
- **Master password KDF ran on the UI thread (ANR risk)** — `UnlockScreen` now
  runs `setUpNewVault`/`unlockWithPassword` on `Dispatchers.Default` with a
  loading state.
- **`CryptoManager` was dead code** — the documented Keystore master-key layer
  is now actually integrated: the password-wrapped DB key is additionally
  wrapped by the hardware-backed Keystore key before hitting SharedPreferences,
  so the persisted blob can't be brute-forced off-device.
- **Vault list went stale after adding a login** — `VaultListScreen` now
  collects a Room `Flow` (`observeAll`) instead of a one-shot load; inserts run
  in a single transaction.
- Smaller: broadened login-field detection (numeric PIN fields), `EncryptedBlob`
  value equality + a round-trip unit test, `markUsed()` wired on chip insert,
  autofill empty-`fillContexts` guard, removed unused imports.

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
- **Auto-lock duration** now defaults to 5 minutes and resets on real user
  activity, but it's still a `VaultSession` constructor default rather than a
  Settings-screen slider — small, mechanical change once you decide the UI.
  Note: it does not yet lock on app-backgrounding (only on idle timeout).
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
