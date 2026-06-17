package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ConversationMessage::class, UserMemory::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao
}
