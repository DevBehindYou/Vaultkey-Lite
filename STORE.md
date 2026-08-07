# Play Store submission notes

Everything the Play Console asks for, pre-answered for VaultKey's offline model.
Pair this with `DEPLOYMENT.md` (signing + upload) and `PRIVACY.md` (host it at a
public URL — GitHub Pages of this repo works — and paste that URL into the
listing).

## Data safety form

VaultKey collects and shares **no** data. Answer the form as:

- **Does your app collect or share any of the required user data types?** → No.
- **Is all of the user data encrypted in transit?** → N/A (no data leaves the
  device; there is no network transmission — the release build has no `INTERNET`
  permission).
- **Do you provide a way for users to request that their data is deleted?** →
  Data is local-only; uninstalling the app deletes it. There is no server-side
  data.

If the console requires per-type answers: mark "Passwords" and "Other info" as
**collected: No, shared: No** — they are stored locally and encrypted, never
collected by the developer.

## Listing content checklist

- [ ] App name: **VaultKey**
- [ ] Short description (≤80 chars): e.g. "Offline, encrypted password vault with
      a private keyboard. No cloud, no tracking."
- [ ] Full description: lead with offline/zero-knowledge; explain the keyboard +
      autofill; state plainly that a lost master password means an unrecoverable
      vault (this is a feature, not a bug).
- [ ] Privacy Policy URL (required): public URL of `PRIVACY.md`.
- [ ] App category: Tools (or Productivity).
- [ ] Content rating questionnaire completed.
- [ ] Target audience: not directed at children.
- [ ] Screenshots (phone, min 2): unlock, vault list, add/edit, keyboard.
      Feature graphic 1024×500.
- [ ] Adaptive icon: currently a vector placeholder (`ic_launcher_foreground.xml`)
      — replace with final brand art before launch.

## Pre-launch technical checklist

- [ ] `flutter build appbundle --release` produces a signed AAB (secrets set — see
      `DEPLOYMENT.md`).
- [ ] `aapt dump permissions` on the release artifact shows only `USE_BIOMETRIC`.
- [ ] `targetSdk` meets the current Play requirement (derived from the Flutter
      SDK; bump the pinned Flutter version if Play requires a newer target).
- [ ] Tested restore-from-backup path is disabled (`android:allowBackup="false"`
      is set) so encrypted state can't be exfiltrated via ADB backup.
- [ ] Data-safety form matches the "collects nothing" reality above.
