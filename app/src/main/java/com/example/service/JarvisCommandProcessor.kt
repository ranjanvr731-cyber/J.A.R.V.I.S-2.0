package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.database.UserMemory
import com.example.data.database.JarvisRepository
import kotlinx.coroutines.flow.first
import java.util.Locale

object JarvisCommandProcessor {
    private const val TAG = "JarvisCommandProcessor"

    // Application memory cache of voice protocols and custom commands
    private val inMemoryProtocols = mutableMapOf<String, String>()
    private val inMemoryCustomCommands = mutableMapOf<String, String>()

    // Initialize by loading existing memories from database to memory cache
    suspend fun initialize(repository: JarvisRepository) {
        try {
            val allMemories = repository.allMemories.first()
            synchronized(this) {
                inMemoryProtocols.clear()
                inMemoryCustomCommands.clear()
                
                for (mem in allMemories) {
                    val category = mem.category.lowercase(Locale.US)
                    val key = mem.key.lowercase(Locale.US).trim()
                    if (category == "protocol") {
                        inMemoryProtocols[key] = mem.value
                    } else if (category == "shortcut" || category == "custom_command") {
                        inMemoryCustomCommands[key] = mem.value
                    }
                }
            }
            Log.d(TAG, "Command Processor initialized from database. In-memory cache loaded: ${inMemoryProtocols.size} protocols, ${inMemoryCustomCommands.size} custom commands.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading persistent memories into voice cache", e)
        }
    }

    // Register a new custom command to application memory and database
    suspend fun registerCustomCommand(
        key: String, 
        action: String, 
        repository: JarvisRepository
    ) {
        val trimmedKey = key.lowercase(Locale.US).trim()
        synchronized(this) {
            inMemoryCustomCommands[trimmedKey] = action
        }
        
        // Persist to SQLite Room backing store
        try {
            repository.deleteMemoryByKey(key)
            repository.insertMemory(
                UserMemory(
                    key = key, 
                    value = action, 
                    category = "shortcut"
                )
            )
            Log.d(TAG, "Custom command stored physically and in-memory: '$trimmedKey' -> '$action'")
        } catch (e: Exception) {
            Log.e(TAG, "Database sync fail for command '$key'", e)
        }
    }

    // Register a new protocol to application memory and database
    suspend fun registerProtocol(
        key: String, 
        action: String, 
        repository: JarvisRepository
    ) {
        val trimmedKey = key.lowercase(Locale.US).trim()
        synchronized(this) {
            inMemoryProtocols[trimmedKey] = action
        }
        
        // Persist to sqlite Room backing store
        try {
            repository.deleteMemoryByKey(key)
            repository.insertMemory(
                UserMemory(
                    key = key, 
                    value = action, 
                    category = "protocol"
                )
            )
            Log.d(TAG, "Protocol stored physically and in-memory: '$trimmedKey' -> '$action'")
        } catch (e: Exception) {
            Log.e(TAG, "Database sync fail for protocol '$key'", e)
        }
    }

    // Direct fetch of all in-memory custom commands
    fun getInMemoryCustomCommands(): Map<String, String> = synchronized(this) {
        inMemoryCustomCommands.toMap()
    }

    // Direct fetch of all in-memory protocols
    fun getInMemoryProtocols(): Map<String, String> = synchronized(this) {
        inMemoryProtocols.toMap()
    }

    // Check if voice phrase matches any cached custom command/protocol in-memory
    fun findMatchingAction(speakText: String): MatchedAction? {
        val cleaned = speakText.lowercase(Locale.US).trim().removeSuffix(".").removeSuffix("?").trim()
        
        synchronized(this) {
            // Find exact or partial match in custom commands
            for ((key, action) in inMemoryCustomCommands) {
                if (cleaned == key || cleaned.contains(key)) {
                    return MatchedAction(key = key, action = action, type = "shortcut")
                }
            }
            // Find exact or partial match in protocols
            for ((key, action) in inMemoryProtocols) {
                if (cleaned == key || cleaned.contains(key)) {
                    return MatchedAction(key = key, action = action, type = "protocol")
                }
            }
        }
        return null
    }

