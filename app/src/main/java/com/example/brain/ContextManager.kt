package com.example.brain

import android.util.Log
import com.example.data.database.ConversationMessage

class ContextManager {
    private val TAG = "JarvisContextManager"
    private var lastTopic: String? = null
    private var lastSubjectNoun: String? = null

    // Analyze conversation messages to track active subject nouns
    fun updateContextHistory(recentMessages: List<ConversationMessage>) {
        if (recentMessages.isEmpty()) return
        
        // Scan the last few messages for probable subjects
        val lastUserMessage = recentMessages.lastOrNull { it.sender == "USER" }?.text ?: return
        
        // Simple regex of potential primary interest subjects
        val nounsRegex = Regex("(?:about|on|like|call|open|run)\\s+([a-zA-Z0-9 ]+)")
        val match = nounsRegex.find(lastUserMessage.lowercase())
        if (match != null) {
            lastSubjectNoun = match.groupValues[1].trim()
            Log.d(TAG, "Assessed current subject focus noun: '$lastSubjectNoun'")
        }
    }

    // Resolve ambiguous queries referencing pronoun triggers
    fun resolvePronounsInQuery(queryText: String): String {
        val lower = queryText.lowercase()
        val subject = lastSubjectNoun ?: return queryText
        
        return if (lower == "what is it" || lower == "explain it" || lower == "where is it" || lower == "analyze it") {
            val resolved = queryText.replace("it", "'$subject'", ignoreCase = true)
            Log.i(TAG, "Pronoun 'it' resolved contextually for focus topic: '$subject'")
            resolved
        } else {
            queryText
        }
    }

    fun getActiveSubject(): String? = lastSubjectNoun
}
