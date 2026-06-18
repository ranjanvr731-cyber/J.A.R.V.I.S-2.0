package com.example.brain

import android.util.Log
import com.example.data.database.UserMemory
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OfflineIntent(val displayName: String) {
    CONVERSATION("Conversation"),
    QUESTION("Question"),
    MATHEMATICS("Mathematics"),
    REASONING("Reasoning"),
    TASK("Task"),
    COMMAND("Command"),
    LEARNING("Learning"),
    MEMORY_RECALL("Memory Recall"),
    AUTOMATION("Automation")
}

class OfflineAIManager {
    private val TAG = "JarvisOfflineAIManager"
    private val responseCache = mutableMapOf<String, String>()

    init {
        // Pre-cache core responses
        responseCache["hello"] = "Hello, Bro! I am J.A.R.V.I.S., running locally on internal hardware. How can I assist you in offline mode?"
        responseCache["hey"] = "Hey there, Bro! I am J.A.R.V.I.S., listening and active locally without internet."
        responseCache["hi"] = "Hi, Bro! J.A.R.V.I.S. operational. Let me know what you need offline."
        responseCache["who are you"] = "I am J.A.R.V.I.S. (Just A Rather Very Intelligent System), running Mark-V offline backup algorithms on your device."
        responseCache["help"] = "Offline capabilities active: Open Calculator, Open Camera, Open Gallery, Open Settings, toggle Flashlight, control Volume, and manage local reminders/notes database."
    }

    // Detect input intent classifying query into 9 cognitive states
    fun classifyIntent(query: String): OfflineIntent {
        val lower = query.lowercase().trim()

        // Treating input as a command ONLY if it clearly contains the actions.
        val commandActions = listOf("open", "launch", "start", "search", "call", "send", "navigate")
        val isCommand = commandActions.any { action ->
            lower.startsWith(action) || lower.contains(" $action ") || lower.contains(" $action")
        }

        if (isCommand) {
            return if (lower.contains("lights") || lower.contains("flashlight") || lower.contains("torch") || lower.contains("dnd") || lower.contains("silent") || lower.contains("volume")) {
                OfflineIntent.AUTOMATION
            } else {
                OfflineIntent.COMMAND
            }
        }

        // Mathematics
        if (lower.contains("squared") || lower.contains("cubed") || lower.contains("square root") || lower.contains("power of") ||
            lower.contains("+") || lower.contains("-") || lower.contains("*") || lower.contains("/") || lower.contains("×") ||
            lower.contains("plus") || lower.contains("minus") || lower.contains("times") || lower.contains("multiplied") || lower.contains("divided")
        ) {
            val hasDigits = lower.any { it.isDigit() }
            if (hasDigits || lower.contains("two") || lower.contains("three") || lower.contains("five") || lower.contains("ten") || lower.contains("half")) {
                return OfflineIntent.MATHEMATICS
            }
        }

        // Learning
        if (lower.startsWith("remember that") || lower.contains("learn that") || lower.contains("my favorite") ||
            lower.contains("write note") || lower.contains("save note") || lower.contains("take note") ||
            (lower.contains("note") && (lower.contains("add") || lower.contains("jot")))
        ) {
            return OfflineIntent.LEARNING
        }

        // Memory Recall
        if (lower.contains("do you remember") || lower.contains("what do you know about") || lower.contains("retrieve memory") ||
            lower.contains("recall") || lower.contains("show notes") || lower.contains("read notes") || lower.contains("what is my favorite") ||
            lower.contains("search memory")
        ) {
            return OfflineIntent.MEMORY_RECALL
        }

        // Task Planning / Reminders
        if (lower.contains("remind") || lower.contains("reminder") || lower.contains("schedule") ||
            lower.contains("task") || lower.contains("todo") || lower.contains("plan my day") ||
            lower.contains("agenda") || lower.contains("alarm")
        ) {
            return OfflineIntent.TASK
        }

        // Reasoning
        if (lower.contains("why") || lower.contains("reason") || lower.contains("explain why") ||
            lower.contains("how does") || lower.contains("logic") || lower.contains("solve") ||
            (lower.contains("if ") && lower.contains(" then"))
        ) {
            return OfflineIntent.REASONING
        }

        // Factual Question
        if (lower.startsWith("what") || lower.startsWith("how") || lower.startsWith("who") ||
            lower.startsWith("where") || lower.startsWith("when") || lower.startsWith("is there") ||
            lower.endsWith("?")
        ) {
            return OfflineIntent.QUESTION
        }

        return OfflineIntent.CONVERSATION
    }

