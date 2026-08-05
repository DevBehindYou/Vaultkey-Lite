package com.vaultkey.ime

import android.view.inputmethod.EditorInfo
import com.vaultkey.core.data.CredentialRepository
import com.vaultkey.core.data.DecryptedCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * This file is NOT a full IME — it's the glue layer meant to plug into the
 * forked HeliBoard codebase (package `helium314.keyboard.latin`, class
 * `LatinIME extends InputMethodService`) for the production build. For a
 * buildable-today path, `SimpleVaultIME.kt` in this same module also uses it
 * directly, without HeliBoard, as a walking-skeleton proof of concept.
 *
 * Integration points inside a HeliBoard fork (verify file/class names against
 * the exact fork commit before wiring up):
 *
 *   1. LatinIME.onStartInputView(editorInfo, restarting) →
 *      call onFieldFocused(editorInfo) here.
 *   2. The existing suggestion strip view already knows how to render a row
 *      of chips for dictionary word suggestions — this injector produces the
 *      same chip shape so it slots into that rendering code rather than
 *      building a second UI surface.
 *   3. LatinIME.onFinishInputView() → call clearSuggestions().
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
        return variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_USERNAME
    }
}

data class CredentialChip(val id: String, val label: String)
