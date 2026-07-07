package com.undy.puttrack.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sign
import kotlin.math.sin

/**
 * Short audible confirmation for hands-free use: a quick bright beep when a voice
 * command was understood, a low buzz when it wasn't, so the golfer doesn't need to
 * look at the screen after every putt. Synthesized directly with [AudioTrack] rather
 * than [android.media.ToneGenerator]'s proprietary tones, which render as near-identical
 * generic beeps on some OEMs — a plain sine blip and a low square-wave buzz stay
 * clearly distinct in both pitch and texture on every device.
 */
class SoundFeedback {

    fun playRecognized() = playTone(frequencyHz = 1600.0, durationMillis = 90, square = false)

    fun playUnrecognized() = playTone(frequencyHz = 220.0, durationMillis = 320, square = true)

    fun release() = Unit

    private fun playTone(frequencyHz: Double, durationMillis: Int, square: Boolean) {
        val sampleRate = 44100
        val sampleCount = sampleRate * durationMillis / 1000
        val fadeSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        val samples = ShortArray(sampleCount)
        for (i in samples.indices) {
            val angle = 2.0 * PI * i * frequencyHz / sampleRate
            val raw = if (square) sign(sin(angle)) else sin(angle)
            val envelope = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > sampleCount - fadeSamples -> (sampleCount - i).toDouble() / fadeSamples
                else -> 1.0
            }
            samples[i] = (raw * envelope * Short.MAX_VALUE * 0.6).toInt().toShort()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size)
        audioTrack.setNotificationMarkerPosition(sampleCount)
        audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) = track.release()
            override fun onPeriodicNotification(track: AudioTrack) = Unit
        })
        audioTrack.play()
    }
}
