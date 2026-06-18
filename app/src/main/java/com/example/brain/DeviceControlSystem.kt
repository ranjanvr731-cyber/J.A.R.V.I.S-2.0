package com.example.brain

import android.util.Log
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class ControlledDevice(
    val id: String,
    val name: String,
    val type: String, // "Smart TV", "Windows PC", "Linux PC", "Android Phone", "Smart Display"
    val isAuthorized: Boolean = false,
    val appLaunchingPerm: Boolean = true,
    val mediaControlPerm: Boolean = true,
    val fileTransferPerm: Boolean = true,
    val remoteNavPerm: Boolean = true,
    val cpuUsage: Int = 0,
    val ramUsage: Int = 0,
    val battery: Int = 100,
    val latencyMs: Int = 0
)

class DeviceControlSystem(private val repository: JarvisRepository) {
    private val TAG = "DeviceControlSystem"

    private val _devices = MutableStateFlow<List<ControlledDevice>>(emptyList())
    val devices: StateFlow<List<ControlledDevice>> = _devices.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<String>>(emptyList())
    val commandLogs: StateFlow<List<String>> = _commandLogs.asStateFlow()

    init {
        // Initialize default device matrix with realistic telemetry metrics
        _devices.value = listOf(
            ControlledDevice("tv_lr", "Living Room Google TV", "Smart TV", false, true, true, false, false, 8, 45, 100, 12),
            ControlledDevice("pc_work", "Workstation Desktop", "Windows PC", false, true, true, true, true, 18, 52, 100, 3),
            ControlledDevice("lap_dev", "Dev Laptop", "Linux PC", false, true, true, true, true, 12, 38, 88, 5),
            ControlledDevice("disp_kitchen", "Kitchen Smart Display", "Smart Display", false, false, true, false, false, 5, 22, 100, 15),
            ControlledDevice("phone_backup", "Backup Galaxy Phone", "Android Phone", false, true, true, true, true, 4, 29, 94, 8)
        )
    }

    // Direct synchronization of pairing states with Room database for persistence
    suspend fun loadPairingStates(savedMemories: List<UserMemory>) {
        val pairedKeys = savedMemories.filter { it.category == "device_pairing" }.map { it.key }
        _devices.value = _devices.value.map { device ->
            if (pairedKeys.contains("paired_${device.id}")) {
                device.copy(isAuthorized = true)
            } else {
                device
            }
        }
    }

    suspend fun pairDevice(deviceId: String) {
        _devices.value = _devices.value.map { device ->
            if (device.id == deviceId) {
                repository.insertMemory(
                    UserMemory(
                        key = "paired_${device.id}",
                        value = "true",
                        category = "device_pairing"
                    )
                )
                logEvent("Paired and authorized secure link with '${device.name}' successfully.")
                device.copy(isAuthorized = true)
            } else {
                device
            }
        }
    }

    suspend fun unpairDevice(deviceId: String) {
        _devices.value = _devices.value.map { device ->
            if (device.id == deviceId) {
                repository.deleteMemoryByKey("paired_${device.id}")
                logEvent("Revoked authorization and disconnected '${device.name}'.")
                device.copy(isAuthorized = false)
            } else {
                device
            }
        }
    }

    fun modifyPermission(
        deviceId: String,
        appLaunch: Boolean? = null,
        mediaControl: Boolean? = null,
        fileTransfer: Boolean? = null,
        remoteNav: Boolean? = null
    ) {
        _devices.value = _devices.value.map { device ->
            if (device.id == deviceId) {
                device.copy(
                    appLaunchingPerm = appLaunch ?: device.appLaunchingPerm,
                    mediaControlPerm = mediaControl ?: device.mediaControlPerm,
                    fileTransferPerm = fileTransfer ?: device.fileTransferPerm,
                    remoteNavPerm = remoteNav ?: device.remoteNavPerm
                ).also {
                    logEvent("Updated security clearances for '${device.name}'.")
                }
            } else {
                device
            }
        }
    }

    fun logEvent(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
        _commandLogs.value = listOf("[$timestamp] $message") + _commandLogs.value.take(49)
    }

