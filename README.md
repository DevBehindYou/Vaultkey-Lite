# VaultKey — full project

A hand-written Android Studio project (4 modules) implementing an offline,
encrypted password locker with a private keyboard. **This is a native
Android project written in Kotlin — not Flutter.** If you were expecting a
Flutter project: there isn't one here, and none of this codebase runs under
the Flutter SDK. See "No Flutter, no Android Studio needed" below.

**Nothing here has been compiled** — this sandbox has no Android SDK or
network access — so treat it as a careful, consistent draft that needs a
real Gradle sync/CI run to catch any typos or API mismatches. A manual code
review pass was done (see `PHASES.md`'s "Fixed this session" list for real
bugs found and fixed this way), but that's not a substitute for actually
running it.

## No Flutter, no Android Studio needed

This project only needs a JDK + the Android SDK to build — both of which
the two GitHub Actions workflows in `.github/workflows/` provision
automatically on GitHub's own runners. You can develop entirely by pushing
commits and reading CI results, without installing anything locally:

- **`android-ci.yml`** — runs on every push/PR: unit tests, lint, debug APK build.
- **`android-release.yml`** — runs when you push a tag like `v0.1.0`: signed release AAB + APK, attached to a GitHub Release, with optional auto-upload to the Play Console.

Full setup instructions (keystore, secrets, Play Console service account)
are in `DEPLOYMENT.md`.

**iOS**: there is no iOS project in this repo, and none of this Kotlin code
is portable to it — the keyboard, Autofill service, and Keystore usage are
all Android-specific system integrations. `IOS_NOTES.md` explains exactly
why and what a real iOS version would require.

## Module map

```
vaultkey/
├── app/          — the vault UI (Compose): unlock, list, add/edit, settings
├── vault-core/   — encryption, Room+SQLCipher database, repository (no networking, ever)
├── keyboard/     — SimpleVaultIME (working PoC keyboard) + the suggestion-injector glue
└── autofill/     — VaultAutofillService, handles browser/website matching
```

## Reading order

1. `01-research-open-source-keyboard.md` — which keyboard to fork for production typing, and why.
2. `02-architecture.md` — full system design, data model, encryption, threat model.
3. `DATA_FLOW.md` — sequence diagrams for every core flow (save, suggest, autofill, unlock, biometric).
4. `UX_UI_DESIGN.md` — screen inventory, components, states, accessibility and mobile-optimization review.
5. `INTEGRATION.md` — how the 4 modules wire together and where they touch Android system services.
6. `03-ui-ux-mockup.html` (in the outputs folder, not this zip) — open in a browser.
7. `PHASES.md` — what's built, what's stubbed, what was fixed in review, what to do next.
8. `DEPLOYMENT.md` — CI/CD, keystore + Play Store setup, versioning.
9. `IOS_NOTES.md` — why iOS needs a separate project, and what that would take.
10. `keyboard/FORK_NOTES.md` — how to swap `SimpleVaultIME` for a real HeliBoard fork.
11. `build-dependencies.md` — early dependency notes, superseded by `gradle/libs.versions.toml` but kept as a readable "why" reference.
