package com.example.brain

import android.util.Log
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.first
import java.util.Locale

class LearningEngine(private val repository: JarvisRepository) {
    private val TAG = "JarvisLearningEngine"
    private val commandUsageCounts = mutableMapOf<String, Int>()

    // Track a verbal command usage
    fun trackCommand(command: String) {
        val cmd = command.lowercase(Locale.US).trim()
        val count = commandUsageCounts.getOrDefault(cmd, 0) + 1
        commandUsageCounts[cmd] = count
        Log.d(TAG, "Command Usage Stats: '$cmd' clicked/spoken $count times.")
    }

    // Get command analytics
    fun getFrequentlyUsedCommands(): List<Pair<String, Int>> {
        return commandUsageCounts.toList().sortedByDescending { it.second }.take(10)
    }

    // Learn from direct user correction (e.g. "No, correct answer is...")
    suspend fun registerCorrection(originalPrompt: String, userCorrection: String) {
        Log.i(TAG, "Registering cognitive correction: Prompt='$originalPrompt' -> Corrected='$userCorrection'")
        
        // Save the correction to memories
        val cleanKey = "correction_${System.currentTimeMillis()}"
        repository.insertMemory(
            UserMemory(
                key = cleanKey,
                value = "If asked about '$originalPrompt', user corrected to: $userCorrection",
                category = "correction"
            )
        )
    }

    // Build user profile parameters based of their interaction histories
    suspend fun compileUserBehaviorProfile(): String {
        val memories = repository.allMemories.first()
        val nameMem = memories.firstOrNull { it.key.lowercase() == "user name" }?.value ?: "User"
        val preferences = memories.filter { it.category == "preference" }
        
        return buildString {
            append("User: $nameMem\n")
            append("Style: Casual Adaptive\n")
            append("Preferences count: ${preferences.size}\n")
            append("Command cache logs: ${commandUsageCounts.size} active tracks\n")
        }
    }
}
