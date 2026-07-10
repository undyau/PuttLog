package com.undy.puttrack.voice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

private const val SCO_CONNECT_TIMEOUT_MILLIS = 2000L

/**
 * Best-effort routes the microphone through a connected Bluetooth headset for the duration of a
 * listening session, since [android.speech.SpeechRecognizer] leaves audio input routing entirely
 * to the OS/recognizer service, which does not reliably pick up a Bluetooth SCO link on its own.
 * No-ops cleanly whenever there's no headset, no permission, or the handshake fails/times out, so
 * phone-mic-only behavior is unaffected.
 */
class BluetoothScoRouter(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var active = false
    private var usingModernApi = false
    private var previousMode = AudioManager.MODE_NORMAL
    private var scoReceiver: BroadcastReceiver? = null

    fun start(onReady: () -> Unit) {
        if (active) {
            onReady()
            return
        }
        active = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startModern(onReady)
            } else {
                startLegacy(onReady)
            }
        } catch (e: SecurityException) {
            active = false
            onReady()
        }
    }

    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        scoReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
        }
        scoReceiver = null

        val manager = audioManager ?: return
        if (!hasBluetoothConnectPermission()) return
        try {
            if (usingModernApi) {
                manager.clearCommunicationDevice()
            } else {
                manager.isBluetoothScoOn = false
                manager.stopBluetoothSco()
            }
            manager.mode = previousMode
        } catch (e: SecurityException) {
            // Best-effort teardown; nothing more we can do without the permission.
        }
    }

    private fun startModern(onReady: () -> Unit) {
        val manager = audioManager
        if (manager == null || !hasBluetoothConnectPermission()) {
            active = false
            onReady()
            return
        }
        val device = manager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        if (device == null) {
            active = false
            onReady()
            return
        }
        previousMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        usingModernApi = manager.setCommunicationDevice(device)
        if (!usingModernApi) {
            manager.mode = previousMode
            active = false
        }
        onReady()
    }

    private fun startLegacy(onReady: () -> Unit) {
        val manager = audioManager
        val headsetConnected = BluetoothAdapter.getDefaultAdapter()
            ?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        if (manager == null || !headsetConnected) {
            active = false
            onReady()
            return
        }

        var settled = false
        fun finish() {
            if (settled) return
            settled = true
            handler.removeCallbacksAndMessages(null)
            scoReceiver?.let { receiver -> runCatching { context.unregisterReceiver(receiver) } }
            scoReceiver = null
            onReady()
        }

        previousMode = manager.mode
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        usingModernApi = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val state = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_ERROR
                )
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED ||
                    state == AudioManager.SCO_AUDIO_STATE_ERROR
                ) {
                    finish()
                }
            }
        }
        scoReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.postDelayed(::finish, SCO_CONNECT_TIMEOUT_MILLIS)

        manager.startBluetoothSco()
        manager.isBluetoothScoOn = true
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
