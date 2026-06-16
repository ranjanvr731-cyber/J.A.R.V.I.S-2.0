package com.example.data.database

import kotlinx.coroutines.flow.Flow

class JarvisRepository(private val jarvisDao: JarvisDao) {
    val allMessages: Flow<List<ConversationMessage>> = jarvisDao.getAllMessages()
    val allMemories: Flow<List<UserMemory>> = jarvisDao.getAllMemories()

    suspend fun insertMessage(message: ConversationMessage) {
        jarvisDao.insertMessage(message)
    }

    suspend fun clearChatHistory() {
        jarvisDao.clearChatHistory()
    }

    suspend fun insertMemory(memory: UserMemory) {
        jarvisDao.insertMemory(memory)
    }

    suspend fun deleteMemory(id: Long) {
        jarvisDao.deleteMemory(id)
    }

    suspend fun deleteMemoryByKey(key: String) {
        jarvisDao.deleteMemoryByKey(key)
    }
}
