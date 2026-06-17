package com.example.brain

import android.util.Log

class WakeWordManager {
    private val TAG = "JarvisWakeWordManager"
    private var isCurrentlyListening = false
    private var isMinimized = false
    private var microphoneFailureCount = 0
    private val maxMicRetries = 5
    
    // Conforms strictly to requirements: Hey Jarvis, Jarvis, Hello Jarvis
    private val recognizedWakeWords = listOf("hey jarvis", "jarvis", "hello jarvis")

    // State query methods
    fun setListening(listening: Boolean) {
        this.isCurrentlyListening = listening
        Log.i(TAG, "Background Wake-Word Continuous Loop: active=$listening")
    }

    fun isListening(): Boolean = isCurrentlyListening

    fun setMinimizedState(minimized: Boolean) {
        this.isMinimized = minimized
        Log.d(TAG, "Application minimization state updated. Minimized=$minimized. (Adapting battery optimizations)")
    }

    fun isMinimized(): Boolean = isMinimized

    // Validates if spelling matches any of our target wake words
    fun isWakeWordDetected(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        val match = recognizedWakeWords.any { lowerText.contains(it) }
        if (match) {
            Log.i(TAG, "Success! Registered vocal wake trigger sequence matching target parameters on: '$lowerText'.")
        }
        return match
    }

    // Handles microphone errors & tracks retry thresholds
    fun registerMicrophoneError(): Boolean {
        microphoneFailureCount++
        Log.e(TAG, "System Audio Microphone error caught. Active fail cycle: $microphoneFailureCount of $maxMicRetries")
        return microphoneFailureCount < maxMicRetries
    }

    fun resetErrorLogs() {
        microphoneFailureCount = 0
    }

    fun shouldTriggerRecovery(): Boolean {
        return microphoneFailureCount >= maxMicRetries
    }

    // Energy management based of user activity status
    fun getBatteryOptimizationClass(): String {
        return if (isMinimized) {
            "LOW_POWER_STANDBY"
        } else {
            "HIGH_PERFORMANCE_REALTIME"
        }
    }
}
