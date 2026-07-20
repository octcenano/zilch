package com.zilch.blemesh.voice

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * BleVoiceCall — Llamada de voz peer-to-peer por BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  ARQUITECTURA
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Flujo de audio:
 *
 *  EMISOR:                                    RECEPTOR:
 *  ┌──────────┐                               ┌──────────┐
 *  │Micrófono │ → PCM 8kHz mono 16bit         │Altavoz   │
 *  └────┬─────┘                               └────▲─────┘
 *       │                                         │
 *       ▼                                         │
 *  ┌──────────┐    BLE GATT Write    ┌──────────┐│
 *  │AES-GCM  │ ──────────────────→ │AES-GCM  │─┘
 *  │Encrypt   │                     │Decrypt   │
 *  └──────────┘                     └──────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  PARÁMETROS DE AUDIO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * - Sample rate: 8000 Hz (suficiente para voz, bajo consumo)
 * - Channels: Mono
 * - Bit depth: 16 bits (PCM)
 * - Buffer size: 160 samples (20ms por frame)
 * ═══════════════════════════════════════════════════════════════════════════
 */
class BleVoiceCall(
    private val context: Context,
    private val encryptionKey: SecretKey,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BleVoiceCall"
        private const val SAMPLE_RATE = 8000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_SAMPLES = 160
        private const val GCM_NONCE_SIZE = 12
        private const val GCM_TAG_SIZE = 16
    }

    enum class CallState {
        IDLE, CONNECTING, ACTIVE, ENDED, ERROR
    }

    private val _state = MutableStateFlow(CallState.IDLE)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var callJob: Job? = null
    private var durationJob: Job? = null
    private val isRecording = AtomicBoolean(false)

    /** Callback para enviar audio cifrado al peer vía BLE */
    var onAudioChunkReady: ((ByteArray) -> Unit)? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startCall() {
        if (_state.value == CallState.ACTIVE) return

        _state.value = CallState.CONNECTING
        Log.i(TAG, "Iniciando llamada de voz...")

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord no se pudo inicializar")
            _state.value = CallState.ERROR
            return
        }

        isRecording.set(true)
        _state.value = CallState.ACTIVE

        callJob = scope.launch(Dispatchers.IO) {
            try {
                audioRecord?.startRecording()
                audioTrack?.play()

                val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
                while (isRecording.get() && isActive) {
                    val read = audioRecord?.read(buffer, 0, BUFFER_SIZE_SAMPLES) ?: -1
                    if (read > 0 && !_isMuted.value) {
                        val pcmBytes = shortArrayToByteArray(buffer, read)
                        val encrypted = encryptAudio(pcmBytes)
                        onAudioChunkReady?.invoke(encrypted)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en grabación: ${e.message}")
                _state.value = CallState.ERROR
            }
        }

        durationJob = scope.launch {
            while (_state.value == CallState.ACTIVE) {
                delay(1000L)
                _duration.value = _duration.value + 1
            }
        }
    }

    fun receiveAudioChunk(encryptedChunk: ByteArray) {
        if (_state.value != CallState.ACTIVE) return
        scope.launch(Dispatchers.IO) {
            try {
                val pcmBytes = decryptAudio(encryptedChunk)
                val shorts = byteArrayToShortArray(pcmBytes)
                audioTrack?.write(shorts, 0, shorts.size)
            } catch (e: Exception) {
                Log.w(TAG, "Error descifrando audio: ${e.message}")
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = _isSpeakerOn.value
    }

    fun endCall() {
        Log.i(TAG, "Finalizando llamada de voz")
        isRecording.set(false)
        callJob?.cancel()
        durationJob?.cancel()
        try {
            audioRecord?.stop(); audioRecord?.release()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.stop(); audioTrack?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        audioTrack = null
        _state.value = CallState.ENDED
        _duration.value = 0
        _isMuted.value = false
        _isSpeakerOn.value = false
        try {
            @Suppress("DEPRECATION")
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        } catch (_: Exception) {
        }
    }

    private fun encryptAudio(pcm: ByteArray): ByteArray {
        val nonce = ByteArray(GCM_NONCE_SIZE)
        java.security.SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_SIZE * 8, nonce))
        val encrypted = cipher.doFinal(pcm)
        val ciphertext = encrypted.copyOfRange(0, encrypted.size - GCM_TAG_SIZE)
        val tag = encrypted.copyOfRange(encrypted.size - GCM_TAG_SIZE, encrypted.size)
        return nonce + tag + ciphertext
    }

    private fun decryptAudio(data: ByteArray): ByteArray {
        val nonce = data.copyOfRange(0, GCM_NONCE_SIZE)
        val tag = data.copyOfRange(GCM_NONCE_SIZE, GCM_NONCE_SIZE + GCM_TAG_SIZE)
        val ciphertext = data.copyOfRange(GCM_NONCE_SIZE + GCM_TAG_SIZE, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(GCM_TAG_SIZE * 8, nonce))
        return cipher.doFinal(ciphertext + tag)
    }

    private fun shortArrayToByteArray(shorts: ShortArray, length: Int): ByteArray {
        val bytes = ByteArray(length * 2)
        for (i in 0 until length) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    private fun byteArrayToShortArray(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            shorts[i] = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
        }
        return shorts
    }
}
