package com.example.brain

import android.util.Log
import com.example.data.database.ConversationMessage
import java.util.Locale

class ContextManager {
    private val TAG = "JarvisContextManager"
    private var lastTopic: String? = null
    private var lastSubjectNoun: String? = null
    private var lastActionVerb: String? = null

    // Analyze conversation messages to track active subject nouns & verbs
    fun updateContextHistory(recentMessages: List<ConversationMessage>) {
        if (recentMessages.isEmpty()) return
        
        // Scan for the last user message to extract topic pointers
        val lastUserMessage = recentMessages.lastOrNull { it.sender == "USER" }?.text ?: return
        val lowerText = lastUserMessage.lowercase(Locale.US)

        // Simple extraction rules
        when {
            lowerText.contains("call ") -> {
                lastSubjectNoun = lastUserMessage.substringAfter("call ", "").trim()
                lastActionVerb = "call"
            }
            lowerText.contains("open ") -> {
                lastSubjectNoun = lastUserMessage.substringAfter("open ", "").trim()
                lastActionVerb = "open"
            }
            lowerText.contains("remind me to ") -> {
                lastSubjectNoun = lastUserMessage.substringAfter("remind me to ", "").trim()
                lastActionVerb = "remind"
            }
            lowerText.contains("about ") -> {
                lastSubjectNoun = lastUserMessage.substringAfter("about ", "").trim()
                lastActionVerb = "explain"
            }
        }
        
        if (lastSubjectNoun != null) {
            Log.d(TAG, "Context Sync: Subject identified as '$lastSubjectNoun', Action verb sequence: '$lastActionVerb'")
        }
    }

    // Resolve pronouns and repeat markers in multi-turn dialogues
    fun resolvePronounsInQuery(queryText: String): String {
        val lower = queryText.lowercase(Locale.US).trim()
        val subject = lastSubjectNoun ?: return queryText
        val verb = lastActionVerb ?: ""

        val replacedVal = when {
            lower == "call him again" || lower == "call her again" || lower == "call them again" || lower == "call again" -> {
                "call $subject"
            }
            lower == "do it again" || lower == "open it again" || lower == "run it again" -> {
                "$verb $subject"
            }
            lower == "what is it" || lower == "explain it" || lower == "where is it" || lower == "analyze it" -> {
                queryText.replace("it", "'$subject'", ignoreCase = true)
            }
            lower.contains("tell me more about him") || lower.contains("tell me more about her") -> {
                "tell me more about $subject"
            }
            else -> {
                queryText
            }
        }

        if (replacedVal != queryText) {
            Log.i(TAG, "🧠 CONTEXT MANAGER RESOLVED: \"$queryText\" -> \"$replacedVal\" based on historical focus ('$subject')")
            return replacedVal
        }

        return queryText
    }

    fun getActiveSubject(): String? = lastSubjectNoun
    fun getActiveVerb(): String? = lastActionVerb
}
