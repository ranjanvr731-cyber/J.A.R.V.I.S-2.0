package com.example.brain

import android.util.Log
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale

class LearningEngine(private val repository: JarvisRepository) {
    private val TAG = "JarvisLearningEngine"
    private val commandUsageCounts = mutableMapOf<String, Int>()
    
    // Dynamic behavior registers
    private val userVocabulary = mutableSetOf<String>()
    private var averageSentenceLength = 0f
    private var totalInputsAnalyzed = 0
    private var detectedSpeakingRate = "Normal" // "Fast", "Normal", "Expressive"
    private var mostActiveHour = -1

    // Tracks user commands to build automation intelligence
    fun trackCommand(command: String) {
        val cmd = command.lowercase(Locale.US).trim()
        val count = commandUsageCounts.getOrDefault(cmd, 0) + 1
        commandUsageCounts[cmd] = count
        Log.i(TAG, "Dynamic Command Logging: '$cmd' invocation count optimized to $count.")
    }

    fun getFrequentlyUsedCommands(): List<Pair<String, Int>> {
        return commandUsageCounts.toList().sortedByDescending { it.second }.take(10)
    }

    // Adapt J.A.R.V.I.S's speaking pattern based on user's query attributes
    fun analyzeandAdaptToSpeakingPattern(input: String) {
        totalInputsAnalyzed++
        val words = input.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val currentLen = words.size
        averageSentenceLength = ((averageSentenceLength * (totalInputsAnalyzed - 1)) + currentLen) / totalInputsAnalyzed
        
        // Track unique vocabulary
        words.forEach { word ->
            if (word.length > 4 && userVocabulary.size < 200) {
                userVocabulary.add(word.lowercase(Locale.US))
            }
        }

        // Analyze input time of day
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        mostActiveHour = hour

        // Speaking rate adaptation
        detectedSpeakingRate = when {
            averageSentenceLength < 5 -> "Fast"
            averageSentenceLength > 12 -> "Expressive/Detailed"
            else -> "Normal"
        }
        
        Log.d(TAG, "Assessed speaking metrics: wordAverage=$averageSentenceLength, vocabSize=${userVocabulary.size}, rate=$detectedSpeakingRate")
    }

    // Registers user clarifications or corrections (e.g. "No, correct answer is ...")
    suspend fun registerCorrection(originalPrompt: String, userCorrection: String) {
        Log.i(TAG, "Registering correction. Prompt='$originalPrompt' -> Corrected='$userCorrection'")
        val cleanKey = "correction_${originalPrompt.lowercase(Locale.US).trim().hashCode()}"
        
        // We'll store it as a specific memory under category "preference" or "event"
        val cleanValue = "When asked '$originalPrompt', answer is: $userCorrection"
        
        val encryptionKey = "correction_${originalPrompt.lowercase(Locale.US).trim()}"
        repository.deleteMemoryByKey(encryptionKey)
        repository.insertMemory(
            UserMemory(
                key = encryptionKey,
                value = android.util.Base64.encodeToString(cleanValue.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP),
                category = "preference",
                importance = 5 // Highest priority, should never be flushed out
            )
        )
    }

    // Looks up if the user has corrected a similar query before
    suspend fun getCorrectionLookup(query: String): String? {
        val lowerQuery = query.lowercase(Locale.US).trim()
        val memories = repository.allMemories.firstOrNull() ?: return null
        
        // Find if a key matches "correction_"
        memories.forEach { mem ->
            if (mem.key.startsWith("correction_")) {
                val origPart = mem.key.substringAfter("correction_")
                if (lowerQuery.contains(origPart) || origPart.contains(lowerQuery)) {
                    val decrypted = try {
                        String(android.util.Base64.decode(mem.value, android.util.Base64.NO_WRAP), Charsets.UTF_8)
                    } catch (e: Exception) {
                        mem.value
                    }
                    if (decrypted.contains("answer is: ")) {
                        return decrypted.substringAfter("answer is: ").trim()
                    }
                }
            }
        }
        return null
    }

    // Compile dynamic Behavior Profile for display on diagnostics dashboard
    fun compileUserBehaviorProfile(): String {
        return buildString {
            append("--- BEHAVIOR PROFILE MATRIX ---\n")
            append("🔋 Adaptive Dialogue Mode: CASUAL_TANGLE\n")
            append("📈 User Speaking Tempo: $detectedSpeakingRate\n")
            append("🗣️ Avg Dialog Length: %.1f words\n".format(averageSentenceLength))
            append("💾 Active Vocab Depth: ${userVocabulary.size} terms logged\n")
            append("🕒 Peak Engagement Hour: ${if (mostActiveHour == -1) "Awaiting Logs" else "$mostActiveHour:00"}\n")
            append("💡 Automation Suggestion: ")
            if (commandUsageCounts.isNotEmpty()) {
                val mostFav = commandUsageCounts.toList().maxByOrNull { it.second }?.first ?: ""
                append("Schedule automatic macro trigger for '$mostFav'")
            } else {
                append("Requires deeper verbal sequence inputs")
            }
        }
    }
}
