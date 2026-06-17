package com.example.brain

import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceManager {
    private val TAG = "JarvisVoiceManager"
    
    // Emotion settings that map to speech parameters
    enum class EmotionMode(val pitch: Float, val speechRate: Float) {
        snappy(1.15f, 1.25f),     // Standard crisp, energetic Jarvis
        casual(1.0f, 1.15f),     // Friendly, conversational
        excited(1.25f, 1.35f),   // Highly collaborative, higher pitch and faster pace
        thoughtful(0.85f, 0.95f),// Soft tone for code explanation or debugging
        security(1.05f, 1.3f)    // Rapid alert tone
    }

    private var currentEmotion = EmotionMode.snappy

    // Get the pitch for the current emotion
    fun getPitch(): Float = currentEmotion.pitch

    // Get the speech rate for the current emotion
    fun getSpeechRate(): Float = currentEmotion.speechRate

    // Identify and update emotional tone from output sentiment
    fun parseEmotionFromResponse(text: String): EmotionMode {
        val lower = text.lowercase(Locale.US)
        currentEmotion = when {
            lower.contains("alert") || lower.contains("warning") || lower.contains("danger") || lower.contains("unauthorized") -> EmotionMode.security
            lower.contains("explain") || lower.contains("analyze") || lower.contains("diagnose") || lower.contains("compiling") -> EmotionMode.thoughtful
            lower.contains("excited") || lower.contains("awesome") || lower.contains("magnificent") || lower.contains("super") -> EmotionMode.excited
            lower.contains("casual") || lower.contains("chill") || lower.contains("bro") -> EmotionMode.casual
            else -> EmotionMode.snappy
        }
        Log.d(TAG, "Assessed text tone mood mapping: $currentEmotion")
        return currentEmotion
    }

    // Configure a TTS engine instance using current parameters
    fun configureTts(tts: TextToSpeech) {
        try {
            tts.setLanguage(Locale.US)
            tts.setPitch(currentEmotion.pitch)
            tts.setSpeechRate(currentEmotion.speechRate)
            Log.d(TAG, "Applied pitch=${currentEmotion.pitch}, rate=${currentEmotion.speechRate} to engine.")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dynamic vocal resonators: ", e)
        }
    }
}
