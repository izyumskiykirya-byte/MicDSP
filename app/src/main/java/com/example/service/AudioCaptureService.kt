package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioEngine
import com.example.data.DspPreferencesRepository
import com.example.model.AudioStats
import com.example.model.DspSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground Service for continuous in-car microphone audio DSP processing.
 * Works seamlessly in background when running navigation apps (Yandex, Google, 2GIS).
 */
class AudioCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "car_dsp_audio_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        // Engine instance accessible to ViewModel while service is running
        var activeEngine: AudioEngine? = null
            private set
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var preferencesRepository: DspPreferencesRepository
    private var engine: AudioEngine? = null

    inner class LocalBinder : Binder() {
        fun getService(): AudioCaptureService = this@AudioCaptureService
    }

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = DspPreferencesRepository(applicationContext)
        createNotificationChannel()

        val newEngine = AudioEngine(applicationContext)
        engine = newEngine
        activeEngine = newEngine
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProcessing()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundServiceNotification()
                startProcessing()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingIntentFlags)

        val stopIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProcessing() {
        serviceScope.launch {
            preferencesRepository.dspSettingsFlow.collect { settings ->
                val effectiveSettings = if (settings.applyLastPresetOnLaunch) {
                    val foundPreset = com.example.model.PresetRepository.findPresetByName(settings.activePresetName)
                    if (foundPreset != null && settings.activePresetName != "Пользовательский" && settings.activePresetName != "Откалибровано (Авто)") {
                        foundPreset.settings.copy(
                            selectedInputType = settings.selectedInputType,
                            autoStartOnBoot = settings.autoStartOnBoot,
                            applyLastPresetOnLaunch = settings.applyLastPresetOnLaunch,
                            activePresetName = settings.activePresetName
                        )
                    } else {
                        settings
                    }
                } else {
                    com.example.model.PresetRepository.CITY.settings.copy(
                        selectedInputType = settings.selectedInputType,
                        autoStartOnBoot = settings.autoStartOnBoot,
                        applyLastPresetOnLaunch = false
                    )
                }
                engine?.updateSettings(effectiveSettings)
                if (!engine!!.isEngineRunning()) {
                    engine?.start()
                }
            }
        }
    }

    private fun stopProcessing() {
        engine?.stop()
    }

    fun updateSettings(settings: DspSettings) {
        engine?.updateSettings(settings)
    }

    fun getAudioStatsFlow(): StateFlow<AudioStats>? = engine?.audioStats

    fun isEngineActive(): Boolean = engine?.isEngineRunning() == true

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopProcessing()
        activeEngine = null
        engine = null
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