    // Parser helper for identifying whether the user's verbal input is a command/protocol registration request
    fun parseRegistration(spokenText: String): ProcessedRegistration? {
        val cleaned = spokenText.lowercase(Locale.US).trim().removeSuffix(".").removeSuffix("?").trim()
        
        // Regex 1: "add/register/save/create custom protocol named [name] to/as [action]"
        val protoRegex = Regex("(?:add|register|save|create)\\s+(?:a\\s+)?(?:custom\\s+)?protocol\\s+(?:named\\s+)?([a-zA-Z0-9 ]+)\\s+(?:to|as)\\s+(.+)")
        val protoMatch = protoRegex.find(cleaned)
        if (protoMatch != null) {
            val name = protoMatch.groupValues[1].trim()
            val action = protoMatch.groupValues[2].trim()
            if (name.isNotEmpty() && action.isNotEmpty()) {
                return ProcessedRegistration(name = name, action = action, type = "protocol")
            }
        }
        
        // Regex 2: "add/register/save/create custom command/shortcut named [name] to/as [action]"
        val commandRegex = Regex("(?:add|register|save|create|remember)\\s+(?:a\\s+)?(?:custom\\s+)?(?:voice\\s+)?(?:command|shortcut|action)\\s+(?:named\\s+)?([a-zA-Z0-9 ]+)\\s+(?:to|as)\\s+(.+)")
        val commandMatch = commandRegex.find(cleaned)
        if (commandMatch != null) {
            val name = commandMatch.groupValues[1].trim()
            val action = commandMatch.groupValues[2].trim()
            if (name.isNotEmpty() && action.isNotEmpty() && !listOf("me", "today", "tomorrow", "this").contains(name)) {
                return ProcessedRegistration(name = name, action = action, type = "shortcut")
            }
        }

        // Regex 3: "remember to [action] when I say [phrase]"
        val whenISayRegex = Regex("(?:remember\\s+to|register\\s+to|save\\s+to)\\s+(.+)\\s+when\\s+i\\s+say\\s+([a-zA-Z0-9 ]+)")
        val whenISayMatch = whenISayRegex.find(cleaned)
        if (whenISayMatch != null) {
            val action = whenISayMatch.groupValues[1].trim()
            val phrase = whenISayMatch.groupValues[2].trim()
            if (action.isNotEmpty() && phrase.isNotEmpty()) {
                return ProcessedRegistration(name = phrase, action = action, type = "shortcut")
            }
        }
        
        return null
    }

    data class MatchedAction(val key: String, val action: String, val type: String)
    data class ProcessedRegistration(val name: String, val action: String, val type: String)

    data class AdaptiveVoiceParams(
        val pitch: Float,
        val rate: Float,
        val styleName: String,
        val description: String
    )

