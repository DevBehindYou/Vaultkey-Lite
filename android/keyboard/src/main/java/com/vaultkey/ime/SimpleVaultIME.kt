package com.vaultkey.ime

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.vaultkey.core.VaultKeyGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * PHASE 2a — walking skeleton, restyled to look like a real modern keyboard
 * (Gboard-style light theme: white rounded keys, gray special keys, blue
 * action key) rather than the flat unstyled buttons from the first pass —
 * see PHASES.md for the before/after note on why this needed a second look.
 *
 * Still a proof-of-concept for the suggestion mechanism, not production
 * typing (no autocorrect/gestures/other languages) — see keyboard/FORK_NOTES.md
 * for swapping in a real HeliBoard fork later, which this restyle doesn't
 * change the plan for.
 */
class SimpleVaultIME : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var injector: CredentialSuggestionInjector
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var keysContainer: LinearLayout
    private var shifted = false
    private var lastChips: List<CredentialChip> = emptyList()

    // ---- Gboard-ish palette ----
    private val keyboardBg = Color.parseColor("#E5E5E8")
    private val letterKeyBg = Color.parseColor("#FFFFFF")
    private val letterKeyPressedBg = Color.parseColor("#D8DAE0")
    private val specialKeyBg = Color.parseColor("#D3D6DA")
    private val specialKeyPressedBg = Color.parseColor("#C2C5CB")
    private val actionKeyBg = Color.parseColor("#2F4EEA")
    private val actionKeyPressedBg = Color.parseColor("#2540C7")
    private val letterTextColor = Color.parseColor("#1F1F23")
    private val specialTextColor = Color.parseColor("#3C3C43")

    override fun onCreate() {
        super.onCreate()
        VaultKeyGraph.init(applicationContext)
        injector = CredentialSuggestionInjector(
            repository = VaultKeyGraph.credentialRepository,
            scope = serviceScope,
            onSuggestionsReady = { chips -> renderSuggestions(chips) }
        )
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(keyboardBg)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        suggestionStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(suggestionStrip)
        })

        keysContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keysContainer)
        renderKeys()

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
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

    // ---- key rendering ----

    private fun renderKeys() {
        keysContainer.removeAllViews()
        keysContainer.addView(keyRow(listOf("q","w","e","r","t","y","u","i","o","p")))
        keysContainer.addView(inset(keyRow(listOf("a","s","d","f","g","h","j","k","l")), insetDp = 18))
        keysContainer.addView(keyRow(
            listOf("⇧","z","x","c","v","b","n","m","⌫"),
            wideKeys = setOf("⇧", "⌫"),
            specialKeys = setOf("⇧", "⌫")
        ))
        keysContainer.addView(bottomRow())
    }

    /** Centers a row with side padding, matching Gboard's staggered middle row. */
    private fun inset(row: LinearLayout, insetDp: Int): View =
        FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(dp(insetDp), 0, dp(insetDp), 0)
            addView(row)
        }

    private fun keyRow(
        keys: List<String>,
        wideKeys: Set<String> = emptySet(),
        specialKeys: Set<String> = emptySet()
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        keys.forEach { key ->
            addView(makeKey(
                label = displayLabel(key),
                weight = if (key in wideKeys) 1.5f else 1f,
                isSpecial = key in specialKeys,
                isAction = false,
                onClick = { onKeyPress(key) }
            ))
        }
    }

    private fun bottomRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

        addView(makeKey("123", weight = 1.4f, isSpecial = true, isAction = false, onClick = {
            /* symbols layout — not needed for the PoC */
        }))
        addView(makeKey("English (US)", weight = 5f, isSpecial = true, isAction = false, small = true, onClick = {
            currentInputConnection?.commitText(" ", 1)
        }))
        addView(makeKey("Go", weight = 1.6f, isSpecial = false, isAction = true, onClick = {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_GO)
        }))
    }

    private fun makeKey(
        label: String,
        weight: Float,
        isSpecial: Boolean,
        isAction: Boolean,
        small: Boolean = false,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = if (small) 12f else 16f
        setTextColor(if (isAction) Color.WHITE else if (isSpecial) specialTextColor else letterTextColor)
        background = keyDrawable(isSpecial, isAction)
        stateListAnimator = null // no default Material press elevation jump on plain Button
        layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        setPadding(0, 0, 0, 0)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    /** Rounded-rect key background with a distinct pressed-state color — no XML drawables needed. */
    private fun keyDrawable(isSpecial: Boolean, isAction: Boolean): StateListDrawable {
        val (normal, pressed) = when {
            isAction -> actionKeyBg to actionKeyPressedBg
            isSpecial -> specialKeyBg to specialKeyPressedBg
            else -> letterKeyBg to letterKeyPressedBg
        }
        fun rounded(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6).toFloat()
            setColor(color)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed))
            addState(intArrayOf(), rounded(normal))
        }
    }

    private fun displayLabel(key: String): String =
        if (shifted && key.length == 1 && key.first().isLetter()) key.uppercase() else key

    private fun onKeyPress(key: String) {
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
                text = "🔑  Use ${chip.label} login"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(18).toFloat()
                    setColor(actionKeyBg)
                }
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                textSize = 13f
                val lp = LinearLayout.LayoutParams(WRAP_CONTENT, dp(36))
                lp.setMargins(dp(4), 0, dp(4), 0)
                layoutParams = lp
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    insertCredential(chip.id)
                }
            })
        }
    }

    private fun insertCredential(chipId: String) {
        val credential = injector.credentialFor(chipId) ?: return
        // A real UI would know which field (username vs password) has focus;
        // EditorInfo.inputType at time of tap tells you that — omitted here
        // for brevity in this walking skeleton.
        currentInputConnection?.commitText(credential.username, 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
