package com.example.audio

import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.dsp.DspProcessor
import com.example.model.AudioInputType
import com.example.model.AudioStats
import com.example.model.DspSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Robust Automotive Audio Engine.
 * Manages AudioRecord capture, hardware effects (AEC/NS/AGC),
 * software DSP pipeline, AudioTrack monitoring, device routing and audio focus.
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AutoAudioEngine"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val dspProcessor = DspProcessor(SAMPLE_RATE)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    // Hardware AudioFx instances
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null

    private var hardwareNsAvailable = false
    private var hardwareAecAvailable = false
    private var hardwareAgcAvailable = false

    private val isRunning = AtomicBoolean(false)
    private var captureThread: Thread? = null

    private var currentSettings = DspSettings(sampleRate = SAMPLE_RATE)
    private val _audioStats = MutableStateFlow(AudioStats())
    val audioStats: StateFlow<AudioStats> = _audioStats.asStateFlow()

    // Audio Focus request
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    init {
        checkHardwareCapabilities()
    }

    private fun checkHardwareCapabilities() {
        try {
            hardwareNsAvailable = NoiseSuppressor.isAvailable()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed checking NoiseSuppressor availability", e)
        }
        try {
            hardwareAecAvailable = AcousticEchoCanceler.isAvailable()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed checking AcousticEchoCanceler availability", e)
        }
        try {
            hardwareAgcAvailable = AutomaticGainControl.isAvailable()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed checking AutomaticGainControl availability", e)
        }
        Log.i(TAG, "Hardware AudioFx: NS=$hardwareNsAvailable, AEC=$hardwareAecAvailable, AGC=$hardwareAgcAvailable")
    }

    fun updateSettings(settings: DspSettings) {
        currentSettings = settings.copy(sampleRate = SAMPLE_RATE)
        dspProcessor.configureFilters(currentSettings)
        applyHardwareEffectsState()
    }

    private fun requestAudioFocus(): Boolean {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.d(TAG, "Audio focus GAINED")
                    hasAudioFocus = true
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Car navigation audio ducking (e.g. Yandex Navigator voice prompt)
                    Log.d(TAG, "Audio focus DUCKED / TRANSIENT LOSS")
                    hasAudioFocus = false
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.d(TAG, "Audio focus LOST permanently")
                    hasAudioFocus = false
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioFocusRequest = request
            val res = audioManager.requestAudioFocus(request)
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } else {
            @Suppress("DEPRECATION")
            val res = audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        hasAudioFocus = false
    }

    @Synchronized
    fun start(): Boolean {
        if (isRunning.get()) {
            return true
        }

        requestAudioFocus()

        val minRecBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        val recBufferSize = maxOf(minRecBufferSize, 2048 * 2)

        try {
            // Select VOICE_COMMUNICATION to enable hardware AEC/NS on car head unit SoCs
            val audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION

            val record = AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT,
                recBufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed, falling back to AudioSource.MIC")
                val fallbackRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    AUDIO_FORMAT,
                    recBufferSize
                )
                if (fallbackRecord.state != AudioRecord.STATE_INITIALIZED) {
                    fallbackRecord.release()
                    return false
                }
                audioRecord = fallbackRecord
            } else {
                audioRecord = record
            }

            // Route to preferred input device (USB Audio, 3.5mm, Built-in, BT SCO)
            routeInputDevice(audioRecord, currentSettings.selectedInputType)

            // Attach hardware effects
            attachHardwareEffects(audioRecord!!.audioSessionId)

            // Setup optional AudioTrack monitor
            setupAudioTrack()

            audioRecord?.startRecording()
            isRunning.set(true)

            startCaptureThread(recBufferSize)
            Log.i(TAG, "AudioEngine started successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioEngine", e)
            stop()
            return false
        }
    }

    private fun routeInputDevice(record: AudioRecord?, inputType: AudioInputType) {
        if (record == null) return
        val targetType = inputType.deviceType

        if (inputType == AudioInputType.BLUETOOTH_SCO) {
            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d(TAG, "Started Bluetooth SCO routing")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to start Bluetooth SCO", e)
            }
        } else {
            try {
                if (audioManager.isBluetoothScoOn) {
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed stopping Bluetooth SCO", e)
            }
        }

        if (targetType != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val matchedDevice = devices.firstOrNull { it.type == targetType }
            if (matchedDevice != null) {
                val success = record.setPreferredDevice(matchedDevice)
                Log.i(TAG, "Routed input to ${matchedDevice.productName} (type=$targetType, success=$success)")
            } else {
                Log.w(TAG, "Requested device type $targetType not currently found among input devices")
            }
        }
    }

    private fun attachHardwareEffects(sessionId: Int) {
        releaseHardwareEffects()

        if (hardwareNsAvailable) {
            try {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = currentSettings.hardwareNsEnabled
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not create hardware NoiseSuppressor", e)
            }
        }

        if (hardwareAecAvailable) {
            try {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = currentSettings.hardwareAecEnabled
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not create hardware AcousticEchoCanceler", e)
            }
        }

        if (hardwareAgcAvailable) {
            try {
                automaticGainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = currentSettings.hardwareAgcEnabled
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not create hardware AutomaticGainControl", e)
            }
        }
    }

    private fun applyHardwareEffectsState() {
        noiseSuppressor?.let {
            if (it.enabled != currentSettings.hardwareNsEnabled) {
                it.enabled = currentSettings.hardwareNsEnabled
            }
        }
        echoCanceler?.let {
            if (it.enabled != currentSettings.hardwareAecEnabled) {
                it.enabled = currentSettings.hardwareAecEnabled
            }
        }
        automaticGainControl?.let {
            if (it.enabled != currentSettings.hardwareAgcEnabled) {
                it.enabled = currentSettings.hardwareAgcEnabled
            }
        }
    }

    private fun releaseHardwareEffects() {
        try { noiseSuppressor?.release() } catch (_: Throwable) {}
        noiseSuppressor = null
        try { echoCanceler?.release() } catch (_: Throwable) {}
        echoCanceler = null
        try { automaticGainControl?.release() } catch (_: Throwable) {}
        automaticGainControl = null
    }

    private fun setupAudioTrack() {
        try {
            val minPlayBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
            val playBufferSize = maxOf(minPlayBufferSize, 2048 * 2)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                playBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AudioTrack monitor initialization failed", e)
        }
    }

    private fun startCaptureThread(bufferSize: Int) {
        captureThread = Thread({
            // Set urgent audio thread priority to prevent dropouts
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val pcmChunk = ByteArray(1024 * 2) // 1024 16-bit mono frames (approx ~21ms at 48kHz)
            var totalFrames: Long = 0
            var lastStatsTime = 0L

            while (isRunning.get()) {
                val record = audioRecord ?: break
                val bytesRead = record.read(pcmChunk, 0, pcmChunk.size)

                if (bytesRead > 0) {
                    totalFrames += (bytesRead / 2)

                    // Execute low-level DSP pipeline
                    val stats = dspProcessor.process(pcmChunk, bytesRead, currentSettings)

                    // Real-time audio monitor playback if enabled
                    if (currentSettings.monitorPlaybackEnabled && audioTrack != null) {
                        try {
                            audioTrack?.write(pcmChunk, 0, bytesRead)
                        } catch (e: Throwable) {
                            Log.w(TAG, "AudioTrack write failed", e)
                        }
                    }

                    // Throttle UI stats emission to ~30fps for smooth Compose UI
                    val now = System.currentTimeMillis()
                    if (now - lastStatsTime >= 33) {
                        lastStatsTime = now
                        _audioStats.value = stats.copy(
                            hardwareNsAvailable = hardwareNsAvailable,
                            hardwareAecAvailable = hardwareAecAvailable,
                            hardwareAgcAvailable = hardwareAgcAvailable,
                            hardwareNsActive = noiseSuppressor?.enabled == true,
                            hardwareAecActive = echoCanceler?.enabled == true,
                            hardwareAgcActive = automaticGainControl?.enabled == true,
                            framesProcessedTotal = totalFrames,
                            activeInputDeviceName = currentSettings.selectedInputType.displayName
                        )
                    }
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION || bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                }
            }
        }, "CarAudioDspThread").apply { start() }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }

        try {
            captureThread?.join(500)
        } catch (_: InterruptedException) {}
        captureThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
        audioTrack = null

        releaseHardwareEffects()
        abandonAudioFocus()

        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        } catch (_: Throwable) {}

        _audioStats.value = AudioStats(
            hardwareNsAvailable = hardwareNsAvailable,
            hardwareAecAvailable = hardwareAecAvailable,
            hardwareAgcAvailable = hardwareAgcAvailable,
            activeInputDeviceName = currentSettings.selectedInputType.displayName
        )
        Log.i(TAG, "AudioEngine stopped successfully")
    }

    fun isEngineRunning(): Boolean = isRunning.get()
}
