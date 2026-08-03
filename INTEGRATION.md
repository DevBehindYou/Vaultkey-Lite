# Integration

How the four modules actually connect at runtime, and every point where
this app integrates with an Android system service. Read `02-architecture.md`
first for *why* things are split this way — this doc is about the concrete
wiring.

## Module dependency graph

```
        app
      /  |   \
keyboard | autofill
      \  |   /
     vault-core
```

- `vault-core` depends on nothing else in this project (keeps the GPL-3.0
  boundary in `keyboard` from leaking into the encryption/data layer).
- `keyboard` and `autofill` both depend only on `vault-core` — they never
  depend on each other or on `app`.
- `app` depends on all three, but mainly so its Settings screen can
  deep-link into system dialogs for enabling the keyboard/autofill service —
  it doesn't call into their internals directly.

## The shared runtime object: VaultKeyGraph

Three independent Android entry points — `MainActivity` (in `app`),
`SimpleVaultIME` (in `keyboard`), and `VaultAutofillService` (in
`autofill`) — all need to see the **same** unlocked vault. Android doesn't
give you a built-in way to share an object between a foreground Activity, a
background IME, and a background system-bound Service unless they share a
process, so the integration point is:

1. All three run in the app's **default process** (no `android:process` in
   any manifest) — this is a hard requirement, not a suggestion.
2. `VaultKeyGraph` (in `vault-core`) is a plain Kotlin `object` — Android
   guarantees exactly one instance per process, which is exactly the
   "one `VaultSession` per running app" property needed.
3. `VaultKeyApplication.onCreate()` calls `VaultKeyGraph.init()` first, since
   Android always constructs `Application` before any `Activity`/`Service` in
   the same process. The IME and autofill service also call `init()`
   defensively (it's a no-op if already initialized) in case some OEM/launch
   path ever starts one of them without going through the normal
   Application lifecycle first.

Practical consequence: **unlocking the vault from the vault app also unlocks
it for the keyboard and autofill service**, immediately, with no IPC/Binder
call involved — they're reading the same in-memory `VaultSession`.

## System integration points

| Component | Android system surface | Manifest entry |
|---|---|---|
| `SimpleVaultIME` | `InputMethodManager` (the system keyboard picker) | `<service>` with `BIND_INPUT_METHOD`, `android.view.InputMethod` intent-filter, `res/xml/method.xml` |
| `VaultAutofillService` | `AutofillManager` (Settings → Autofill service) | `<service>` with `BIND_AUTOFILL_SERVICE`, `android.service.autofill.AutofillService` intent-filter, `res/xml/autofill_service.xml` |
| `CryptoManager` / `BiometricUnlock` | `AndroidKeyStore` provider + `BiometricPrompt` | No manifest entry — `USE_BIOMETRIC` permission only |
| `VaultDatabase` | SQLCipher's native library, bundled via the `net.zetetic:sqlcipher-android` dependency | N/A |

## Integrating the real HeliBoard fork (Phase 2b)

Covered in detail in `keyboard/FORK_NOTES.md` — the short version for this
doc's purposes: only `keyboard`'s internals change. `CredentialSuggestionInjector`,
its `CredentialChip` data class, and its dependency on `vault-core` all stay
exactly as they are; the fork just becomes the thing calling
`onFieldFocused()`/`clearSuggestions()` instead of `SimpleVaultIME`. No other
module needs to know this happened.

## What is deliberately NOT integrated (and why that's a feature, not a gap)

- **No analytics/crash-reporting SDK.** Any third-party SDK is a networking
  dependency by default, which would break the "no INTERNET permission
  anywhere" claim that's central to this app's trust story (see
  `02-architecture.md`'s threat model). If you add one later, it must be
  audited for offline-safety first, and probably shouldn't live in
  `vault-core`, `keyboard`, or `autofill` at all — `app` is the only module
  where it could arguably belong, and even then it changes what "fully
  offline" means for the whole product.
- **No cloud sync/backup.** Same reasoning — `android:allowBackup="false"`
  in the app manifest is intentional, not a TODO.
