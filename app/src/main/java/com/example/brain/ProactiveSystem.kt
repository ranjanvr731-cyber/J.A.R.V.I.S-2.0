package com.example.brain

import android.util.Log
import java.util.Calendar

class ProactiveSystem {
    private val TAG = "JarvisProactiveSystem"

    data class ProactiveAlert(
        val type: String, // "BATTERY", "MISSED_CALL", "NOTIFICATION"
        val title: String,
        val description: String,
        val promptAction: String,
        var isDismissed: Boolean = false
    )

    private val activeAlerts = mutableListOf<ProactiveAlert>()

    init {
        // Seed default alerts to look active and functional
        activeAlerts.add(ProactiveAlert(
            type = "BATTERY",
            title = "🔋 Low Battery Warning",
            description = "Battery level has dipped to 14%. Power drainage active.",
            promptAction = "DND_ON" // Simulated protocol triggers
        ))
        activeAlerts.add(ProactiveAlert(
            type = "MISSED_CALL",
            title = "📞 Communication Missed",
            description = "You have 3 missed calls from Ravi (Study Companion).",
            promptAction = "CALL_RAVI"
        ))
        activeAlerts.add(ProactiveAlert(
            type = "NOTIFICATION",
            title = "🛎️ Priority Alerts Sync",
            description = "Receive 5 notifications from Android Study group and Git compiler.",
            promptAction = "READ_NOTIFICATIONS"
        ))
    }

    fun getAlerts(): List<ProactiveAlert> = activeAlerts.filter { !it.isDismissed }

    fun dismissAlert(title: String) {
        activeAlerts.find { it.title == title }?.isDismissed = true
        Log.d(TAG, "Dismissed proactive alert: $title")
    }

    fun compileMorningBriefing(userName: String, taskCount: Int): String {
        return """
            🌅 GOOD MORNING, ${userName.uppercase()}!
            
            🌤️ [WEATHER] Local ambient temperature is 28°C under clear skies.
            🗓️ [CALENDAR] All diagnostic and cognitive modules are 100% online.
            📝 [TODAY'S PLAN] You have $taskCount pending tasks enqueued in your active registers.
            📰 [BULLETINS] Kotlin v2.1 codebases are compiling successfully globally.
            
            Let's have a highly productive day, Bro! Say 'Hey Jarvis, start coding' to begin!
        """.trimIndent()
    }

    fun compileNightBriefing(userName: String, completedCount: Int, pendingCount: Int): String {
        return """
            🌌 GOOD EVENING BRIEFING, ${userName.uppercase()}:
            
            ✅ [OBJECTIVES COMPLETED] Excellent work, Bro! You successfully finished $completedCount tasks today.
            ⏳ [PENDING SLOTS] We have $pendingCount tasks scheduled to carry over into tomorrow's routine.
            ⚙️ [STABILITY STATUS] Live memory index verified intact, sandbox insulated securely.
            
            Have a peaceful rest, Bro. I am on continuous background sentinel duty!
        """.trimIndent()
    }
}
