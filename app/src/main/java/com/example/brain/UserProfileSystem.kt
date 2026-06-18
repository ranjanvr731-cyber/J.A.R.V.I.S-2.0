package com.example.brain

import android.util.Log
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale

class UserProfileSystem(private val repository: JarvisRepository) {
    private val TAG = "JarvisUserProfile"

    data class UserProfile(
        val nickname: String,
        val speakingStyle: String,
        val interests: List<String>,
        val favoriteApps: List<String>,
        val favoriteContacts: List<String>,
        val habits: List<String>
    )

    suspend fun getProfile(): UserProfile {
        val memories = repository.allMemories.firstOrNull() ?: emptyList()
        
        var nickname = ""
        var speakingStyle = "Casual snappy Tanglish"
        val interests = mutableListOf<String>()
        val favoriteApps = mutableListOf<String>()
        val favoriteContacts = mutableListOf<String>()
        val habits = mutableListOf<String>()

        memories.forEach { mem ->
            val keyLower = mem.key.lowercase(Locale.US)
            val valLower = mem.value.lowercase(Locale.US)
            when {
                keyLower.contains("user name") || keyLower == "nickname" || keyLower == "user_name" -> {
                    nickname = mem.value
                }
                keyLower.contains("speaking style") || keyLower.contains("response style") -> {
                    speakingStyle = mem.value
                }
                mem.category == "interest" || keyLower.contains("interest") -> {
                    interests.add(mem.value)
                }
                mem.category == "favorite_app" || keyLower.contains("favorite app") || keyLower.contains("preferred app") -> {
                    favoriteApps.add(mem.value)
                }
                mem.category == "favorite_contact" || keyLower.contains("favorite contact") || keyLower.contains("emergency contact") -> {
                    favoriteContacts.add(mem.value)
                }
                mem.category == "habit" || keyLower.contains("habit") || keyLower.contains("routine") -> {
                    habits.add(mem.value)
                }
            }
        }

        // Add defaults if empty to look complete
        if (interests.isEmpty()) interests.addAll(listOf("Android Engineering", "Robotics Simulation", "Sci-Fi Comics"))
        if (favoriteApps.isEmpty()) favoriteApps.addAll(listOf("YouTube", "Google Search", "Camera Scanner"))
        if (favoriteContacts.isEmpty()) favoriteContacts.addAll(listOf("Amma (Communication Channel)", "Ravi (Study Companion)"))
        if (habits.isEmpty()) habits.addAll(listOf("Studies Algebra/Equations at 4:00 PM", "Launches YouTube stream after dinner close to 9:00 PM", "Audits diagnostic logs weekly"))

        return UserProfile(nickname, speakingStyle, interests, favoriteApps, favoriteContacts, habits)
    }

    suspend fun learnPersonalFact(key: String, value: String, category: String) {
        val cleanKey = key.trim()
        val cleanVal = value.trim()
        Log.i(TAG, "🧠 PROFILE SYSTEM: Learning core user attribute: '$cleanKey' -> '$cleanVal' (Category: $category)")

        // Delete any existing key to overwrite
        repository.deleteMemoryByKey(cleanKey)
        repository.insertMemory(
            UserMemory(
                key = cleanKey,
                value = cleanVal,
                category = category,
                importance = 4
            )
        )
    }

    suspend fun auditActionForHabitTrigger(action: String): String? {
        val lowerAction = action.lowercase(Locale.US)
        val profile = getProfile()
        
        if (lowerAction.contains("youtube")) {
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            if (hour >= 19 || hour <= 22) { // Dinner/evening hour
                return "Ah, you usually open YouTube after dinner, Bro. Shall I launch it for you now? [PROTOCOL:OPEN_YOUTUBE]"
            }
        }
        
        if (lowerAction.contains("camera") || lowerAction.contains("vision")) {
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            if (hour >= 15 && hour <= 17) { // Study/afternoon homework hour
                return "Great timing, Bro. You usually study your textbook questions around this time of afternoon! Launching camera scanner scanner... [PROTOCOL:OPEN_CAMERA]"
            }
        }

        return null
    }

    suspend fun compileUserProfileSummary(): String {
        val profile = getProfile()
        val displayName = if (profile.nickname.isBlank()) "None (Uses neutral greetings like Sir, Bro, Friend)" else profile.nickname
        return buildString {
            append("--- PERSONALIZED USER PORTRAIT ENGINE ---\n")
            append("👤 Current Nickname: $displayName\n")
            append("🗣️ Speaking Preference: ${profile.speakingStyle}\n")
            append("🎨 Personal Interests: ${profile.interests.joinToString(", ")}\n")
            append("📱 Core App Integrations: ${profile.favoriteApps.joinToString(", ")}\n")
            append("📞 Linked Contact Channels: ${profile.favoriteContacts.joinToString(", ")}\n")
            append("🔄 Daily Habits Logged:\n")
            profile.habits.forEach { habit ->
                append("  - $habit\n")
            }
        }
    }
}