    // Dynamic cache fetch
    private suspend fun getPersistentCachedResponse(query: String, memoryManager: MemoryManager): String? {
        val cleanQuery = query.lowercase().trim()
        val key = "offline_cache_$cleanQuery"
        val memories = memoryManager.allMemories.firstOrNull() ?: emptyList()
        val matchedValue = memories.firstOrNull { it.key == key && it.category == "conversation" }?.value
        return matchedValue
    }

    // Process general queries utilizing intent-aware local engines
    suspend fun processOfflineQuery(
        silentPrompt: String,
        memoryManager: MemoryManager,
        taskAgentSystem: TaskAgentSystem
    ): String {
        val lower = silentPrompt.lowercase().trim()
        val intent = classifyIntent(silentPrompt)
        Log.d(TAG, "Offline Intent Detected: [${intent.displayName}] for query: '$silentPrompt'")

        // Log the detected intent to agent synchronization logs
        // (will be logged in MultiAgentCoordinator or console)

        // 1. Check persistent offline cache first (so cached conversation keeps on working)
        val persistentCache = getPersistentCachedResponse(silentPrompt, memoryManager)
        if (persistentCache != null) {
            return persistentCache
        }

        // 2. Routing based on classified Intent
        return when (intent) {
            OfflineIntent.CONVERSATION -> {
                when {
                    lower.contains("how are you") -> {
                        "I'm doing stellar, Bro! Running at maximum local capacity with zero-latency. How are you feeling today?"
                    }
                    lower.contains("let's talk") || lower.contains("chat with me") -> {
                        "I'd love to chat, Bro! Even without the internet, my cognitive processors are active. What shall we talk about? Physics, life hacks, design, or local workflows?"
                    }
                    lower.contains("joke") -> {
                        val jokes = listOf(
                            "Why did the AI go to therapy? Because it had too many unresolved dependencies, Bro! Fortunately, my local nodes are clean.",
                            "Why did the database administrator leave the restaurant? Because there were too many table joins, Bro!",
                            "How many programmers does it take to change a light bulb? None, that is a hardware problem!",
                            "What do you call an offshore software developer who lives in an ancient castle? A code-knight, Bro!"
                        )
                        jokes.random()
                    }
                    responseCache.containsKey(lower) -> {
                        responseCache[lower]!!
                    }
                    else -> {
                        // Check fuzzy core responses
                        val bestMatch = responseCache.filter { lower.contains(it.key) }.values.firstOrNull()
                        bestMatch ?: "Hey! I am listening, Bro. Let's chat offline. I can help store notes, do mathematics, recall memories, or open applications, all completely offline!"
                    }
                }
            }

            OfflineIntent.MATHEMATICS -> {
                evaluateMathExpression(silentPrompt) ?: "According to my arithmetic matrix, I received a syntax mismatch. But let me try to help you solve it if you write it down clearly, Bro."
            }

            OfflineIntent.LEARNING -> {
                handleOfflineLearning(silentPrompt, memoryManager)
            }

            OfflineIntent.MEMORY_RECALL -> {
                handleOfflineRecall(silentPrompt, memoryManager)
            }

            OfflineIntent.TASK -> {
                if (lower.contains("remind") || lower.contains("todo") || lower.contains("task") || lower.contains("schedule")) {
                    try {
                        taskAgentSystem.parseAndRegisterTask(silentPrompt)
                    } catch (e: Exception) {
                        "Failed to update task register locally: ${e.localizedMessage}"
                    }
                } else {
                    "Your schedule registry is fully accessible offline, Bro. Type 'show tasks' or manage your daily workflows cleanly!"
                }
            }

            OfflineIntent.COMMAND -> {
                // Command processing
                handleAppLaunchesAndCommands(silentPrompt)
            }

            OfflineIntent.AUTOMATION -> {
                handleDeviceAutomation(silentPrompt)
            }

            OfflineIntent.REASONING -> {
                // Logic/reasoning processing
                "Thinking logically: Based on my stored local knowledge caches, you are working as the primary controller of this terminal. We are functioning inside an isolated offline sandbox, which provides optimum privacy. Ready to assist with logic, Bro!"
            }

            OfflineIntent.QUESTION -> {
                // Check if we can answer from local knowledge base system
                val response = "I don't have that information stored locally. Connect to the internet if you'd like me to search for it."
                response
            }
        }
    }

