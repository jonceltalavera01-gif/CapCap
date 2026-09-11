package com.darkhorses.PedalConnect.ui.theme

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Thin wrapper around Android's built-in TextToSpeech engine. Safe to call
 * [speak] from anywhere — including plain (non-Composable) callbacks like a
 * LocationListener — since it just forwards to the underlying engine, which
 * is thread-tolerant for enqueueing utterances.
 *
 * If no TTS engine is installed on the device, [engine] stays null and
 * [speak] silently no-ops rather than crashing — turn-by-turn text/visuals
 * still work exactly as before.
 */
class TtsController internal constructor(private val engine: TextToSpeech?) {
    fun speak(text: String, flush: Boolean = false) {
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine?.speak(text, mode, null, text.hashCode().toString())
    }

    fun stop() {
        engine?.stop()
    }
}

/**
 * Creates and owns a [TtsController] for the lifetime of the composable that
 * calls this. Initializes once (not per-recomposition) and shuts the engine
 * down cleanly when the composable leaves composition.
 */
@Composable
fun rememberTtsController(): TtsController {
    val context: Context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
            // On failure, engine stays whatever it was assigned below —
            // TtsController.speak() no-ops safely either way.
        }
        engine = instance
        onDispose {
            instance.stop()
            instance.shutdown()
        }
    }

    return remember(engine) { TtsController(engine) }
}