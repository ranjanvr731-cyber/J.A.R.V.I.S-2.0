package com.example.brain

import android.util.Base64
import android.util.Log
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Locale

class MemoryManager(private val repository: JarvisRepository) {
    private val TAG = "JarvisMemoryManager"

    // Base64 helper for local simulation of secure encryption privacy layer
    private fun encrypt(plainText: String): String {
        return try {
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    private fun decrypt(cipherText: String): String {
        return try {
            String(Base64.decode(cipherText, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }

    // Live Flow of decrypted memories
    val allMemories: Flow<List<UserMemory>> = repository.allMemories.map { list ->
        list.map { it.copy(value = decrypt(it.value)) }
    }

    // Filtered list by category: personal, conversation, preference, task, event
    fun getMemoriesByCategory(category: String): Flow<List<UserMemory>> {
        return allMemories.map { list ->
            list.filter { it.category.equals(category, ignoreCase = true) }
        }
    }

    // Advanced search algorithm (matching decrypted keywords and importance scores)
    fun searchMemories(query: String): Flow<List<UserMemory>> {
        return allMemories.map { list ->
            list.filter {
                it.key.contains(query, ignoreCase = true) ||
                it.value.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }.sortedByDescending { it.importance }
        }
    }

    // Retrieve relevant context for RAG processing
    suspend fun retrieveRelevantContext(query: String): String {
        val list = allMemories.map { it.toList() }.firstOrNull() ?: emptyList()
        if (list.isEmpty()) return ""

        val queryWords = query.lowercase(Locale.US).split("\\s+".toRegex())
        val matchedMemories = list.map { mem ->
            var score = 0
            val keyLower = mem.key.lowercase(Locale.US)
            val valLower = mem.value.lowercase(Locale.US)
            
            queryWords.forEach { word ->
                if (word.length > 2) {
                    if (keyLower.contains(word)) score += 3
                    if (valLower.contains(word)) score += 2
                }
            }
            score to mem
        }.filter { it.first > 0 }
         .sortedWith(compareByDescending<Pair<Int, UserMemory>> { it.first }.thenByDescending { it.second.importance })
         .take(5)
         .map { it.second }

        if (matchedMemories.isEmpty()) return ""

        return buildString {
            append("RETRIEVED HIGH-RANKING RAG MEMORY CONTEXT:\n")
            matchedMemories.forEach { mem ->
                append("- ${mem.category.uppercase()}: Key=[${mem.key}], Value=[${mem.value}] (Rank/Importance Score: ${mem.importance}/5)\n")
            }
            append("\n")
        }
    }

    // Learn and categorize memory automatically
    suspend fun learnMemory(key: String, value: String, category: String, importance: Int = 3) {
        val cleanCat = category.trim().lowercase(Locale.US)
        val validCategory = when (cleanCat) {
            "personal", "conversation", "preference", "task", "event" -> cleanCat
            "fact" -> "personal"
            "routine" -> "preference"
            else -> "preference"
        }

        // Check if there is already a memory with this exact key
        repository.deleteMemoryByKey(key.trim())

        val encryptedVal = encrypt(value.trim())
        val memory = UserMemory(
            key = key.trim(),
            value = encryptedVal,
            category = validCategory,
            importance = importance.coerceIn(1, 5)
        )
        repository.insertMemory(memory)
        Log.i(TAG, "Secured memory block: [${memory.key}] ranked similarity scale ${memory.importance}/5.")
    }

    // Update memory
    suspend fun updateMemory(id: Long, key: String, value: String, category: String, importance: Int) {
        val encryptedVal = encrypt(value.trim())
        val memory = UserMemory(
            id = id,
            key = key.trim(),
            value = encryptedVal,
            category = category.trim().lowercase(Locale.US),
            importance = importance.coerceIn(1, 5)
        )
        repository.insertMemory(memory)
        Log.d(TAG, "Memory block hot-patched: ID=$id, Importance=${memory.importance}")
    }

    // Delete memory
    suspend fun forgetMemory(id: Long) {
        repository.deleteMemory(id)
        Log.i(TAG, "Deleted memory block ID=$id.")
    }

    // Purges older, low-priority records, while strictly preserving high priority items (Importance >= 4)
    suspend fun compactMemories() {
        val list = repository.allMemories.map { it.toList() }.firstOrNull() ?: return
        // Delete unimportant memories that are older than, say, 7 days
        val thresholdTime = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        list.forEach { mem ->
            if (mem.importance < 4 && mem.timestamp < thresholdTime) {
                repository.deleteMemory(mem.id)
                Log.d(TAG, "Auto-compacted historical memory: ${mem.key}")
            }
        }
    }
}
