package com.vaultkey.app

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.vaultkey.core.VaultKeyGraph
import com.vaultkey.ime.CredentialChip
import com.vaultkey.ime.CredentialSuggestionInjector
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodChannel
import io.flutter.FlutterInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * PHASE 6 — Flutter-rendered keyboard.
 *
 * This class is the entire native footprint of the keyboard: it registers
 * with Android as an InputMethodService (a system requirement — see
 * README.md, there is no Dart-only way to do this) and hosts a FlutterView
 * so every visual element — keys, colors, the suggestion strip — is drawn
 * by lib/keyboard/keyboard_app.dart instead of native code. Everything this
 * class does beyond that is glue:
 *   - detect field focus and look up matching credentials (still native —
 *     CredentialSuggestionInjector talks to the encrypted vault, which has
 *     no Dart binding, same reasoning as VaultAutofillService)
 *   - relay "commit this text" / "delete N characters" calls FROM Dart into
 *     the real InputConnection, which is an Android-only API Dart can't
 *     touch directly
 *
 * Known cost of this approach (see PHASES.md/UX_UI_DESIGN.md for the full
 * discussion before this was chosen): a second Flutter engine runs inside
 * the keyboard's process, which is heavier and slower to first-show than a
 * plain native keyboard. The engine is created once and cached for the
 * process lifetime specifically to keep that cost to a one-time hit rather
 * than paying it every time the keyboard opens.
 */
class FlutterVaultIME : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var injector: CredentialSuggestionInjector
    private var flutterView: FlutterView? = null
    private var channel: MethodChannel? = null

    override fun onCreate() {
        super.onCreate()
        VaultKeyGraph.init(applicationContext)
        injector = CredentialSuggestionInjector(
            repository = VaultKeyGraph.credentialRepository,
            scope = serviceScope,
            onSuggestionsReady = { chips -> pushSuggestions(chips) }
        )
    }

    override fun onCreateInputView(): View {
        val engine = engineHolder.getOrCreate(applicationContext)

        channel = MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL_NAME).also { ch ->
            ch.setMethodCallHandler { call, result ->
                // Typing / inserting via the keyboard is user activity too —
                // keep the shared session's idle auto-lock timer from firing
                // while the user is actively using the keyboard elsewhere.
                VaultKeyGraph.session.notifyUserActivity()
                when (call.method) {
                    "commitText" -> {
                        currentInputConnection?.commitText(call.argument<String>("text").orEmpty(), 1)
                        result.success(null)
                    }
                    "deleteSurroundingText" -> {
                        val count = call.argument<Int>("count") ?: 1
                        currentInputConnection?.deleteSurroundingText(count, 0)
                        result.success(null)
                    }
                    "performEditorAction" -> {
                        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_GO)
                        result.success(null)
                    }
                    "insertCredential" -> {
                        val chipId = call.argument<String>("chipId")
                        val credential = chipId?.let { injector.credentialFor(it) }
                        if (credential != null) {
                            currentInputConnection?.commitText(credential.username, 1)
                            // Record usage so "recently used" ordering can work.
                            serviceScope.launch { VaultKeyGraph.credentialRepository.markUsed(credential.id) }
                        }
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
        }

        // TextureView-backed FlutterView, NOT the default SurfaceView one: a
        // SurfaceView renders solid black inside an InputMethodService window
        // (its surface isn't composited within the IME window's z-order — that
        // was the full-screen black keyboard). A TextureView draws into the
        // normal view hierarchy, so the Flutter UI shows correctly.
        val view = FlutterView(this, FlutterTextureView(this)).apply { attachToFlutterEngine(engine) }
        flutterView = view
        // No Activity lifecycle exists in a Service, so the engine's own
        // lifecycle notion has to be driven by hand — this tells Flutter's
        // framework "you're visible and should render," which it otherwise
        // infers automatically from an Activity's onResume/onPause.
        engine.lifecycleChannel.appIsResumed()

        // Constrain the input view height. Returned bare, the FlutterView expands
        // to fill the whole IME window (the full-screen keyboard); an IME input
        // view must be only as tall as the keyboard. ~330dp matches the Dart
        // layout (suggestion strip + four key rows) plus room for the gesture
        // inset the Dart SafeArea reserves at the bottom.
        val keyboardHeightPx = (330 * resources.displayMetrics.density).toInt()
        return FrameLayout(this).apply {
            // Opaque keyboard-gray background: a FlutterTextureView preserves its
            // alpha channel, so any pixel the Flutter scene doesn't paint (and
            // the moment before its first frame) would otherwise show the app
            // behind the keyboard through. This matches _KeyboardColors.keysBackground.
            setBackgroundColor(0xFFDCDAD5.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            addView(
                view,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, keyboardHeightPx),
            )
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        info?.let { injector.onFieldFocused(it) }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        injector.clearSuggestions()
        engineHolder.engineOrNull()?.lifecycleChannel?.appIsInactive()
    }

    override fun onDestroy() {
        super.onDestroy()
        flutterView?.detachFromFlutterEngine()
        channel?.setMethodCallHandler(null)
        serviceScope.cancel()
        // The engine itself is deliberately NOT destroyed here — it's cached
        // for the process lifetime (see FlutterEngineHolder) so reopening
        // the keyboard doesn't pay Dart-VM/engine startup cost again.
    }

    private fun pushSuggestions(chips: List<CredentialChip>) {
        channel?.invokeMethod(
            "onSuggestions",
            chips.map { mapOf("id" to it.id, "label" to it.label) }
        )
    }

    companion object {
        private const val CHANNEL_NAME = "com.vaultkey.app/keyboard"
        private val engineHolder = FlutterEngineHolder()
    }
}

/**
 * Creates the keyboard's FlutterEngine exactly once per process and reuses
 * it — a fresh engine per keyboard-open would make every single keyboard
 * appearance pay a Dart-VM cold-start cost, which is the difference between
 * "usable" and "not usable" for something meant to pop up instantly.
 */
private class FlutterEngineHolder {
    private var engine: FlutterEngine? = null

    fun engineOrNull(): FlutterEngine? = engine

    fun getOrCreate(context: android.content.Context): FlutterEngine {
        engine?.let { return it }

        val loader: FlutterLoader = FlutterInjector.instance().flutterLoader()
        if (!loader.initialized()) {
            loader.startInitialization(context.applicationContext)
        }
        loader.ensureInitializationComplete(context.applicationContext, null)

        val newEngine = FlutterEngine(context.applicationContext)
        newEngine.dartExecutor.executeDartEntrypoint(
            DartExecutor.DartEntrypoint(loader.findAppBundlePath(), "keyboardMain")
        )
        engine = newEngine
        return newEngine
    }
}
