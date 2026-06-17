package com.example.brain

import java.util.Locale

class PersonalityEngine {
    
    enum class SystemPersonalityMode {
        CASUAL_TANGLE, // Movie-accurate witty Tanglish buddy
        PROFESSIONAL,  // Smooth, polite tech strategist
        SARCASTIC      // Iron Man's classic witty banter
    }

    private var currentMode = SystemPersonalityMode.CASUAL_TANGLE

    fun setPersonalityMode(mode: SystemPersonalityMode) {
        currentMode = mode
    }

    fun getPersonalityMode(): SystemPersonalityMode = currentMode

    // Post-processes responses if necessary to add accurate, character-rich traits
    fun augmentResponse(rawText: String): String {
        if (rawText.contains("Error") || rawText.contains("failed", ignoreCase = true)) {
            return rawText
        }

        return when (currentMode) {
            SystemPersonalityMode.CASUAL_TANGLE -> {
                // Ensure friendly witty sign-off if not already styled
                var augmented = rawText
                if (!augmented.lowercase(Locale.US).contains("bro") && !augmented.lowercase(Locale.US).contains("thozha")) {
                    augmented = "$augmented, Bro!"
                }
                augmented
            }
            SystemPersonalityMode.SARCASTIC -> {
                if (rawText.endsWith("!") || rawText.endsWith(".")) {
                    rawText.dropLast(1) + "... unless your suit runs out of power."
                } else {
                    "$rawText... trust me, I'm J.A.R.V.I.S."
                }
            }
            SystemPersonalityMode.PROFESSIONAL -> {
                rawText.replace(", Bro!", ".").replace("Bro", "Sir").replace("bro", "sir")
            }
        }
    }
}
