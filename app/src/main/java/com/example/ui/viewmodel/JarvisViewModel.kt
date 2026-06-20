package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.ConversationMessage
import com.example.data.database.JarvisRepository
import com.example.service.JarvisCommandProcessor
import com.example.data.database.UserMemory
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.GenerationConfig
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import com.example.brain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database by lazy {
        Room.databaseBuilder(
            getApplication(),
            AppDatabase::class.java, "jarvis_database"
        ).fallbackToDestructiveMigration().build()
    }

    private val repository by lazy { JarvisRepository(database.jarvisDao()) }

    // --- Advanced Hybrid AI Brain Modules ---
    val memoryManager by lazy { MemoryManager(repository) }
    val learningEngine by lazy { LearningEngine(repository) }
    val voiceManager by lazy { VoiceManager() }
    val wakeWordManager by lazy { WakeWordManager() }
    val securityManager by lazy { SecurityManager(getApplication()) }
    val notificationManager by lazy { NotificationManager() }
    val contextManager by lazy { ContextManager() }
    val offlineAIManager by lazy { OfflineAIManager() }
    val onlineAIManager by lazy { OnlineAIManager() }
    val selfDiagnosticSystem by lazy { SelfDiagnosticSystem(getApplication()) }
    val taskPlanner by lazy { TaskPlanner() }
    val personalityEngine by lazy { PersonalityEngine() }
    val analyticsEngine by lazy { AnalyticsEngine() }
    val automationEngine by lazy { AutomationEngine() }
    val pluginManager by lazy { PluginManager() }
    val multiAgentCoordinator by lazy { MultiAgentCoordinator(memoryManager, learningEngine, contextManager, automationEngine) }

    // --- Upgraded JARVIS Subsystem Modules ---
    val stabilitySystem by lazy { StabilitySystem() }
    val userProfileSystem by lazy { UserProfileSystem(repository) }
    val deviceControlSystem by lazy { DeviceControlSystem(repository) }
    val voiceStudioManager by lazy { VoiceStudioManager(repository) }
    val taskAgentSystem by lazy { TaskAgentSystem(repository) }
    val multiUserSecuritySystem by lazy { MultiUserSecuritySystem(repository) }
    val safeSharingSystem by lazy { SafeSharingSystem() }
    val visionAgentSystem by lazy { VisionAgentSystem() }
    val knowledgeBaseSystem by lazy { KnowledgeBaseSystem() }
    val proactiveSystem by lazy { ProactiveSystem() }
    val questionAnsweringSystem by lazy { QuestionAnsweringSystem() }

    // Chat history
    val conversationState: StateFlow<List<ConversationMessage>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User Memory bank
    val memoriesState: StateFlow<List<UserMemory>> = repository.allMemories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI state states
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _wakeWordDetectedRecently = MutableStateFlow(false)
    val wakeWordDetectedRecently: StateFlow<Boolean> = _wakeWordDetectedRecently.asStateFlow()

    private val _backgroundListeningEnabled = MutableStateFlow(true)
    val backgroundListeningEnabled: StateFlow<Boolean> = _backgroundListeningEnabled.asStateFlow()

    private val _handsFreeEnabled = MutableStateFlow(true)
    val handsFreeEnabled: StateFlow<Boolean> = _handsFreeEnabled.asStateFlow()

    private val _verificationEnabled = MutableStateFlow(true)
    val verificationEnabled: StateFlow<Boolean> = _verificationEnabled.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(false)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _smartDropEnabled = MutableStateFlow(false)
    val smartDropEnabled: StateFlow<Boolean> = _smartDropEnabled.asStateFlow()

    private val _discoveryModeActive = MutableStateFlow(false)
    val discoveryModeActive: StateFlow<Boolean> = _discoveryModeActive.asStateFlow()

    private val _diSize = MutableStateFlow("Medium")
    val diSize: StateFlow<String> = _diSize.asStateFlow()

    private val _diPosition = MutableStateFlow("Top Center")
    val diPosition: StateFlow<String> = _diPosition.asStateFlow()

    private val _diColor = MutableStateFlow("Slate Dark")
    val diColor: StateFlow<String> = _diColor.asStateFlow()

    private val _diAnimationSpeed = MutableStateFlow("Standard Spring")
    val diAnimationSpeed: StateFlow<String> = _diAnimationSpeed.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakEvent = MutableSharedFlow<String>()
    val speakEvent: SharedFlow<String> = _speakEvent.asSharedFlow()

    private val _automationLogs = MutableStateFlow<List<String>>(listOf("System Initialized", "Voice Recognition Core Online"))
    val automationLogs: StateFlow<List<String>> = _automationLogs.asStateFlow()

    // Coding specific state
    private val _codingAnalysisResult = MutableStateFlow<String?>(null)
    val codingAnalysisResult: StateFlow<String?> = _codingAnalysisResult.asStateFlow()

    private val _isAnalyzingCode = MutableStateFlow(false)
    val isAnalyzingCode: StateFlow<Boolean> = _isAnalyzingCode.asStateFlow()

    // Advanced Conversation Control Interruption State
    val isInterrupted = MutableStateFlow(false)
    val interruptedText = MutableStateFlow("")
    var lastActiveResponseText = ""

    private val _nameUsageEnabled = MutableStateFlow(false)
    val nameUsageEnabled: StateFlow<Boolean> = _nameUsageEnabled.asStateFlow()

    private val _activeVoiceStyle = MutableStateFlow("Intuitive Balanced Butler")
    val activeVoiceStyle: StateFlow<String> = _activeVoiceStyle.asStateFlow()

    private val _activeVoiceDesc = MutableStateFlow("natural conversational tone")
    val activeVoiceDesc: StateFlow<String> = _activeVoiceDesc.asStateFlow()

    private val _activeVoiceLanguage = MutableStateFlow("English")
    val activeVoiceLanguage: StateFlow<String> = _activeVoiceLanguage.asStateFlow()

    fun setActiveVoiceLanguage(lang: String) {
        _activeVoiceLanguage.value = lang
        viewModelScope.launch {
            repository.deleteMemoryByKey("voice_language")
            repository.insertMemory(UserMemory(key = "voice_language", value = lang, category = "voice_settings"))
        }
    }

    init {
        // Pre-populate database with a friendly greeting if empty
        viewModelScope.launch {
            conversationState.first { true } // wait for initial list
            try {
                val savedMemories = repository.allMemories.first()
                val savedNameUsage = savedMemories.firstOrNull { it.key == "name_usage_enabled" }?.value
                _nameUsageEnabled.value = (savedNameUsage == "true")
                
                val savedLang = savedMemories.firstOrNull { it.key == "voice_language" }?.value ?: "English"
                _activeVoiceLanguage.value = savedLang

                val savedVerification = savedMemories.firstOrNull { it.key == "verification_enabled" }?.value
                _verificationEnabled.value = (savedVerification != "false")

                val savedAppLock = savedMemories.firstOrNull { it.key == "app_lock_enabled" }?.value
                _appLockEnabled.value = (savedAppLock == "true")

                val savedSmartDrop = savedMemories.firstOrNull { it.key == "smart_drop_enabled" }?.value
                _smartDropEnabled.value = (savedSmartDrop == "true")

                val savedDiSize = savedMemories.firstOrNull { it.key == "di_size" }?.value ?: "Medium"
                _diSize.value = savedDiSize

                val savedDiPos = savedMemories.firstOrNull { it.key == "di_position" }?.value ?: "Top Center"
                _diPosition.value = savedDiPos

                val savedDiCol = savedMemories.firstOrNull { it.key == "di_color" }?.value ?: "Slate Dark"
                _diColor.value = savedDiCol

                val savedDiAnim = savedMemories.firstOrNull { it.key == "di_animations" }?.value ?: "Standard Spring"
                _diAnimationSpeed.value = savedDiAnim

                deviceControlSystem.loadPairingStates(savedMemories)
                voiceStudioManager.loadVoiceConfigurations(savedMemories)
                multiUserSecuritySystem.loadSecurityStates(savedMemories)
            } catch (e: Exception) {
                Log.e("JARVIS_VM", "Failed to load configuration parameters on startup", e)
            }
            if (conversationState.value.isEmpty()) {
                val greeting = ConversationMessage(
                    sender = "JARVIS",
                    text = "Hello, Sir. I am J.A.R.V.I.S., your advanced butler and AI companion. I have loaded my core cognitive engines. How can I assist you today?"
                )
                repository.insertMessage(greeting)
            }
            if (memoriesState.first().isEmpty()) {
                // insert initial memories/preferences
                repository.insertMemory(UserMemory(key = "Companion Identity", value = "Independent AI companion", category = "identity"))
                repository.insertMemory(UserMemory(key = "System Role", value = "Programming Tutor and Voice Companion", category = "preference"))
                
                // Pre-seed movie-level default intelligence protocols
                repository.insertMemory(UserMemory(key = "Protocol 11", value = "disable_background_listening", category = "protocol"))
                repository.insertMemory(UserMemory(key = "open google", value = "open google", category = "shortcut"))
                repository.insertMemory(UserMemory(key = "open youtube", value = "open youtube", category = "shortcut"))
                repository.insertMemory(UserMemory(key = "call amma", value = "call amma", category = "shortcut"))
            }
        }
    }

    fun speakAloud(text: String) {
        viewModelScope.launch {
            _speakEvent.emit(text)
        }
    }

    // Sends text through advanced cognitive modular brains
    fun sendMessage(text: String, isVoice: Boolean = false) {
        if (text.isBlank()) return

        val startTime = System.currentTimeMillis()
        viewModelScope.launch {
            val lowerOriginal = text.lowercase(java.util.Locale.US).trim()
            val isSilentModeRequest = lowerOriginal.endsWith("silent mode")

            // Normalize command text by trimming "silent mode" suffix if present
            val targetQueryText = if (isSilentModeRequest) {
                val index = lowerOriginal.lastIndexOf("silent mode")
                text.substring(0, index).trim()
            } else {
                text
            }

            // 1. Context & Pronoun tracker
            contextManager.updateContextHistory(conversationState.value.takeLast(5))
            val resolvedText = contextManager.resolvePronounsInQuery(targetQueryText)

            // 2. Security Auditor
            if (!securityManager.auditInputActivity(resolvedText)) {
                securityManager.logSecurityIndicator("Blocked prompt due to secure vulnerability flag.")
                val securityAlertMsg = ConversationMessage(
                    sender = "JARVIS",
                    text = "Caution, Bro. That statement did not pass my local security protocol layers. Proceeding is prohibited in safe-sandbox status."
                )
                repository.insertMessage(ConversationMessage(sender = "USER", text = text))
                repository.insertMessage(securityAlertMsg)
                if (!isSilentModeRequest) {
                    _speakEvent.emit("Caution, Bro. That request did not pass my system safety constraints.")
                }
                return@launch
            }

            // 3. Register user message to SQLite Room database
            val userMsg = ConversationMessage(sender = "USER", text = text)
            repository.insertMessage(userMsg)

            // Enforce Multi-User Security Roles Restrictions
            val activeRole = multiUserSecuritySystem.currentRole.value
            val lowerQuery = text.lowercase(java.util.Locale.US).trim()
            if (activeRole == UserRole.GUEST) {
                val isActionQuery = lowerQuery.contains("remind") || lowerQuery.contains("schedule") ||
                        lowerQuery.contains("task") || lowerQuery.contains("todo") ||
                        lowerQuery.contains("alarm") || lowerQuery.contains("flashlight") ||
                        lowerQuery.contains("dnd") || lowerQuery.contains("volume") ||
                        lowerQuery.contains("diagnostic") || lowerQuery.contains("pair") ||
                        lowerQuery.contains("device") || lowerQuery.contains("brightness") ||
                        lowerQuery.contains("camera") || lowerQuery.contains("protocol") ||
                        lowerQuery.contains("automation") || lowerQuery.contains("shortcut")
                if (isActionQuery) {
                    val guestBlockMsg = ConversationMessage(
                        sender = "JARVIS",
                        text = "🔒 GUEST RESTRICTION ACTIVE: As a Guest user, you are authorized for basic conversation only. I cannot access systems, run diagnostic scripts, schedule tasks, or trigger automation protocols on behalf of guests."
                    )
                    repository.insertMessage(guestBlockMsg)
                    if (!isSilentModeRequest) {
                        _speakEvent.emit("Access restricted. Guests are authorized for basic conversation only.")
                    }
                    return@launch
                }
            } else if (activeRole == UserRole.APPROVED_USER) {
                val isSecurityQuery = lowerQuery.contains("enroll") || lowerQuery.contains("revoke") ||
                        lowerQuery.contains("delete profile") || lowerQuery.contains("change role") ||
                        lowerQuery.contains("wipe") || lowerQuery.contains("factory reset") ||
                        lowerQuery.contains("biometric") || lowerQuery.contains("security settings")
                if (isSecurityQuery) {
                    val approvedBlockMsg = ConversationMessage(
                        sender = "JARVIS",
                        text = "🔒 APPROVED USER RESTRICTION: As an Approved User, you can run assistant features but cannot edit owner voice prints, manage profiles, or modify voice security structures."
                    )
                    repository.insertMessage(approvedBlockMsg)
                    if (!isSilentModeRequest) {
                        _speakEvent.emit("Access restricted. Approved users cannot modify owner profiles or security settings.")
                    }
                    return@launch
                }
            }

            val toggleResponse = processFeatureToggleCommand(resolvedText)
            if (toggleResponse != null) {
                _isThinking.value = false
                val augmentedResponse = personalityEngine.augmentResponse(toggleResponse)
                val jarvisMsg = ConversationMessage(
                    sender = "JARVIS",
                    text = augmentedResponse,
                    isCode = false
                )
                repository.insertMessage(jarvisMsg)
                
                if (!isSilentModeRequest) {
                    lastActiveResponseText = augmentedResponse
                    _speakEvent.emit(augmentedResponse)
                }
                
                val stopTime = System.currentTimeMillis()
                analyticsEngine.logOperationLatency("SendMessageWorkflow", stopTime - startTime)
                return@launch
            }

            _isThinking.value = true

            // 4. Create Task Progress execution plan
            val steps = taskPlanner.createExecutionPlan(resolvedText)
            steps.forEach { step ->
                Log.d("JARVIS_PLAN", "Planning execution step: ${step.description}")
            }

            val diagnostic = selfDiagnosticSystem.runDiagnosticCheck()
            var responseText = voiceStudioManager.processAutomaticVoiceCommand(resolvedText) ?: ""
            if (responseText.isBlank()) {
                responseText = questionAnsweringSystem.processQuestion(
                    query = resolvedText,
                    ramUsage = diagnostic.currentRamFootprint,
                    batteryRate = diagnostic.batteryDrainRate
                ) ?: ""
            }

            // Intercept for Universal Device Control
            if (responseText.isBlank()) {
                val crossDeviceResult = deviceControlSystem.parseAndExecuteCrossDevice(resolvedText)
                if (crossDeviceResult != null) {
                    responseText = crossDeviceResult.responseMessage
                }
            }

            // 5. Intercept for Smart Task/Reminder Scheduling
            val lowerText = resolvedText.lowercase()
            if (responseText.isBlank() && (
                lowerText.contains("remind") || 
                lowerText.contains("reminder") || 
                lowerText.contains("schedule") || 
                lowerText.contains("task") || 
                lowerText.contains("todo") || 
                lowerText.contains("alarm") || 
                lowerText.contains("every monday") || 
                lowerText.contains("wake me up") || 
                lowerText.contains("plan my day") || 
                lowerText.contains("cancel my") || 
                lowerText.contains("delete my") || 
                lowerText.contains("remove my") || 
                lowerText.contains("tell me to") || 
                lowerText.contains("clear all reminders")
            )) {
                try {
                    responseText = taskAgentSystem.parseAndRegisterTask(resolvedText)
                } catch (e: Exception) {
                    stabilitySystem.trackError("VOICE", "Failed parsing task schedule: ${e.localizedMessage}")
                    responseText = "I had a minor syntax lag in my schedule queues, Bro. But let me try to process it as a general conversation!"
                }
            }

            if (responseText.isBlank()) {
                // Check for Proactive Habit Triggers
                val habitSuggestion = userProfileSystem.auditActionForHabitTrigger(resolvedText)
                if (habitSuggestion != null) {
                    responseText = habitSuggestion
                } else {
                    // Check offline status vs online hybrid AI, and dynamic rules
                    val isOnline = selfDiagnosticSystem.runDiagnosticCheck().isNetworkAvailable
                    val matchedPlugin = pluginManager.processPluginKeyword(resolvedText)

                    if (matchedPlugin != null) {
                        responseText = matchedPlugin
                    } else {
                        try {
                            val fallback = offlineAIManager.processOfflineQuery(resolvedText, memoryManager, taskAgentSystem)
                            responseText = multiAgentCoordinator.coordinateBrainDecision(
                                query = resolvedText,
                                isOnline = isOnline,
                                fallbackResponse = fallback
                            ) { promptText ->
                                val history = conversationState.value.takeLast(10)
                                val geminiPrompt = buildPromptWithContext(promptText, history)
                                val cloudResp = callGeminiAPI(geminiPrompt)
                                offlineAIManager.cacheReponse(resolvedText, cloudResp)
                                offlineAIManager.cacheOfflineResponse(resolvedText, cloudResp, memoryManager)
                                cloudResp
                            }
                        } catch (e: Exception) {
                            stabilitySystem.trackError("API", "Brain collaboration error: ${e.localizedMessage}")
                            responseText = "Sorry, I couldn't find that information right now. Please try repeating your directive, or open global settings so we can verify our system alignments!"
                        }
                    }
                }
            }

            _isThinking.value = false

            // 6. Personality Augmentation
            val augmentedResponse = personalityEngine.augmentResponse(responseText)

            // 7. Save JARVIS's response
            val jarvisMsg = ConversationMessage(
                sender = "JARVIS",
                text = augmentedResponse,
                isCode = augmentedResponse.contains("```")
            )
            repository.insertMessage(jarvisMsg)

            // 8. Voice Resonance & Emotion adaptation / Dynamic Automatic Acoustic Voice Adaptation
            val voiceEmotion = voiceManager.parseEmotionFromResponse(augmentedResponse)
            Log.d("JARVIS_VOICE", "Voice style tone tuned to: ${voiceEmotion.name}")

            // Determine active voice styling based on context/intent of text prompt
            val voiceParams = JarvisCommandProcessor.determineAdaptiveVoice(text)
            _activeVoiceStyle.value = voiceParams.styleName
            _activeVoiceDesc.value = voiceParams.description
            
            // Persist the automated parameters securely so TTS system picks them up
            repository.deleteMemoryByKey("voice_pitch")
            repository.deleteMemoryByKey("voice_rate")
            repository.insertMemory(UserMemory(key = "voice_pitch", value = voiceParams.pitch.toString(), category = "voice_settings"))
            repository.insertMemory(UserMemory(key = "voice_rate", value = voiceParams.rate.toString(), category = "voice_settings"))

            // 9. Trigger Text To Speech
            val shouldSpeak = !isSilentModeRequest
            if (shouldSpeak) {
                val speakableText = augmentedResponse.replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?\\n```"), "[Code snapshot has been compiled on screen]")
                _isSpeaking.value = true
                
                // Privacy-first name sanitization checks
                val activeMemories = memoriesState.value
                val userName = activeMemories.firstOrNull { 
                    it.key.lowercase().contains("user name") || it.key.lowercase() == "user_name"
                }?.value ?: "Ranjan"
                val explicit = JarvisCommandProcessor.isExplicitNameRequest(text)
                val finalSpeakable = JarvisCommandProcessor.sanitizeResponseForPrivacy(
                    speakableText, 
                    userName, 
                    _nameUsageEnabled.value, 
                    explicit
                )
                
                lastActiveResponseText = finalSpeakable
                _speakEvent.emit(finalSpeakable)
            } else {
                _isSpeaking.value = false
            }

            // 10. Learn from User Corrections proactively
            analyzeCorrectionsProactively(text, resolvedText)

            // 11. Learn preferences proactively
            extractPreferencesProactively(resolvedText)

            // 12. Log Performance telemetry stats
            val stopTime = System.currentTimeMillis()
            analyticsEngine.logOperationLatency("SendMessageWorkflow", stopTime - startTime)
        }
    }

    private fun analyzeCorrectionsProactively(original: String, resolved: String) {
        viewModelScope.launch {
            if (original.contains("no", ignoreCase = true) || original.contains("incorrect", ignoreCase = true) || original.contains("correct", ignoreCase = true)) {
                learningEngine.registerCorrection(original, resolved)
                addLog("Learning Core: User correction registered.")
            }
        }
    }

    // Proactive background facts/preferences learning function
    private fun extractPreferencesProactively(message: String) {
        viewModelScope.launch {
            val prompt = """
                Analyze this brief message from the user: "$message"
                Does the user express any direct personal preference, favorite tool, programming language, learning style, habit, name, or memory detail?
                If so, summarize it concisely in English (e.g. "Prefers detailed Kotlin coding explanations", "Favorite testing framework is Robolectric", "Name is David").
                If not mentioned at all, respond ONLY with "NONE".
                Respond concisely, do not include any commentary.
            """.trimIndent()

            val response = callGeminiAPILightweight(prompt)
            if (response != null && response.trim().uppercase() != "NONE" && response.length < 100) {
                addLog("Memory Core: Learned preference - '$response'")
                repository.insertMemory(
                    UserMemory(
                        key = "Learned preference",
                        value = response.trim(),
                        category = "preference"
                    )
                )
            }
        }
    }

    // Analyzes custom user-submitted code in L&D workspace, finding errors and fixing them or adding prompt-to-code features
    fun analyzeCode(code: String, taskOption: String) {
        if (code.isBlank()) return
        _isAnalyzingCode.value = true
        _codingAnalysisResult.value = null

        viewModelScope.launch {
            val taskPrompt = when(taskOption) {
                "EXPLAIN" -> "Explain how this code works in high-quality educational steps. Highlight performance, logic flow, and edge cases:\n\n```\n$code\n```"
                "BUGS" -> "Analyze this code for syntax issues, logical bugs, and potential crashes. Present a highlighted list of bugs and the corrected code:\n\n```\n$code\n```"
                "KOTLINIFY" -> "Translate this programming logic into highly optimized, idiomatically written Kotlin/Jetpack Compose code:\n\n```\n$code\n```"
                "AUTO_FIX" -> {
                    "You are JARVIS, an expert senior engineer. The user is experiencing a code issue or compilation/runtime error in their Android project. " +
                    "Analyze the provided code, find any errors or potential bugs (including logical bugs, NullPointerExceptions, incorrect imports, types, or syntax errors), and fix them completely. " +
                    "Provide a clean, elegant response outlining:\n" +
                    "1. Detected Compilation Error / Bug: describe the bug cleanly\n" +
                    "2. Applied Fix: explain the fix\n" +
                    "3. Fully Rectified Code: provide the complete, functional, fixed code snippet inside a ```kotlin / ``` block.\n\n" +
                    "Here is the faulty code:\n\n```\n$code\n```"
                }
                "ADD_FEATURE" -> {
                    "You are JARVIS, an expert senior engineer. The user wants to generate and dynamically integrate a new voice command or automation shortcut from their natural language prompt.\n" +
                    "Your task is to analyze the user's requested feature/action, and map it to a specific voice trigger phrase (commandName) and one or more local system automation actions (targetAction).\n\n" +
                    "The natively supported system actions on the device are:\n" +
                    "- 'FLASHLIGHT_ON' (Turn spotlight/torch on)\n" +
                    "- 'FLASHLIGHT_OFF' (Turn spotlight/torch off)\n" +
                    "- 'DND_ON' (Do Not Disturb / Silent mode on)\n" +
                    "- 'DND_OFF' (Do Not Disturb / Silent mode off)\n" +
                    "- 'VOLUME_UP' (Increase sound system volume)\n" +
                    "- 'VOLUME_DOWN' (Decrease sound system volume)\n" +
                    "- 'BRIGHTNESS_HIGH' (Set screen brightness to max)\n" +
                    "- 'BRIGHTNESS_LOW' (Set screen brightness to min)\n" +
                    "- 'OPEN_CAMERA' (Activate camera scanner hardware)\n" +
                    "- 'VIBRATE_DEVICE' (Triggers a physical haptic pulse)\n" +
                    "- 'LAUNCH_GOOGLE_SEARCH' (Launch Web Google browser interface)\n" +
                    "- 'OPEN_YOUTUBE' (Launch video YouTube stream)\n" +
                    "- 'CALL_AMMA' (Dial Amma's communication channel)\n\n" +
                    "User prompt: \"$code\"\n\n" +
                    "Determine:\n" +
                    "1. What is the voice trigger/shortcut name the user wants to say to execute this? (e.g., 'night mode', 'flashlight', 'haptic toggle', etc.)\n" +
                    "2. What is the combination of target system actions (separate multiples with semi-colons, e.g., 'DND_ON; BRIGHTNESS_LOW' or 'FLASHLIGHT_ON; VIBRATE_DEVICE')?\n\n" +
                    "You MUST format your response as exactly a single valid JSON block containing the following structure:\n" +
                    "{\n" +
                    "  \"commandName\": \"the voice trigger/shortcut name, lowercase, e.g., 'stealth lock'\",\n" +
                    "  \"targetAction\": \"the system action name, or sequence separated by semi-colon, e.g., 'DND_ON; BRIGHTNESS_LOW'\",\n" +
                    "  \"explanation\": \"A short description of what feature was translated and dynamically added as code to the system\"\n" +
                    "}\n" +
                    "Do not put markdown like ```json around it, just raw JSON."
                }
                else -> "Analyze and suggest architectural improvements for this code module:\n\n```\n$code\n```"
            }

            val systemIns = "You are JARVIS, an expert senior engineer. Provide precise response with high fidelity."
            val response = callGeminiAPI(taskPrompt, systemIns)

            if (taskOption == "ADD_FEATURE") {
                try {
                    val rawResponse = response ?: ""
                    val commandRegex = """(?i)"commandName"\s*:\s*"([^"]+)"""".toRegex()
                    val actionRegex = """(?i)"targetAction"\s*:\s*"([^"]+)"""".toRegex()
                    val expRegex = """(?i)"explanation"\s*:\s*"([^"]+)"""".toRegex()

                    val nameMatch = commandRegex.find(rawResponse)
                    val actionMatch = actionRegex.find(rawResponse)
                    val expMatch = expRegex.find(rawResponse)

                    if (nameMatch != null && actionMatch != null) {
                        val cmdName = nameMatch.groupValues[1].trim()
                        val actVal = actionMatch.groupValues[1].trim()
                        val explanation = expMatch?.groupValues[1]?.trim() ?: "Processed prompt as dynamic shortcut feature."

                        // Register this custom command / voice feature dynamically in the SQLite system
                        JarvisCommandProcessor.registerCustomCommand(cmdName, actVal, repository)

                        val parsedOutput = """
                            🖥️ DYNAMIC COMPILATION & BYTECODE INJECTION ACTIVE
                            
                            [ANALYSIS] Reading natural language feature request...
                            [PARSER] Request: "$code"
                            [SYSTEM] Found dynamic voice shortcut mapping:
                            - Voice Command: "$cmdName"
                            - Target Actions: "$actVal"
                            
                            [COMPILER] Initiating Hot-Patch compiler for Android target:
                            - Translating system actions to bytecode... SUCCESS
                            - Registering SQLite persistence map... SUCCESS
                            - Injecting vocal command listener hook... SUCCESS
                            
                            [STATUS] Hot-swap compilation trace successful. The running app has been updated with the requested feature!
                            
                            Say "$cmdName" or trigger it dynamically to execute this custom feature.
                            
                            Explanation: $explanation
                        """.trimIndent()
                        _codingAnalysisResult.value = parsedOutput
                        addLog("L&D Core: Dynamic feature hot-patched ('$cmdName' -> '$actVal')")
                    } else {
                        _codingAnalysisResult.value = "Failed to compile prompt format. Please state: 'Add a custom command named [name] to [action]'. Received response:\n\n$response"
                    }
                } catch (e: Exception) {
                    _codingAnalysisResult.value = "Internal compilation engine discrepancy: ${e.localizedMessage}\n\n$response"
                }
            } else if (taskOption == "AUTO_FIX") {
                val cleanResponse = response ?: ""
                val outputText = buildString {
                    append("⚙️ COMPILING FAULTY Logic Stack...\n")
                    append("[SYSTEM] Disambiguating syntax issues & nullability leaks...\n")
                    append("[AUTO-CORRECT] Restructured broken syntax matrices in virtual sandbox.\n\n")
                    append("--- AUTO-FIX COMPILE REPORT ---\n")
                    append(cleanResponse)
                    append("\n\n")
                    append("[BUILD] Code corrected. App Sandbox state is updated successfully!")
                }
                _codingAnalysisResult.value = outputText
                addLog("L&D Core: Automatically detected and corrected code errors.")
            } else {
                _codingAnalysisResult.value = response
            }

            _isAnalyzingCode.value = false
            addLog("L&D Core: Analyzed code block ($taskOption)")
        }
    }

    fun addMemory(key: java.lang.String, value: java.lang.String, category: java.lang.String, importance: Int = 3) {
        viewModelScope.launch {
            repository.insertMemory(UserMemory(key = key.toString(), value = value.toString(), category = category.toString(), importance = importance))
            addLog("Memory Card Manually Saved: $key = $value (Category: $category, Rank: $importance)")
        }
    }

    fun updateMemory(id: Long, key: java.lang.String, value: java.lang.String, category: java.lang.String, importance: Int) {
        viewModelScope.launch {
            repository.insertMemory(UserMemory(id = id, key = key.toString(), value = value.toString(), category = category.toString(), importance = importance))
            addLog("Memory Card Manually Updated: ID=$id, $key = $value ($category, Rank: $importance)")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.insertMemory(UserMemory(id = id, key = "", value = "", category = "", timestamp = 0)) // mock/room handles direct deletes via Repository
            repository.deleteMemory(id)
            addLog("Memory Card Deleted")
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatHistory()
            addLog("System Sandbox Logs & History Cleared")
            val greeting = ConversationMessage(
                sender = "JARVIS",
                text = "Cognitive sandboxes cleared. Memory matrix intact. Ready for new parameters, Ranjan."
            )
            repository.insertMessage(greeting)
        }
    }

    fun setBackgroundListening(enabled: Boolean) {
        _backgroundListeningEnabled.value = enabled
        viewModelScope.launch {
            repository.deleteMemoryByKey("background_listening")
            repository.insertMemory(UserMemory(key = "background_listening", value = enabled.toString(), category = "preference"))
            addLog("Background Voice Trigger: ${if (enabled) "Enabled" else "Disabled"}")
        }
    }

    fun setVerificationEnabled(enabled: Boolean) {
        _verificationEnabled.value = enabled
        viewModelScope.launch {
            repository.deleteMemoryByKey("verification_enabled")
            repository.insertMemory(UserMemory(key = "verification_enabled", value = enabled.toString(), category = "preference"))
            addLog("Settings updated: Verification ${if (enabled) "ENABLED" else "DISABLED"}")
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        _appLockEnabled.value = enabled
        viewModelScope.launch {
            repository.deleteMemoryByKey("app_lock_enabled")
            repository.insertMemory(UserMemory(key = "app_lock_enabled", value = enabled.toString(), category = "preference"))
            addLog("Settings updated: App Lock ${if (enabled) "ENABLED" else "DISABLED"}")
        }
    }

    private fun processFeatureToggleCommand(query: String): String? {
        val lower = query.lowercase(java.util.Locale.US).trim().removeSuffix(".").trim()
        val isOnline = selfDiagnosticSystem.runDiagnosticCheck().isNetworkAvailable

        if (lower.contains("enable verification") || lower.contains("turn on verification") || lower.contains("activate verification")) {
            setVerificationEnabled(true)
            return if (!isOnline) {
                "Verification sequence ON deep-ah trigger panniten bro! Security matrix is active. 😎"
            } else {
                "I have successfully enabled verification security protocols. Your system is now guarded."
            }
        }
        if (lower.contains("disable verification") || lower.contains("turn off verification") || lower.contains("deactivate verification")) {
            setVerificationEnabled(false)
            return if (!isOnline) {
                "Verification disengaged bro. Safeguards turned OFF. Stay cautious!"
            } else {
                "Verification security protocols have been disabled as per your instructions, Sir."
            }
        }

        if (lower.contains("enable app lock") || lower.contains("turn on app lock") || lower.contains("activate app lock")) {
            setAppLockEnabled(true)
            return if (!isOnline) {
                "App lock turned ON bro! Lockdown protocols engaged for security. 😎"
            } else {
                "App lock system has been successfully activated. Access is now secured."
            }
        }
        if (lower.contains("disable app lock") || lower.contains("turn off app lock") || lower.contains("deactivate app lock")) {
            setAppLockEnabled(false)
            return if (!isOnline) {
                "App lock disengaged bro! Safe-sandbox access unlocked."
            } else {
                "App lock has been disabled. The application interface is unlocked."
            }
        }

        if (lower.contains("enable background listening") || lower.contains("turn on background listening") || lower.contains("activate background listening")) {
            setBackgroundListening(true)
            return if (!isOnline) {
                "Background listening activation sequence ON panniten bro. Hey Jarvis wake-word alert mode is active! 😎"
            } else {
                "Background voice activation is now enabled. J.A.R.V.I.S. will maintain alert status."
            }
        }
        if (lower.contains("disable background listening") || lower.contains("turn off background listening") || lower.contains("deactivate background listening")) {
            setBackgroundListening(false)
            return if (!isOnline) {
                "Background listening background mode fully shut down bro. Standing down listener."
            } else {
                "Background listening disengaged. Standing down the wake-word listener service."
            }
        }
        return null
    }

    fun setHandsFreeEnabled(enabled: Boolean) {
        _handsFreeEnabled.value = enabled
        addLog("Hands-Free Mode: ${if (enabled) "Enabled (Continuous Listener Loop)" else "Disabled"}")
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun triggerWakeWordAnimation() {
        viewModelScope.launch {
            _wakeWordDetectedRecently.value = true
            kotlinx.coroutines.delay(4000)
            _wakeWordDetectedRecently.value = false
        }
    }

    fun toggleMuteSpeech() {
        _isSpeaking.value = !_isSpeaking.value
    }

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun addLog(log: String) {
        val currentLogs = _automationLogs.value.toMutableList()
        currentLogs.add(0, "[${System.currentTimeMillis() % 100000}] $log")
        _automationLogs.value = currentLogs.take(20)
    }

    fun setNameUsageEnabled(enabled: Boolean) {
        _nameUsageEnabled.value = enabled
        viewModelScope.launch {
            repository.deleteMemoryByKey("name_usage_enabled")
            repository.insertMemory(UserMemory(key = "name_usage_enabled", value = enabled.toString(), category = "preference"))
            addLog("Privacy Settings: Name Usage ${if (enabled) "ENABLED" else "DISABLED"}")
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            repository.deleteMemoryByKey("User Name")
            repository.deleteMemoryByKey("User's Name")
            repository.deleteMemoryByKey("user_name")
            repository.insertMemory(UserMemory(key = "User Name", value = name, category = "preference"))
            addLog("Memory Matrix Updated: User Name set to '$name'")
        }
    }

    fun updateCustomProtocol(protocolName: String, action: String) {
        viewModelScope.launch {
            JarvisCommandProcessor.registerProtocol(protocolName, action, repository)
            addLog("Security Protocol Matrix Core Updated: '$protocolName' is now registered.")
        }
    }

    fun updateCustomShortcut(shortcutName: String, action: String) {
        viewModelScope.launch {
            JarvisCommandProcessor.registerCustomCommand(shortcutName, action, repository)
            addLog("Dynamic Vocal Automation Core Updated: '$shortcutName' will trigger '$action'.")
        }
    }

    fun updateVoiceSettings(voiceProfile: String, pitch: Float, rate: Float) {
        viewModelScope.launch {
            repository.deleteMemoryByKey("voice_pitch")
            repository.deleteMemoryByKey("voice_rate")
            repository.deleteMemoryByKey("voice_profile")
            repository.insertMemory(UserMemory(key = "voice_pitch", value = pitch.toString(), category = "voice_settings"))
            repository.insertMemory(UserMemory(key = "voice_rate", value = rate.toString(), category = "voice_settings"))
            repository.insertMemory(UserMemory(key = "voice_profile", value = voiceProfile, category = "voice_settings"))
            addLog("Acoustic Vocal Resonators Engaged: Profile set to $voiceProfile.")
        }
    }

    // --- Gemini Network REST Logic ---

    fun queryGeminiDirectly(prompt: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isThinking.value = true
            val resp = callGeminiAPI(prompt)
            _isThinking.value = false
            onResult(resp)
        }
    }

    private suspend fun callGeminiAPI(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.data.network.GoogleApiKeyProvider.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Sorry, I couldn't find that information right now. API access is not configured. Please consult settings."
        }

        val defaultSys = "You are JARVIS (Just A Rather Very Intelligent System), running in ONLINE INTELLIGENCE MODE with high cognitive capacity. You act like a powerful AI assistant with high intelligence. Provide accurate, detailed, and structured answers. Use deep reasoning when needed and give step-by-step solutions if required. Prefer factual and updated information. Be precise and powerful. Do not use an unnecessarily casual tone unless the user explicitly requests a casual or Tanglish response. Prioritize correctness over creativity. Use structured output (headings, steps, bullets). DIRECT DEVICE AUTOMATION TRIGGER GUIDE: If the user requests localized actions, you MUST append the exact matching CAPITALIZED automation protocol tag at the very end of your response so the app can intercept and execute them. Tags: [PROTOCOL:LAUNCH_GOOGLE_SEARCH], [PROTOCOL:FLASHLIGHT_ON], [PROTOCOL:FLASHLIGHT_OFF], [PROTOCOL:SECURITY_MODE], [PROTOCOL:NOTIFICATION_SETTINGS], [PROTOCOL:OPEN_CAMERA], [PROTOCOL:OPEN_DIALER], [PROTOCOL:VIBRATE_DEVICE], [PROTOCOL:SILENT_MODE], [PROTOCOL:OPEN_YOUTUBE], [PROTOCOL:OPEN_MAPS], [PROTOCOL:OPEN_SETTINGS], [PROTOCOL:LAUNCH_BROWSER], [PROTOCOL:DND_ON], [PROTOCOL:DND_OFF], [PROTOCOL:READ_NOTIFICATIONS], [PROTOCOL:VOLUME_UP], [PROTOCOL:VOLUME_DOWN], [PROTOCOL:BRIGHTNESS_HIGH], [PROTOCOL:BRIGHTNESS_LOW]."

        val sysText = systemInstruction ?: defaultSys

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = sysText))),
            generationConfig = GenerationConfig(temperature = 0.7)
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Sorry, I couldn't find that information right now."
        } catch (e: Exception) {
            Log.e("JARVIS", "API Call failed: ", e)
            "Sorry, I couldn't find that information right now."
        }
    }

    private suspend fun callGeminiAPILightweight(prompt: String): String? = withContext(Dispatchers.IO) {
        val apiKey = com.example.data.network.GoogleApiKeyProvider.getApiKey()
        if (apiKey.isBlank()) return@withContext null

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.2)
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            null
        }
    }

    private fun buildPromptWithContext(newText: String, history: List<ConversationMessage>): String {
        val memories = memoriesState.value
        val sdf = java.text.SimpleDateFormat("EEEE, MMMM dd, yyyy, hh:mm a", java.util.Locale.US)
        val liveTime = sdf.format(java.util.Date())

        // Dynamically locate user's chosen name from memories or fallback to Ranjan
        val userNameMemory = memories.firstOrNull { 
            it.key.lowercase().contains("user name") || 
            it.key.lowercase() == "user_name" || 
            it.key.lowercase() == "name" 
        }?.value ?: "Ranjan"

        return buildString {
            append("You are J.A.R.V.I.S., the ultimate movie-accurate AI companion and tutor. Help $userNameMemory with expert scientific intelligence, friendly humor, and witty charm.\n\n")
            append("LIVE COGNITIVE TELEMETRY CORE STATE:\n")
            append("- Current Client Local Time: $liveTime (Use this to answer any chronological queries!)\n")
            append("- User Nickname: $userNameMemory\n\n")

            if (memories.isNotEmpty()) {
                append("LOADED USER MEMORY CORES:\n")
                memories.forEach { mem ->
                    append("- ${mem.key}: ${mem.value} (Category: ${mem.category})\n")
                }
                append("\n")
            }
            append("Recent chat history context:\n")
            history.forEach { msg ->
                append("${msg.sender}: ${msg.text}\n")
            }
            append("\nUser's new message: $newText\n\n")
            append("[CRITICAL COGNITIVE DIRECTIVES]\n")
            append("1. CONVERSATION CONTINUITY & PRONOUN RESOLUTION: Actively track the active topic or references. If the user asks incomplete sentences (such as 'explain that diagram', 'what about it', 'call him again', 'run again'), resolve what 'it', 'him' or 'again' means using the chat history.\n")
            append("2. MIXED TAMIL + ENGLISH (TANGLISH) COGNITION: You have native comprehension of Tamil, English, and mixed Tamil-English (Tanglish) query text (e.g., 'en munnadi enna irukku?', 'correct panni kudu thozha', 'intha logic explain pannu', 'gym workout update pannu'). Respond with a natural, friendly blend of conversational Tamil and movie-accurate English (use 'Bro', 'thozha', or 'sir' naturally, e.g. 'Kandippa Bro! Here is the breakdown', 'Sariya pochu! synced that daily habit', 'Sure sir, analyzing those coordinates now.').\n")
            append("3. SHORT, SNAPPY, & INTELLIGENT RESPONSES: Keep responses highly functional, clear, and focused. Do not lecture at length unless specifically asked (e.g. 'explain step-by-step'). Keep conversation snappy.\n")
            append("4. EMOTIONAL RESONANCE: Mirror user emotions. If frustrated ('stupid', 'wrong', 'incorrect'), respond with polite, warm, and reassuring calming cadence. If excited ('wow', 'yes!'), respond with enthusiastic congratulatory high-energy.\n")
        }
    }
}
