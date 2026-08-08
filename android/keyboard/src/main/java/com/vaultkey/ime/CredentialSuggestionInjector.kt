package com.vaultkey.ime

import android.view.inputmethod.EditorInfo
import com.vaultkey.core.data.CredentialRepository
import com.vaultkey.core.data.DecryptedCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * This file is NOT a full IME — it's the glue layer that talks to the
 * encrypted vault on behalf of whichever InputMethodService is actually
 * registered. As of Phase 6, that's `FlutterVaultIME` in the `android/app`
 * module (a thin Kotlin shell hosting a Flutter-rendered keyboard UI — see
 * INTEGRATION.md and PHASES.md). `SimpleVaultIME.kt`, still in this same
 * module, is kept only as an unregistered reference implementation of a
 * pure-native keyboard (no Flutter engine involved) in case that approach
 * is ever reverted to.
 *
 * If a real HeliBoard fork replaces either of those someday (see
 * keyboard/FORK_NOTES.md), this class's job stays the same — it's the one
 * piece that doesn't change no matter which keyboard shell calls it:
 *
 *   1. Call onFieldFocused(editorInfo) when a text field gains focus.
 *   2. Render the CredentialChip list this produces however that shell
 *      renders suggestions (a dictionary-style strip, a Flutter widget, etc).
 *   3. Call clearSuggestions() when the field loses focus / input view closes.
 */
class CredentialSuggestionInjector(
    private val repository: CredentialRepository,
    private val scope: CoroutineScope,
    private val onSuggestionsReady: (List<CredentialChip>) -> Unit
) {
    // Decrypted values live here only as long as the suggestion strip is
    // showing them — cleared on the next field focus or on IME teardown.
    private var activeCredentials: Map<String, DecryptedCredential> = emptyMap()

    fun onFieldFocused(editorInfo: EditorInfo) {
        if (!looksLikeLoginField(editorInfo)) {
            clearSuggestions()
            return
        }
        val packageName = editorInfo.packageName ?: run { clearSuggestions(); return }

        scope.launch {
            val matches = repository.findForPackageName(packageName)
            activeCredentials = matches.associateBy { it.id }
            onSuggestionsReady(matches.map { CredentialChip(id = it.id, label = it.label) })
        }

        // Web-domain matching for browsers is intentionally NOT done here — a
        // raw IME never receives the URL, only the browser's package name.
        // That case is handled by VaultAutofillService's inline suggestions.
    }

    /** Look up the plaintext for a chip the user just tapped, to insert via InputConnection. */
    fun credentialFor(chipId: String): DecryptedCredential? = activeCredentials[chipId]

    fun clearSuggestions() {
        activeCredentials = emptyMap()
        onSuggestionsReady(emptyList())
    }

    private fun looksLikeLoginField(info: EditorInfo): Boolean {
        val variation = info.inputType and android.text.InputType.TYPE_MASK_VARIATION
        // Note: Android has no "username" input-type variation — there is no
        // TYPE_TEXT_VARIATION_USERNAME. Username fields are surfaced through
        // autofill hints (handled by VaultAutofillService), not InputType, so
        // the IME strip keys off the password/email variations plus the
        // visible-password one.
        return variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }
}

data class CredentialChip(val id: String, val label: String)
