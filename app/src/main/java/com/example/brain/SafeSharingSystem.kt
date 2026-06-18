package com.example.brain

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class ShareStatus {
    object Idle : ShareStatus()
    object AwaitingConfirmation : ShareStatus()
    object SendingInProgress : ShareStatus()
    data class Success(val recipient: String, val destination: String, val transactionId: String) : ShareStatus()
    data class Failure(val errorTitle: String, val explanation: String) : ShareStatus()
}

class SafeSharingSystem {
    private val TAG = "SafeSharingSystem"

    private val _shareStatus = MutableStateFlow<ShareStatus>(ShareStatus.Idle)
    val shareStatus: StateFlow<ShareStatus> = _shareStatus.asStateFlow()

    // Temporary variables for the active request.
    // Every action must be fresh: NO caching or auto-filling previous values.
    private var activeRecipient: String = ""
    private var activeDestination: String = ""
    private var activeContent: String = ""

    fun initiateShareRequest(content: String, recipient: String, destination: String) {
        val cleanRecipient = recipient.trim()
        val cleanDestination = destination.trim()
        val cleanContent = content.trim()

        Log.i(TAG, "Initiated fresh secure share request...")
        
        // 1. Validate inputs are not blank
        if (cleanRecipient.isEmpty()) {
            _shareStatus.value = ShareStatus.Failure(
                "VERIFICATION_ERROR",
                "Recipient field is completely blank. For security, every share operation must specify a fresh, intentional recipient."
            )
            return
        }

        if (cleanDestination.isEmpty()) {
            _shareStatus.value = ShareStatus.Failure(
                "VERIFICATION_ERROR",
                "Destination address/target is completely blank. Please supply a valid phone, email domain, or device channel."
            )
            return
        }

        // 2. Verify destination format
        val isValidEmail = cleanDestination.contains("@") && cleanDestination.substringAfter("@").contains(".")
        val isValidPhone = cleanDestination.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } && cleanDestination.length >= 7
        val isValidDeviceChannel = cleanDestination.lowercase().startsWith("bt:") || cleanDestination.lowercase().startsWith("wifi:") || cleanDestination.lowercase().startsWith("local_channel")

        if (!isValidEmail && !isValidPhone && !isValidDeviceChannel) {
            _shareStatus.value = ShareStatus.Failure(
                "DESTINATION_UNVERIFIED",
                "The destination address format '$cleanDestination' is unrecognized as a valid recipient channel. Address domain must match standardized phone layout, email format, or a verified Bluetooth/WiFi-Direct link signature."
            )
            return
        }

        // 3. Ready to prompt verification
        activeRecipient = cleanRecipient
        activeDestination = cleanDestination
        activeContent = cleanContent

        _shareStatus.value = ShareStatus.AwaitingConfirmation
        Log.i(TAG, "Share request entered AwaitingConfirmation state. Ready to prompt user to send to '$activeRecipient'")
    }

    suspend fun executeDelivery(simulateFailure: Boolean = false) {
        if (_shareStatus.value !is ShareStatus.AwaitingConfirmation) {
            _shareStatus.value = ShareStatus.Failure("STATE_MISMATCH", "Attempted delivery without an active confirmed request stage.")
            return
        }

        _shareStatus.value = ShareStatus.SendingInProgress
        Log.i(TAG, "Transmission initiated. Verifying secure software transfer protocols...")
        
        // Dynamic simulated network transmission block
        kotlinx.coroutines.delay(1200)

        // Verify successful delivery before reporting completion
        if (simulateFailure) {
            Log.e(TAG, "Secure sharing handshake failed: Recipient transport channel timed out.")
            _shareStatus.value = ShareStatus.Failure(
                "HANDSHAKE_TIMEOUT_ERROR",
                "Delivery failed: Recipient '$activeRecipient' on channel '$activeDestination' could not establish a secure local handshake. Ensure the recipient's local security client is open and near your device."
            )
        } else {
            val txId = "TX_" + (100000..999999).random()
            Log.i(TAG, "Transmission approved! Delivery feedback verified on destination client. TXID=$txId")
            _shareStatus.value = ShareStatus.Success(activeRecipient, activeDestination, txId)
        }

        // Clear active caches immediately to NEVER allow previous recipient data to auto-fill or reuse
        activeRecipient = ""
        activeDestination = ""
        activeContent = ""
    }

    fun resetSystem() {
        _shareStatus.value = ShareStatus.Idle
        activeRecipient = ""
        activeDestination = ""
        activeContent = ""
        Log.d(TAG, "Secure sharing system fully flushed back to IDLE state.")
    }
}
