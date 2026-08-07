# Integration

How the Flutter UI, the three native modules, and the MethodChannel bridge
actually connect at runtime, plus every point where this app touches an
Android system service. Read `02-architecture.md` first for *why* things are
split this way — this doc is about the concrete wiring.

## Layer diagram

```
   lib/main.dart (Flutter/Dart — vault app UI)     lib/keyboard/keyboard_app.dart (Flutter/Dart — keyboard UI)
        |                                                     |
        |  MethodChannel                                      |  MethodChannel
        |  "com.vaultkey.app/vault"                           |  "com.vaultkey.app/keyboard"
        v                                                     v
   MainActivity.kt  ---------------- same module ----------------  FlutterVaultIME.kt
   (FlutterFragmentActivity)      android/app                      (InputMethodService)
        |                                                     |
        +--------------------------- both depend on ---------------------------+
        v
android/vault-core  <---- android/keyboard (CredentialSuggestionInjector, CredentialChip — pure logic, no UI, no service)
        ^
        |
android/autofill (VaultAutofillService — no UI of its own; the OS renders the suggestion dropdown/inline chip from data this returns)
```

Two separate Flutter engines run in this app: one hosted by `MainActivity`
(the vault app UI, entrypoint `main()`), one hosted by `FlutterVaultIME`
(the keyboard UI, entrypoint `keyboardMain()`). They share no state directly
— each talks to native Kotlin over its own `MethodChannel`, and the native
side reads/writes the same `VaultKeyGraph` singleton either way. See
`PHASES.md`'s Phase 6 notes for why this two-engine, Service-hosted pattern
is the highest-risk part of the whole build.

- `lib/` never touches Android APIs directly — every native capability is
  reached through `VaultChannel` (main app) or `KeyboardChannel` (keyboard).
- `android/vault-core` still depends on nothing else in this project.
- `android/keyboard` is now a pure-logic library — `CredentialSuggestionInjector`
  and `CredentialChip` only, no registered service, no UI. `android/app`
  depends on it directly (imports those two classes from `FlutterVaultIME.kt`).
- `android/autofill` still depends only on `vault-core`.
- The keyboard's `InputMethodService` subclass (`FlutterVaultIME`) lives in
  `android/app`, not `android/keyboard`, specifically so it can resolve the
  Flutter engine dependency the same way `MainActivity` does — only the
  module the Flutter Gradle plugin is applied to can do that reliably.
  Splitting the Flutter-hosting code across two modules would mean
  hand-pinning a `io.flutter:flutter_embedding_*` artifact version in
  `android/keyboard` separately from whatever version Flutter's plugin
  resolves for `android/app`, which risks a runtime engine/framework
  version mismatch — not a risk worth taking for a module split that
  doesn't otherwise need it.

## The two MethodChannel contracts

`com.vaultkey.app/vault` (`MainActivity.kt` ↔ `lib/services/vault_channel.dart`):

| Method | Dart → Kotlin args | Returns |
|---|---|---|
| `getVaultState` | — | `"uninitialized"` / `"locked"` / `"unlocked"` |
| `createVault` | `password` | `bool` |
| `unlockWithPassword` | `password` | `bool` |
| `lock` | — | — |
| `isBiometricAvailable` | — | `bool` |
| `isBiometricEnabled` | — | `bool` |
| `enrollBiometric` | — | `bool` (shows a real `BiometricPrompt`) |
| `unlockWithBiometric` | — | `bool` (shows a real `BiometricPrompt`) |
| `disableBiometric` | — | — |
| `getCredentialSummaries` | — | `List<{id, label, username}>` |
| `getCredentialDetail` | `id` | `{id, label, username, password, notes}?` |
| `addCredential` | `label, webDomain, packageName, username, password, notes` | — |
| `openImeSettings` | — | opens system IME picker |
| `openAutofillSettings` | — | opens system Autofill-service picker |

Password is deliberately excluded from `getCredentialSummaries` — the list
screen never receives plaintext passwords, only `getCredentialDetail` (for
one specific credential, on demand) does. See `DATA_FLOW.md`.

`com.vaultkey.app/keyboard` (`FlutterVaultIME.kt` ↔ `lib/keyboard/keyboard_channel.dart`):

| Direction | Method | Args | Purpose |
|---|---|---|---|
| Kotlin → Dart | `onSuggestions` | `[{id, label}]` | Pushed whenever a field gains/loses focus — empty list clears the strip |
| Dart → Kotlin | `commitText` | `text` | Types a character/string via the real `InputConnection` |
| Dart → Kotlin | `deleteSurroundingText` | `count` | Backspace |
| Dart → Kotlin | `performEditorAction` | — | Triggers `IME_ACTION_GO` |
| Dart → Kotlin | `insertCredential` | `chipId` | Looks up the decrypted credential and commits its username |

Note the asymmetry: the vault channel is entirely Dart-initiated (Dart asks,
Kotlin answers). The keyboard channel is bidirectional — Kotlin has to push
suggestions to Dart proactively, since Dart has no way to know a text field
somewhere else in the OS just gained focus.

## The shared runtime object: VaultKeyGraph (unchanged by the Flutter migration)

Three independent Android entry points still need to see the same unlocked
vault: `MainActivity` (now Flutter-hosting, in `android/app`),
`FlutterVaultIME` (in `android/app`), and `VaultAutofillService` (in
`android/autofill`). This still works exactly as it did before Flutter:

1. All three run in the app's default process (no `android:process` override).
2. `VaultKeyGraph` (in `vault-core`) is a plain Kotlin `object` — one
   instance per process.
3. `VaultKeyApplication.onCreate()` (still exists, just relocated to
   `android/app/src/main/kotlin/com/vaultkey/app/VaultKeyApplication.kt`)
   calls `VaultKeyGraph.init()` first. `MainActivity.configureFlutterEngine()`,
   the IME, and the autofill service all call `init()` defensively too (a
   no-op if already done).

Practical consequence, unchanged: unlocking via the Flutter UI immediately
unlocks the vault for the keyboard and autofill service too — same-process
in-memory state, no IPC.

## System integration points (unchanged)

| Component | Android system surface | Manifest entry |
|---|---|---|
| `FlutterVaultIME` | `InputMethodManager` | `android/app/src/main/AndroidManifest.xml` |
| `VaultAutofillService` | `AutofillManager` | `android/autofill/src/main/AndroidManifest.xml` |
| `CryptoManager` / `BiometricUnlock` | `AndroidKeyStore` + `BiometricPrompt` | `USE_BIOMETRIC` permission only |
| `VaultDatabase` | SQLCipher native library | N/A |

## What changed vs. what didn't

**Changed:** the UI layer (Compose → Flutter/Dart) and the Gradle project
layout (native modules relocated under `android/` to match Flutter's
expected structure, `app` module rebuilt as a thin Flutter host).

**Did not change:** `VaultSession`, `CryptoManager`, `PasswordKeyDerivation`,
`BiometricUnlock`, `FieldCipher`, `VaultMetadataStore`, `CredentialRepository`
(plus the new `getById()`), `CredentialSuggestionInjector`,
`VaultAutofillService`, and `VaultKeyGraph` — all byte-for-byte the same
logic as documented in `DATA_FLOW.md` and `02-architecture.md`.

## Integrating the real HeliBoard fork (Phase 2b) — still applies as-is
Nothing about the Flutter migration changes `keyboard/FORK_NOTES.md` — the
fork would replace `FlutterVaultIME`'s Kotlin shell (or the Dart UI, or both) — see PHASES.md's Phase 6 notes on what that now means. Neither Flutter nor
`MainActivity` need to know that happened.
