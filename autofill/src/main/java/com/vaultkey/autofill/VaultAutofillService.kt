package com.vaultkey.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.vaultkey.core.VaultKeyGraph
import com.vaultkey.core.crypto.VaultState
import com.vaultkey.core.data.DecryptedCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Registered as the device's Autofill service. This is what lets the keyboard
 * suggest a credential for a *website inside a browser* — a plain IME only
 * ever sees the browser's package name, never the URL (see the note in
 * CredentialSuggestionInjector.kt). On Android 11+ results returned here can
 * render as an *inline* suggestion, inside the keyboard's own strip, so it
 * still feels like one unified keyboard rather than two autofill UIs.
 */
class VaultAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        VaultKeyGraph.init(applicationContext)
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val contexts = request.fillContexts
        if (contexts.isEmpty()) {
            callback.onSuccess(null)
            return
        }
        val structure = contexts.last().structure
        val fields = findLoginFields(structure)

        if (fields.usernameId == null && fields.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        // The vault must already be unlocked (e.g. via the keyboard/app) for
        // autofill to return anything — this service never itself prompts
        // for the master password, keeping the "no surprise UI" expectation
        // users have of autofill dropdowns.
        if (VaultKeyGraph.session.state != VaultState.Unlocked) {
            callback.onSuccess(null)
            return
        }
        // Using autofill counts as activity — don't let the idle timer lock
        // the vault mid-form-fill.
        VaultKeyGraph.session.notifyUserActivity()

        serviceScope.launch {
            val domain = fields.webDomain
            val matches = if (domain != null) {
                VaultKeyGraph.credentialRepository.findForWebDomain(domain)
            } else emptyList()

            if (matches.isEmpty()) {
                callback.onSuccess(null)
                return@launch
            }

            val response = FillResponse.Builder().apply {
                matches.forEach { credential ->
                    addDataset(buildDataset(credential, fields))
                    // For API 30+, attach an InlinePresentation built from
                    // request.inlineSuggestionsRequest here as well, so the
                    // same dataset also renders inside the keyboard strip
                    // instead of (or in addition to) the dropdown. Omitted
                    // from this reference version — see developer.android.com
                    // /guide/topics/text/ime-autofill for the exact builder.
                }
            }.build()

            callback.onSuccess(response)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Detect a submitted, not-yet-known login form and prompt "Save to
        // VaultKey?" via a follow-up Activity here. Writes always go through
        // CredentialRepository — this service never handles plaintext beyond
        // what's needed to build that one prompt.
        callback.onSuccess()
    }

    private fun buildDataset(credential: DecryptedCredential, fields: LoginFields): Dataset {
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, "VaultKey: ${credential.label}")
        }
        return Dataset.Builder(presentation).apply {
            fields.usernameId?.let { setValue(it, AutofillValue.forText(credential.username), presentation) }
            fields.passwordId?.let { setValue(it, AutofillValue.forText(credential.password), presentation) }
        }.build()
    }

    /**
     * Web-domain hints come from properly structured autofill nodes
     * (View.setAutofillHints / HTML autocomplete attributes exposed through
     * WebView). Not every site/browser combination exposes this — it's the
     * same best-effort extraction every Autofill service on Android does.
     */
    private fun findLoginFields(structure: AssistStructure): LoginFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var webDomain: String? = null

        fun walk(node: AssistStructure.ViewNode) {
            node.webDomain?.let { webDomain = it }
            val hints = node.autofillHints
            if (hints != null) {
                if (hints.any { it == android.view.View.AUTOFILL_HINT_USERNAME || it == android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS }) {
                    usernameId = node.autofillId
                }
                if (hints.any { it == android.view.View.AUTOFILL_HINT_PASSWORD }) {
                    passwordId = node.autofillId
                }
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode)
        }
        return LoginFields(usernameId, passwordId, webDomain)
    }

    private data class LoginFields(
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val webDomain: String?
    )
}
