# VaultKey — Privacy Policy

_Last updated: 2026-08-07_

VaultKey is an **offline password manager**. Your data never leaves your device.

## What we collect

**Nothing.** VaultKey has no account, no sign-in, and no analytics. It does not
transmit your credentials, usage, diagnostics, or any other data off the device.

- The release build ships with **no `INTERNET` permission** — the app is
  technically incapable of network transmission. (You can verify this yourself:
  `aapt dump permissions` on the release APK/AAB lists only `USE_BIOMETRIC`.)
- There is no cloud backup, sync, or telemetry, and no third-party SDKs that
  collect data.

## What is stored, and where

Everything is stored locally, encrypted, in the app's private storage:

- Your logins (labels, usernames, passwords, notes, and app/website matches) are
  held in a SQLCipher-encrypted database, with each sensitive field additionally
  encrypted with AES-256-GCM.
- The database key is derived from your master password (PBKDF2-HMAC-SHA256) and,
  if you enable it, wrapped by a hardware-backed key in the Android Keystore for
  biometric unlock.
- Your master password is never stored. If you forget it, the vault **cannot be
  recovered** — this is by design.

## How your data is used

Credentials are decrypted only in memory, only after you unlock the vault, and
only to (a) display them to you, (b) fill them via the VaultKey keyboard, or
(c) fill them via the Android Autofill service you explicitly enabled. The vault
auto-locks after a period of inactivity.

## Permissions

| Permission | Why |
|---|---|
| `USE_BIOMETRIC` | Optional face/fingerprint unlock. VaultKey never sees biometric data — Android only returns an authorization result. |

The keyboard (input method) and autofill service are Android system integrations
you turn on yourself in Settings; neither sends data anywhere.

## Clipboard

When you copy a password, VaultKey clears the clipboard after ~30 seconds to
limit exposure to other apps.

## Contact

Questions about this policy: open an issue on the project repository.