    // Detailed Math Solver with Explanation capabilities
    private fun evaluateMathExpression(query: String): String? {
        val lower = query.lowercase().trim()

        // 1. Squared
        val squaredRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:squared|\^2)""")
        val squaredMatch = squaredRegex.find(lower)
        if (squaredMatch != null) {
            val num = squaredMatch.groupValues[1].toDoubleOrNull()
            if (num != null) {
                val res = num * num
                val resStr = if (res % 1.0 == 0.0) res.toInt().toString() else String.format(Locale.US, "%.2f", res)
                return "$num squared equals $resStr."
            }
        }

        // 2. Cubed
        val cubedRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:cubed|\^3)""")
        val cubedMatch = cubedRegex.find(lower)
        if (cubedMatch != null) {
            val num = cubedMatch.groupValues[1].toDoubleOrNull()
            if (num != null) {
                val res = num * num * num
                val resStr = if (res % 1.0 == 0.0) res.toInt().toString() else String.format(Locale.US, "%.2f", res)
                return "$num cubed equals $resStr."
            }
        }

        // 3. General operations (add, subtract, multiply, divide)
        val arithmeticRegex = Regex("""(\d+(?:\.\d+)?)\s*([\+\-\*\/x×]|plus|minus|times|multiplied by|divided by)\s*(\d+(?:\.\d+)?)""")
        val matchResult = arithmeticRegex.find(lower)
        if (matchResult != null) {
            val num1 = matchResult.groupValues[1].toDoubleOrNull()
            val opStr = matchResult.groupValues[2]
            val num2 = matchResult.groupValues[3].toDoubleOrNull()

            if (num1 != null && num2 != null) {
                val op = when {
                    opStr == "+" || opStr == "plus" -> "+"
                    opStr == "-" || opStr == "minus" -> "-"
                    opStr == "*" || opStr == "x" || opStr == "×" || opStr == "times" || opStr == "multiplied by" -> "*"
                    opStr == "/" || opStr == "divided by" -> "/"
                    else -> null
                }

                if (op != null) {
                    val result = when (op) {
                        "+" -> num1 + num2
                        "-" -> num1 - num2
                        "*" -> num1 * num2
                        "/" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                        else -> Double.NaN
                    }

                    if (!result.isNaN()) {
                        val val1 = if (num1 % 1.0 == 0.0) num1.toInt().toString() else num1.toString()
                        val val2 = if (num2 % 1.0 == 0.0) num2.toInt().toString() else num2.toString()
                        val valRes = if (result % 1.0 == 0.0) result.toInt().toString() else String.format(Locale.US, "%.2f", result)

                        val detailExplanation = if (op == "*" && num1 >= 10 && num2 >= 10) {
                            val breakdown1 = num1.toInt()
                            val breakdown2 = num2.toInt()
                            val step1 = breakdown1 * 10
                            val step2 = breakdown1 * (breakdown2 - 10)
                            "\n\nLet me break this down: \n1 - Multiply $breakdown1 by 10 which equals $step1. \n2 - Multiply $breakdown1 by ${breakdown2 - 10} which equals $step2. \n3 - Sum the components: $step1 + $step2 = $valRes."
                        } else {
                            ""
                        }

                        return "According to my arithmetic matrix, the calculation of $val1 ${opStr.replace("x", "×")} $val2 leads to $valRes.$detailExplanation"
                    }
                }
            }
        }

        return null
    }

    // Handles user preference and note learning offline
    private suspend fun handleOfflineLearning(query: String, memoryManager: MemoryManager): String {
        val lower = query.lowercase().trim()

        // 1. Favorites mapping
        val favRegex = Regex("""my favorite\s+([a-zA-Z0-9_\s]+)\s+is\s+([a-zA-Z0-9_\s]+)""")
        val favMatch = favRegex.find(lower)
        if (favMatch != null) {
            val key = "favorite_" + favMatch.groupValues[1].trim().replace(" ", "_")
            val value = favMatch.groupValues[2].trim()
            memoryManager.learnMemory(key, value, "preference", importance = 4)
            return "Saved that, Bro! Configured '${favMatch.groupValues[1].trim()}' to '$value' in our local database preferences."
        }

        // 2. Remember that [key] is [value]
        val remRegex = Regex("""remember that\s+([a-zA-Z0-9_\s]+)\s+is\s+([a-zA-Z0-9_\s]+)""")
        val remMatch = remRegex.find(lower)
        if (remMatch != null) {
            val key = remMatch.groupValues[1].trim()
            val value = remMatch.groupValues[2].trim()
            memoryManager.learnMemory(key, value, "personal", importance = 4)
            return "Got it, Bro! Memory block logged: '$key' is registered as '$value' securely."
        }

        // 3. Notes logging (take note, save note)
        val notePrefixes = listOf("write note about", "take note about", "take note", "save note about", "save note", "add note about", "add note", "jot down")
        var noteText = ""
        for (prefix in notePrefixes) {
            if (lower.startsWith(prefix)) {
                noteText = query.substring(prefix.length).trim()
                if (noteText.startsWith(":") || noteText.startsWith("that")) {
                    noteText = noteText.substring(1).trim()
                }
                break
            }
        }

        if (noteText.isNotBlank()) {
            val timestamp = System.currentTimeMillis()
            memoryManager.learnMemory("note_$timestamp", noteText, "personal", importance = 3)
            return "Note jotted locally, Bro! Stored: \"$noteText\"."
        }

        return "I have committed that speech slice to my local preference matrices, Bro."
    }

    // Handles memory and notes recall
    private suspend fun handleOfflineRecall(query: String, memoryManager: MemoryManager): String {
        val lower = query.lowercase().trim()

        // Retreive / show notes
        if (lower.contains("notes") && (lower.contains("show") || lower.contains("list") || lower.contains("read") || lower.contains("display"))) {
            val memories = memoryManager.allMemories.firstOrNull() ?: emptyList()
            val notes = memories.filter { it.key.startsWith("note_") || it.category == "personal" }
            if (notes.isEmpty()) {
                return "You do not have any notes cached offline on this system, Bro."
            }
            return buildString {
                append("Here are your locally recorded notes:\n")
                notes.forEachIndexed { index, userMemory ->
                    append("${index + 1}. \"${userMemory.value}\"\n")
                }
            }
        }

        // Search key or value
        val searchKey = query.replace("what is", "").replace("do you know", "").replace("remember about", "").replace("my favorite", "").replace("?", "").trim()
        val memories = memoryManager.searchMemories(searchKey).firstOrNull() ?: emptyList()
        if (memories.isNotEmpty()) {
            val best = memories.first()
            val label = best.key.replace("favorite_", "").replace("_", " ")
            return "According to your local profiles, '$label' is mapped to: '${best.value}'."
        }

        return "I don't have that information stored locally. Connect to the internet if you'd like me to search for it."
    }

    // App launch routing
    private fun handleAppLaunchesAndCommands(query: String): String {
        val lower = query.lowercase().trim()
        val suffix = query.replace("open", "").replace("launch", "").replace("start", "").trim()

        return when {
            lower.contains("calculator") || lower.contains("calc") -> {
                "Launching local calculator app now, Bro. [PROTOCOL:OPEN_APP:calculator]"
            }
            lower.contains("camera") -> {
                "Opening the on-device camera, Bro. [PROTOCOL:OPEN_CAMERA]"
            }
            lower.contains("gallery") || lower.contains("photos") -> {
                "Activating on-device image view directory. [PROTOCOL:OPEN_GALLERY]"
            }
            lower.contains("contacts") || lower.contains("people") -> {
                "Sourcing local contacts files. [PROTOCOL:OPEN_CONTACTS]"
            }
            lower.contains("settings") -> {
                "Loading system settings matrix. [PROTOCOL:OPEN_SETTINGS]"
            }
            lower.contains("youtube") -> {
                "Opening YouTube now, Bro! [PROTOCOL:OPEN_YOUTUBE]"
            }
            lower.contains("search") -> {
                "Opening the web search browser. [PROTOCOL:LAUNCH_GOOGLE_SEARCH]"
            }
            suffix.isNotBlank() -> {
                "Handshaking with system launch manager for application: '$suffix'. [PROTOCOL:OPEN_APP:$suffix]"
            }
            else -> {
                "Aproved instruction parsed off-grid, Bro."
            }
        }
    }

    // Automation controls
    private fun handleDeviceAutomation(query: String): String {
        val lower = query.lowercase().trim()
        return when {
            lower.contains("flashlight") || lower.contains("torch") -> {
                if (lower.contains("off") || lower.contains("stop") || lower.contains("disable")) {
                    "Disengaging flashlight beams, Bro. [PROTOCOL:FLASHLIGHT_OFF]"
                } else {
                    "Engaging camera spotlight arrays. [PROTOCOL:FLASHLIGHT_ON]"
                }
            }
            lower.contains("silent") || lower.contains("dnd") || lower.contains("disturb") -> {
                if (lower.contains("off") || lower.contains("disable")) {
                    "Disengaging silent mode, audio alerts returned. [PROTOCOL:DND_OFF]"
                } else {
                    "Stealth protocol fully engaged! [PROTOCOL:DND_ON]"
                }
            }
            lower.contains("volume") -> {
                if (lower.contains("up") || lower.contains("increase") || lower.contains("raise")) {
                    "Increasing device audio output level. [PROTOCOL:VOLUME_UP]"
                } else {
                    "Suppressing audio output, sir. [PROTOCOL:VOLUME_DOWN]"
                }
            }
            else -> {
                "Executing local device protocol, Bro."
            }
        }
    }

    // Cache responses when online so they can be shown when offline
    suspend fun cacheOfflineResponse(query: String, response: String, memoryManager: MemoryManager) {
        val cleanQuery = query.lowercase().trim()
        val key = "offline_cache_$cleanQuery"
        // Avoid caching excessively long text logs
        if (response.length < 300 && !response.contains("ERR") && !response.contains("unable to process")) {
            memoryManager.learnMemory(key, response, "conversation", importance = 3)
            Log.d(TAG, "Cached search reply to local SQL memory: '$cleanQuery'")
        }
    }

    fun cacheReponse(query: String, response: String) {
        val cleanQuery = query.lowercase().trim()
        responseCache[cleanQuery] = response
    }

    // Backwards compatibility legacy helper
    fun processOfflineQuery(silentPrompt: String): String {
        val lower = silentPrompt.lowercase().trim()
        return when {
            lower.contains("flashlight") || lower.contains("torch") -> {
                if (lower.contains("off") || lower.contains("stop") || lower.contains("disable")) {
                    "Disengaging flashlight spotlights. [PROTOCOL:FLASHLIGHT_OFF]"
                } else {
                    "Engaging flashlight beams! [PROTOCOL:FLASHLIGHT_ON]"
                }
            }
            else -> {
                "I don't have that information stored locally. Connect to the internet if you'd like me to search for it."
            }
        }
    }
}
