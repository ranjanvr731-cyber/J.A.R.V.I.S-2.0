package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "USER" or "JARVIS"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCode: Boolean = false,
    val language: String? = null
)

@Entity(tableName = "user_memories")
data class UserMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String, // "personal", "conversation", "preference", "task", "event"
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Int = 3 // Memory ranking (1 to 5)
)
