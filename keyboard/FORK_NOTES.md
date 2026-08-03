# Upgrading from SimpleVaultIME to a real HeliBoard fork (Phase 2b)

`SimpleVaultIME` is a real, working keyboard — good enough to prove the
suggestion-chip mechanism end-to-end — but it has no autocorrect, no gesture
typing, no non-English layouts, and a bare-bones look. Once Phase 2a is
verified on a device, replace it with the actual fork:

1. Fork `github.com/Helium314/HeliBoard`, clone it locally (this environment
   has no network access, so that clone has to happen on your own machine).
2. Copy its `app/` module source into this module under its own package
   (e.g. `com.vaultkey.ime.heliboard`), keeping its GPL-3.0 license file —
   this module is the GPL-3.0 boundary described in
   `01-research-open-source-keyboard.md`.
3. In the fork's `LatinIME` class:
   - In `onStartInputView(editorInfo, restarting)`, add a call to
     `credentialInjector.onFieldFocused(editorInfo)` (construct the injector
     once in `onCreate()`, same pattern as `SimpleVaultIME.onCreate()`).
   - In `onFinishInputView(finishingInput)`, add
     `credentialInjector.clearSuggestions()`.
   - Find the fork's suggestion-strip rendering call (search for where it
     renders dictionary word suggestions — typically something like
     `setSuggestedWords(...)` on its `SuggestionStripView`) and add a
     branch that instead renders `CredentialChip` entries as pill-style
     views when the injector's callback fires with a non-empty list, using
     the same visual treatment as the dictionary suggestions.
   - On a chip tap, call `injector.credentialFor(chipId)` and commit the
     right field (username or password, based on the currently focused
     field's `EditorInfo.inputType`) via `currentInputConnection`.
4. Delete `SimpleVaultIME.kt` and repoint `keyboard/src/main/AndroidManifest.xml`'s
   `<service>` entry at the fork's IME class instead.
5. `CredentialSuggestionInjector.kt` and `CredentialChip` do not need to
   change — that was the point of keeping them separate from the typing
   surface from the start.
