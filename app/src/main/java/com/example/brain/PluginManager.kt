package com.example.brain

import android.util.Log

class PluginManager {
    private val TAG = "JarvisPluginManager"
    private val activePlugins = mutableListOf<PluginSkill>()

    data class PluginSkill(
        val name: String,
        val description: String,
        val keywordTriggers: List<String>,
        var isEnabled: Boolean = true
    )

    init {
        // Pre-supply default plugin tools
        activePlugins.add(PluginSkill("Calculator Logic", "Handles solving complex equations", listOf("calculate", "multiply", "divide", "add", "subtract")))
        activePlugins.add(PluginSkill("Movie Trivia", "Tells witticisms about Marvel comics", listOf("tony", "iron man", "stark", "pepper")))
        activePlugins.add(PluginSkill("Developer Diagnostics", "Inspects system health logs in real time", listOf("diagnose", "health reports", "status report", "self check")))
    }

    // Install dynamic skills
    fun installPlugin(name: String, desc: String, keywords: List<String>) {
        val skill = PluginSkill(name, desc, keywords.map { it.lowercase().trim() })
        activePlugins.add(skill)
        Log.i(TAG, "Dynamic Skill Plugin installed successfully: '$name'")
    }

    // Intercept with plugin keywords
    fun processPluginKeyword(input: String): String? {
        val lower = input.lowercase()
        val matchPlugin = activePlugins.firstOrNull { it.isEnabled && it.keywordTriggers.any { kw -> lower.contains(kw) } }
        if (matchPlugin != null) {
            Log.d(TAG, "Delegating prompt focus to active dynamic plugin module: '${matchPlugin.name}'")
            return when (matchPlugin.name) {
                "Movie Trivia" -> "Ah, Mr. Stark! He built me in a cave with a box of scraps! Anything else, Bro?"
                "Calculator Logic" -> "Calculating parameters... Matrix vectors loaded. Please specify numbers to formulate the sum!"
                else -> null
            }
        }
        return null
    }

    fun getInstalledPlugins(): List<PluginSkill> = activePlugins.toList()
}
