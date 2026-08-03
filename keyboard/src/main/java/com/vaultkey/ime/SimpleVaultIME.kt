package com.vaultkey.ime

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.vaultkey.core.VaultKeyGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * PHASE 2a — walking skeleton.
 *
 * A deliberately minimal but real, compilable keyboard: three letter rows, a
 * shift/backspace/space/enter row, and a suggestion strip above the keys that
 * shows credential chips when a login field is focused. It exists so the
 * *suggestion mechanism* can be built, run on a device/emulator, and tested
 * before investing in wiring up the full HeliBoard fork (PHASE 2b), which is
 * where production-quality typing (autocorrect, gestures, layouts, languages)
 * belongs. Swap onCreateInputView()'s contents for the fork's view and reuse
 * CredentialSuggestionInjector as-is when that happens.
 */
class SimpleVaultIME : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var injector: CredentialSuggestionInjector
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var keysContainer: LinearLayout
    private var shifted = false
    // Re-applied after a shift-triggered key re-render so toggling shift
    // never makes the currently-showing suggestion chips disappear.
    private var lastChips: List<CredentialChip> = emptyList()

    override fun onCreate() {
        super.onCreate()
        VaultKeyGraph.init(applicationContext) // no-op if VaultKeyApplication already did this
        injector = CredentialSuggestionInjector(
            repository = VaultKeyGraph.credentialRepository,
            scope = serviceScope,
            onSuggestionsReady = { chips -> renderSuggestions(chips) }
        )
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DCDAD5"))
        }

        suggestionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        root.addView(HorizontalScrollView(this).apply { addView(suggestionStrip) })

        keysContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keysContainer)
        renderKeys()

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        VaultKeyGraph.session.notifyUserActivity()
        info?.let { injector.onFieldFocused(it) }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        injector.clearSuggestions()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ---- key handling ----

    /** Rebuilds ONLY the letter/bottom rows — never touches suggestionStrip. */
    private fun renderKeys() {
        keysContainer.removeAllViews()
        keysContainer.addView(keyRow(listOf("q","w","e","r","t","y","u","i","o","p")))
        keysContainer.addView(keyRow(listOf("a","s","d","f","g","h","j","k","l")))
        keysContainer.addView(keyRow(listOf("⇧","z","x","c","v","b","n","m","⌫"), wideKeys = setOf("⇧", "⌫")))
        keysContainer.addView(bottomRow())
    }

    private fun keyRow(keys: List<String>, wideKeys: Set<String> = emptySet()) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            keys.forEach { key ->
                addView(Button(this@SimpleVaultIME).apply {
                    text = if (shifted && key.length == 1 && key.first().isLetter()) key.uppercase() else key
                    layoutParams = LinearLayout.LayoutParams(
                        0, dp(46), if (key in wideKeys) 1.6f else 1f
                    ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                    textSize = 14f
                    setOnClickListener { onKeyPress(key) }
                })
            }
        }

    private fun bottomRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        addView(Button(this@SimpleVaultIME).apply {
            text = "123"
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1.4f)
            setOnClickListener { /* symbols layout — not needed for the PoC */ }
        })
        addView(Button(this@SimpleVaultIME).apply {
            text = ""
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 6f)
            setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        })
        addView(Button(this@SimpleVaultIME).apply {
            text = "go"
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1.4f)
            setOnClickListener {
                currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_GO)
            }
        })
    }

    private fun onKeyPress(key: String) {
        VaultKeyGraph.session.notifyUserActivity() // keep the vault alive while actively typing
        when (key) {
            "⇧" -> { shifted = !shifted; renderKeys() } // rebuilds keys only — suggestionStrip untouched
            "⌫" -> currentInputConnection?.deleteSurroundingText(1, 0)
            else -> currentInputConnection?.commitText(if (shifted) key.uppercase() else key, 1)
        }
    }

    // ---- suggestion strip rendering ----

    private fun renderSuggestions(chips: List<CredentialChip>) {
        lastChips = chips
        suggestionStrip.removeAllViews()
        if (chips.isEmpty()) {
            suggestionStrip.visibility = View.GONE
            return
        }
        suggestionStrip.visibility = View.VISIBLE
        chips.forEach { chip ->
            suggestionStrip.addView(TextView(this).apply {
                text = "  Use ${chip.label} login  "
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2F4EEA"))
                gravity = Gravity.CENTER
                textSize = 12f
                val lp = LinearLayout.LayoutParams(WRAP_CONTENT, dp(34))
                lp.setMargins(dp(4), 0, dp(4), 0)
                layoutParams = lp
                setOnClickListener { insertCredential(chip.id) }
            })
        }
    }

    private fun insertCredential(chipId: String) {
        val credential = injector.credentialFor(chipId) ?: return
        // A real UI would know which field (username vs password) has focus;
        // EditorInfo.inputType at time of tap tells you that — omitted here
        // for brevity in this walking skeleton.
        currentInputConnection?.commitText(credential.username, 1)
        injector.markUsed(chipId)
        VaultKeyGraph.session.notifyUserActivity()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
