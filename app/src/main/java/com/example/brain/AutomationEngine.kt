package com.example.brain

import android.util.Log

class AutomationEngine {
    private val TAG = "JarvisAutomationEngine"
    private val activeRoutines = mutableListOf<UserRoutine>()

    data class UserRoutine(
        val routineName: String,
        val triggersText: String,
        val executionsSeq: String, // Semi-colon separated protocols
        var isActive: Boolean = true
    )

    init {
        // Pre-create defaults
        activeRoutines.add(UserRoutine("Flash & Silent", "stealth mode", "FLASHLIGHT_OFF; DND_ON"))
        activeRoutines.add(UserRoutine("Morning Assist", "wake up", "READ_NOTIFICATIONS; VIBRATE_DEVICE"))
    }

    // Register a routine
    fun addRoutine(name: String, trigger: String, actions: String) {
        val routine = UserRoutine(name.trim(), trigger.lowercase().trim(), actions.trim())
        activeRoutines.add(routine)
        Log.i(TAG, "New automation routine scheduled: '$name' triggered when user says '$trigger'")
    }

    // Get matching automation routine
    fun findAutomationAction(input: String): String? {
        val cleaned = input.lowercase().trim()
        val routine = activeRoutines.firstOrNull { it.isActive && (it.triggersText == cleaned || cleaned.contains(it.triggersText)) }
        if (routine != null) {
            Log.d(TAG, "Triggering automated custom routine match: ${routine.routineName} -> execute ${routine.executionsSeq}")
            return routine.executionsSeq
        }
        return null
    }

    fun getRoutines(): List<UserRoutine> = activeRoutines.toList()
}
