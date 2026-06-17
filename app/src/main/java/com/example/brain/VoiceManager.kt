package com.example.brain

import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceManager {
    private val TAG = "JarvisVoiceManager"
    
    // Multi-emotional states conforming exactly to requirements
    enum class EmotionMode(val displayName: String, val pitch: Float, val speechRate: Float) {
        HAPPY("Happy", 1.2f, 1.2f),
        SAD("Sad", 0.85f, 0.9f),
        ANGRY("Angry", 1.1f, 1.4f),
        EXCITED("Excited", 1.3f, 1.35f),
        NEUTRAL("Neutral", 1.0f, 1.15f),
        STRESSED("Stressed", 1.15f, 1.25f)
    }

    private var currentEmotion = EmotionMode.NEUTRAL
    private val speechQueue = mutableListOf<String>()
    private var isCurrentlySpeaking = false

    // State query metrics
    fun getPitch(): Float = currentEmotion.pitch
    fun getSpeechRate(): Float = currentEmotion.speechRate
    fun getActiveEmotion(): EmotionMode = currentEmotion

    // Detects user emotional state from input message and adjusts speaking resonators
    fun detectEmotionFromInput(text: String): EmotionMode {
        val lower = text.lowercase(Locale.US)
        currentEmotion = when {
            lower.contains("happy") || lower.contains("great") || lower.contains("good") || lower.contains("glad") || lower.contains("superb") -> EmotionMode.HAPPY
            lower.contains("sad") || lower.contains("bad") || lower.contains("sorry") || lower.contains("hurt") || lower.contains("cry") -> EmotionMode.SAD
            lower.contains("angry") || lower.contains("hate") || lower.contains("stupid") || lower.contains("rubbish") || lower.contains("annoyed") -> EmotionMode.ANGRY
            lower.contains("excited") || lower.contains("wow") || lower.contains("awesome") || lower.contains("wonderful") -> EmotionMode.EXCITED
            lower.contains("stress") || lower.contains("worry") || lower.contains("anxious") || lower.contains("scared") || lower.contains("help") -> EmotionMode.STRESSED
            else -> EmotionMode.NEUTRAL
        }
        Log.i(TAG, "Assessed human conversational sentiment state matches: ${currentEmotion.displayName} (Tuning voice envelope pitch=${currentEmotion.pitch}x, rate=${currentEmotion.speechRate}x)")
        return currentEmotion
    }

    // Identifies dynamic speaking tone parameters
    fun parseEmotionFromResponse(text: String): EmotionMode {
        return detectEmotionFromInput(text)
    }

    // Thread-safe response queuing to prevent speech interruption bugs and overlap
    fun queueSpeech(text: String) {
        synchronized(speechQueue) {
            speechQueue.add(text)
            Log.d(TAG, "Enqueued speech utterance: '${text.take(30)}...'. Current queue size: ${speechQueue.size}")
        }
    }

    fun popNextSpeech(): String? {
        synchronized(speechQueue) {
            if (speechQueue.isNotEmpty()) {
                val next = speechQueue.removeAt(0)
                isCurrentlySpeaking = true
                return next
            }
            isCurrentlySpeaking = false
            return null
        }
    }

    fun clearSpeechQueue() {
        synchronized(speechQueue) {
            speechQueue.clear()
            isCurrentlySpeaking = false
            Log.i(TAG, "Speech queue fully purged to handle direct interrupt protocol.")
        }
    }

    fun setSpeakingStatus(status: Boolean) {
        isCurrentlySpeaking = status
    }

    fun isSpeakingNow(): Boolean = isCurrentlySpeaking

    // Safe, fluent speech configuration engine
    fun configureTts(tts: TextToSpeech) {
        try {
            tts.setLanguage(Locale.US)
            tts.setPitch(currentEmotion.pitch)
            tts.setSpeechRate(currentEmotion.speechRate)
            Log.d(TAG, "Successfully configured speech resonators: pitch=${currentEmotion.pitch}, rate=${currentEmotion.speechRate}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply dynamic voice configurations cleanly: ", e)
        }
    }
}
