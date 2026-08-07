# VaultKey — Project Audit & Deployment-Readiness Report

_Static review of the entire codebase (no compiler/SDK was available in the
review environment — see "How to verify" at the bottom). Every finding below
cites the file it lives in. Fixes already applied are marked **✅ FIXED**._

---

## 0. Update — full deployment-readiness pass completed

After the initial audit, a full pass was implemented across every approved
track. Status of the roadmap in §4 is now:

**Track A — Security & data integrity — ✅ done**
- Removed `fallbackToDestructiveMigration()` → schema bumps now fail loudly
  instead of wiping a real vault; Room schema export wired (`room.schemaLocation`,
  `vault-core/schemas/`).
- `markUsed` now called on keyboard insert and detail open (was dead).
- Dead `CryptoManager` deleted; stale comments corrected.
- New password-free `getAllSummaries()` (username-only) for the list; all
  multi-row decryption moved to `Dispatchers.Default` (off the main thread).
- Native async channel handlers now convert exceptions into MethodChannel
  errors (`runOnVault`) so a repo failure can't hang the Dart `await`.

**Track B — Core PM features — ✅ done**
- Edit **and** delete a login (repository `updateCredential`/`deleteCredential`
  in a Room transaction; DAO `@Update`/`@Delete`; channel + Dart wired).
- Copy username/password to clipboard with 30s auto-clear.
- Secure password generator (`Random.secure`) + strength meter (`lib/utils/password.dart`).
- Show-password toggles on unlock and add/edit; submit-on-enter + autofocus.
- Real dark theme following the system setting; "Lock vault now" in Settings.

**Track C — Keyboard buildout — ✅ done**
- Rebuilt from a letters-only POC into a usable keyboard: number + two symbol
  layers, punctuation (`,`/`.`) on the bottom row, shift with double-tap
  caps-lock, and backspace auto-repeat on hold.

**Track D — CI/CD, tests & release — ✅ done**
- Real Dart test suite added (`test/` — was empty, so `flutter test` ran nothing):
  password generator/strength, model mapping, channel-mapping via mock messenger.
- CI hardened: pinned+cached Flutter SDK, concurrency-cancel, advisory
  `dart format` gate. Pruned all dead Compose/nav/lifecycle deps from the version
  catalog. `pubspec.lock` now committed (app reproducibility).

**Track E — Store & compliance — ✅ done**
- `PRIVACY.md` (offline/zero-collection policy), `STORE.md` (Play data-safety
  answers + listing checklist), `.gitattributes` (LF normalization).
- Accessibility: tooltips/semantics on icon-only controls (FAB, settings,
  copy/reveal, keyboard specials).

