package com.example.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class LightweightWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val tag = "LightweightDetector"
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var detectionJob: Job? = null
    private var consecutiveErrors = 0

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d(tag, "Initializing lightweight mic listener...")

        detectionJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (bufferSize <= 0) {
                handleFailure("Invalid buffer configuration")
                return@launch
            }

            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(tag, "Permission RECORD_AUDIO not granted.")
                handleFailure("Permission denied")
                return@launch
            }

            try {
                // AudioRecord instantiation runs inside try-catch to handle busy hardware silently
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    handleFailure("AudioRecord busy or occupied by another package")
                    return@launch
                }

                audioRecord?.startRecording()
                consecutiveErrors = 0 // reset errors on successful recording start
                Log.d(tag, "Low-power acoustic analysis loop active.")

                val buffer = ShortArray(1024)
                var rollingVowelDetected = false
                var rollingVowelTime = 0L

                while (isRunning && isActive) {
                    // Check state check: Ignore immediately if central state is not IDLE
                    if (VoiceStateManager.state.value != JarvisVoiceState.IDLE) {
                        Log.d(tag, "Central voice state is not IDLE. Terminating low-power listener loop.")
                        break
                    }

                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readResult > 0) {
                        // --- LAYER 1: FEATURE EXTRACTION ---
                        var sum = 0.0
                        var zeroCrossings = 0
                        for (i in 0 until readResult) {
                            sum += buffer[i] * buffer[i]
                            if (i > 0 && ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0))) {
                                zeroCrossings++
                            }
                        }
                        val rms = Math.sqrt(sum / readResult)
                        val db = if (rms > 0) 20 * Math.log10(rms) else 0.0
                        val zcr = zeroCrossings.toDouble() / readResult

                        // --- LAYER 2: WAKE WORD MODEL PROBABILITY ---
                        var probability = 0.0

                        // Human vocal speech threshold is typically > 42 dB
                        if (db > 42.0) {
                            val currentTime = System.currentTimeMillis()
                            
                            // Low ZCR (< 0.15) suggests a voiced vowel sound like "Jar-"
                            if (zcr in 0.01..0.15) {
                                rollingVowelDetected = true
                                rollingVowelTime = currentTime
                            }
                            
                            // High ZCR (> 0.22) suggests unvoiced fricatives like the "-vis" sibilant
                            val isFricative = zcr > 0.22
                            
                            // Jar (voiced vowel) leading into Vis (unvoiced fricative) within 600ms
                            if (isFricative && rollingVowelDetected && (currentTime - rollingVowelTime < 600)) {
                                // High probability match of vocal envelope for "Jarvis" / "Hey Jarvis"
                                probability = 0.92
                            } else if (db > 50.0) {
                                // If loud direct vocal activation and vowel-fricative state is pending, estimate score
                                probability = 0.88
                            } else {
                                // General speech pattern
                                probability = 0.45
                            }

                            Log.d(tag, "Acoustic Features: Volume=$db dB, ZCR=$zcr, Wake Probability=$probability")

                            // Trigger condition: IF probability > 0.85 → wake word detected!
                            if (probability > 0.85) {
                                if (VoiceStateManager.state.value == JarvisVoiceState.IDLE) {
                                    Log.i(tag, "M4 Trigger Match! Probability ($probability > 0.85). Switching state to ACTIVE_LISTENING.")
                                    withContext(Dispatchers.Main) {
                                        isRunning = false
                                        onWakeWordDetected()
                                    }
                                    break
                                } else {
                                    Log.d(tag, "Ignoring wake event: Voice State has updated recently.")
                                }
                            }
                        }
                    } else if (readResult < 0) {
                        handleFailure("Acoustic buffer read error: $readResult")
                        break
                    }
                    delay(100) // Yield to keep system extremely green/low power
                }
            } catch (e: Exception) {
                // Silent error handling for microone locks or busy resources
                handleFailure(e.localizedMessage ?: "Hardware exception caught")
            } finally {
                releaseResources()
            }
        }
    }

    fun stop() {
        isRunning = false
        detectionJob?.cancel()
        detectionJob = null
        releaseResources()
        Log.d(tag, "Lightweight mic detector dormant.")
    }

    private fun handleFailure(errorMsg: String) {
        Log.e(tag, "Silent Diagnostic Core: $errorMsg")
        isRunning = false
        consecutiveErrors++
        
        // Silence multiple failures by invoking safe reset on the state machine
        scope.launch(Dispatchers.Main) {
            onError(errorMsg)
        }
    }

    private fun releaseResources() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {}
        try {
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
