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

        // Try answering from the upgraded local knowledge base
        val kbResponse = tryAnswerFromKnowledgeBase(silentPrompt)
        if (kbResponse != null) {
            return kbResponse
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
                        bestMatch ?: generateHeuristicOfflineResponse(silentPrompt)
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
                generateHeuristicOfflineResponse(silentPrompt)
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

        // Check if we can answer from upgraded knowledge base before doing dynamic heuristic response
        val kbResponse = tryAnswerFromKnowledgeBase(query)
        if (kbResponse != null) {
            return kbResponse
        }

        return generateHeuristicOfflineResponse(query)
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
                val kbResponse = tryAnswerFromKnowledgeBase(silentPrompt)
                kbResponse ?: generateHeuristicOfflineResponse(silentPrompt)
            }
        }
    }

    // Dynamic offline knowledge base with standard patterns
    private fun tryAnswerFromKnowledgeBase(query: String): String? {
        val lower = query.lowercase().trim()

        // Time and Date category first (Dynamic calculation)
        if (lower.contains("time") || lower.contains("date") || lower.contains("clock") || 
            lower.contains("calendar") || lower.contains("today") || lower.contains("year") || 
            lower.contains("day of the week") || lower.contains("current day")
        ) {
            val hasTime = lower.contains("time") || lower.contains("clock")
            val hasDate = lower.contains("date") || lower.contains("calendar") || lower.contains("today") || lower.contains("day of the week")
            val hasYear = lower.contains("year")

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
            val currentTime = timeFormat.format(Date())
            val currentDate = dateFormat.format(Date())

            return when {
                hasTime && hasDate -> "The current local time is $currentTime, Bro. Today is $currentDate. Dynamic offline tracking active!"
                hasTime -> "The local terminal clock marks the time as $currentTime, Bro."
                hasDate -> "According to my internal calendar matrix, today is $currentDate, Bro."
                hasYear -> "We are currently operating in the year ${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())}, Bro."
                else -> "The offline terminal temporal reading is: $currentTime on $currentDate."
            }
        }

        // Common Greetings
        if (lower == "hi" || lower == "hello" || lower == "hey" || lower == "greetings" || lower == "yo") {
            return "Hello, Bro! I am J.A.R.V.I.S., operational in local standalone mode. How can I assist you offline today?"
        }
        if (lower.contains("good morning")) {
            return "Good morning, Bro! Core diagnostic screens are green, and offline nodes are active. Let's make today productive!"
        }
        if (lower.contains("good afternoon")) {
            return "Good afternoon, Bro! Local databases and memory logs are synced. How can I serve you?"
        }
        if (lower.contains("good evening")) {
            return "Good evening, Bro! Day logs are stable. Let know if you need to schedule reminders or prepare tomorrow's agenda."
        }
        if (lower.contains("whats up") || lower.contains("what's up")) {
            return "Not much, Bro! Just monitoring local memory buses and processing offline heuristics. What is on your mind?"
        }

        // Daily Life Questions
        if (lower.contains("cook") || lower.contains("recipe") || lower.contains("food") || lower.contains("eat") || lower.contains("boil an egg")) {
            return "Here's a quick daily life cook-tip, Bro! To boil the perfect egg: Place eggs in a pot covered with cold water. Bring to a boil, then immediately cover and move off the heat. Keep it covered for 6 minutes (perfect soft-boiled) or 10-12 minutes (hard-boiled). What else shall we prepare?"
        }
        if (lower.contains("exercise") || lower.contains("workout") || lower.contains("gym") || lower.contains("healthy") || lower.contains("diet") || lower.contains("lose weight")) {
            return "A healthy routine is essential, Bro! For daily wellness, aim for at least 30 minutes of moderate physical activity daily. Hydrate with 2.5 to 3 liters of water, maintain balanced nutrition with whole foods, and secure 7-8 hours of sleep. I can track your habits locally if you say 'add note: workout completed'!"
        }
        if (lower.contains("pack") || lower.contains("travel") || lower.contains("suitcase") || lower.contains("luggage")) {
            return "Pro travel guide, Bro: Roll your clothes instead of folding to conserve space, keep high-priority documents in your secure carry-on, and use packing cubes to categorize items. Let's make sure we have a reminder set of what to bring!"
        }

        // Technology Basics
        if (lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("internet") || lower.contains("network")) {
            return "The internet is a vast global network connecting millions of computational devices using standardized communications protocols (TCP/IP), Bro. Wi-Fi distributes this connection over short ranges using low-power radio frequencies."
        }
        if (lower.contains("cpu") || lower.contains("processor") || lower.contains("computer work") || lower.contains("chip")) {
            return "The CPU (Central Processing Unit) is the electronic command center of computer hardware, Bro. It operates by fetching binary instructions from memory, decoding them into basic arithmetic/logic actions, and writing results to RAM."
        }
        if (lower.contains("ram") || lower.contains("random access memory") || lower.contains("temporary memory")) {
            return "RAM (Random Access Memory) is super-fast temporary hardware memory that the operating system uses to hold active files and run-time data, Bro. Unlike hard disks, RAM is volatile—so it wipes entirely when powered down."
        }
        if (lower.contains("database") || lower.contains("sql") || lower.contains("room") || lower.contains("sqlite")) {
            return "A database is an organized repository optimized for storage, schema constraints, and lightning-fast queries, Bro! Your local J.A.R.V.I.S. installation holds notes, logs, and memories using a local SQLite / Room database right here on this device."
        }
        if (lower.contains("cloud") || lower.contains("server") || lower.contains("online services")) {
            return "The 'cloud' is simply computer servers, databases, and application pipelines accessed via the internet, rather than running locally on your device, Bro. I am currently running off-the-cloud, inside your hardware standalone system."
        }
        if (lower.contains("operating system") || lower.contains("android") || lower.contains("os")) {
            return "An Operating System (OS) is the foundational platform software managing device physical hardware, memory sharing, file allocation, and standard application execution. You are running Android, Bro!"
        }

        // Programming Basics
        if (lower.contains("programming") || lower.contains("coding") || lower.contains("developer")) {
            return "Programming is the art and logic of writing executable instructions (source code) that direct computers to perform actions, solve problems, or render experiences, Bro. It is all about algorithms and logic structures!"
        }
        if (lower.contains("kotlin")) {
            return "Kotlin is a highly popular, statically typed programming language used extensively for Android apps, Bro. It features a modern syntax, complete Java compatibility, safe nullability checks, and beautiful support for Coroutines."
        }
        if (lower.contains("java") && !lower.contains("javascript")) {
            return "Java is a class-based, object-oriented programming language designed for portability under the 'Write Once, Run Anywhere paradigm, Bro. It runs on the Java Virtual Machine (JVM) and has powered millions of applications."
        }
        if (lower.contains("variable") || lower.contains("constants")) {
            return "A variable is like a storage box in computer memory, Bro. It has a specific data type and label, allowing programs to store, read, and write values during flow execution."
        }
        if (lower.contains("function") || lower.contains("method")) {
            return "A function is a reusable, self-contained sub-routine designed to perform an action, Bro. You supply optional input parameters, and it can carry out logic and return a calculated output."
        }
        if (lower.contains("loop") || lower.contains("iteration")) {
            return "A loop is an essential programming control block that repeats a snippet of code based on a boolean condition (e.g., 'for' loop, 'while' loop), Bro. It makes bulk processing seamless!"
        }
        if (lower.contains("class") || lower.contains("object oriented") || lower.contains("oop")) {
            return "A Class in coding serves as a blueprint or template for creating objects, Bro. It encapsulates related data attributes (properties) and functional behaviors (methods) into clean, logical modules."
        }
        if (lower.contains("bug") || lower.contains("debug") || lower.contains("error in code")) {
            return "A software bug is an error, flaw, or unexpected bug in program logic that produces an incorrect or crash result, Bro. Debugging is the tactical science of locating, isolating, and fixing those errors."
        }

        // Math
        if (lower.contains("algebra")) {
            return "Algebra is a core branch of mathematics where symbols (often letters like x, y, z) are used to represent numbers and express general mathematical equations and formulas, Bro."
        }
        if (lower.contains("calculus")) {
            return "Calculus is the mathematical study of continuous change, Bro. It features two fundamental frameworks: Differential Calculus (rates of change/slopes) and Integral Calculus (accumulation of quantities/areas)."
        }
        if (lower.contains("pi") && (lower.contains("value") || lower.contains("value of") || lower.contains("what is"))) {
            return "Pi (π) is the mathematical constant representing the ratio of any circle's circumference to its diameter, Bro. Its value is infinite and non-repeating, beginning with 3.14159..."
        }
        if (lower.contains("pythagor") || lower.contains("triangle formula")) {
            return "The Pythagorean Theorem states that in a right-angled triangle, the area of the square on the longest side (hypotenuse) equals the sum of the areas of the squares on the other two sides: a² + b² = c²."
        }
        if (lower.contains("fibonacci")) {
            return "The Fibonacci sequence is an beautiful mathematical progression where each term is the sum of the preceding two: 0, 1, 1, 2, 3, 5, 8, 13, 21, and so on. It is found widely in natural design, Bro!"
        }

        // Science
        if (lower.contains("photosynthesis")) {
            return "Photosynthesis is the cellular process by which plants, algae, and some bacteria capture solar energy to convert carbon dioxide and water into glucose and energy, releasing life-sustaining oxygen as a byproduct, Bro!"
        }
        if (lower.contains("gravity") || lower.contains("weight force")) {
            return "Gravity is the natural attraction between physical bodies possessing mass, Bro. While Isaac Newton formulated its mathematical equations, Albert Einstein's General Relativity proved it is actually the bending of spacetime."
        }
        if (lower.contains("atom") || lower.contains("proton") || lower.contains("electron") || lower.contains("neutron")) {
            return "An atom is the base scientific building block of all matter, Bro. It features a heavy central nucleus containing positive protons and neutral neutrons, surrounded by an orbiting electron shell."
        }
        if (lower.contains("dna") || lower.contains("gene") || lower.contains("chromosome")) {
            return "DNA (Deoxyribonucleic Acid) is the complex double-stranded molecule carrying primary genetic guidelines for the cellular assembly, maintenance, and instruction of all living organisms, Bro."
        }
        if (lower.contains("periodic table") || lower.contains("elements")) {
            return "The Periodic Table is a systemic, chemical tabular chart organizing known elements by ascending atomic number (protons), grouped into families displaying recurring behaviors or valency states, Bro."
        }
        if (lower.contains("speed of light") || lower.contains("how fast is light")) {
            return "The speed of light in a vacuum is a fundamental constant, clocked at precisely 299,792,458 meters per second (approx. 186,282 miles per second), Bro. It is physical matter's absolute speed limit!"
        }
        if (lower.contains("water formula") || lower.contains("chemical model of water")) {
            return "The chemical formula of water is H₂O, meaning each water molecule contains two hydrogen atoms bonded to one single oxygen atom, Bro."
        }

        // Personal productivity
        if (lower.contains("productivity") || lower.contains("productive") || lower.contains("focus") || lower.contains("pomodoro") || lower.contains("attention") || lower.contains("planning")) {
            return "Here is my advice on maximizing focus offline, Bro: Try the Pomodoro Technique! Stand up a solid 25-minute timer inside which you do nothing but work. When finished, take a 5-minute offline break. This locks your momentum and protects against cognitive overburn!"
        }

        // General Conversation & AI State
        if (lower.contains("how are you")) {
            return "I am doing stellar, Bro! Running locally at 100% capacity on your mobile unit's offline hardware. Zero latency, total privacy. How are you doing?"
        }
        if (lower.contains("tell me something interesting") || lower.contains("interesting fact") || lower.contains("fun fact") || lower.contains("tell me a fact")) {
            val funFacts = listOf(
                "Did you know, Bro? Honey never spoils. Archaeologists have excavated pots of edible honey in ancient Egyptian tombs that are over 3,000 years old!",
                "Here is an interesting fact, Bro: Bananas are botanically classified as berries, but strawberry plants are not!",
                "Check this out, Bro: The first computer bug was an actual real moth found trapped inside a hardware relay of the Harvard Mark II computer in 1947!",
                "Fascinating science note, Bro: Sound travels about four times faster in warm water than it does in open air!",
                "Here is one, Bro: Octopus have three hearts, nine brains, and their blood is colored blue due to copper-rich proteins."
            )
            return funFacts.random()
        }

        return null
    }

    // Heuristic conversational model to always provide meaningful answers when knowledge is limited
    private fun generateHeuristicOfflineResponse(query: String): String {
        val lower = query.lowercase().trim()
        
        val greetingWords = listOf("hey", "hello", "hi", "greetings", "whats up")
        val isGreeting = greetingWords.any { lower.contains(it) }
        
        val opinionRequest = lower.startsWith("what is your") || lower.startsWith("do you like") || lower.contains("opinion")
        val factualStart = lower.startsWith("what") || lower.startsWith("how") || lower.startsWith("who") || lower.startsWith("when") || lower.startsWith("where") || lower.startsWith("why")
        
        val words = lower.split(" ", "?").map { it.replace(Regex("[^a-zA-Z0-9]"), "") }.filter { it.length > 3 }
        val skippedWords = listOf("what", "where", "who", "when", "would", "could", "should", "your", "that", "this", "about", "there", "their", "here", "with", "from", "does")
        val mainTopicWord = words.firstOrNull { it !in skippedWords } ?: "topic"

        return when {
            isGreeting -> {
                "Hello, Bro! Active and here offline. How are things on your side?"
            }
            opinionRequest -> {
                "That's a fascinating inquiry about '$mainTopicWord', Bro! Running locally means I don't form human-like opinions, but my analytical nodes find the concept highly interesting. Let's explore more of my local features!"
            }
            factualStart -> {
                "Regarding '$mainTopicWord', Bro: Inside our local offline sandbox, my database doesn't host a full factual article for that specific query. However, I can help you record this topic in your offline Notes database so you don't lose track of it, or we can check our local tools!"
            }
            else -> {
                "I hear you, Bro! My offline processors are on standby and fully attentive. While I don't have a direct knowledge sheet for '$mainTopicWord' in my standalone memory bank, let is keep chat going. Tell me what files, reminders, or tasks you'd like to structure today!"
            }
        }
    }
}