**Still needs a real toolchain (can't be done statically here):** run
`flutter analyze`/`flutter test`/`flutter build apk` to catch any Dart API drift;
generate the committed Gradle wrapper jar; supply the final adaptive launcher
icon; capture store screenshots. See §5.

---

_Original audit below, for the record._

---

## 1. Context, scope & the problem it solves

**VaultKey is an offline, zero-knowledge password manager for Android.** It
stores logins encrypted at rest and fills them in two ways: a custom keyboard
(IME) and the Android Autofill framework. There is no cloud, no account, no
network — `vault-core` is contractually forbidden from ever gaining an HTTP
dependency (`build-dependencies.md`), and the release manifest carries no
`INTERNET` permission.

**The security model (this is the product):**

| Layer | Mechanism |
|---|---|
| Master password → key | PBKDF2-HMAC-SHA256, 210k iterations (`PasswordKeyDerivation.kt`) |
| Biometric → key | Keystore AES key, `setUserAuthenticationRequired(true)`, invalidated on re-enrollment (`BiometricUnlock.kt`) |
| DB at rest | SQLCipher (AES-256) whole-file encryption (`VaultDatabase.kt`) |
| Per-field | Second AES-GCM layer with the live session key, _inside_ the encrypted DB (`FieldCipher.kt`) |
| Key lifetime | One in-memory copy, dropped on lock/idle (`VaultSession.kt`) |

**Architecture:** Flutter renders both UIs — the vault app (`main()`) and the
keyboard (`keyboardMain()`) — as **two separate Flutter engines** in one
process. A thin Kotlin layer does only what Dart cannot: register the
`InputMethodService` and `AutofillService`, run the crypto/Room/SQLCipher core,
and bridge each engine over its own `MethodChannel`. All three native entry
points share one `VaultKeyGraph` singleton, so unlocking anywhere unlocks
everywhere in-process.

**Maturity:** This is a **hand-written draft that has never been compiled**
(stated plainly in `README.md`). So the deployment-readiness priority order is:
**(1) make it compile → (2) fix correctness/security bugs → (3) fill product
gaps → (4) harden CI/CD & tests.**

---

## 2. Severity-ranked findings

### 🔴 CRITICAL — build-breaking, data-loss, or security-model bugs

| # | Finding | Location | Status |
|---|---|---|---|
| C1 | **Won't compile: SQLCipher package mismatch.** Code imported `net.sqlcipher.database.SupportFactory` (legacy `android-database-sqlcipher`), but the dependency is `net.zetetic:sqlcipher-android:4.6.1`, whose package is `net.zetetic.database.sqlcipher.*`. Unresolved reference → `vault-core` fails → nothing builds. | `VaultDatabase.kt:12` | ✅ FIXED |
| C2 | **SQLCipher wipes the live session key.** `SupportFactory`'s `clearPassphrase` defaults to `true`, zeroing the passphrase array after the first DB open. `VaultSession.rawDbKey` was that same array, so after the first query it became all-zeros — later **biometric enrollment from Settings would wrap a zeroed key**, silently breaking biometric unlock. Now passes a `.clone()`. | `VaultDatabase.kt:59`, `VaultSession.kt:127` | ✅ FIXED |
| C3 | **Auto-lock never re-arms.** `VaultSession.notifyUserActivity()` is documented "call on every interaction" but **nothing ever called it** — the 30s idle timer started at unlock and fired regardless of activity, locking the vault mid-use (e.g. part-way through adding a login → save throws "Vault is locked"). Now called on every vault-channel and keyboard-channel interaction. | `VaultSession.kt:117`, `MainActivity.kt`, `FlutterVaultIME.kt` | ✅ FIXED |
| C4 | **Data-loss on any schema change.** `fallbackToDestructiveMigration()` silently deletes the entire vault when the schema version changes. Acceptable in dev, **unacceptable once real users have data.** Needs real `Migration` objects (or remove so a bad upgrade throws instead of wiping). | `VaultDatabase.kt:63` | ⬜ Roadmap |

### 🟠 HIGH — crashes, leaks, missing core features

| # | Finding | Location | Status |
|---|---|---|---|
| H1 | **Undisposed `TextEditingController`s** (2 in unlock, 6 in add/edit) — real leak; `flutter_lints` fails on it. | `unlock_screen.dart`, `add_edit_screen.dart` | ✅ FIXED |
| H2 | **Infinite spinner on any channel error.** `_StartupGate` and the screen loaders never handled a thrown/`hasError` future — a single `MethodChannel` failure (very likely during first bring-up) left a dead, un-recoverable spinner. Now surfaced with a way forward. | `main.dart`, `unlock_screen.dart`, `vault_list_screen.dart` | ✅ FIXED (list/unlock/gate) |
| H3 | **Cannot edit or delete a credential.** List row opens a read-only dialog; the "add" screen is add-only; the repository has no `update`/`delete`. A password manager must have these. | `vault_list_screen.dart`, `CredentialRepository.kt` | ⬜ Roadmap |
| H4 | **No copy-to-clipboard, no password generator, no strength meter, no clipboard auto-clear.** Table-stakes PM features are entirely absent. | `vault_list_screen.dart`, `add_edit_screen.dart` | ⬜ Roadmap |
| H5 | **Keyboard is a non-functional proof-of-concept.** No numbers/symbols layer (the `123` key's `onTap` is empty), no punctuation, no long-press, no autorepeat/backspace-hold, no cursor control. Either a launch blocker or the keyboard must be de-scoped for v1. | `keyboard_app.dart:140` | ⬜ Roadmap / decision |

### 🟡 MEDIUM — robustness, performance, mobile, config

| # | Finding | Location | Status |
|---|---|---|---|
| M1 | **`getCredentialSummaries` decrypts full records incl. passwords**, then throws the passwords away — needless CPU and needless plaintext in memory. Add a summary path that skips password/notes decryption. | `MainActivity.kt:109`, `CredentialRepository.kt:56` | ⬜ Roadmap |
| M2 | **Field decryption runs on the main thread** (`getAll().map{decrypt()}` resumes on `Dispatchers.Main`) — jank for large vaults. Move mapping to `Dispatchers.Default`. | `CredentialRepository.kt` | ⬜ Roadmap |
| M3 | **No dark theme.** `theme.dart` builds only a light theme (no `darkTheme`/`themeMode`); the keyboard is dark while the app is light. | `theme.dart` | ⬜ Roadmap |
| M4 | **`markUsed()` is dead code** — never called, so "recently used" ordering can never work. Wire it into autofill fill + keyboard insert + detail open. | `CredentialRepository.kt:66` | ⬜ Roadmap |
| M5 | **`CryptoManager` is entirely unused** (confirmed by grep — only its own file references it; even the doc comments in `Entities.kt`/`VaultDatabase.kt` that name it are stale). Remove it or wire it, and fix the stale comments. | `CryptoManager.kt` | ⬜ Roadmap |
| M6 | **Dead build dependencies.** After the Flutter migration, `libs.versions.toml` still declares Compose BOM, `activity-compose`, `navigation-compose`, `lifecycle-*`, material-icons, etc. — none referenced by the shipping build. Prune for a clean, auditable dependency graph. | `libs.versions.toml` | ⬜ Roadmap |
| M7 | **Keyboard/IME window isn't `FLAG_SECURE`.** The vault Activity is (`MainActivity.kt:44`), but the keyboard's suggestion strip renders credential labels without it. | `FlutterVaultIME.kt` | ⬜ Roadmap |
| M8 | **No "show password" on entry fields; no submit-on-enter/autofocus.** (`revealPassword` exists only in the detail dialog.) | `unlock_screen.dart`, `add_edit_screen.dart` | ⬜ Roadmap |

### 🟢 LOW — polish, a11y, store readiness

- **Accessibility:** icon-only controls (settings, FAB, visibility toggle) lack `Semantics`/tooltips; keyboard keys expose no semantics; `muted` `#87868C` on paper likely fails WCAG AA contrast. (`vault_list_screen.dart`, `keyboard_app.dart`)
- **Room `@Entity data class` with `ByteArray`** triggers `equals()/hashCode()` lint warnings — expected but should be suppressed or restructured. (`Entities.kt`)
- **Autofill gaps (known):** `onSaveRequest` is a no-op (can't offer "save this login?"); inline (API 30+) suggestions stubbed. (`VaultAutofillService.kt:91`)
- **Store readiness:** no privacy policy, no Play Data-safety mapping, no store listing/screenshots, no adaptive-icon polish pass.

---

## 3. What was fixed in this pass

1. **C1** — SQLCipher import → `net.zetetic.database.sqlcipher.SupportFactory` (unblocks the whole build).
2. **C2** — `SupportFactory(passphrase.clone())` so SQLCipher can't wipe the live session key / break later biometric enrollment.
3. **C3** — `notifyUserActivity()` wired into both `MethodChannel` handlers; auto-lock now behaves like a real idle timer.
4. **C2/C3 safety** — `rawDbKey`/`database`/`fieldCipher` marked `@Volatile` (auto-lock mutates them off-thread).
5. **H1** — all `TextEditingController`s disposed.
6. **H2** — error handling in `_StartupGate`, `unlock_screen._load/_submit`, `vault_list._refresh`, `add_edit._save` (no more dead spinners; failures are surfaced).
7. UTF-8 made explicit across the encrypt/decrypt path (protects the JVM crypto tests).
8. `pubspec.yaml` version → `0.1.0+1` (valid Play `versionCode`).

_None of these required a product decision; all are pure correctness/robustness._

---

## 4. Deployment-readiness roadmap (prioritized)

**Track A — Security & data integrity (do first, before any real user data)**
- C4 real Room migrations (or fail-closed) · M4 wire `markUsed` · M5 remove dead `CryptoManager` + fix stale comments · M1/M2 summary path + off-main decrypt · confirm SQLCipher native lib loads at runtime.

**Track B — Core password-manager features**
- H3 edit/delete (+ repository `update`/`delete`) · H4 copy-to-clipboard w/ auto-clear, password generator, strength meter · M8 show-password + submit/autofocus · M3 dark theme.

**Track C — Keyboard (product decision needed)**
- H5: either invest in a usable Dart keyboard (numbers/symbols/punctuation/long-press) or de-scope the keyboard for v1 and ship Autofill-only. This is the single biggest scope fork.

**Track D — CI/CD, tests & release hardening**
- Commit a pinned Gradle wrapper (stop bootstrapping it in CI) · pin the Flutter version (not just `channel: stable`) · add caching + concurrency-cancel · add ktlint/detekt + `dart format --set-exit-if-changed` · add real Dart widget/repo tests (there is **no `test/` dir**, so `flutter test` runs nothing today) · M6 prune dead deps · finalize env-var release signing + Play upload.

**Track E — Store & compliance**
- Privacy policy, Play Data-safety declaration, listing assets, accessibility pass.

---

## 5. How to verify (must run on a real toolchain)

This review was static. Before trusting any of the above, on a machine with the
Flutter SDK + Android SDK:

```bash
flutter pub get
flutter analyze                       # catches Dart typos/API drift this pass couldn't
cd android && ./gradlew :vault-core:compileDebugKotlin   # confirms C1/C2 compile
cd .. && flutter build apk --debug    # end-to-end; watch FlutterVaultIME (highest-risk file)
```

Then on a device/emulator: create a vault → add a login with a package name of
an installed app → set VaultKey as the system keyboard → confirm the suggestion
chip appears and inserts. The riskiest runtime path is the second Flutter engine
hosted inside `FlutterVaultIME` (see `PHASES.md` Phase 6).
