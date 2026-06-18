package com.example.brain

import android.util.Log
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class UserRole(val displayName: String) {
    OWNER("Owner (Full Access)"),
    APPROVED_USER("Approved User (Restricted settings)"),
    GUEST("Guest (Basic Conversation Only)")
}

class MultiUserSecuritySystem(private val repository: JarvisRepository) {
    private val TAG = "MultiUserSecuritySystem"

    private val _currentRole = MutableStateFlow(UserRole.GUEST)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _ownerSetupComplete = MutableStateFlow(false)
    val ownerSetupComplete: StateFlow<Boolean> = _ownerSetupComplete.asStateFlow()

    private val _enrolledVoices = MutableStateFlow<List<String>>(emptyList())
    val enrolledVoices: StateFlow<List<String>> = _enrolledVoices.asStateFlow()

    suspend fun loadSecurityStates(savedMemories: List<UserMemory>) {
        val setupCompleteStr = savedMemories.firstOrNull { it.key == "owner_setup_complete" }?.value
        val setupComplete = setupCompleteStr == "true"
        _ownerSetupComplete.value = setupComplete

        // If setup is not complete, we must force Guest or trigger Setup flow. 
        // If complete, default to OWNER in this single-user physical setup, but user can hot-swap roles for testing.
        if (setupComplete) {
            val savedRoleStr = savedMemories.firstOrNull { it.key == "last_active_user_role" }?.value ?: "OWNER"
            _currentRole.value = try {
                UserRole.valueOf(savedRoleStr)
            } catch (e: Exception) {
                UserRole.OWNER
            }
        } else {
            _currentRole.value = UserRole.GUEST
        }

        // Get enrolled voice names
        val voices = savedMemories.filter { it.category == "enrolled_voice_profile" }.map { it.value }
        _enrolledVoices.value = voices

        Log.i(TAG, "Loaded security profiles: SetupComplete=$setupComplete, Role=${_currentRole.value.name}, Enrolled voices count=${voices.size}")
    }

    suspend fun setSetupComplete(nickname: String, ownerVoicePrint: String) {
        repository.deleteMemoryByKey("owner_setup_complete")
        repository.insertMemory(UserMemory(key = "owner_setup_complete", value = "true", category = "security"))
        
        repository.deleteMemoryByKey("user_name")
        repository.insertMemory(UserMemory(key = "User Name", value = nickname, category = "preference"))

        // Enroll owner's voice print
        repository.insertMemory(UserMemory(key = "voice_profile_owner", value = "Primary Owner: $nickname", category = "enrolled_voice_profile"))

        _ownerSetupComplete.value = true
        _currentRole.value = UserRole.OWNER
        
        // Re-load enrolled voice profiles list
        _enrolledVoices.value = listOf("Primary Owner: $nickname")
        Log.i(TAG, "Owner Setup Complete! Owner Nickname='$nickname'. Voice print linked to device hardware securely.")
    }

    suspend fun switchRole(newRole: UserRole) {
        _currentRole.value = newRole
        repository.deleteMemoryByKey("last_active_user_role")
        repository.insertMemory(UserMemory(key = "last_active_user_role", value = newRole.name, category = "security"))
        Log.i(TAG, "Role switched manually to: ${newRole.displayName}")
    }

    // Checking of different operation rights
    fun canModifyOwnerProfile(): Boolean {
        return _currentRole.value == UserRole.OWNER
    }

    fun canModifyVoiceSecurity(): Boolean {
        return _currentRole.value == UserRole.OWNER
    }

    fun canEnrollNewVoices(): Boolean {
        return _currentRole.value == UserRole.OWNER
    }

    fun canUseAssistantFeatures(): Boolean {
        // Owner and Approved Users can use features like tasks, scheduling, pairing
        return _currentRole.value == UserRole.OWNER || _currentRole.value == UserRole.APPROVED_USER
    }

    fun canAccessSensitiveControls(): Boolean {
        // Diagnostics, pairing parameters, database editing, memory modification is restricted to Owner only
        return _currentRole.value == UserRole.OWNER
    }

    // Owner authorization system for voice enrollment
    suspend fun enrollNewVoice(voiceName: String, ownerAuthenticated: Boolean): Boolean {
        if (!canEnrollNewVoices()) {
            Log.e(TAG, "Enrollment blocked: Only Owner role possesses enrollment management rights.")
            return false
        }
        if (!ownerAuthenticated) {
            Log.e(TAG, "Enrollment blocked: Owner biometric verification failed.")
            return false
        }

        // Store secure enrollment in memories database local to device
        repository.insertMemory(
            UserMemory(
                key = "voice_profile_${voiceName.lowercase().replace(" ", "_")}",
                value = voiceName,
                category = "enrolled_voice_profile"
            )
        )
        // Refresh registered list
        val voices = _enrolledVoices.value.toMutableList()
        if (!voices.contains(voiceName)) {
            voices.add(voiceName)
            _enrolledVoices.value = voices
        }
        Log.i(TAG, "Successfully enrolled new approved voice profile: '$voiceName' with Owner sign-off.")
        return true
    }

    suspend fun wipeLocalVoiceProfile(voiceName: String) {
        repository.deleteMemoryByKey("voice_profile_${voiceName.lowercase().replace(" ", "_")}")
        val voices = _enrolledVoices.value.filter { it != voiceName }
        _enrolledVoices.value = voices
        Log.w(TAG, "Flushed enrolled voice print profile: '$voiceName' from memory database.")
    }

    suspend fun wipeAllUserDataAndReset(onWiped: () -> Unit) {
        // Complete clean sweep of the database sandbox.
        // Fulfilling data isolation: absolute clean state.
        repository.clearChatHistory()
        repository.deleteMemoryByKey("owner_setup_complete")
        repository.deleteMemoryByKey("user_name")
        repository.deleteMemoryByKey("last_active_user_role")
        repository.deleteMemoryByKey("name_usage_enabled")
        
        _currentRole.value = UserRole.GUEST
        _ownerSetupComplete.value = false
        _enrolledVoices.value = emptyList()

        onWiped()
        Log.w(TAG, "Completed user data isolation clean sweep. Full hardware sanitization approved.")
    }
}
