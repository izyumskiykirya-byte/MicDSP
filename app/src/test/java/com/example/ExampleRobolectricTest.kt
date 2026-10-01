package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dsp.DspProcessor
import com.example.dsp.NoiseCalibrator
import com.example.model.DspSettings
import com.example.model.PresetRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("АвтоЗвук DSP", appName)
    }

    @Test
    fun `dsp processor processes buffer and computes valid db levels`() {
        val processor = DspProcessor(48000)
        val settings = PresetRepository.CITY.settings
        processor.configureFilters(settings)

        // Generate 1024 16-bit PCM samples with 1kHz sine wave
        val sampleCount = 1024
        val buffer = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val sampleVal = (kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * i / 48000.0) * 16000.0).toInt().toShort()
            buffer[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        val stats = processor.process(buffer, buffer.size, settings)
        assertTrue("Input RMS should be greater than -60 dBFS", stats.inputRmsDb > -60f)
        assertTrue("Input RMS should be less than or equal to 0 dBFS", stats.inputRmsDb <= 0f)
        assertTrue("Output RMS should be greater than -60 dBFS", stats.outputRmsDb > -60f)
        assertFalse("16000 amplitude sine wave should not cause hard clipping", stats.isClipping)
    }

    @Test
    fun `noise gate silences low level background noise`() {
        val processor = DspProcessor(48000)
        val settings = DspSettings(
            noiseGateEnabled = true,
            noiseGateThresholdDb = -30.0f,
            gainBoosterDb = 0f
        )
        processor.configureFilters(settings)

        // Very quiet noise (amplitude 50 out of 32767 -> approx -56 dBFS)
        val sampleCount = 1024
        val buffer = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val sampleVal = (50).toShort()
            buffer[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        // Process a couple chunks to allow gate release envelope to close
        repeat(5) {
            processor.process(buffer, buffer.size, settings)
        }
        val stats = processor.process(buffer, buffer.size, settings)
        assertFalse("Noise gate should close on quiet noise", stats.isGateOpen)
    }

    @Test
    fun `noise calibrator calculates optimal thresholds for noisy highway environment`() {
        val calibrator = NoiseCalibrator()
        val settings = DspSettings(
            noiseGateThresholdDb = -45.0f,
            softwareNsStrength = 0.50f
        )

        // Simulate 200 samples of noisy highway (-31 dBFS RMS, -26 dBFS peak)
        for (i in 0 until 200) {
            val jitter = (i % 5) * 0.4f
            calibrator.addSample(-31.0f + jitter, -26.0f + jitter)
        }

        val result = calibrator.computeResult(settings)
        assertTrue("Environment should detect highway/high noise", result.environmentDescription.contains("трасса", ignoreCase = true))
        assertTrue("Suggested gate threshold should be higher than default", result.suggestedGateThresholdDb > -30.0f)
        assertTrue("Suggested software NS strength should be aggressive (>0.8)", result.suggestedSoftwareNsStrength >= 0.85f)
        assertTrue("Suggested HPF should be higher to cut road vibrations", result.suggestedHpfCutoffHz >= 110f)
    }

    @Test
    fun `noise calibrator calculates gentle thresholds for quiet cabin environment`() {
        val calibrator = NoiseCalibrator()
        val settings = DspSettings(
            noiseGateThresholdDb = -35.0f,
            softwareNsStrength = 0.80f
        )

        // Simulate 200 samples of quiet parked car (-55 dBFS RMS, -48 dBFS peak)
        for (i in 0 until 200) {
            val jitter = (i % 3) * 0.2f
            calibrator.addSample(-55.0f + jitter, -48.0f + jitter)
        }

        val result = calibrator.computeResult(settings)
        assertTrue("Environment should detect quiet cabin", result.environmentDescription.contains("тихий салон", ignoreCase = true))
        assertTrue("Suggested gate threshold should be lower for quiet voice", result.suggestedGateThresholdDb <= -42.0f)
        assertTrue("Suggested software NS strength should be mild", result.suggestedSoftwareNsStrength <= 0.40f)
        assertTrue("Suggested HPF should preserve natural warmth", result.suggestedHpfCutoffHz <= 80f)
    }

    @Test
    fun `boot receiver responds to action boot completed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = com.example.receiver.BootReceiver()
        val intent = android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED)
        
        // Receiver should handle intent gracefully without throwing
        receiver.onReceive(context, intent)
    }

    @Test
    fun `preset repository can lookup all standard presets by name`() {
        val city = PresetRepository.findPresetByName("Город / Шумная дорога")
        val highway = PresetRepository.findPresetByName("Трасса (Высокая скорость)")
        val quiet = PresetRepository.findPresetByName("Тихий салон (Стоянка)")
        val studio = PresetRepository.findPresetByName("Студийный голос")

        assertNotNull(city)
        assertNotNull(highway)
        assertNotNull(quiet)
        assertNotNull(studio)

        assertEquals("Город / Шумная дорога", city?.name)
        assertEquals("Трасса (Высокая скорость)", highway?.name)
        assertTrue(highway!!.settings.applyLastPresetOnLaunch)
    }
}
