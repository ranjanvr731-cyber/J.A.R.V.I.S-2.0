package com.example.brain

import android.util.Log
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

class MemoryManager(private val repository: JarvisRepository) {
    private val TAG = "JarvisMemoryManager"

    // Live Flow of all memories
    val allMemories: Flow<List<UserMemory>> = repository.allMemories

    // Retrieve categorized memories
    fun getMemoriesByCategory(category: String): Flow<List<UserMemory>> {
        return repository.allMemories.map { list ->
            list.filter { it.category.equals(category, ignoreCase = true) }
        }
    }

    // Search memories matching query
    fun searchMemories(query: String): Flow<List<UserMemory>> {
        return repository.allMemories.map { list ->
            list.filter { 
                it.key.contains(query, ignoreCase = true) || 
                it.value.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }
        }
    }

    // Save a new memory
    suspend fun learnMemory(key: String, value: String, category: String) {
        val memory = UserMemory(
            key = key.trim(),
            value = value.trim(),
            category = category.trim().lowercase(Locale.US)
        )
        repository.insertMemory(memory)
        Log.d(TAG, "Learned user attribute: ${memory.key} -> ${memory.value} (${memory.category})")
    }

    // Update an existing memory
    suspend fun updateMemory(id: Long, key: String, value: String, category: String) {
        val memory = UserMemory(
            id = id,
            key = key.trim(),
            value = value.trim(),
            category = category.trim().lowercase(Locale.US)
        )
        repository.insertMemory(memory)
        Log.d(TAG, "Updated user memory: id=$id, key=$key")
    }

    // Delete memory
    suspend fun forgetMemory(id: Long) {
        repository.deleteMemory(id)
        Log.d(TAG, "Successfully wiped memory block: id=$id")
    }

    // Clear everything
    suspend fun wipeCognitiveHistory() {
        repository.clearChatHistory()
        Log.d(TAG, "Chat cognitive histories wiped clean.")
    }
}
