package com.example.brain

import android.util.Log

class OfflineAIManager {
    private val TAG = "JarvisOfflineAIManager"
    private val responseCache = mutableMapOf<String, String>()

    init {
        // Pre-cache core responses
        responseCache["hello"] = "Hello, Bro! I am currently running in offline localized backup mode. No internet detected."
        responseCache["who are you"] = "I am J.A.R.V.I.S., running offline on internal hardware algorithms."
        responseCache["help"] = "Offline commands supported: Flashlight, DND, volume control, open camera, make call, open settings."
    }

    // Intercepts input, determines if can resolve offline
    fun processOfflineQuery(silentPrompt: String): String {
        val lower = silentPrompt.lowercase().trim()
        
        // Exact cache lookups
        for ((key, ans) in responseCache) {
            if (lower.contains(key)) {
                return ans
            }
        }

        // Action prediction based on commands
        return when {
            lower.contains("flashlight") || lower.contains("torch") -> {
                if (lower.contains("off")) {
                    "Disengaging flashlight spotlight now, Bro. [PROTOCOL:FLASHLIGHT_OFF]"
                } else {
                    "Engaging flashlight spot illuminator! [PROTOCOL:FLASHLIGHT_ON]"
                }
            }
            lower.contains("silent") || lower.contains("do not disturb") || lower.contains("dnd") -> {
                if (lower.contains("off")) {
                    "Disengaging Silent Mode, Bro. [PROTOCOL:DND_OFF]"
                } else {
                    "Engaging Do Not Disturb stealth protocol! [PROTOCOL:DND_ON]"
                }
            }
            lower.contains("volume") -> {
                if (lower.contains("up") || lower.contains("increase")) {
                    "Increasing device audio gain standard level. [PROTOCOL:VOLUME_UP]"
                } else {
                    "Reducing audio gain parameters. [PROTOCOL:VOLUME_DOWN]"
                }
            }
            lower.contains("open youtube") || lower.contains("youtube") -> {
                "Launching YouTube streaming, Bro! [PROTOCOL:OPEN_YOUTUBE]"
            }
            lower.contains("open camera") || lower.contains("camera") -> {
                "Activating camera sensor arrays! [PROTOCOL:OPEN_CAMERA]"
            }
            lower.contains("search") -> {
                "Launching internet browser interface. [PROTOCOL:LAUNCH_GOOGLE_SEARCH]"
            }
            else -> "Offline connection warning: Internet linkage is currently passive, Bro. I am ready to handle system controls."
        }
    }

    // Cache responses when online so they can be shown when offline
    fun cacheReponse(query: String, response: String) {
        if (response.length < 250) {
            responseCache[query.lowercase().trim()] = response
            Log.d(TAG, "Cached query reply for offline accessibility: '$query'")
        }
    }
}
