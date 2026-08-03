# iOS — why there's nothing to build yet

Short version: **this codebase is 100% native Android (Kotlin)**, and the
three system-level capabilities the whole app is built around don't have
Android-equivalent APIs on iOS. There's no Xcode project sitting in this
repo for a CI workflow to build — writing one means starting a second,
mostly-separate app in Swift, not adding a build step.

## Where the architecture doesn't transfer

| Android capability this app relies on | iOS equivalent |
|---|---|
| Custom keyboard set as the **system default** input method, receiving `EditorInfo` for every text field system-wide | iOS **Custom Keyboard Extensions** exist, but users can only add them as *one of several* keyboards (switched via the globe key) — there's no "set as default, replacing the system keyboard everywhere" concept, and Apple deliberately restricts what a custom keyboard can see (no access to secure/password fields' content at all, by design) |
| **Autofill Framework** + inline suggestions, letting one service supply credentials into any app or browser | iOS has **AutoFill Credential Provider Extensions** (`ASCredentialProviderViewController`) — a real equivalent, but a completely different extension type, API, and integration model than a keyboard extension |
| **Android Keystore** hardware-backed key wrapping | iOS **Secure Enclave** via the Keychain Services API — conceptually similar, but a different API surface entirely |
| Reading `packageName`/web-domain hints from `AssistStructure` | No direct equivalent; iOS credential providers work off **Associated Domains** entitlements and the system's own AutoFill data model instead |

None of the Kotlin in this repo — `VaultSession`, `CredentialSuggestionInjector`,
`VaultAutofillService`, the Compose UI — runs on iOS. A Kotlin Multiplatform
rewrite of just `vault-core`'s pure-logic pieces (encryption, matching) is
plausible later, but the two system-integration pieces (keyboard, autofill)
would need to be written from scratch as a **Credential Provider Extension**
in Swift, since that's the correct/sanctioned iOS mechanism — not a keyboard
extension at all, despite the Android app calling the equivalent piece a
"keyboard."

## If you want an iOS version later

That's a legitimate, separate scope of work: a new Xcode project, a Swift
(or Kotlin Multiplatform, if you want to share the encryption/matching
logic) codebase, its own `fastlane`/GitHub Actions pipeline (which *does*
need a macOS runner — `runs-on: macos-latest` — since Xcode builds can't run
on Linux), and its own App Store Connect setup (API key instead of a Google
Play service account, TestFlight instead of internal testing tracks).

Worth scoping as its own project once the Android app is validated, rather
than folding into this repo's CI as an afterthought.
