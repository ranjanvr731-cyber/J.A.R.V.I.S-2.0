package com.example.brain

import android.util.Log

class NotificationManager {
    private val TAG = "JarvisNotificationManager"
    private val notificationList = mutableListOf<SystemNotification>()

    data class SystemNotification(
        val packageId: String,
        val senderTitle: String,
        val textBody: String,
        val timestamp: Long = System.currentTimeMillis(),
        val priority: String = "Normal" // "Urgent" or "Normal"
    )

    // Receive a notification payload
    fun registerNotification(packageId: String, sender: String, contentText: String) {
        val cleanPkg = packageId.substringAfterLast(".")
        val isPriority = contentText.contains("urgent", ignoreCase = true) || 
                         contentText.contains("emergency", ignoreCase = true) ||
                         sender.contains("boss", ignoreCase = true) || 
                         sender.contains("amma", ignoreCase = true)

        val notification = SystemNotification(
            packageId = cleanPkg,
            senderTitle = sender,
            textBody = contentText,
            priority = if (isPriority) "Urgent" else "Normal"
        )
        notificationList.add(0, notification)
        Log.d(TAG, "Notification added: $sender from $cleanPkg (Priority=${notification.priority})")
    }

    // List notifications
    fun getNotifications(): List<SystemNotification> = notificationList.toList()

    // Summarize missed alerts or notifications
    fun summarizeMissedNotifications(): String {
        if (notificationList.isEmpty()) {
            return "No missed notifications received, Bro. You are currently fully up to date."
        }
        val urgentOnes = notificationList.filter { it.priority == "Urgent" }
        return buildString {
            append("Bro, you have ${notificationList.size} missed notifications! ")
            if (urgentOnes.isNotEmpty()) {
                append("Specially, ${urgentOnes.size} are marked high priority: ")
                urgentOnes.forEach {
                    append("Message from '${it.senderTitle}' saying '${it.textBody}'. ")
                }
            } else {
                append("The last notification was from '${notificationList.first().senderTitle}' on app '${notificationList.first().packageId}': '${notificationList.first().textBody}'. ")
            }
        }
    }

    // Clear notification storage
    fun markAllAsRead() {
        notificationList.clear()
        Log.i(TAG, "All internal notifications marked as read.")
    }
}
