# Data Flow Diagrams

Every flow below starts and ends on-device — there is no network hop in any
of these diagrams, anywhere, which is the point.

## 1. Saving a new credential

```mermaid
sequenceDiagram
    participant U as User
    participant UI as AddEditScreen (Dart)
    participant CH as MethodChannel (MainActivity.kt)
    participant Repo as CredentialRepository
    participant FC as FieldCipher
    participant DB as VaultDatabase (SQLCipher)

    U->>UI: fills label, domain/package, username, password
    UI->>CH: invokeMethod("addCredential", {...})
    CH->>Repo: addCredential(...)
    Repo->>FC: encrypt(username), encrypt(password), encrypt(notes)
    FC-->>Repo: EncryptedBlob (iv + ciphertext) per field
    Repo->>DB: insertCredential(entity), insertMatch(...) per match
    DB-->>Repo: rows written
    Repo-->>CH: (suspend fun returns)
    CH-->>UI: result.success(null)
    UI-->>U: Navigator.pop() back to Vault list
```

Every other flow below (native-app suggestion, browser autofill, password
unlock, biometric unlock) is untouched by the Flutter migration — none of
them go through Dart at all, since the keyboard and autofill service are
still fully native and never talk to Flutter or `MainActivity`.

## 2. Suggestion popup inside a native app

```mermaid
sequenceDiagram
    participant App as Some other app (e.g. GitHub app)
    participant IME as SimpleVaultIME
    participant Inj as CredentialSuggestionInjector
    participant Repo as CredentialRepository
    participant DB as VaultDatabase

    App->>IME: field gains focus (onStartInputView, EditorInfo)
    IME->>Inj: onFieldFocused(editorInfo)
    Inj->>Inj: looksLikeLoginField? (checks inputType variation)
    Inj->>Repo: findForPackageName(editorInfo.packageName)
    Repo->>DB: findByMatch(PACKAGE_NAME, ...)
    DB-->>Repo: matching CredentialEntity rows
    Repo->>Repo: decrypt each field via FieldCipher
    Repo-->>Inj: List<DecryptedCredential>
    Inj-->>IME: onSuggestionsReady(chips)
    IME->>IME: renderSuggestions() — chip appears above keys
    App-->>App: user taps chip
    IME->>Inj: credentialFor(chipId)
    IME->>App: currentInputConnection.commitText(username)
```

Note: this path only ever sees the **package name** — see Data Flow #3 for
why website matching inside a browser needs the Autofill path instead.

## 3. Suggestion inside a browser (website matching)

```mermaid
sequenceDiagram
    participant Browser
    participant AF as VaultAutofillService
    participant Repo as CredentialRepository
    participant DB as VaultDatabase
    participant IME as Keyboard (inline suggestion strip)

    Browser->>AF: onFillRequest(structure) — includes AssistStructure
    AF->>AF: findLoginFields(structure) walks the view tree for webDomain + autofill hints
    AF->>AF: check VaultKeyGraph.session.state == Unlocked
    AF->>Repo: findForWebDomain(domain)
    Repo->>DB: findByMatch(WEB_DOMAIN, normalized domain)
    DB-->>Repo: matching rows
    Repo->>Repo: decrypt via FieldCipher
    Repo-->>AF: List<DecryptedCredential>
    AF->>AF: build FillResponse with one Dataset per credential
    AF-->>Browser: FillResponse
    Browser-->>IME: system renders the dropdown (or inline suggestion, once wired — see PHASES.md)
```

## 4. Unlock with master password

```mermaid
sequenceDiagram
    participant U as User
    participant UI as UnlockScreen (Dart)
    participant CH as MethodChannel (MainActivity.kt)
    participant S as VaultSession
    participant PKD as PasswordKeyDerivation
    participant MD as VaultMetadataStore

    U->>UI: types master password
    UI->>CH: invokeMethod("unlockWithPassword", {password})
    CH->>S: unlockWithPassword(password)
    S->>MD: loadSalt(), loadWrappedPasswordKey()
    S->>PKD: unwrapDbKey(password, salt, wrapped)
    alt correct password
        PKD-->>S: raw 32-byte dbKey
        S->>S: activate(dbKey) — opens VaultDatabase, creates FieldCipher, starts auto-lock timer
        S-->>CH: true
        CH-->>UI: result.success(true)
        UI-->>U: Navigator to Vault list
    else wrong password
        PKD-->>S: throws AEADBadTagException
        S-->>CH: false
        CH-->>UI: result.success(false)
        UI-->>U: "Wrong password"
    end
```

## 5. Biometric enrollment + unlock

```mermaid
sequenceDiagram
    participant U as User
    participant UI as UnlockScreen / SettingsScreen
    participant BH as BiometricPromptHelper
    participant BU as BiometricUnlock (Keystore)
    participant S as VaultSession
    participant MD as VaultMetadataStore

    Note over UI: Enrollment (only offered if BiometricPromptHelper.isAvailable())
    UI->>S: biometricEnrollCipher()
    S->>BU: ensureBiometricKeyExists(), encryptCipher()
    BU-->>S: Cipher (ENCRYPT_MODE, tied to Keystore key)
    UI->>BH: enroll(activity, cipher)
    BH->>U: system BiometricPrompt UI
    U-->>BH: fingerprint/face confirmed
    BH-->>UI: authorized Cipher
    UI->>S: completeBiometricEnrollment(authorizedCipher)
    S->>BU: wrapDbKey(authorizedCipher, rawDbKey)
    S->>MD: saveBiometricUnlock(wrapped)

    Note over UI: Everyday unlock
    UI->>S: biometricUnlockCipher()
    S->>MD: loadWrappedBiometricKey()
    S->>BU: decryptCipher(iv)
    BU-->>S: Cipher (DECRYPT_MODE)
    UI->>BH: unlock(activity, cipher)
    BH->>U: system BiometricPrompt UI
    U-->>BH: fingerprint/face confirmed
    BH-->>UI: authorized Cipher
    UI->>S: unlockWithBiometricCipher(authorizedCipher)
    S->>BU: unwrapDbKey(authorizedCipher, wrapped)
    S->>S: activate(dbKey)
```

## What never appears in any of these diagrams

A network call. Search every sequence above for an arrow leaving the
device — there isn't one. That's the entire point of the "no INTERNET
permission anywhere" design decision in `02-architecture.md`.
