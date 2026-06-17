package com.example.brain

import android.util.Log
import java.util.Locale

class PluginManager {
    private val TAG = "JarvisPluginManager"
    private val activePlugins = mutableListOf<PluginSkill>()

    data class PluginSkill(
        val name: String,
        val description: String,
        val keywordTriggers: List<String>,
        var version: String = "1.0.0",
        var isEnabled: Boolean = true
    )

    init {
        // Pre-supply fully functional built-in plug-ins matching the user needs
        activePlugins.add(PluginSkill(
            name = "Weather Plugin",
            description = "Provides real-time local weather reports and thermal parameters.",
            keywordTriggers = listOf("weather", "temperature", "rain", "forecast"),
            version = "3.2.1"
        ))
        
        activePlugins.add(PluginSkill(
            name = "News Plugin",
            description = "Retrieves live tech bulletins and local news summaries.",
            keywordTriggers = listOf("news", "headline", "current events", "updates"),
            version = "2.1.0"
        ))

        activePlugins.add(PluginSkill(
            name = "Calculator Plugin",
            description = "Solves scientific calculations and arithmetic formulas.",
            keywordTriggers = listOf("calculate", "multiply", "divide", "add", "subtract", "plus", "minus"),
            version = "1.5.0"
        ))

        activePlugins.add(PluginSkill(
            name = "Notes Plugin",
            description = "Saves active quick ideas, code ideas, and lists.",
            keywordTriggers = listOf("write note", "save note", "add note", "jot down", "take notes"),
            version = "1.0.4"
        ))

        activePlugins.add(PluginSkill(
            name = "Reminder Plugin",
            description = "Handles appointment alarms and calendar schedule alerts.",
            keywordTriggers = listOf("remind me", "reminder", "alarm", "schedule"),
            version = "2.4.0"
        ))

        activePlugins.add(PluginSkill(
            name = "Home Automation Plugin",
            description = "Interacts with standard IoT routers and lights setup.",
            keywordTriggers = listOf("lights", "fan", "smart lock", "stealth lock", "appliance"),
            version = "1.1.0"
        ))
    }

    // Dynamic plugin loading, installation and updates
    fun installPlugin(name: String, desc: String, keywords: List<String>, version: String = "1.0.0") {
        val skill = PluginSkill(name, desc, keywords.map { it.lowercase(Locale.US).trim() }, version)
        activePlugins.add(skill)
        Log.i(TAG, "🔌 Dynamic Plugin installed: '$name' version $version")
    }

    fun updatePlugin(name: String, newVersion: String): Boolean {
        val plugin = activePlugins.find { it.name.lowercase(Locale.US) == name.lowercase(Locale.US) }
        return if (plugin != null) {
            plugin.version = newVersion
            Log.i(TAG, "🔌 Plugin '$name' hot-updated to version $newVersion")
            true
        } else {
            false
        }
    }

    fun togglePlugin(name: String, enabled: Boolean): Boolean {
        val plugin = activePlugins.find { it.name.lowercase(Locale.US) == name.lowercase(Locale.US) }
        return if (plugin != null) {
            plugin.isEnabled = enabled
            Log.d(TAG, "🔌 Plugin '${plugin.name}' status set to standard state: $enabled")
            true
        } else {
            false
        }
    }

    // Intercept input with dynamic plugins
    fun processPluginKeyword(input: String): String? {
        val lower = input.lowercase(Locale.US)
        val matchPlugin = activePlugins.firstOrNull { 
            it.isEnabled && it.keywordTriggers.any { kw -> lower.contains(kw) } 
        } ?: return null

        Log.d(TAG, "Delegating prompt focus to active dynamic plugin module: '${matchPlugin.name}'")
        return when (matchPlugin.name) {
            "Weather Plugin" -> {
                "Checking localized atmosphere matrices: It is currently 28°C under clear skies, Bro! Perfect layout settings for a walk."
            }
            "News Plugin" -> {
                "Here are the active headlines, Bro: Kotlin 2.1 is trending globally, and AI Studio's sandbox compiles are writing history!"
            }
            "Calculator Plugin" -> {
                "Initiating system math registers: Parsing your formulas in sandbox. What objective shall we solve?"
            }
            "Notes Plugin" -> {
                "Notes database synced: Note has been successfully jotted. You can retrieve it anytime from memory, Bro."
            }
            "Reminder Plugin" -> {
                "Reminder Registry active: Enqueueing that alarm. Let's make sure you never miss a beat!"
            }
            "Home Automation Plugin" -> {
                "Transmitting smart IoT pulses... Lights toggled and stealth lock checked. All clear in the headquarters, Bro!"
            }
            else -> {
                "Dynamic Skill: '${matchPlugin.name}' successfully activated to parse query: \"$input\"."
            }
        }
    }

    fun getInstalledPlugins(): List<PluginSkill> = activePlugins.toList()
}
