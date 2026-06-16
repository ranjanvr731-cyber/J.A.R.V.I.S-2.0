package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _notifications = mutableListOf<String>()
        val recentNotifications: List<String> get() = synchronized(_notifications) { _notifications.toList() }

        fun clearNotifications() = synchronized(_notifications) {
            _notifications.clear()
        }
        
        fun addNotificationText(text: String) = synchronized(_notifications) {
            _notifications.add(0, text)
            if (_notifications.size > 15) {
                _notifications.removeAt(_notifications.size - 1)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbnNonNull = sbn ?: return
        val packageName = sbnNonNull.packageName ?: "Unknown Product"
        val extras = sbnNonNull.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        if (title.isNotEmpty() || text.isNotEmpty()) {
            val formatted = "Source: $packageName | Sender/Title: $title | Description: $text"
            Log.d("JARVIS_NOTIF", formatted)
            addNotificationText(formatted)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional tracking
    }
}
