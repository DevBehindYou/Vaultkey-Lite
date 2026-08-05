# Integration

How the Flutter UI, the three native modules, and the MethodChannel bridge
actually connect at runtime, plus every point where this app touches an
Android system service. Read `02-architecture.md` first for *why* things are
split this way — this doc is about the concrete wiring.

## Layer diagram

```
   lib/ (Flutter/Dart)
        |
        |  MethodChannel "com.vaultkey.app/vault"
        v
android/app (MainActivity.kt — FlutterFragmentActivity)
        |
   depends on
        v
android/vault-core  <---- android/keyboard, android/autofill (also depend on vault-core, not on app)
```

- `lib/` never touches Android APIs directly — every native capability
  (crypto, database, biometrics, system settings intents) is reached through
  the one `VaultChannel` class (`lib/services/vault_channel.dart`), which
  calls a single `MethodChannel` implemented in `MainActivity.kt`.
- `android/vault-core` still depends on nothing else in this project — the
  Flutter migration didn't change this boundary at all.
- `android/keyboard` and `android/autofill` still depend only on
  `vault-core`, exactly as before — they have no dependency on `android/app`
  or any awareness that the UI is now Flutter instead of Compose.

## The MethodChannel contract

Channel name: `com.vaultkey.app/vault`. Every method below is implemented in
`MainActivity.kt`'s `setMethodCallHandler` and called from
`VaultChannel` in Dart:

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

## The shared runtime object: VaultKeyGraph (unchanged by the Flutter migration)

Three independent Android entry points still need to see the same unlocked
vault: `MainActivity` (now Flutter-hosting, in `android/app`),
`SimpleVaultIME` (in `android/keyboard`), and `VaultAutofillService` (in
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
| `SimpleVaultIME` | `InputMethodManager` | `android/keyboard/src/main/AndroidManifest.xml` |
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
fork replaces `SimpleVaultIME`'s internals only, and neither Flutter nor
`MainActivity` need to know that happened.
