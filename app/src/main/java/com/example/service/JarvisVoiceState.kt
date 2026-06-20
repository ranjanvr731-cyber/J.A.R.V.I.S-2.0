package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class JarvisVoiceState {
    IDLE,               // Wake word listening mode (Microphone trigger always active if background listening is true)
    ACTIVE_LISTENING,   // Full microphone speech recognizer listening for command
    PROCESSING          // Thinking (AI) or Speaking (TTS)
}

object VoiceStateManager {
    private val _state = MutableStateFlow(JarvisVoiceState.IDLE)
    val state: StateFlow<JarvisVoiceState> = _state.asStateFlow()
    
    private var lastStateTransitionTime = 0L
    private var lastCycleEndTime = 0L

    @Synchronized
    fun updateState(newState: JarvisVoiceState): Boolean {
        val oldState = _state.value
        if (oldState == newState) {
            return false // No state can restart itself
        }
        
        val currentTime = System.currentTimeMillis()
        
        // Cooldown: Add 2-3 second cooldown after each cycle.
        // A cycle ends when transitioning from PROCESSING back to IDLE.
        if (oldState == JarvisVoiceState.PROCESSING && newState == JarvisVoiceState.IDLE) {
            lastCycleEndTime = currentTime
        }
        
        // Ignore wake word / prevent transition from IDLE to ACTIVE_LISTENING if in cooldown
        if (oldState == JarvisVoiceState.IDLE && newState == JarvisVoiceState.ACTIVE_LISTENING) {
            val elapsedSinceCycleEnd = currentTime - lastCycleEndTime
            if (elapsedSinceCycleEnd < 2500) {
                android.util.Log.d("VoiceStateManager", "Holding transition to ACTIVE_LISTENING due to active 2.5s cooldown (elapsed: ${elapsedSinceCycleEnd}ms)")
                return false
            }
        }

        _state.value = newState
        lastStateTransitionTime = currentTime
        android.util.Log.i("JarvisVoiceState", "Central Voice State updated to: $newState")
        return true
    }
}
