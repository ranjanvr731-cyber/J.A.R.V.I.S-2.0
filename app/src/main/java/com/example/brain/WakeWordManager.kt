package com.example.brain

import android.util.Log

class WakeWordManager {
    private val TAG = "JarvisWakeWordManager"
    private var isListening = false
    private var microphoneFailureCount = 0
    private val maxMicRetries = 4

    // Sets whether wake word is listening
    fun setListening(listening: Boolean) {
        this.isListening = listening
        Log.d(TAG, "Vocal listener loop updated: active=$listening")
    }

    // Check listening status
    fun isListening(): Boolean = isListening

    // Error recovery for microphone
    fun registerMicrophoneError(): Boolean {
        microphoneFailureCount++
        Log.e(TAG, "Hardware Microphone error detected. Count = $microphoneFailureCount")
        return microphoneFailureCount < maxMicRetries
    }

    // Clear error logs on successful initialization
    fun resetErrorLogs() {
        microphoneFailureCount = 0
    }

    // Assess whether continuous stream needs safety reset
    fun shouldTriggerRecovery(): Boolean {
        return microphoneFailureCount >= maxMicRetries
    }
}
