package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.DspPreferencesRepository
import com.example.service.AudioCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for head unit system boot completion.
 * Receives BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, and QUICKBOOT_POWERON.
 * Restores DSP processing and applies last preset if auto-start is enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "Received boot broadcast: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val repository = DspPreferencesRepository(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = repository.dspSettingsFlow.first()
                    Log.i(
                        "BootReceiver",
                        "Loaded boot preferences: autoStart=${settings.autoStartOnBoot}, applyLastPreset=${settings.applyLastPresetOnLaunch}, lastActivePreset='${settings.activePresetName}'"
                    )

                    if (settings.autoStartOnBoot) {
                        Log.i("BootReceiver", "Auto-starting AudioCaptureService on head unit boot...")
                        val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
                            this.action = AudioCaptureService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } else {
                        Log.i("BootReceiver", "Auto-start on boot is disabled by user in settings")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed starting audio service on boot", e)
                }
            }
        }
    }
}
