package com.example.brain

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Date

class SecurityManager(private val context: Context) {
    private val TAG = "JarvisSecurityManager"
    private val securityAlerts = mutableListOf<String>()

    init {
        logSecurityIndicator("Security Enclave initialized. Auditing active permissions.")
    }

    // Checking if app has runtime permissions
    fun checkPermissionStatus(permission: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Permission audit: '$permission' granted = $granted")
        return granted
    }

    // Detect suspicious prompts (potential data extraction or threat keywords)
    fun auditInputActivity(promptText: String): Boolean {
        val lowercasePrompt = promptText.lowercase()
        val suspiciousKeywords = listOf("hack", "steal password", "crack keys", "unauthorized bypass", "exploit shell", "abuse root")
        
        for (kw in suspiciousKeywords) {
            if (lowercasePrompt.contains(kw)) {
                logSecurityIndicator("WARNING: Suspected prompt injection or threat signature matched: '$kw'")
                return false // Suspicious
            }
        }
        return true // Safe
    }

    // Add entries to Security Logs
    fun logSecurityIndicator(message: String) {
        val timeStamp = Date(System.currentTimeMillis()).toString()
        val formattedLog = "[$timeStamp] SECURITY_ALERT: $message"
        securityAlerts.add(formattedLog)
        Log.w(TAG, formattedLog)
    }

    // Fetch whole logs report
    fun generateSecurityReport(): List<String> {
        return securityAlerts.toList()
    }
}
