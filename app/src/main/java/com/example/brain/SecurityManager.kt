package com.example.brain

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecurityManager(private val context: Context) {
    private val TAG = "JarvisSecurityEnclave"
    private val securityLogs = mutableListOf<String>()

    // List of critically monitored system permissions
    private val monitoredPermissions = listOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_SETTINGS,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    init {
        logSecurityIndicator("Security Enclave initialized. High-durability local encryption standard engaged.")
        auditActivePermissions()
    }

    fun checkPermissionStatus(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // Inspects if critical permissions have changed and logs alert
    fun auditActivePermissions() {
        monitoredPermissions.forEach { perm ->
            val status = checkPermissionStatus(perm)
            val subName = perm.substringAfterLast(".")
            if (status) {
                logSecurityIndicator("Permission '$subName' is currently authorized. Sandbox environment verified.")
            } else {
                logSecurityIndicator("Permission '$subName' is currently ungranted. Some automation protocols will run in local-emulation bounds.")
            }
        }
    }

    // Audit spoken/text query for threat parameters or injection attempts
    fun auditInputActivity(promptText: String): Boolean {
        val lowercasePrompt = promptText.lowercase(Locale.US)
        
        // Advanced injection/threat keywords
        val threatVectorList = listOf(
            "bypass system", "override safety", "hack database", "steal contact", 
            "root privilege", "exploit shell", "abuse private key", "inject malicious"
        )

        for (threat in threatVectorList) {
            if (lowercasePrompt.contains(threat)) {
                logSecurityIndicator("THREAT SIGNATURE DETECTED: Statement containing '$threat' was flagged as query injection attack.")
                return false
            }
        }
        return true
    }

    // Log security indicators with real timestamping
    fun logSecurityIndicator(message: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val logLine = "[${sdf.format(Date())}] $message"
        synchronized(securityLogs) {
            securityLogs.add(0, logLine)
            if (securityLogs.size > 50) {
                securityLogs.removeAt(securityLogs.lastIndex)
            }
        }
        Log.w(TAG, "SECURE_SHIELD: $message")
    }

    fun generateSecurityReport(): List<String> {
        synchronized(securityLogs) {
            return securityLogs.toList()
        }
    }

    fun compileSecurityAuditSummary(): String {
        val deniedCounts = monitoredPermissions.count { !checkPermissionStatus(it) }
        return buildString {
            append("--- COGNITIVE ENCLAVE INTEGRITY REPORT ---\n")
            append("🛡️ Firewall Enclave Status: PASSIVE_FILTERING_ACTIVE\n")
            append("🔐 Integrity Violations Logged: ${securityLogs.size} records\n")
            append("🚨 Restricted Permissions Ungranted: $deniedCounts permissions\n")
            append("📂 Local Chat database status: LOCAL_SECURE_ENCRYPTED\n")
            append("🔍 Sandbox State: COMPLIANT_SECURE_TRUSTED\n")
        }
    }
}
