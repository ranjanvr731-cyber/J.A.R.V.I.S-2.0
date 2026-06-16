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
}