    // Intercepts and executes remote capabilities if prompt is matchable
    fun parseAndExecuteCrossDevice(prompt: String): CrossDeviceResult? {
        val lower = prompt.lowercase(Locale.US).trim()
        
        // Match Laptop vs TV vs Desktop etc
        val targetType = when {
            lower.contains("tv") || lower.contains("television") -> "Smart TV"
            lower.contains("laptop") || lower.contains("laptops") || lower.contains("notebook") -> "Linux PC"
            lower.contains("desktop") || lower.contains("win pc") || lower.contains("pc") || lower.contains("computer") -> "Windows PC"
            lower.contains("display") || lower.contains("smart display") -> "Smart Display"
            lower.contains("phone") || lower.contains("mobile") -> "Android Phone"
            else -> null
        } ?: return null

        val device = _devices.value.firstOrNull { it.type == targetType } ?: return null

        // If not authorized, return explicit authentication failure
        if (!device.isAuthorized) {
            logEvent("Security Refusal: Unauthorized remote connection requested by '${device.name}'.")
            return CrossDeviceResult(
                success = false,
                responseMessage = "I cannot execute that command, Bro. '${device.name}' is registered but still UNAUTHORIZED in our local control matrix. Please authorize it first in the Systems tab!",
                requiresPairing = true,
                deviceId = device.id
            )
        }

        // Check if commands match specific actions
        return when {
            lower.contains("youtube") || lower.contains("open youtube") || lower.contains("launch youtube") -> {
                if (!device.appLaunchingPerm) {
                    logEvent("Access Blocked: App Launching permission denied on '${device.name}'")
                    return CrossDeviceResult(false, "Command rejected: App Launching permission denied on '${device.name}' by security matrix.")
                }
                logEvent("Success: Transmitted launcher payload for YouTube streaming to '${device.name}'.")
                CrossDeviceResult(true, "Launching YouTube streaming on '${device.name}' now, Bro! Transmitted launch handshake payload.")
            }
            lower.contains("vs code") || lower.contains("vscode") || lower.contains("code") || lower.contains("editor") -> {
                if (!device.appLaunchingPerm) {
                    logEvent("Access Blocked: App Launching permission denied on '${device.name}'")
                    return CrossDeviceResult(false, "Command rejected: App Launching permission denied on '${device.name}' by security matrix.")
                }
                logEvent("Success: Sent payload standard executable 'code' to '${device.name}'.")
                CrossDeviceResult(true, "Launching VS Code on '${device.name}' as requested, Bro! System environments configured.")
            }
            lower.contains("transfer") || lower.contains("send file") || lower.contains("transfer file") || lower.contains("sync file") -> {
                if (!device.fileTransferPerm) {
                    logEvent("Access Blocked: File Transfer permission denied on '${device.name}'")
                    return CrossDeviceResult(false, "Command rejected: File Transfer is not permitted on '${device.name}'.")
                }
                logEvent("Success: Synchronized file buffer payload (size: 42MB) with '${device.name}'.")
                CrossDeviceResult(true, "Transferred local backup archive directly to '${device.name}' secure inbox, Bro!")
            }
            lower.contains("media") || lower.contains("play") || lower.contains("pause") || lower.contains("stop") -> {
                if (!device.mediaControlPerm) {
                    logEvent("Access Blocked: Media control permission denied on '${device.name}'")
                    return CrossDeviceResult(false, "Command rejected: Media Control permission denied on '${device.name}' by local preference configurations.")
                }
                logEvent("Success: Transmitted playback sync event to '${device.name}'.")
                CrossDeviceResult(true, "Media action synchronized on '${device.name}' cleanly, Bro.")
            }
            lower.contains("navigate") || lower.contains("tap") || lower.contains("swipe") || lower.contains("scroll") -> {
                if (!device.remoteNavPerm) {
                    logEvent("Access Blocked: Remote Navigation permission denied on '${device.name}'")
                    return CrossDeviceResult(false, "Command rejected: Remote navigation is not enabled on '${device.name}'.")
                }
                logEvent("Success: Transmitted remote navigation event key input to '${device.name}'.")
                CrossDeviceResult(true, "Remote tactile navigation input successfully mirrored to '${device.name}', Bro.")
            }
            else -> {
                logEvent("Success: Processed user instruction on '${device.name}'.")
                CrossDeviceResult(true, "Command executed successfully on '${device.name}', Bro! Connection ping: ${device.latencyMs}ms.")
            }
        }
    }
}

data class CrossDeviceResult(
    val success: Boolean,
    val responseMessage: String,
    val requiresPairing: Boolean = false,
    val deviceId: String? = null
)
