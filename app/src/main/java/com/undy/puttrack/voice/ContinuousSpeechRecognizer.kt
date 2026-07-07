package com.undy.puttrack.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wraps [SpeechRecognizer], which only listens for a single utterance per
 * startListening() call, and restarts it automatically after every result or
 * error so the session feels continuously "listening" until [stop] is called.
 * Prefers on-device recognition (Android 12+ with an on-device recognizer installed)
 * so audio never leaves the phone, but falls back to standard network-capable
 * recognition when on-device recognition isn't available on this device.
 * Must be created/used from the main thread.
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val onResult: (List<String>) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit = {},
    private val onUnavailable: () -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        // Ask for several candidate transcriptions rather than just the top guess: the
        // vocabulary we care about is tiny (a number, then "make" or "miss"), so checking
        // every candidate against that pattern catches cases where the #1 guess is noise
        // but a lower-ranked alternative is exactly right.
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        // Shorten the silence window the recognizer waits before deciding an utterance is
        // done, so it restarts listening sooner after each word instead of adding lag that
        // can clip the start of the next short command.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
        // Only used by the network-capable fallback recognizer; on-device recognition
        // ignores this since it's inherently offline.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun isOnDeviceRecognitionAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start() {
        if (active) return
        val onDevice = isOnDeviceRecognitionAvailable()
        if (!onDevice && !SpeechRecognizer.isRecognitionAvailable(context)) {
            onUnavailable()
            return
        }
        active = true
        if (recognizer == null) {
            val newRecognizer = if (onDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            recognizer = newRecognizer.apply { setRecognitionListener(listener) }
        }
        listenOnce()
    }

    fun stop() {
        active = false
        recognizer?.stopListening()
        onListeningChanged(false)
    }

    fun destroy() {
        active = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun listenOnce() {
        if (!active) return
        onListeningChanged(true)
        recognizer?.startListening(recognizerIntent)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (candidates.isNotEmpty()) onResult(candidates)
            if (active) listenOnce() else onListeningChanged(false)
        }

        override fun onError(error: Int) {
            if (active) listenOnce() else onListeningChanged(false)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
