package com.example.messenger

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SecretMessengerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SecretDatabase.getDatabase(application)
    val dao = db.secretDao()

    // --- State Stream Observers ---
    val configFlow: StateFlow<SecretConfigEntity?> = dao.getConfigFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Raw encrypted data from Db
    val rawFriendsFlow: StateFlow<List<SecretFriendEntity>> = dao.getAllFriendsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- In-Memory Cryptographic Credentials (NEVER plain-persisted) ---
    private val _secretPasscode = MutableStateFlow<String?>(null)
    val secretPasscode: StateFlow<String?> = _secretPasscode.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Decrypted friends list resolved reactively using in-memory passcode
    val decryptedFriends: StateFlow<List<DecryptedFriend>> = combine(rawFriendsFlow, _secretPasscode) { rawList, passcode ->
        if (passcode == null) emptyList()
        else {
            rawList.map { entity ->
                val plainName = SecretCrypto.decryptAES(entity.encryptedName, passcode)
                DecryptedFriend(
                    id = entity.id,
                    name = if (plainName.isEmpty()) "[Encrypted Name]" else plainName,
                    avatarName = entity.avatarName,
                    status = entity.status,
                    createdAt = entity.createdAt
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chat Management (Saves current chat state & drafts for emergency hide)
    private val _activeFriendId = MutableStateFlow<Long?>(null)
    val activeFriendId: StateFlow<Long?> = _activeFriendId.asStateFlow()

    private val _activeDraftMap = MutableStateFlow<Map<Long, String>>(emptyMap())
    val activeDraftMap: StateFlow<Map<Long, String>> = _activeDraftMap.asStateFlow()

    // Observe active messages decrypted reactively
    val activeMessages: StateFlow<List<DecryptedMessage>> = _activeFriendId.flatMapLatest { friendId ->
        if (friendId == null) {
            flowOf(emptyList())
        } else {
            // Read raw from db combined with passcode
            dao.getMessagesForFriendFlow(friendId).combine(_secretPasscode) { rawMsgs, passcode ->
                if (passcode == null) emptyList()
                else {
                    rawMsgs.map { entity ->
                        val plainText = SecretCrypto.decryptAES(entity.encryptedText, passcode)
                        val plainPath = entity.encryptedAttachmentPath?.let { SecretCrypto.decryptAES(it, passcode) }
                        DecryptedMessage(
                            id = entity.id,
                            friendId = entity.friendId,
                            isIncoming = entity.isIncoming,
                            text = if (plainText.isEmpty() && entity.attachmentType != null) "" else plainText,
                            attachmentType = entity.attachmentType,
                            attachmentPath = plainPath,
                            timestamp = entity.timestamp,
                            isRead = entity.isRead
                        )
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Setup / Unlock UI Flags
    private val _showPasscodeScreen = MutableStateFlow(false)
    val showPasscodeScreen: StateFlow<Boolean> = _showPasscodeScreen.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        createStealthNotificationChannel()
    }

    // --- Authentication & Locks ---
    fun setPasscodeScreenVisible(visible: Boolean) {
        _showPasscodeScreen.value = visible
        if (!visible) {
            _errorMessage.value = null
        }
    }

    suspend fun verifyAndUnlock(enteredPasscode: String): Boolean {
        return withContext(Dispatchers.IO) {
            val config = dao.getConfigDirect() ?: return@withContext false
            val attemptHash = SecretCrypto.hashString(enteredPasscode)
            if (config.passcodeHash == attemptHash) {
                _secretPasscode.value = enteredPasscode
                _isUnlocked.value = true
                _errorMessage.value = null
                true
            } else {
                _errorMessage.value = "Access Denied."
                false
            }
        }
    }

    suspend fun createSecretProfile(
        username: String,
        passcode: String,
        textCode: String,
        voicePhrase: String,
        avatar: String = "avatar_spy_1"
    ) {
        withContext(Dispatchers.IO) {
            val passcodeHash = SecretCrypto.hashString(passcode)
            val textCodeHash = SecretCrypto.hashString(textCode)
            val voicePhraseHash = SecretCrypto.hashString(voicePhrase)

            // Encrypt parameters using the custom passcode so they can be securely viewed in-app
            val encryptedUser = SecretCrypto.encryptAES(username, passcode)
            val encryptedTextCode = SecretCrypto.encryptAES(textCode, passcode)
            val encryptedVoicePhrase = SecretCrypto.encryptAES(voicePhrase, passcode)

            val newConfig = SecretConfigEntity(
                id = 1,
                encryptedUsername = encryptedUser,
                passcodeHash = passcodeHash,
                textCodeHash = textCodeHash,
                voicePhraseHash = voicePhraseHash,
                encryptedTextCode = encryptedTextCode,
                encryptedVoicePhrase = encryptedVoicePhrase,
                avatarName = avatar
            )
            dao.saveConfig(newConfig)
            _secretPasscode.value = passcode
            _isUnlocked.value = true
            _errorMessage.value = null
        }
    }

    // Reset profile configuration (for complete safety or diagnostic wipes)
    fun wipeSecretMessenger() {
        viewModelScope.launch(Dispatchers.IO) {
            _secretPasscode.value = null
            _isUnlocked.value = false
            _activeFriendId.value = null
            db.clearAllTables()
        }
    }

    // Emergency Lock / Quick Hide: Purges keys instantly from RAM
    fun emergencyLock() {
        _secretPasscode.value = null
        _isUnlocked.value = false
        _showPasscodeScreen.value = false
        _errorMessage.value = null
        Log.d("SECRET_MESSENGER", "Emergency lock triggered: Cryptographic keys successfully wiped from memory.")
    }

    // --- Friend Management ---
    fun setActiveFriend(friendId: Long?) {
        _activeFriendId.value = friendId
        if (friendId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                dao.markMessagesAsRead(friendId)
            }
        }
    }

    fun updateDraft(friendId: Long, draft: String) {
        val current = _activeDraftMap.value.toMutableMap()
        if (draft.isEmpty()) {
            current.remove(friendId)
        } else {
            current[friendId] = draft
        }
        _activeDraftMap.value = current
    }

    fun initiateFriendRequest(name: String, avatar: String) {
        val passcode = _secretPasscode.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val encryptedName = SecretCrypto.encryptAES(name, passcode)
            val newFriend = SecretFriendEntity(
                encryptedName = encryptedName,
                avatarName = avatar,
                status = "SENT_REQUEST"
            )
            val fid = dao.insertFriend(newFriend)

            // Simulate automatic friend approval and responsive agent replies offline!
            simulateInteractiveFriendResponse(fid, name)
        }
    }

    fun acceptIncomingFriendRequest(friendId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val friend = dao.getFriendById(friendId) ?: return@launch
            val updated = friend.copy(status = "FRIEND")
            dao.updateFriend(updated)
        }
    }

    fun removeFriend(friendId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteFriendById(friendId)
            dao.deleteMessagesForFriend(friendId)
            if (_activeFriendId.value == friendId) {
                _activeFriendId.value = null
            }
        }
    }

    // --- Messaging and Attachments ---
    fun sendSecretMessage(
        friendId: Long,
        text: String,
        attachmentType: String? = null,
        attachmentPath: String? = null
    ) {
        val passcode = _secretPasscode.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val encryptedText = SecretCrypto.encryptAES(text, passcode)
            val encryptedPath = attachmentPath?.let { SecretCrypto.encryptAES(it, passcode) }

            val message = SecretMessageEntity(
                friendId = friendId,
                isIncoming = false,
                encryptedText = encryptedText,
                attachmentType = attachmentType,
                encryptedAttachmentPath = encryptedPath,
                isRead = true
            )
            dao.insertMessage(message)

            // Clear draft
            updateDraft(friendId, "")

            // Trigger simulated responder with stealth notification!
            val friend = dao.getFriendById(friendId)
            val friendName = friend?.let { SecretCrypto.decryptAES(it.encryptedName, passcode) } ?: "Agent"
            triggerMockIncomingReply(friendId, friendName, text)
        }
    }

    // Interactive offline system responding to words
    private fun simulateInteractiveFriendResponse(friendId: Long, name: String) {
        val passcode = _secretPasscode.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
            // Friend accepts request or is accepted
            val friend = dao.getFriendById(friendId) ?: return@launch
            val updated = friend.copy(status = "FRIEND")
            dao.updateFriend(updated)

            // Queue initial incoming message
            val welcomeText = "Handshake verified, Bro. This is $name. Undercover channel established securely. Send 'brief' to see coordinates!"
            val encryptedText = SecretCrypto.encryptAES(welcomeText, passcode)
            val welcomeMsg = SecretMessageEntity(
                friendId = friendId,
                isIncoming = true,
                encryptedText = encryptedText,
                attachmentType = null,
                encryptedAttachmentPath = null,
                isRead = false
            )
            dao.insertMessage(welcomeMsg)
            postStealthNotification()
        }
    }

    private fun triggerMockIncomingReply(friendId: Long, friendName: String, triggerText: String) {
        val passcode = _secretPasscode.value ?: return
        val query = triggerText.lowercase().trim()
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(3000) // Realistic secure processing pause

            val responses = when {
                query.contains("hello") || query.contains("hey") || query.contains("hi") -> {
                    listOf("Handshake active, Bro. Waiting on your operational instructions.")
                }
                query.contains("brief") || query.contains("mission") || query.contains("coordinate") -> {
                    listOf(
                        "Mission Status: Safehouse activated. Vector markers are scheduled for 0400 hours.",
                        "Coordinates: Grid 44-B, Sector Alpha. Encrypted comms strictly maintained under passcode."
                    )
                }
                query.contains("status") || query.contains("ready") -> {
                    listOf("All systems checked. Quantum key distribution intact. No plaintext indicators spotted.")
                }
                query.contains("voice") -> {
                    listOf("Secure voice packet transmitted successfully, Bro.")
                }
                query.contains("image") || query.contains("file") -> {
                    listOf("Secure asset decryption completed. Over and out.")
                }
                else -> {
                    listOf("Declassified feedback: Received instruction '${triggerText}'. Relaying through secure pipeline node...")
                }
            }

            for (resp in responses) {
                val encryptedText = SecretCrypto.encryptAES(resp, passcode)
                val msg = SecretMessageEntity(
                    friendId = friendId,
                    isIncoming = true,
                    encryptedText = encryptedText,
                    attachmentType = if (query.contains("voice") && resp.contains("voice")) "VOICE" else null,
                    encryptedAttachmentPath = if (query.contains("voice") && resp.contains("voice")) {
                        SecretCrypto.encryptAES("simulated_secure_vrec_01.aac", passcode)
                    } else null,
                    isRead = false
                )
                dao.insertMessage(msg)
                postStealthNotification()
                if (responses.size > 1) {
                    kotlinx.coroutines.delay(1500)
                }
            }
        }
    }

    // --- Stealth Notification System ---
    private fun createStealthNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "System Security Diagnostics"
            val desc = "Notifications regarding system telemetry verification."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("stealth_comm_channel_v1", name, importance).apply {
                description = desc
            }
            val manager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun postStealthNotification() {
        val context = getApplication<Application>()
        
        // Stealth content based on System Locale (Tamil vs English)
        val defaultLocale = Locale.getDefault()
        val isTamil = defaultLocale.language == "ta" || defaultLocale.displayName.lowercase().contains("tamil")
        
        val stealthTitle = if (isTamil) "அபாய எச்சரிக்கை" else "System System Diagnostics"
        val stealthText = if (isTamil) "நீங்கள் எதையோ மறந்துவிட்டீர்கள்." else "You forgot something."

        // Clicking notification launches MainActivity with explicit tap routing extra
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SECRET_NOTIFICATION_TAP", true)
        }

        val requestId = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "stealth_comm_channel_v1")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim) // Decoupled custom-stealth status icon
            .setContentTitle(stealthTitle)
            .setContentText(stealthText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Completely hide contents on lock screen

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(72517, builder.build())
        Log.d("STEALTH_NOTIF", "Stealth Notification posted: $stealthText")
    }
}

// --- Domain Models ---
data class DecryptedFriend(
    val id: Long,
    val name: String,
    val avatarName: String,
    val status: String,
    val createdAt: Long
)

data class DecryptedMessage(
    val id: Long,
    val friendId: Long,
    val isIncoming: Boolean,
    val text: String,
    val attachmentType: String?,
    val attachmentPath: String?,
    val timestamp: Long,
    val isRead: Boolean
)