    fun determineAdaptiveVoice(prompt: String): AdaptiveVoiceParams {
        val lower = prompt.lowercase(Locale.US).trim()
        
        // 1. Time-of-day check (late night/soft style)
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val isLateNight = hour >= 22 || hour < 6

        // 2. Emotional/Intent Cues
        val isFrustrated = lower.contains("wrong") || lower.contains("incorrect") || lower.contains("fail") || 
                           lower.contains("annoyed") || lower.contains("stupid") || lower.contains("useless") || 
                           lower.contains("shut up") || lower.contains("frustrated") || lower.contains("slow") ||
                           lower.contains("hate")
                          
        val isExcited = lower.contains("awesome") || lower.contains("wow") || lower.contains("great") || 
                         lower.contains("amazing") || lower.contains("cool") || lower.contains("excit") || 
                         lower.contains("superb") || lower.contains("love it") || lower.contains("fantastic") || 
                         lower.endsWith("!") || lower.contains("yes!")

        val isStudying = lower.contains("explain") || lower.contains("study") || lower.contains("learn") || 
                         lower.contains("solve") || lower.contains("why") || lower.contains("concept") || 
                         lower.contains("physics") || lower.contains("chemistry") || lower.contains("math") || 
                         lower.contains("science") || lower.contains("course") || lower.contains("diagram") ||
                         lower.contains("biology")

        val isQuickQuestion = lower.length < 25 || lower.startsWith("what is") || lower.startsWith("who is") || 
                               lower.startsWith("time") || lower.startsWith("date") || lower.startsWith("weather") ||
                               lower.contains("quick") || lower.contains("brief")

        // 3. Selection
        return when {
            isFrustrated -> AdaptiveVoiceParams(
                pitch = 0.82f, // Calm, reassuring, deep
                rate = 0.88f,  // Deliberate, comforting pace
                styleName = "Calmed & Clear Resonator",
                description = "calmed, soothing, professional, clear"
            )
            isExcited -> AdaptiveVoiceParams(
                pitch = 1.22f, // Higher pitch for high energy/enthusiasm
                rate = 1.10f,  // Fast, exciting cadence
                styleName = "High-Energy Enthusiast",
                description = "enthusiastic, rich, high-energy, faster pacing"
            )
            isLateNight -> AdaptiveVoiceParams(
                pitch = 0.90f, // Softer, quiet, warm pitch
                rate = 0.80f,  // Very cozy, slow, low volume vibe
                styleName = "Nocturnal Cozy Whisperer",
                description = "relaxed, late-night, softer, whisperer pace"
            )
            isStudying -> AdaptiveVoiceParams(
                pitch = 1.02f, // Clear, balanced professional tone
                rate = 0.92f,  // Measured, easy to understand
                styleName = "Scholarly Instructor",
                description = "clear, professional style with patient pacing"
            )
            isQuickQuestion -> AdaptiveVoiceParams(
                pitch = 1.10f, // Snappy cadence
                rate = 1.15f,  // Efficient, high-throughput delivery
                styleName = "Snappy Fact Delivery",
                description = "brief, fast pacing, highly efficient"
            )
            else -> AdaptiveVoiceParams(
                pitch = 0.95f, // Dynamic default
                rate = 1.00f,
                styleName = "Intuitive Balanced Butler",
                description = "natural conversational tone"
            )
        }
    }

    fun isExplicitNameRequest(prompt: String): Boolean {
        val lower = prompt.lowercase(Locale.US)
        return lower.contains("what is my name") || 
               lower.contains("say my name") || 
               lower.contains("who am i") || 
               lower.contains("do you know my name") ||
               lower.contains("do you know who i am") ||
               lower.contains("greet me with my name") ||
               lower.contains("personalized greeting")
    }

    fun sanitizeResponseForPrivacy(
        text: String, 
        userName: String, 
        nameUsageEnabled: Boolean, 
        explicitRequest: Boolean
    ): String {
        if (nameUsageEnabled || explicitRequest) {
            return text
        }
        if (userName.isBlank()) return text
        
        // Find user name precisely using word boundaries to avoid partial words replacement
        val escapedName = Regex.escape(userName)
        val patternWithCommaAndSpaceBefore = Regex(",\\s*$escapedName\\b", RegexOption.IGNORE_CASE)
        val patternWithSpaceAndCommaAfter = Regex("\\b$escapedName,\\s*", RegexOption.IGNORE_CASE)
        val patternWithCommaBeforeAtEnd = Regex(",\\s*$escapedName\\s*\\.?", RegexOption.IGNORE_CASE)
        val patternStandalone = Regex("\\b$escapedName\\b", RegexOption.IGNORE_CASE)
        
        var sanitized = text
        sanitized = sanitized.replace(patternWithCommaAndSpaceBefore, "")
        sanitized = sanitized.replace(patternWithSpaceAndCommaAfter, "")
        sanitized = sanitized.replace(patternWithCommaBeforeAtEnd, ".")
        sanitized = sanitized.replace(patternStandalone, "")
        
        // Let's do cleanup of double dots or spaces
        sanitized = sanitized.replace(Regex("\\s+"), " ")
        sanitized = sanitized.replace("..", ".")
        sanitized = sanitized.replace(",.", ".")
        sanitized = sanitized.replace(" .", ".")
        sanitized = sanitized.trim()
        
        return sanitized
    }
}
