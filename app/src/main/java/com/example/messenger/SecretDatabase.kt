package com.example.messenger

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Secret Configuration Entity (Profile setup, passcode, secret codes)
@Entity(tableName = "secret_config")
data class SecretConfigEntity(
    @PrimaryKey val id: Int = 1,
    val encryptedUsername: String = "",
    val passcodeHash: String = "",       // SHA-256 of passcode
    val textCodeHash: String = "",       // SHA-256 of stealth text trigger (e.g. *#777#)
    val voicePhraseHash: String = "",    // SHA-256 of stealth voice phrase (e.g. open sesame)
    val encryptedTextCode: String = "",  // Encrypted text code (readable when unlocked)
    val encryptedVoicePhrase: String = "", // Encrypted voice phrase (readable when unlocked)
    val avatarName: String = "avatar_spy_1"
)

// 2. Secret Friend Entity (Encrypted lists, requests and friendships)
@Entity(tableName = "secret_friends")
data class SecretFriendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedName: String,
    val avatarName: String,
    val status: String,                 // "SENT_REQUEST", "RECEIVED_REQUEST", "FRIEND"
    val createdAt: Long = System.currentTimeMillis()
)

// 3. Secret Message Entity (Encrypted messaging table)
@Entity(tableName = "secret_messages")
data class SecretMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val friendId: Long,
    val isIncoming: Boolean,
    val encryptedText: String,
    val attachmentType: String?,        // null, "VOICE", "IMAGE", "FILE"
    val encryptedAttachmentPath: String?, // Encrypted uri or local filesystem path
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

// 4. Data Access Object for Secret Messenger DB
@Dao
interface SecretDao {
    @Query("SELECT * FROM secret_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<SecretConfigEntity?>

    @Query("SELECT * FROM secret_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigDirect(): SecretConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: SecretConfigEntity)

    @Query("SELECT * FROM secret_friends ORDER BY createdAt DESC")
    fun getAllFriendsFlow(): Flow<List<SecretFriendEntity>>

    @Query("SELECT * FROM secret_friends WHERE id = :id LIMIT 1")
    suspend fun getFriendById(id: Long): SecretFriendEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: SecretFriendEntity): Long

    @Update
    suspend fun updateFriend(friend: SecretFriendEntity)

    @Query("DELETE FROM secret_friends WHERE id = :id")
    suspend fun deleteFriendById(id: Long)

    @Query("SELECT * FROM secret_messages WHERE friendId = :friendId ORDER BY timestamp ASC")
    fun getMessagesForFriendFlow(friendId: Long): Flow<List<SecretMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SecretMessageEntity): Long

    @Query("UPDATE secret_messages SET isRead = 1 WHERE friendId = :friendId AND isIncoming = 1")
    suspend fun markMessagesAsRead(friendId: Long)

    @Query("DELETE FROM secret_messages WHERE friendId = :friendId")
    suspend fun deleteMessagesForFriend(friendId: Long)
}

// 5. Standalone Room Database for Secret Messenger
@Database(entities = [SecretConfigEntity::class, SecretFriendEntity::class, SecretMessageEntity::class], version = 1, exportSchema = false)
abstract class SecretDatabase : RoomDatabase() {
    abstract fun secretDao(): SecretDao

    companion object {
        @Volatile
        private var INSTANCE: SecretDatabase? = null

        fun getDatabase(context: Context): SecretDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecretDatabase::class.java,
                    "secret_messenger_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
