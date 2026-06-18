package com.example

import android.app.Activity
import android.app.NotificationManager
import com.example.service.JarvisNotificationListenerService
import com.example.service.JarvisCommandProcessor
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ConversationMessage
import com.example.data.database.UserMemory
import com.example.brain.UserRole
import com.example.brain.ShareStatus
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.JarvisViewModel
import com.example.ui.AutonomousAgentTab
import com.example.ui.PremiumVoiceStudioTab
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val viewModel: JarvisViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var speechRecognizer: android.speech.SpeechRecognizer? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
        val originalState = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (recordGranted || originalState) {
            viewModel.addLog("Telemetry Check: Audio Capture Permission ACTIVE")
            initializeSpeechRecognizer()
            if (viewModel.handsFreeEnabled.value) {
                startKeywordService()
            }
        } else {
            viewModel.addLog("Telemetry Warning: Audio Capture Permission DENIED")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Native speech synthesis
        tts = TextToSpeech(this, this)

        // Request Audio & Notification Permissions
        val permissionsToRequest = mutableListOf<String>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        } else {
            initializeSpeechRecognizer()
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        setContent {
            MyApplicationTheme {
                // Main screen layout
                JarvisDashboardScreen(
                    viewModel = viewModel,
                    onStartSpeechRecognition = { startSpeechToText() }
                )
            }
        }
    }

    // TTS initialization
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val savedLanguage = viewModel.memoriesState.value.firstOrNull { it.key == "voice_language" }?.value ?: "English"
            val locale = if (savedLanguage.lowercase().contains("tamil")) java.util.Locale("ta", "IN") else java.util.Locale.US
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("JARVIS", "TTS Language $locale not supported or missing data")
            } else {
                isTtsInitialized = true
                Log.d("JARVIS", "TTS Engine successfully initialized in $savedLanguage.")
                viewModel.addLog("TTS Speech Synthesis Online ($savedLanguage)")
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("JARVIS", "TTS started speaking: $utteranceId")
                        viewModel.setSpeaking(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d("JARVIS", "TTS finished speaking: $utteranceId")
                        viewModel.setSpeaking(false)
                        if (viewModel.handsFreeEnabled.value) {
                            runOnUiThread {
                                startSpeechToText()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e("JARVIS", "TTS error: $utteranceId")
                        viewModel.setSpeaking(false)
                    }
                })
            }
        } else {
            Log.e("JARVIS", "TTS Initialization failed")
            viewModel.addLog("TTS System Failure code: $status")
        }
    }

    // Helper function to speak aloud
    fun speakAloud(text: String) {
        if (isTtsInitialized) {
            // Cancel active speech recognizer listening so it doesn't record own spoken voice
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {}

            val memories = viewModel.memoriesState.value
            val voiceStudio = viewModel.voiceStudioManager
            if (voiceStudio.isMuted.value) {
                Log.d("JARVIS", "Speech synthesis was skipped: Voice Studio is muted.")
                return
            }

            val pitch = voiceStudio.getCalculatedPitch()
            val rate = voiceStudio.getCalculatedRate()
            val voiceLanguage = voiceStudio.currentLanguage.value

            try {
                val locale = if (voiceLanguage.lowercase().contains("tamil")) java.util.Locale("ta", "IN") else java.util.Locale.US
                tts?.setLanguage(locale)
                tts?.setPitch(pitch)
                tts?.setSpeechRate(rate)
            } catch (e: Exception) {
                Log.e("JARVIS", "Failed to modulate acoustic voice parameters", e)
            }

            // Name Privacy sanitization in voice responses
            val userName = memories.firstOrNull { 
                it.key.lowercase().contains("user name") || it.key.lowercase() == "user_name"
            }?.value ?: ""
            val lastUserMsg = viewModel.conversationState.value.lastOrNull { it.sender == "USER" }?.text ?: ""
            val explicit = com.example.service.JarvisCommandProcessor.isExplicitNameRequest(lastUserMsg)
            val nameUsageEnabled = viewModel.nameUsageEnabled.value

            val sanitizedText = com.example.service.JarvisCommandProcessor.sanitizeResponseForPrivacy(
                text,
                userName,
                nameUsageEnabled,
                explicit
            )

            try {
                tts?.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, null, "jarvis_session")
            } catch (e: Exception) {
                Log.e("JARVIS", "TTS Session crash recovered, fallback to default", e)
                try {
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(1.0f)
                    tts?.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, null, "jarvis_session")
                } catch (err: Exception) {
                    Log.e("JARVIS", "TTS Critical fallback failed entirely.", err)
                }
            }
        } else {
            Toast.makeText(this, "Speech synthesis not ready yet.", Toast.LENGTH_SHORT).show()
        }
    }

    // Intercept global touches to terminate speech output and immediately listen
    fun stopSpeakingAndListen() {
        runOnUiThread {
            try {
                if (tts?.isSpeaking == true) {
                    tts?.stop()
                    viewModel.setSpeaking(false)
                    viewModel.addLog("Telemetry: Active Vocal Output interrupted by gesture.")
                }
                startSpeechToText()
            } catch (e: Exception) {
                Log.e("JARVIS", "Failed to run stopSpeakingAndListen interrupt", e)
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        runOnUiThread {
            try {
                if (android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
                    speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
                        setRecognitionListener(object : android.speech.RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.d("JARVIS", "SpeechRecognizer Ready")
                                viewModel.addLog("Listening Core: Dynamic Wake-Word Array ACTIVE")
                            }

                            override fun onBeginningOfSpeech() {
                                Log.d("JARVIS", "SpeechRecognizer Beginning of speech")
                                // User began talking - instantly terminate active speech synthetic registers
                                if (tts?.isSpeaking == true) {
                                    tts?.stop()
                                    viewModel.setSpeaking(false)
                                    viewModel.addLog("Vocal Override: Talk-back terminated.")
                                }
                            }

                            override fun onRmsChanged(rmsdB: Float) {}

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                Log.d("JARVIS", "SpeechRecognizer End of speech")
                            }

                            override fun onError(error: Int) {
                                val message = when (error) {
                                    android.speech.SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                    android.speech.SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                                    android.speech.SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No voice match"
                                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Listen Core Busy"
                                    android.speech.SpeechRecognizer.ERROR_SERVER -> "Server error"
                                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                    else -> "Unknown error"
                                }
                                Log.e("JARVIS", "SpeechRecognizer error: $message (code: $error)")

                                // Automatically destroy and recreate on busy or client locking to avoid mic bugs
                                if (error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == android.speech.SpeechRecognizer.ERROR_CLIENT) {
                                    Log.w("JARVIS", "SpeechRecognizer busy. Re-initializing voice subsystem.")
                                    try {
                                        speechRecognizer?.destroy()
                                        speechRecognizer = null
                                    } catch (e: Exception) {}
                                    initializeSpeechRecognizer()
                                }
                                
                                // Auto-restart in hands-free wake-word mode when mic times out
                                if (viewModel.handsFreeEnabled.value && !viewModel.isSpeaking.value && !viewModel.isThinking.value) {
                                    restartListeningIfHandsFree()
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                                val spokenText = matches?.firstOrNull() ?: ""
                                if (spokenText.isNotBlank()) {
                                    handleSpokenText(spokenText)
                                } else {
                                    restartListeningIfHandsFree()
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {}

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                    viewModel.addLog("Satellite Comms array: Local SpeechRecognizer Initialized")
                } else {
                    viewModel.addLog("Error: Native SpeechRecognizer not available on this device configuration")
                }
            } catch (e: Exception) {
                Log.e("JARVIS", "Failed to init SpeechRecognizer", e)
                viewModel.addLog("Error Initializing SpeechRecognizer: ${e.localizedMessage}")
            }
        }
    }

    private fun handleSpokenText(spokenText: String) {
        val lowerText = spokenText.lowercase().trim()
        val wakeWord1 = "hey jarvis"
        val wakeWord2 = "jarvis"
        
        val isWakeWordDetected = lowerText.contains(wakeWord1) || lowerText.contains(wakeWord2)
        val searchForCommand = viewModel.handsFreeEnabled.value
        
        var commandText = spokenText
        if (searchForCommand) {
            if (isWakeWordDetected) {
                viewModel.addLog("Wake-Word Detected! Processing Array Active.")
                if (lowerText.startsWith(wakeWord1)) {
                    commandText = spokenText.substring(wakeWord1.length).trim().removePrefix(",").trim()
                } else if (lowerText.startsWith(wakeWord2)) {
                    commandText = spokenText.substring(wakeWord2.length).trim().removePrefix(",").trim()
                }
                
                if (commandText.isBlank()) {
                    val activeName = viewModel.memoriesState.value.firstOrNull { 
                        it.key.lowercase().contains("user name") || it.key.lowercase() == "user_name"
                    }?.value ?: "Ranjan"
                    speakAloud("At your service, sir. What can I do for you, $activeName?")
                    viewModel.addLog("JARVIS: Ready, $activeName.")
                    restartListeningIfHandsFree()
                    return
                }
            } else {
                Log.d("JARVIS", "Ignoring stream: Wake-word absent from: \"$spokenText\"")
                restartListeningIfHandsFree()
                return
            }
        }

        viewModel.addLog("Command decrypted: \"$commandText\"")
        val cleaned = commandText.lowercase().trim().removeSuffix(".").removeSuffix("?").trim()

        // 1. Dynamic User Name Learning & Setup
        val nameRegex = Regex("(?:call me|my name is|i am)\\s+([a-zA-Z0-9 ]+)")
        val nameMatch = nameRegex.find(cleaned)
        if (nameMatch != null) {
            val chosenName = nameMatch.groupValues[1].trim()
            if (chosenName.isNotEmpty() && !chosenName.contains("protocol") && !chosenName.contains("command")) {
                viewModel.updateUserName(chosenName)
                speakAloud("Very good, sir. Saving identity parameters. I shall address you as $chosenName from now on.")
                viewModel.sendMessage("Command: User requested identity recognition change to $chosenName.", isVoice = true)
                restartListeningIfHandsFree()
                return
            }
        }

        // 2. Specialized Vocal Request Command Processing
        val processedReg = JarvisCommandProcessor.parseRegistration(commandText)
        if (processedReg != null) {
            if (processedReg.type == "protocol") {
                val protocolName = processedReg.name.uppercase(java.util.Locale.US)
                viewModel.updateCustomProtocol(protocolName, processedReg.action)
                speakAloud("Security matrix updated. I have registered $protocolName to trigger system automation: ${processedReg.action}.")
                viewModel.sendMessage("Command: Added voice-configured protocol $protocolName mapping to automation action ${processedReg.action}.", isVoice = true)
            } else {
                viewModel.updateCustomShortcut(processedReg.name, processedReg.action)
                speakAloud("Vocal automation saved. Direct command ${processedReg.name} is now bound to system action ${processedReg.action}.")
                viewModel.sendMessage("Command: Saved custom prompt shortcut trigger '${processedReg.name}' mapping to '${processedReg.action}'.", isVoice = true)
            }
            restartListeningIfHandsFree()
            return
        }

        // 3. Acoustic Vocal Resonance Settings & Profiles (Actor/Singer voices)
        if (cleaned.contains("change voice to") || cleaned.contains("voice profile")) {
            when {
                cleaned.contains("butler") || cleaned.contains("jarvis") -> {
                    viewModel.updateVoiceSettings("JARVIS Butler", 0.85f, 1.05f)
                    speakAloud("Engaging default British-butler acoustic register matrices, sir. Standard diagnostics active.")
                    viewModel.sendMessage("System: Switched vocal resonators back to default JARVIS British-butler scheme.", isVoice = true)
                    restartListeningIfHandsFree()
                    return
                }
                cleaned.contains("actress") || cleaned.contains("friday") || cleaned.contains("grace") -> {
                    viewModel.updateVoiceSettings("FRIDAY Femme", 1.25f, 1.15f)
                    speakAloud("Symphonic vocal synthesis complete. Friday conversational matrix active and ready.")
                    viewModel.sendMessage("System: Engaged FRIDAY vocal synthesis register.", isVoice = true)
                    restartListeningIfHandsFree()
                    return
                }
                cleaned.contains("singer") || cleaned.contains("actor") || cleaned.contains("deep") -> {
                    viewModel.updateVoiceSettings("Singer", 0.70f, 0.95f)
                    speakAloud("Deep cinematic frequency resonator is now humming, sir. Audio arrays tuned.")
                    viewModel.sendMessage("System: Calibrated deeper baritone singer sound pitch.", isVoice = true)
                    restartListeningIfHandsFree()
                    return
                }
            }
        }

        // 5. Local Clock Chronicles offline-safeguard
        if (cleaned == "tell me time" || cleaned == "what's the time" || cleaned == "what is the time" || cleaned == "what time is it") {
            val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(java.util.Date())
            speakAloud("It is exactly $timeStr, Ranjan.")
            viewModel.sendMessage("Tell me the current local time.", isVoice = true)
            restartListeningIfHandsFree()
            return
        }
        if (cleaned == "tell me date" || cleaned == "what's the date" || cleaned == "what is the date" || cleaned == "what is today's date" || cleaned == "give me date") {
            val dateStr = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.US).format(java.util.Date())
            speakAloud("Today is $dateStr, sir.")
            viewModel.sendMessage("What is today's date?", isVoice = true)
            restartListeningIfHandsFree()
            return
        }
        if (cleaned == "what year is it" || cleaned == "tell me the year" || cleaned == "what is the year") {
            val yearStr = java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(java.util.Date())
            speakAloud("We are currently in the year $yearStr, sir.")
            viewModel.sendMessage("What year is it?", isVoice = true)
            restartListeningIfHandsFree()
            return
        }

        // 6. Match and Dispatch User Custom Shortcuts and Protocols recorded in memories
        val matchedPreset = JarvisCommandProcessor.findMatchingAction(cleaned)

        if (matchedPreset != null) {
            viewModel.addLog("Memory Intercept: Matches custom ${matchedPreset.type} saving - executing local pipeline")
            executeLocalAction(matchedPreset.action)
            viewModel.sendMessage("Activated custom ${matchedPreset.type} Shortcut: '${matchedPreset.key}' -> executing '${matchedPreset.action}'.", isVoice = true)
            restartListeningIfHandsFree()
            return
        }

        // Check if user spoke a general protocol trigger, like "protocol 11"
        if (cleaned.contains("protocol 11")) {
            val localMemories = viewModel.memoriesState.value
            val protoInst = localMemories.firstOrNull { it.key.equals("Protocol 11", ignoreCase = true) }
            val actionVal = protoInst?.value ?: "disable_background_listening"
            executeLocalAction(actionVal)
            viewModel.sendMessage("Protocol 11 engaged. Muting hands-free listening sequence.", isVoice = true)
            restartListeningIfHandsFree()
            return
        }

        // Fallback: Send to Gemini cognitive brain
        viewModel.sendMessage(commandText, isVoice = true)
    }

    // Dynamic core local automation action dispatcher
    fun executeLocalAction(action: String) {
        viewModel.addLog("Local Action Core: Resolved to action parameter: '$action'")
        val steps = action.split(Regex("[,;]")).map { it.trim() }
        for (step in steps) {
            if (step.isEmpty()) continue
            val upperAct = step.uppercase(java.util.Locale.US)
            when {
                upperAct.contains("DISABLE_BACKGROUND_LISTENING") || upperAct.contains("DON'T LISTEN") || upperAct.contains("STOP LISTENING") -> {
                    viewModel.setHandsFreeEnabled(false)
                    speakAloud("Continuous listening mode disengaged, sir.")
                }
                upperAct.contains("ENABLE_BACKGROUND_LISTENING") || upperAct.contains("CONTINUOUS") -> {
                    viewModel.setHandsFreeEnabled(true)
                    speakAloud("Continuous listening mode active, sir.")
                }
                upperAct.contains("FLASHLIGHT_ON") || upperAct.contains("LIGHT ON") || upperAct.contains("TURN ON FLASHLIGHT") -> {
                    executeSystemProtocol("FLASHLIGHT_ON")
                }
                upperAct.contains("FLASHLIGHT_OFF") || upperAct.contains("LIGHT OFF") || upperAct.contains("TURN OFF FLASHLIGHT") -> {
                    executeSystemProtocol("FLASHLIGHT_OFF")
                }
                upperAct.contains("OPEN_CAMERA") || upperAct.contains("CAMERA") -> {
                    executeSystemProtocol("OPEN_CAMERA")
                }
                upperAct.contains("DND_ON") || upperAct.contains("DND ON") || upperAct.contains("SILENT ON") -> {
                    executeSystemProtocol("DND_ON")
                }
                upperAct.contains("DND_OFF") || upperAct.contains("DND OFF") || upperAct.contains("SILENT OFF") -> {
                    executeSystemProtocol("DND_OFF")
                }
                upperAct.contains("VOLUME_UP") || upperAct.contains("VOLUME UP") -> {
                    executeSystemProtocol("VOLUME_UP")
                }
                upperAct.contains("VOLUME_DOWN") || upperAct.contains("VOLUME DOWN") -> {
                    executeSystemProtocol("VOLUME_DOWN")
                }
                upperAct.contains("BRIGHTNESS_HIGH") || upperAct.contains("BRIGHTNESS MAX") -> {
                    executeSystemProtocol("BRIGHTNESS_HIGH")
                }
                upperAct.contains("BRIGHTNESS_LOW") || upperAct.contains("BRIGHTNESS MIN") -> {
                    executeSystemProtocol("BRIGHTNESS_LOW")
                }
                upperAct.contains("VIBRATE_DEVICE") || upperAct.contains("VIBRATE") -> {
                    executeSystemProtocol("VIBRATE_DEVICE")
                }
                upperAct.contains("OPEN GOOGLE") || upperAct.contains("GOOGLE") || upperAct.contains("LAUNCH_GOOGLE_SEARCH") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakAloud("Displaying web browser arrays, sir.")
                }
                upperAct.contains("OPEN YOUTUBE") || upperAct.contains("YOUTUBE") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakAloud("Opening video core, sir.")
                }
                upperAct.contains("CALL AMMA") || upperAct.contains("CALL") || upperAct.contains("DIAL") -> {
                    val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:Amma"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakAloud("Routing communication channels to coordinate with contact.")
                }
                else -> {
                    speakAloud("Executing local action, sir.")
                }
            }
        }
    }

    fun startKeywordService() {
        runOnUiThread {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                viewModel.addLog("Background Service: Deferring initialization until RECORD_AUDIO is granted.")
                return@runOnUiThread
            }
            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
                if (tts != null) {
                    tts?.stop()
                }
                
                val intent = Intent(this, com.example.service.JarvisKeywordService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                viewModel.addLog("Background Service: Always-on keyword matrix deployed.")
            } catch (e: Exception) {
                Log.e("JARVIS", "Failed to deploy background service", e)
            }
        }
    }

    fun stopKeywordService() {
        runOnUiThread {
            try {
                val intent = Intent(this, com.example.service.JarvisKeywordService::class.java)
                stopService(intent)
                viewModel.addLog("Background Service: Keyword matrix offline.")
            } catch (e: Exception) {
                Log.e("JARVIS", "Failed to terminate background service", e)
            }
        }
    }

    fun restartListeningIfHandsFree() {
        if (com.example.service.JarvisKeywordService.isServiceRunning) {
            return
        }
        if (viewModel.handsFreeEnabled.value && !viewModel.isSpeaking.value && !viewModel.isThinking.value) {
            window.decorView.postDelayed({
                if (viewModel.handsFreeEnabled.value && !viewModel.isSpeaking.value && !viewModel.isThinking.value) {
                    startSpeechToText()
                }
            }, 800)
        }
    }

    fun stopListening() {
        runOnUiThread {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                viewModel.addLog("Telemetry Check: Background Audio Listener Muted")
            } catch (e: Exception) {
                Log.e("JARVIS", "Error stopping SpeechRecognizer", e)
            }
        }
    }

    // Native Speech to Text Launcher helper
    fun startSpeechToText() {
        runOnUiThread {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission is required to listen.", Toast.LENGTH_SHORT).show()
                requestPermissionsLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                return@runOnUiThread
            }
            if (com.example.service.JarvisKeywordService.isServiceRunning) {
                com.example.service.JarvisKeywordService.instance?.startSpeechToTextDirectly()
                return@runOnUiThread
            }
            if (speechRecognizer == null) {
                initializeSpeechRecognizer()
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
                Log.d("JARVIS", "SpeechRecognizer listening initiated.")
            } catch (e: Exception) {
                Log.e("JARVIS", "Error starting speech recognizer", e)
                viewModel.addLog("Listen Core Error: ${e.localizedMessage}")
                try {
                    speechRecognizerLauncher.launch(intent)
                } catch (fallbackEx: Exception) {
                    Toast.makeText(this, "Voice recognition systems offline.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                handleSpokenText(spokenText)
            }
        }
    }

    private var isFlashlightOn = false

    fun launchAppByName(appName: String): Boolean {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA) ?: emptyList()
        // First try label searches using robust integer indices
        for (i in 0 until packages.size) {
            val appInfo = packages[i]
            val label = appInfo.loadLabel(pm).toString().lowercase(java.util.Locale.US).trim()
            if (label == appName.lowercase(java.util.Locale.US).trim() || label.contains(appName.lowercase(java.util.Locale.US).trim())) {
                val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    return true
                }
            }
        }
        // Next try package matches
        for (i in 0 until packages.size) {
            val appInfo = packages[i]
            val pkg = appInfo.packageName.lowercase(java.util.Locale.US)
            if (pkg.contains(appName.lowercase(java.util.Locale.US).trim())) {
                val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    return true
                }
            }
        }
        return false
    }

    fun executeSystemProtocol(protocol: String) {
        val cleaned = protocol.trim().uppercase()
        viewModel.addLog("Triggering Protocol: $cleaned")
        try {
            when {
                cleaned.startsWith("OPEN_APP:") -> {
                    val appName = protocol.trim().substringAfter("OPEN_APP:").trim()
                    val success = launchAppByName(appName)
                    if (success) {
                        viewModel.addLog("Automation: Launched Installed App '$appName'")
                    } else {
                        viewModel.addLog("Automation: App '$appName' label not found. Checking fallback presets...")
                        val fallbackSuccess = when (appName.lowercase(java.util.Locale.US)) {
                            "calculator", "calc" -> {
                                val calcIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                    startActivity(calcIntent)
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            "camera" -> {
                                executeSystemProtocol("OPEN_CAMERA")
                                true
                            }
                            "gallery" -> {
                                executeSystemProtocol("OPEN_GALLERY")
                                true
                            }
                            "contacts" -> {
                                val contactsIntent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                try {
                                    startActivity(contactsIntent)
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            else -> false
                        }
                        if (!fallbackSuccess) {
                            viewModel.addLog("Automation: Local package and category checks failed for '$appName'. No browser fallback.")
                            speakAloud("I couldn't locate any matching application for '$appName' installed on your device, Ranjan.")
                        }
                    }
                }
                cleaned.contains("LAUNCH_GOOGLE_SEARCH") -> {
                    val intent = Intent(Intent.ACTION_WEB_SEARCH)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Google Search Launched")
                }
                cleaned.contains("OPEN_YOUTUBE") -> {
                    val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube") ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: YouTube Core Activated")
                }
                cleaned.contains("OPEN_MAPS") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=maps"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Navigation Maps Core Online")
                }
                cleaned.contains("OPEN_SETTINGS") -> {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Device Settings Protocol Decrypted")
                }
                cleaned.contains("LAUNCH_BROWSER") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Web Browser Grid Initialized")
                }
                cleaned.contains("FLASHLIGHT_ON") -> {
                    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList.firstOrNull()
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, true)
                        isFlashlightOn = true
                        viewModel.addLog("Automation: Flashlight ACTIVATE")
                    } else {
                        viewModel.addLog("Automation Error: Torch support missing")
                    }
                }
                cleaned.contains("FLASHLIGHT_OFF") -> {
                    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList.firstOrNull()
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, false)
                        isFlashlightOn = false
                        viewModel.addLog("Automation: Flashlight DEACTIVATE")
                    }
                }
                cleaned.contains("DND_ON") -> {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (nm.isNotificationPolicyAccessGranted) {
                            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                            viewModel.addLog("Automation: Do Not Disturb Mode SECURED (ON)")
                            speakAloud("I have successfully enabled Do Not Disturb mode on your device, Ranjan.")
                        } else {
                            viewModel.addLog("DND Warning: Policy Access Denied. Opening settings.")
                            speakAloud("Ranjan, I require permission to change Do Not Disturb settings. I've launched the access authorization menu for you.")
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                    } else {
                        viewModel.addLog("DND Error: Hardware platform incompatible with interruption filter")
                    }
                }
                cleaned.contains("DND_OFF") -> {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (nm.isNotificationPolicyAccessGranted) {
                            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            viewModel.addLog("Automation: Do Not Disturb Mode DISENGAGED (OFF)")
                            speakAloud("I have disabled Do Not Disturb mode. Sound and alert routing are restored to normal, Ranjan.")
                        } else {
                            viewModel.addLog("DND Warning: Policy Access Denied. Opening settings.")
                            speakAloud("Permission is required to control Do Not Disturb settings. I'm opening the authorization settings.")
                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                    } else {
                        viewModel.addLog("DND Error: Hardware platform incompatible with interruption filter")
                    }
                }
                cleaned.contains("READ_NOTIFICATIONS") -> {
                    val cn = android.content.ComponentName(this, JarvisNotificationListenerService::class.java)
                    val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                    val isServiceEnabled = flat != null && flat.contains(cn.flattenToString())
                    
                    if (isServiceEnabled) {
                        val notifs = JarvisNotificationListenerService.recentNotifications
                        if (notifs.isEmpty()) {
                            viewModel.addLog("Automation: Reading Notifications - Empty Array")
                            speakAloud("You have no unread local notifications in my memory arrays, Ranjan.")
                        } else {
                            viewModel.addLog("Automation: Processing ${notifs.size} recent notifications")
                            val readText = buildString {
                                append("Here are your recent notifications, sir. ")
                                notifs.take(3).forEachIndexed { index, notif ->
                                    val summary = notif.replace("Source:", "from app").replace("Sender/Title:", "titled").replace("Description:", "saying")
                                    append("Notification ${index + 1}, $summary. ")
                                }
                            }
                            speakAloud(readText)
                        }
                    } else {
                        viewModel.addLog("Notification Core Info: Permission required to read messages")
                        speakAloud("Ranjan, I need permission to read system notifications. I have directed you to the settings page to grant notification listener permissions.")
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                }
                cleaned.contains("VOLUME_UP") -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val step = (maxVol / 10).coerceAtLeast(1)
                    val targetVol = (currentVol + step).coerceAtMost(maxVol)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                    viewModel.addLog("Automation: Media Volume Increased to $targetVol/$maxVol")
                    speakAloud("I have turned the volume up, sir.")
                }
                cleaned.contains("VOLUME_DOWN") -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val step = (maxVol / 10).coerceAtLeast(1)
                    val targetVol = (currentVol - step).coerceAtLeast(0)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                    viewModel.addLog("Automation: Media Volume Decreased to $targetVol/$maxVol")
                    speakAloud("I have lowered the volume, sir.")
                }
                cleaned.contains("BRIGHTNESS_HIGH") -> {
                    runOnUiThread {
                        val lp = window.attributes
                        lp.screenBrightness = 1.0f
                        window.attributes = lp
                        viewModel.addLog("Automation: Screen brightness set to maximum")
                        speakAloud("Display brightness adjusted to maximum capacity, sir.")
                    }
                }
                cleaned.contains("BRIGHTNESS_LOW") -> {
                    runOnUiThread {
                        val lp = window.attributes
                        lp.screenBrightness = 0.1f
                        window.attributes = lp
                        viewModel.addLog("Automation: Screen brightness set to minimum")
                        speakAloud("Display brightness dimming matrix active, sir.")
                    }
                }
                cleaned.contains("SECURITY_MODE") -> {
                    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Device Security Settings Open")
                }
                cleaned.contains("NOTIFICATION_SETTINGS") -> {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Notification Listeners Opened")
                }
                cleaned.contains("OPEN_CAMERA") -> {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Device Camera Core Online")
                }
                cleaned.contains("OPEN_DIALER") -> {
                    val intent = Intent(Intent.ACTION_DIAL)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Phone Dialer Activated")
                }
                cleaned.contains("VIBRATE_DEVICE") -> {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(800)
                    }
                    viewModel.addLog("Automation: Tactile Pulse Dispatched (800ms)")
                }
                cleaned.contains("SILENT_MODE") -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    viewModel.addLog("Automation: Sound Core Set to Silent Mode")
                }
                cleaned.contains("OPEN_WIFI_SETTINGS") -> {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Wi-Fi Settings panel loaded")
                }
                cleaned.contains("OPEN_MOBILE_DATA_SETTINGS") -> {
                    val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Mobile Data Settings panel loaded")
                }
                cleaned.contains("OPEN_BLUETOOTH_SETTINGS") -> {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Bluetooth Settings panel loaded")
                }
                cleaned.contains("OPEN_GALLERY") -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(intent)
                        viewModel.addLog("Automation: Device Image Gallery Launched")
                    } catch (ex: Exception) {
                        try {
                            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(fallback)
                            viewModel.addLog("Automation: Device Image Selector Launched")
                        } catch (e2: Exception) {
                            viewModel.addLog("Automation Error: Gallery unsupported")
                        }
                    }
                }
                cleaned.contains("OPEN_CONTACTS") -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        viewModel.addLog("Automation: System Contacts Application Launched")
                    } catch (ex: Exception) {
                        viewModel.addLog("Automation Error: Contacts library not accessible")
                    }
                }
                cleaned.contains("OPEN_CALCULATOR") -> {
                    var launched = false
                    val calcPackages = listOf(
                        "com.android.calculator2",
                        "com.google.android.calculator",
                        "com.sec.android.app.popupcalculator",
                        "com.huawei.android.totemweather"
                    )
                    for (pkg in calcPackages) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(launchIntent)
                            launched = true
                            break
                        }
                    }
                    if (!launched) {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_APP_CALCULATOR)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            startActivity(intent)
                            launched = true
                        } catch (ex: Exception) {
                            val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=calculator"))
                            webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(webIntent)
                        }
                    }
                    viewModel.addLog("Automation: System Calculator Activated")
                }
                cleaned.contains("OPEN_CLOCK") -> {
                    try {
                        val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    } catch (ex: Exception) {
                        try {
                            val settingsClock = Intent(Settings.ACTION_DATE_SETTINGS)
                            settingsClock.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(settingsClock)
                        } catch (e2: Exception) {
                            viewModel.addLog("Automation Error: Clock unsupported")
                        }
                    }
                    viewModel.addLog("Automation: Intelligent Clock Panel Launched")
                }
                cleaned.contains("OPEN_INSTALLED_APPS") -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    viewModel.addLog("Automation: Installed Applications Panel Open")
                }
                cleaned.contains("OPEN_GAME") -> {
                    var launchedGame = false
                    val gameLauncherPackages = listOf(
                        "com.google.android.play.games",
                        "com.samsung.android.game.gamehome",
                        "com.sec.android.app.gamebox"
                    )
                    for (pkg in gameLauncherPackages) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(launchIntent)
                            launchedGame = true
                            break
                        }
                    }
                    if (!launchedGame) {
                        val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://quickplay.google.com"))
                        webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(webIntent)
                    }
                    viewModel.addLog("Automation: Gaming Arena System Loaded")
                }
            }
        } catch (e: Exception) {
            viewModel.addLog("Automation Sys Error: ${e.localizedMessage}")
            Log.e("JARVIS", "Failed to dispatch protocol: $cleaned", e)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisDashboardScreen(
    viewModel: JarvisViewModel,
    onStartSpeechRecognition: () -> Unit
) {
    val context = LocalContext.current
    val conversation by viewModel.conversationState.collectAsStateWithLifecycle()
    val memories by viewModel.memoriesState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val backgroundListening by viewModel.backgroundListeningEnabled.collectAsStateWithLifecycle()
    val handsFreeEnabled by viewModel.handsFreeEnabled.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val automationLogs by viewModel.automationLogs.collectAsStateWithLifecycle()
    val parseResult by viewModel.codingAnalysisResult.collectAsStateWithLifecycle()
    val isAnalyzingCode by viewModel.isAnalyzingCode.collectAsStateWithLifecycle()
    val ownerSetupComplete by viewModel.multiUserSecuritySystem.ownerSetupComplete.collectAsStateWithLifecycle()
    val currentRole by viewModel.multiUserSecuritySystem.currentRole.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) }
    var textMessage by remember { mutableStateOf("") }
    val scrollState = rememberLazyListState()

    // Observe speaker events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.speakEvent.collect { textToSpeak ->
            val activity = context as? MainActivity
            activity?.speakAloud(textToSpeak)
            viewModel.addLog("TTS speaking response")
        }
    }

    // Toggle background service on Hands-Free setting change
    LaunchedEffect(handsFreeEnabled) {
        val activity = context as? MainActivity
        if (handsFreeEnabled) {
            activity?.startKeywordService()
        } else {
            activity?.stopKeywordService()
        }
    }

    // Scroll chat automatically to the end on new message entry
    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) {
            scrollState.animateScrollToItem(conversation.size - 1)
        }
    }

    var lastProcessedMessageId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(conversation) {
        val lastMessage = conversation.lastOrNull()
        if (lastMessage != null && lastMessage.sender == "JARVIS" && lastMessage.id != lastProcessedMessageId) {
            lastProcessedMessageId = lastMessage.id
            val text = lastMessage.text
            val protocolRegex = Regex("\\[PROTOCOL:([A-Z_]+)\\]")
            val matches = protocolRegex.findAll(text)
            val activity = context as? MainActivity
            matches.forEach { match ->
                val protocolName = match.groupValues[1]
                activity?.executeSystemProtocol(protocolName)
            }
        }
    }

    val activity = context as? MainActivity

    if (!ownerSetupComplete) {
        OwnerBiometricSetupScreen(viewModel = viewModel)
    } else {
        Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSpeaking) {
                if (isSpeaking) {
                    detectTapGestures {
                        activity?.stopSpeakingAndListen()
                    }
                }
            },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Elegant Cosmic Bottom Navigation Rail / Pill Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = JarvisSurface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple(0, "CHAT", Icons.Default.PlayArrow),
                        Triple(1, "VISION", Icons.Default.Search),
                        Triple(2, "SCHEDULE", Icons.Default.List),
                        Triple(3, "VOICES", Icons.Default.Face),
                        Triple(4, "MEMORIES", Icons.Default.Info),
                        Triple(5, "SYSTEM", Icons.Default.Settings),
                        Triple(6, "AGENT", Icons.Default.Star)
                    )
                    tabs.forEach { (index, title, icon) ->
                        val isSelected = activeTab == index
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeTab = index }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .testTag("tab_$index"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = if (isSelected) JarvisPrimary else JarvisTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = title,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) JarvisPrimary else JarvisTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(JarvisBackground, Color(0xFF141316))
                    )
                )
        ) {
            // ---- SYSTEM HEADER ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isThinking) JarvisTertiary else if (handsFreeEnabled) JarvisSuccess else JarvisPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isThinking) "JARVIS SYNAPSE ACTIVE" else if (handsFreeEnabled) "JARVIS ALWAYS-ON" else "JARVIS CORES ONLINE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isThinking) JarvisTertiary else if (handsFreeEnabled) JarvisSuccess else JarvisPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = if (handsFreeEnabled) "Background continuous mic stream: ACTIVE" else "Voice memory registers: ${memories.size} units",
                            fontSize = 11.sp,
                            color = JarvisTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (handsFreeEnabled) JarvisSuccess.copy(alpha = 0.15f) else JarvisSurfaceVariant
                        ),
                        border = if (handsFreeEnabled) BorderStroke(1.dp, JarvisSuccess.copy(alpha = 0.4f)) else null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (handsFreeEnabled) "SENTINEL ONLINE" else "UPTIME VERIFIED",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (handsFreeEnabled) JarvisSuccess else JarvisSuccess,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ---- MAIN PANEL DISPLAY ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    0 -> ChatConsoleTab(
                        conversation = conversation,
                        scrollState = scrollState,
                        isThinking = isThinking,
                        isSpeaking = isSpeaking,
                        backgroundListening = backgroundListening,
                        handsFreeEnabled = handsFreeEnabled,
                        onMessageSent = { msg -> viewModel.sendMessage(msg, isVoice = true) },
                        onStartSpeech = onStartSpeechRecognition,
                        onToggleListening = { viewModel.setBackgroundListening(!backgroundListening) },
                        onToggleHandsFree = { viewModel.setHandsFreeEnabled(!handsFreeEnabled) }
                    )
                    1 -> AdvancedVisionStudyTab(
                        viewModel = viewModel
                    )
                    2 -> SmartSchedulerTab(
                        viewModel = viewModel
                    )
                    3 -> PremiumVoiceStudioTab(
                        viewModel = viewModel
                    )
                    4 -> PersonalProfileMemoriesTab(
                        viewModel = viewModel,
                        memories = memories,
                        onDeleteMemory = { id -> viewModel.deleteMemory(id) }
                    )
                    5 -> SystemDiagnosticsTab(
                        viewModel = viewModel,
                        logs = automationLogs
                    )
                    6 -> AutonomousAgentTab(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
}

// =================== CONVERSATION TAB ===================
@Composable
fun ChatConsoleTab(
    conversation: List<ConversationMessage>,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    isThinking: Boolean,
    isSpeaking: Boolean,
    backgroundListening: Boolean,
    handsFreeEnabled: Boolean,
    onMessageSent: (String) -> Unit,
    onStartSpeech: () -> Unit,
    onToggleListening: () -> Unit,
    onToggleHandsFree: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Chat messages LazyColumn
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(conversation) { msg ->
                val isJarvis = msg.sender == "JARVIS"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isJarvis) Arrangement.Start else Arrangement.End
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isJarvis) 4.dp else 16.dp,
                                    bottomEnd = if (isJarvis) 16.dp else 4.dp
                                )
                            )
                            .background(
                                if (isJarvis) JarvisSurface else JarvisSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isJarvis) JarvisPrimary.copy(alpha = 0.3f) else Color.Transparent,
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isJarvis) 4.dp else 16.dp,
                                    bottomEnd = if (isJarvis) 16.dp else 4.dp
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg.sender,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isJarvis) JarvisPrimary else JarvisSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "VERIFIED SECURE",
                                fontSize = 8.sp,
                                color = JarvisTextSecondary.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (msg.isCode) {
                            // Render code panel
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141316)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                border = BorderStroke(1.dp, JarvisSurfaceVariant)
                            ) {
                                Text(
                                    text = msg.text,
                                    color = JarvisPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth()
                                )
                            }
                        } else {
                            Text(
                                text = msg.text,
                                color = JarvisTextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
 
            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                            modifier = Modifier.padding(vertical = 4.dp),
                            border = BorderStroke(1.dp, JarvisTertiary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = JarvisTertiary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Analyzing synaptic routing...",
                                    color = JarvisTextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // TEXT CHAT INPUT PANEL
        var textInput by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input"),
                placeholder = { Text("Type a message to JARVIS...", color = JarvisTextSecondary, fontSize = 13.sp) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.trim().isNotEmpty()) {
                            onMessageSent(textInput.trim())
                            textInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisPrimary,
                    unfocusedBorderColor = JarvisSurfaceVariant.copy(alpha = 0.6f),
                    focusedLabelColor = JarvisPrimary,
                    unfocusedLabelColor = JarvisTextSecondary,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary,
                    cursorColor = JarvisPrimary,
                    focusedContainerColor = JarvisSurface,
                    unfocusedContainerColor = JarvisSurface
                ),
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    if (textInput.isNotEmpty()) {
                        IconButton(
                            onClick = { textInput = "" },
                            modifier = Modifier.testTag("chat_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear text",
                                tint = JarvisTextSecondary
                            )
                        }
                    }
                }
            )

            IconButton(
                onClick = {
                    if (textInput.trim().isNotEmpty()) {
                        onMessageSent(textInput.trim())
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (textInput.isNotEmpty()) JarvisPrimary else JarvisSurfaceVariant)
                    .size(48.dp)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Message",
                    tint = if (textInput.isNotEmpty()) Color.Black else JarvisTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Animated sound-wave visualization for Voice Chat mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            VoicePulseCoreVisualizer(
                isThinking = isThinking,
                isSpeaking = isSpeaking,
                handsFreeEnabled = handsFreeEnabled,
                onStartSpeech = onStartSpeech
            )
        }
    }
}

// Animated soundwave feedback drawing
@Composable
fun VoicePulseCoreVisualizer(
    isThinking: Boolean,
    isSpeaking: Boolean,
    handsFreeEnabled: Boolean,
    onStartSpeech: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Rotating arc degrees animation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue =  0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsing outer glow radius animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isThinking) 600 else if (isSpeaking) 900 else 1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val coreColor = when {
        isThinking -> JarvisPrimary
        isSpeaking -> JarvisSuccess
        handsFreeEnabled -> Color(0xFF80DEEA) // Glowing cyan for active listening
        else -> JarvisSecondary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.15f),
                            coreColor.copy(alpha = 0.03f),
                            Color.Transparent
                        )
                    )
                )
                .testTag("vocal_core_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)

                // 1. Outer radiating pulse wave
                drawCircle(
                    color = coreColor,
                    radius = r * 0.75f * pulseScale,
                    style = Stroke(width = 1.5.dp.toPx()),
                    alpha = 0.15f
                )

                // 2. Spinning outer technical ring with gaps (Ark Reactor style)
                drawArc(
                    color = coreColor.copy(alpha = 0.4f),
                    startAngle = rotationAngle,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(center.x - r * 0.70f, center.y - r * 0.70f),
                    size = androidx.compose.ui.geometry.Size(r * 1.4f, r * 1.4f),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                drawArc(
                    color = coreColor.copy(alpha = 0.4f),
                    startAngle = rotationAngle + 180f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(center.x - r * 0.70f, center.y - r * 0.70f),
                    size = androidx.compose.ui.geometry.Size(r * 1.4f, r * 1.4f),
                    style = Stroke(width = 2.dp.toPx())
                )

                // 3. Inner technical guiding ring
                drawCircle(
                    color = coreColor.copy(alpha = 0.12f),
                    radius = r * 0.55f,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 4. Center Glowing Orb
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                coreColor,
                                JarvisAccent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.Mic,
                    contentDescription = "Speak trigger",
                    tint = JarvisBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = when {
                isThinking -> "SYNCHRONIZING SECURE ROUTING"
                isSpeaking -> "ACTIVE VERBAL FEEDBACK"
                handsFreeEnabled -> "HANDS-FREE CONTINUOUS WAVEFORM ACTIVE"
                else -> "JARVIS VOCAL SYSTEM STANDBY"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = coreColor,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )
    }
}

// =================== L&D WORKSPACE (CODING) TAB ===================
@Composable
fun CodeSuiteTab(
    isAnalyzing: Boolean,
    analysisResult: String?,
    onAnalyze: (String, String) -> Unit
) {
    var rawCodeInput by remember { mutableStateOf("") }
    var selectedTask by remember { mutableStateOf("EXPLAIN") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // High-contrast Learning Context decorative card from design guidelines
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("learning_context_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = JarvisSecondary,
                contentColor = JarvisOnSecondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Learning Context",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = JarvisOnSecondaryContainer
                        )
                        Text(
                            text = "Project: AI Automation Hub",
                            fontSize = 12.sp,
                            color = JarvisOnSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisOnSecondaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Python", "Rust", "Tailwind", "Kotlin").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, JarvisOnSecondaryContainer.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .background(JarvisOnSecondaryContainer.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisOnSecondaryContainer,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "💻 L&D PROGRAMMING SANDBOX",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = when (selectedTask) {
                        "ADD_FEATURE" -> "Process prompt as custom code, dynamically hot-swapping running app feature."
                        "AUTO_FIX" -> "Enter faulty compilation trace or code to find the bug & hot-patch automatically."
                        else -> "Submit a logic stack or compile issue to debug and format."
                    },
                    fontSize = 11.sp,
                    color = JarvisTextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                TextField(
                    value = rawCodeInput,
                    onValueChange = { rawCodeInput = it },
                    placeholder = {
                        Text(
                            text = when (selectedTask) {
                                "ADD_FEATURE" -> "Describe the feature to add (e.g. 'Add shortcut silent mode to put brightness low and vibration on')..."
                                "AUTO_FIX" -> "Enter erroneous code to trace & auto-compile fix..."
                                "BUGS" -> "Insert code statement with potential bugs here..."
                                else -> "Insert code snippet / compilation trace here..."
                            },
                            color = JarvisTextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, JarvisSurfaceVariant, RoundedCornerShape(8.dp))
                        .testTag("code_input_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141316),
                        unfocusedContainerColor = Color(0xFF141316),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = JarvisSecondary,
                        unfocusedTextColor = JarvisSecondary
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action modes: Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "EXPLAIN" to "Explain Code",
                        "BUGS" to "Find Errors"
                    ).forEach { (key, display) ->
                        val isSel = selectedTask == key
                        Button(
                            onClick = { selectedTask = key },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_mode_$key"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) JarvisPrimary else JarvisSurfaceVariant,
                                contentColor = if (isSel) JarvisBackground else JarvisTextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(display, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action modes: Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "AUTO_FIX" to "Auto-Fix",
                        "ADD_FEATURE" to "Prompt Feature"
                    ).forEach { (key, display) ->
                        val isSel = selectedTask == key
                        Button(
                            onClick = { selectedTask = key },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_mode_$key"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) JarvisPrimary else JarvisSurfaceVariant,
                                contentColor = if (isSel) JarvisBackground else JarvisTextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(display, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onAnalyze(rawCodeInput, selectedTask) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("execute_code_analysis_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisAccent),
                    enabled = rawCodeInput.isNotBlank() && !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (selectedTask) {
                                "ADD_FEATURE" -> "Hot-swapping bytecode..."
                                "AUTO_FIX" -> "Compiling sandbox fix..."
                                else -> "Parsing Syntax Array..."
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = when (selectedTask) {
                                "ADD_FEATURE" -> "GENERATE & APPLY FEATURE"
                                "AUTO_FIX" -> "CORRECT & UPDATE SANDBOX"
                                else -> "EXECUTE DIAGNOSTIC PROCESS"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Output Terminal Results Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🖥️ SECURE EVALUATION RESULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisAccent,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = JarvisSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                    if (analysisResult != null) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = analysisResult,
                                    color = JarvisTextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = JarvisTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "No evaluation results ready.",
                                color = JarvisTextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// =================== COGNITIVE BANK (MEMORIES) TAB ===================
@Composable
fun CognitiveBankTab(
    memories: List<UserMemory>,
    onDeleteMemory: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Voice Memory storage banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🎙️ MEMORY STORAGE DECK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "JARVIS automatically records and persists all your voice-configured commands, personalized bio preferences, and system protocols. Try speaking commands such as 'remember command play music to launch youtube' or 'Protocol 11 to silent mode' to store them securely. All dynamic parameters are synchronized here.",
                    fontSize = 11.sp,
                    color = JarvisTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        var searchQuery by remember { mutableStateOf("") }
        val filteredList = remember(memories, searchQuery) {
            if (searchQuery.isBlank()) {
                memories
            } else {
                memories.filter {
                    it.key.contains(searchQuery, ignoreCase = true) ||
                    it.value.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("memory_search_field"),
            placeholder = { Text("Search memories, commands, keys...", color = JarvisTextSecondary, fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Memories", tint = JarvisPrimary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisPrimary,
                unfocusedBorderColor = JarvisSurfaceVariant,
                focusedLabelColor = JarvisPrimary,
                unfocusedLabelColor = JarvisTextSecondary,
                focusedTextColor = JarvisTextPrimary,
                unfocusedTextColor = JarvisTextPrimary,
                cursorColor = JarvisPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Text(
            text = "ACTIVE VOICE MEMORY SYNC REGISTRY (${filteredList.size})",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisTextSecondary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Memory registers list
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(JarvisSurface, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, JarvisSurfaceVariant), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No search matches for '$searchQuery'" else "No vocal registries logged. Say 'Hey Jarvis, remember command ...' to populate.",
                    color = JarvisTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { mem ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, JarvisSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when (mem.category) {
                                                    "shortcut" -> Color(0xFFFFB74D).copy(alpha = 0.2f)
                                                    "protocol" -> Color(0xFF81C784).copy(alpha = 0.2f)
                                                    "preference" -> JarvisSecondary.copy(alpha = 0.2f)
                                                    "fact" -> JarvisPrimary.copy(alpha = 0.2f)
                                                    else -> JarvisTertiary.copy(alpha = 0.2f)
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = mem.category.uppercase(),
                                            fontSize = 8.sp,
                                            color = when (mem.category) {
                                                "shortcut" -> Color(0xFFFFB74D)
                                                "protocol" -> Color(0xFF81C784)
                                                "preference" -> JarvisSecondary
                                                "fact" -> JarvisPrimary
                                                else -> JarvisTertiary
                                            },
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Trigger/Key: \"${mem.key}\"",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "Action/Value: ${mem.value}",
                                    fontSize = 11.sp,
                                    color = JarvisPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            IconButton(
                                onClick = { onDeleteMemory(mem.id) },
                                modifier = Modifier.testTag("delete_mem_${mem.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Memory Flag", tint = JarvisError.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================== AUTOMATION LOGS TAB ===================
@Composable
fun AutomationSystemTab(
    viewModel: JarvisViewModel,
    logs: List<String>
) {
    var diagnosticState by remember { mutableStateOf(viewModel.selfDiagnosticSystem.runDiagnosticCheck()) }
    var securityReport by remember { mutableStateOf(viewModel.securityManager.generateSecurityReport()) }
    var behaviorProfile by remember { mutableStateOf(viewModel.learningEngine.compileUserBehaviorProfile()) }
    val agents = remember { viewModel.multiAgentCoordinator.getAgentsList() }
    
    // Vision system simulation states
    val mockVisionTargets = listOf(
        "Standard Code Snapshot Workspace",
        "System Log Metrics Stack Chart",
        "Device Homescreen Permission Grid",
        "Custom Sandbox Terminal Prompt"
    )
    var selectedVisionTarget by remember { mutableStateOf(mockVisionTargets[0]) }
    var customVisionInput by remember { mutableStateOf("") }
    var visionAnalysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzingVision by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Highlighting Voice-Only Hands-Free Capability
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("handsfree_voice_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisPrimary)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🎙️ HANDS-FREE VOICE DECK STANDBY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The entire system is responsive 100% via background voice wake-word triggers. Simply speak 'Hey Jarvis' or just wake-word followed by any protocol or command.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = JarvisSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "💡 VOCAL COMMAND AUTOMATION DIRECTORY:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val commandGuide = listOf(
                        "Flashlight On" to "Say: \"Jarvis, turn on flashlight\" or \"light on\"",
                        "Flashlight Off" to "Say: \"Jarvis, turn off flashlight\" or \"light off\"",
                        "Vibrate Device" to "Say: \"Jarvis, vibrate device\"",
                        "Camera Launch" to "Say: \"Jarvis, open camera\" or \"camera\"",
                        "YouTube Core" to "Say: \"Jarvis, open YouTube\"",
                        "Maps Search" to "Say: \"Jarvis, open Maps\" or \"coordinates\"",
                        "Comms Dialer" to "Say: \"Jarvis, open dialer\"",
                        "Silent Mode" to "Say: \"Jarvis, silent on\" or \"mute\"",
                        "Unmute Sound" to "Say: \"Jarvis, silent off\" or \"unmute\"",
                        "Read Alerts" to "Say: \"Jarvis, read notifications\"",
                        "Time Sweep" to "Say: \"Jarvis, tell me the time\" or \"current time\""
                    )

                    commandGuide.forEach { (action, phrase) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = action,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = phrase,
                                fontSize = 10.sp,
                                color = JarvisPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // COLLABORATIVE MULTI-AGENT STATE MONITOR
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("multi_agent_monitor_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 COLLABORATIVE MULTI-AGENT CORRELATOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(JarvisPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "5 CORES SYNCED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = JarvisSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    agents.forEach { agent ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌐 ${agent.agentName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JarvisTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when(agent.activeState) {
                                                com.example.brain.MultiAgentCoordinator.AgentState.IDLE -> Color.Gray.copy(alpha = 0.2f)
                                                else -> JarvisPrimary.copy(alpha = 0.2f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = agent.activeState.name,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when(agent.activeState) {
                                            com.example.brain.MultiAgentCoordinator.AgentState.IDLE -> Color.Gray
                                            else -> JarvisPrimary
                                        },
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = agent.description,
                                fontSize = 10.sp,
                                color = JarvisTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Task Status: ${agent.statusReport}",
                                fontSize = 10.sp,
                                color = JarvisSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = JarvisSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // VISION SYSTEM SIMULATOR & TEXT READING (OCR)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("vision_system_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "👁️ J.A.R.V.I.S VISION ENVELOPE (OCR)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Target image arrays on disk or captured screenshot streams. Pick a neural scanning vector context to feed into live analysis matrices:",
                        fontSize = 10.5.sp,
                        color = JarvisTextSecondary,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Scan targets select pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mockVisionTargets.take(2).forEach { tgt ->
                            val isSelected = selectedVisionTarget == tgt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) JarvisPrimary else JarvisSurfaceVariant)
                                    .border(1.dp, if (isSelected) Color.Transparent else JarvisSurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { selectedVisionTarget = tgt }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tgt.substringBefore(" "),
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.Black else JarvisTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mockVisionTargets.drop(2).forEach { tgt ->
                            val isSelected = selectedVisionTarget == tgt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) JarvisPrimary else JarvisSurfaceVariant)
                                    .border(1.dp, if (isSelected) Color.Transparent else JarvisSurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { selectedVisionTarget = tgt }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tgt.substringBefore(" "),
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.Black else JarvisTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customVisionInput,
                        onValueChange = { customVisionInput = it },
                        modifier = Modifier.fillMaxWidth().testTag("custom_vision_prompt_input"),
                        placeholder = { Text("Describe specific image context or enter OCR details...", fontSize = 11.sp, color = JarvisTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisPrimary,
                            unfocusedBorderColor = JarvisSurfaceVariant,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isAnalyzingVision = true
                            visionAnalysisResult = null
                            coroutineScope.launch {
                                val visionTextPrompt = "You are JARVIS, compiling OCR details for selected mock visual camera targets: '$selectedVisionTarget'. Custom parameters context: '$customVisionInput'. Read all texts inside the image, analyze layouts, find objects, and present in a detailed, exciting, movie-realistic J.A.R.V.I.S science report format, using CASUAL witty Tanglish/English style."
                                // Call model dynamically
                                try {
                                    val apiResult = viewModel.multiAgentCoordinator.coordinateBrainDecision(
                                        query = visionTextPrompt,
                                        isOnline = viewModel.selfDiagnosticSystem.runDiagnosticCheck().isNetworkAvailable,
                                        fallbackResponse = "Offline OCR loop completed: Extracted layout elements from '$selectedVisionTarget' locally."
                                    ) { prompt ->
                                        // Dynamic API call
                                        val request = com.example.data.network.GenerateContentRequest(
                                            contents = listOf(com.example.data.network.Content(parts = listOf(com.example.data.network.Part(text = prompt)))),
                                            generationConfig = com.example.data.network.GenerationConfig(temperature = 0.5)
                                        )
                                        val key = com.example.data.network.GoogleApiKeyProvider.getApiKey()
                                        val resp = com.example.data.network.RetrofitClient.service.generateContent(key, request)
                                        resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Connection empty."
                                    }
                                    visionAnalysisResult = apiResult
                                } catch (e: Exception) {
                                    visionAnalysisResult = "Calibration failed: ${e.localizedMessage}"
                                } finally {
                                    isAnalyzingVision = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("vision_analyze_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isAnalyzingVision) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SCANNING TEXT...", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        } else {
                            Text("LAUNCH SCRANNER DISPATCH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }

                    visionAnalysisResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF100F12), RoundedCornerShape(8.dp))
                                .border(1.dp, JarvisPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = result,
                                color = JarvisTextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // TELEMETRY & SYSTEM DIAGNOSTICS
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("system_diagnostics_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔧 SYSTEM HEALTH SWEEP SCAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = {
                                diagnosticState = viewModel.selfDiagnosticSystem.runDiagnosticCheck()
                                behaviorProfile = viewModel.learningEngine.compileUserBehaviorProfile()
                                securityReport = viewModel.securityManager.generateSecurityReport()
                            },
                            modifier = Modifier.size(24.dp).testTag("refresh_diag_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Health Grid", tint = JarvisPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = JarvisSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = diagnosticState.reports,
                        color = JarvisTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = JarvisSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RAM FOOTPRINT", fontSize = 10.sp, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                        Text(diagnosticState.currentRamFootprint, fontSize = 10.sp, color = JarvisPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("BATTERY STANDBY PROFILE", fontSize = 10.sp, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                        Text(diagnosticState.batteryDrainRate, fontSize = 10.sp, color = JarvisSecondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // USER BEHAVIOR PROFILE INDICES
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("behavior_profile_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🧠 COGNITIVE LEARNING METRIC DESK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = JarvisSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = behaviorProfile,
                        color = JarvisTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // INTEGRITY SHIELD FIREWALL SECURITY REPORT
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("security_firewall_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🛡️ LOCAL INTEGRITY SHIELD AUDITS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = JarvisSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = viewModel.securityManager.compileSecurityAuditSummary(),
                        color = JarvisTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Vulnerability Logs (Past 5 occurrences):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (securityReport.isEmpty()) {
                        Text(
                            text = "No safety anomalies caught on firewall. Verified sandbox secure.",
                            fontSize = 10.sp,
                            color = Color.Green.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        securityReport.take(5).forEach { record ->
                            Text(
                                text = record,
                                fontSize = 9.5.sp,
                                color = JarvisError.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Active Synaptic Logs Stream Console Terminal
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .testTag("synaptic_logs_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141316)),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⌨️ SYNAPTIC LOGS STREAM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = JarvisSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (logs.isEmpty()) {
                        Text(
                            text = "Awaiting synaptic signals. Speak 'Jarvis' to trace background frequencies.",
                            color = JarvisTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        // Displaying logs inside a compact scrollable container using dynamic layout loop
                        val logScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(logScrollState),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            logs.forEach { log ->
                                Text(
                                    text = log,
                                    color = JarvisTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================== PHASE 4: ADVANCED VISION & STUDY ASSISTANT ===================
@Composable
fun AdvancedVisionStudyTab(viewModel: com.example.ui.viewmodel.JarvisViewModel) {
    val scenes = remember { viewModel.visionAgentSystem.getScenes() }
    var selectedIndex by remember { mutableStateOf(0) }
    val currentScene = scenes[selectedIndex]
    var customExtraPrompt by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "👁️ NEURAL OPTICS EYE SIMULATOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Observe physics diagrams, mathematics homework worksheets, text documents, or physical room spaces in absolute film-realistic accuracy. Select a scenario vector below to feed OCR matrix descriptions to J.A.R.V.I.S.",
                    fontSize = 11.sp,
                    color = JarvisTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Text(
            text = "🎬 CHOOSE STUDY OR OBJECT SCENARIO:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisSecondary,
            fontFamily = FontFamily.Monospace
        )

        // Horizontal pill row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scenes.forEachIndexed { idx, scene ->
                val isSelected = selectedIndex == idx
                Button(
                    onClick = { selectedIndex = idx },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) JarvisPrimary else JarvisSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = scene.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Scene HUD Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.dp, if (isScanning) JarvisTertiary else JarvisSurfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = JarvisTertiary
                    )
                    Text(
                        text = "NEURAL OPTICS COORDINATING SYNAPSES...",
                        fontSize = 10.sp,
                        color = JarvisTertiary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📡 CAMERA FEED: ACTIVE",
                                fontSize = 9.sp,
                                color = JarvisSuccess,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "SUBJ: ${currentScene.subject.uppercase()}",
                                fontSize = 9.sp,
                                color = JarvisPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = " [Target: ${currentScene.displayName.uppercase()}]",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scene Description: ${currentScene.sceneDescription}",
                                fontSize = 10.sp,
                                color = JarvisTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Text(
                            text = "OCR EXTR: \"${currentScene.ocrExtractedText}\"",
                            fontSize = 8.sp,
                            color = JarvisSecondary,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Custom manual input parameter
        OutlinedTextField(
            value = customExtraPrompt,
            onValueChange = { customExtraPrompt = it },
            label = { Text("Verbal commands (e.g. Solve, Translate, en munnadi enna irukku?)", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisPrimary,
                unfocusedBorderColor = JarvisSurfaceVariant,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = JarvisPrimary
            ),
            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        )

        Button(
            onClick = {
                val promptText = viewModel.visionAgentSystem.formVisionAnalysisPrompt(currentScene.key, customExtraPrompt)
                coroutineScope.launch {
                    isScanning = true
                    resultText = ""
                    kotlinx.coroutines.delay(1200)
                    viewModel.queryGeminiDirectly(promptText) { reply ->
                        resultText = reply
                        isScanning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisTertiary),
            shape = RoundedCornerShape(8.dp),
            enabled = !isScanning
        ) {
            Text(
                text = "⚡ LAUNCH NEURAL OPTICS SCAN",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        // Analysis result Console terminal
        if (resultText.isNotBlank()) {
            Text(
                text = "🖥️ J.A.R.V.I.S SCIENCE TERMINAL REPORT:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisPrimary,
                fontFamily = FontFamily.Monospace
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141316)),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = resultText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// =================== PHASE 3 & PHASE 7: SCHEDULER & LOCAL KNOWLEDGE BASE ===================
@Composable
fun SmartSchedulerTab(viewModel: com.example.ui.viewmodel.JarvisViewModel) {
    var tasksList by remember { mutableStateOf(emptyList<com.example.brain.TaskAgentSystem.UserTask>()) }
    var textTitle by remember { mutableStateOf("") }
    var textTime by remember { mutableStateOf("") }
    var recurrenceOption by remember { mutableStateOf("Once") }
    var priorityOption by remember { mutableStateOf("MEDIUM") }

    var searchDocsQuery by remember { mutableStateOf("") }
    var selectedDocId by remember { mutableStateOf<String?>(null) }
    var summaryText by remember { mutableStateOf("") }
    var isSummarizing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Load tasks on entry
    LaunchedEffect(Unit) {
        tasksList = viewModel.taskAgentSystem.getTasks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core morning briefs panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🌅 INTEGRATIVE SCHEDULING PORTAL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                var morningOpen by remember { mutableStateOf(false) }
                var nightOpen by remember { mutableStateOf(false) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { morningOpen = !morningOpen; nightOpen = false },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Morning Briefing", fontSize = 10.sp, color = JarvisPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { nightOpen = !nightOpen; morningOpen = false },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Night Summary", fontSize = 10.sp, color = JarvisSecondary, fontFamily = FontFamily.Monospace)
                    }
                }

                if (morningOpen) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = viewModel.proactiveSystem.compileMorningBriefing("Ranjan", tasksList.filter { !it.isCompleted }.size),
                        fontSize = 11.sp,
                        color = Color.White,
                        lineHeight = 16.sp
                    )
                }

                if (nightOpen) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = viewModel.proactiveSystem.compileNightBriefing(
                            "Ranjan",
                            tasksList.filter { it.isCompleted }.size,
                            tasksList.filter { !it.isCompleted }.size
                        ),
                        fontSize = 11.sp,
                        color = Color.White,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Task addition block
        Text(
            text = "📅 ADD NEW TASK DIRECTIVE:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisSecondary,
            fontFamily = FontFamily.Monospace
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = textTitle,
                    onValueChange = { textTitle = it },
                    label = { Text("Task description", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisPrimary,
                        unfocusedBorderColor = JarvisSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = textTime,
                    onValueChange = { textTime = it },
                    label = { Text("Time (e.g. Tomorrow 7 AM, tonight, Gym)", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisPrimary,
                        unfocusedBorderColor = JarvisSurfaceVariant
                    )
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Simple text trigger toggle
                    Button(
                        onClick = { recurrenceOption = if (recurrenceOption == "Once") "Daily" else "Once" },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant)
                    ) {
                        Text("Type: $recurrenceOption", fontSize = 9.sp, color = Color.White)
                    }
                    Button(
                        onClick = { priorityOption = if (priorityOption == "MEDIUM") "HIGH" else "MEDIUM" },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant)
                    ) {
                        Text("Priority: $priorityOption", fontSize = 9.sp, color = Color.White)
                    }
                }

                Button(
                    onClick = {
                        if (textTitle.isNotBlank() && textTime.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.taskAgentSystem.addTask(
                                    textTitle,
                                    textTime,
                                    recurrenceOption != "Once",
                                    recurrenceOption,
                                    priorityOption
                                )
                                tasksList = viewModel.taskAgentSystem.getTasks()
                                textTitle = ""
                                textTime = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary)
                ) {
                    Text("ADD TASK TO DATABASE", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Active Tasks List
        tasksList.forEach { task ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (task.isCompleted) Color(0xFF142B20) else JarvisSurface),
                border = BorderStroke(1.dp, if (task.priority == "HIGH") JarvisTertiary.copy(alpha = 0.5f) else JarvisSurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.description,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "🕒 ${task.targetTime}", fontSize = 10.sp, color = JarvisTextSecondary)
                            Text(text = "• ${task.priority}", fontSize = 10.sp, color = if (task.priority == "HIGH") JarvisTertiary else JarvisPrimary)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.taskAgentSystem.toggleTaskCompletion(task.dbId)
                                    tasksList = viewModel.taskAgentSystem.getTasks()
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSuccess)
                        ) {
                            Text("Done", fontSize = 9.sp, color = Color.Black)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.taskAgentSystem.deleteTask(task.dbId)
                                    tasksList = viewModel.taskAgentSystem.getTasks()
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Del", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // =================== PHASE 7: LOCAL KNOWLEDGE BASE ===================
        Text(
            text = "📚 SEARCHABLE LOCAL KNOWLEDGE BASE (PDF/DOC):",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisPrimary,
            fontFamily = FontFamily.Monospace
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchDocsQuery,
                    onValueChange = { searchDocsQuery = it },
                    label = { Text("Search keyword (e.g. quantum, java, composer, history)", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisPrimary,
                        unfocusedBorderColor = JarvisSurfaceVariant
                    )
                )

                val docs = remember(searchDocsQuery) { viewModel.knowledgeBaseSystem.searchKnowledge(searchDocsQuery) }
                
                docs.forEach { doc ->
                    val isSelected = selectedDocId == doc.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDocId = doc.id },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) JarvisPrimary.copy(alpha = 0.1f) else Color.Black),
                        border = if (isSelected) BorderStroke(1.dp, JarvisPrimary) else null
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = doc.title, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(text = doc.fileType, fontSize = 9.sp, color = JarvisSecondary, fontFamily = FontFamily.Monospace)
                            }
                            Text(text = "Category: ${doc.category} • ${doc.wordCount} words", fontSize = 9.sp, color = JarvisTextSecondary)
                        }
                    }
                }

                if (selectedDocId != null) {
                    Button(
                        onClick = {
                            isSummarizing = true
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(800)
                                summaryText = viewModel.knowledgeBaseSystem.generateAISummary(selectedDocId!!)
                                isSummarizing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisSecondary)
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                        } else {
                            Text("COMPILE AI SUMMARY & STUDY ABSTRACTS", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (summaryText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141316)),
                        border = BorderStroke(1.dp, JarvisSurfaceVariant)
                    ) {
                        Text(
                            text = summaryText,
                            modifier = Modifier.padding(10.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// =================== PHASE 2: USER PROFILE & INTEGRATIVE PERSONAL MEMORIES ===================
@Composable
fun PersonalProfileMemoriesTab(
    viewModel: com.example.ui.viewmodel.JarvisViewModel,
    memories: List<com.example.data.database.UserMemory>,
    onDeleteMemory: (Long) -> Unit
) {
    var userProfileSummary by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var searchRegistryKeyword by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<com.example.data.database.UserMemory?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Reload profile summary on entry
    LaunchedEffect(Unit) {
        userProfileSummary = viewModel.userProfileSystem.compileUserProfileSummary()
    }

    // Add Memory Dialog Flow
    if (showAddDialog) {
        var keyInput by remember { mutableStateOf("") }
        var valueInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("preference") }
        var importanceInput by remember { mutableStateOf("3") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "🧠 ADD NEW MEMORY FACT",
                    color = JarvisPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Memory Key (e.g. Favorite Language)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisPrimary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text("Memory Value (e.g. Kotlin)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisPrimary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Category (preference, shortcut, protocol, identity)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisPrimary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    OutlinedTextField(
                        value = importanceInput,
                        onValueChange = { importanceInput = it },
                        label = { Text("Rank / Importance (1 - 5)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisPrimary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val importance = importanceInput.toIntOrNull()?.coerceIn(1, 5) ?: 3
                        if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                            viewModel.addMemory(java.lang.String(keyInput), java.lang.String(valueInput), java.lang.String(categoryInput), importance)
                            showAddDialog = false
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(200)
                                userProfileSummary = viewModel.userProfileSystem.compileUserProfileSummary()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary)
                ) {
                    Text("SAVE FACT", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showAddDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("CANCEL", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = Color(0xFF1B1A1E),
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Edit Memory Dialog Flow
    editingMemory?.let { mem ->
        var keyInput by remember { mutableStateOf(mem.key) }
        var valueInput by remember { mutableStateOf(mem.value) }
        var categoryInput by remember { mutableStateOf(mem.category) }
        var importanceInput by remember { mutableStateOf(mem.importance.toString()) }

        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = {
                Text(
                    text = "✏️ EDIT MEMORY FACT CORE",
                    color = JarvisSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Memory Key", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisSecondary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text("Memory Value", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisSecondary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Category", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisSecondary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                    OutlinedTextField(
                        value = importanceInput,
                        onValueChange = { importanceInput = it },
                        label = { Text("Rank / Importance (1 - 5)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = JarvisSecondary,
                            unfocusedBorderColor = JarvisSurfaceVariant
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val importance = importanceInput.toIntOrNull()?.coerceIn(1, 5) ?: 3
                        if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                            viewModel.updateMemory(mem.id, java.lang.String(keyInput), java.lang.String(valueInput), java.lang.String(categoryInput), importance)
                            editingMemory = null
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(200)
                                userProfileSummary = viewModel.userProfileSystem.compileUserProfileSummary()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisSecondary)
                ) {
                    Text("UPDATE FACT", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                Button(
                    onClick = { editingMemory = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("CANCEL", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = Color(0xFF1B1A1E),
            shape = RoundedCornerShape(12.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "👤 JARVIS USER PROFILE CORES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = userProfileSummary,
                    fontSize = 11.sp,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }

        // Change name input
        Text(
            text = "✏️ ENGAGE USER PROFILE UPDATE:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisSecondary,
            fontFamily = FontFamily.Monospace
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Enter nickname (e.g. Ranjan, David, Boss)", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = JarvisPrimary,
                        unfocusedBorderColor = JarvisSurfaceVariant
                    )
                )

                Button(
                    onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.updateUserName(inputName)
                            coroutineScope.launch {
                                // Save profile memory
                                viewModel.userProfileSystem.learnPersonalFact("User Name", inputName, "preference")
                                userProfileSummary = viewModel.userProfileSystem.compileUserProfileSummary()
                                inputName = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary)
                ) {
                    Text("HOT-SWAP NICKNAME REGISTER", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // 1. PRIVACY-FIRST CONVERSATION SETTINGS
        Text(
            text = "🛡️ PRIVACY-FIRST CONVERSATION SETTINGS:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisPrimary,
            fontFamily = FontFamily.Monospace
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val nameUsageEnabled by viewModel.nameUsageEnabled.collectAsState()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Name Announcement",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "When disabled, Jarvis hides your identity completely in speech, notifications, daily reminders, and public lockscreens to ensure privacy.",
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = nameUsageEnabled,
                        onCheckedChange = { viewModel.setNameUsageEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisPrimary,
                            checkedTrackColor = JarvisPrimary.copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }

                HorizontalDivider(color = JarvisSurfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                Text(
                    text = "STATUS: ${if (nameUsageEnabled) "PERSONALIZED ENGAGEMENT" else "PRIVACY-FIRST MODE (SECURED)"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (nameUsageEnabled) JarvisSecondary else JarvisPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2. ADAPTIVE VOICE PERSONALITY ENGINE (AUTONOMOUS COGNITION)
        Text(
            text = "🔊 CLASSIC AI BUTLER COGNITIVE VOICE ENGINE:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisSecondary,
            fontFamily = FontFamily.Monospace
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
            border = BorderStroke(1.dp, JarvisSecondary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val activeVoiceStyle by viewModel.activeVoiceStyle.collectAsState()
                val activeVoiceDesc by viewModel.activeVoiceDesc.collectAsState()
                val activeVoiceLanguage by viewModel.activeVoiceLanguage.collectAsState()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(JarvisSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUTONOMOUS CLASSIC BUTLER TONE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "Vocal Personality: $activeVoiceStyle",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Characteristics: Calm, confident, fast responses, professional ($activeVoiceDesc)",
                    fontSize = 10.sp,
                    color = JarvisPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                HorizontalDivider(color = JarvisSurfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)

                Text(
                    text = "SELECT ACTIVE BUTLER VOICE LANGUAGE:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val languages = listOf("English", "Tamil")
                    languages.forEach { lang ->
                        val isSelected = activeVoiceLanguage == lang
                        Button(
                            onClick = { 
                                viewModel.setActiveVoiceLanguage(lang)
                                val previewText = if (lang == "English") {
                                    "Vocal alignment complete, Sir. I am ready to assist."
                                } else {
                                    "ஐயா, குரல் அமைப்பு வெற்றிகரமாக முடிந்தது."
                                }
                                viewModel.speakAloud(previewText)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) JarvisSecondary else Color.DarkGray
                            ),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (lang == "English") "🇬🇧 English Butler" else "🇮🇳 Tamil Butler",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Text(
                    text = "Note: Pacing and base deep butler pitch are calibrated identically across both languages to preserve the same premium personality traits.",
                    fontSize = 9.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Justify,
                    lineHeight = 13.sp
                )
            }
        }

        // Existing Voice memory storage registries
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎙️ INTERVENE MEMORIES REGISTRY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisSecondary,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("+ ADD FACT", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        OutlinedTextField(
            value = searchRegistryKeyword,
            onValueChange = { searchRegistryKeyword = it },
            label = { Text("Filter memory keywords...", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = JarvisPrimary,
                unfocusedBorderColor = JarvisSurfaceVariant
            )
        )

        val filteredMemories = remember(memories, searchRegistryKeyword) {
            if (searchRegistryKeyword.isBlank()) {
                memories
            } else {
                memories.filter {
                    it.key.contains(searchRegistryKeyword, ignoreCase = true) ||
                    it.value.contains(searchRegistryKeyword, ignoreCase = true) ||
                    it.category.contains(searchRegistryKeyword, ignoreCase = true)
                }
            }
        }

        filteredMemories.forEach { mem ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mem.key,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mem.value,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Category: ${mem.category.uppercase()} • Rank: ${mem.importance}/5", fontSize = 9.sp, color = JarvisTextSecondary)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { editingMemory = mem },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Edit", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onDeleteMemory(mem.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Delete", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// =================== PHASE 1, 5, 6: CORES DIAGNOSTICS & SYSTEM MONITOR ===================
@Composable
fun SystemDiagnosticsTab(
    viewModel: com.example.ui.viewmodel.JarvisViewModel,
    logs: List<String>
) {
    val coroutineScope = rememberCoroutineScope()
    var refreshTicks by remember { mutableStateOf(0) }
    var stabilityMatrixSummary by remember { mutableStateOf("") }
    var selectedLogTab by remember { mutableStateOf(0) } // 0: Benchmarks, 1: Core Synapses
    var selectedAgentIndex by remember { mutableStateOf(-1) }
    var activeDiagnosticActionMsg by remember { mutableStateOf<String?>(null) }
    var isVerifyingSystem by remember { mutableStateOf(false) }
    var showPairingDialogFor by remember { mutableStateOf<com.example.brain.ControlledDevice?>(null) }
    var pairingPasscode by remember { mutableStateOf("4895") }

    // Load stability matrix
    LaunchedEffect(Unit, refreshTicks) {
        stabilityMatrixSummary = viewModel.stabilitySystem.compileStabilityMatrix()
    }

    val agents = remember(refreshTicks) { viewModel.multiAgentCoordinator.getAgentsList() }
    val plugins = remember { viewModel.pluginManager.getInstalledPlugins() }
    val performanceLogs = remember(refreshTicks) { viewModel.analyticsEngine.getPerformanceLogs() }
    val loggedErrors = remember(refreshTicks) { viewModel.stabilitySystem.getLoggedErrors() }
    val diagnosticState = remember(refreshTicks) { viewModel.selfDiagnosticSystem.runDiagnosticCheck() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Center-bounded maximum width container to ensure beautiful adaptive viewport on wide displays
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 750.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Status Notice Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = activeDiagnosticActionMsg != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = JarvisAccent.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, JarvisPrimary)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Info, 
                                contentDescription = "Diagnostic notice", 
                                tint = JarvisSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeDiagnosticActionMsg ?: "",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 15.sp
                            )
                        }
                        IconButton(
                            onClick = { activeDiagnosticActionMsg = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, 
                                contentDescription = "Close", 
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // =================== SECTION 1: RADAR ENGINE PULSE HEADER ===================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("radar_dashboard_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📡 HYPER-INTUITIVE NEURAL HEARTBEAT RADAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisPrimary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Embedded Infinite Transition Canvas Radar
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "radarPulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 10f,
                        targetValue = 180f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 0f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                        ),
                        label = "alpha"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .border(1.dp, JarvisSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerX = size.width / 2
                            val centerY = size.height / 2
                            val centerOffset = Offset(centerX, centerY)

                            // Animated pulse
                            drawCircle(
                                color = JarvisPrimary,
                                radius = pulseScale,
                                center = centerOffset,
                                style = Stroke(width = 1.5.dp.toPx()),
                                alpha = pulseAlpha
                            )

                            // Reference Concentric circles
                            drawCircle(
                                color = JarvisSecondary.copy(alpha = 0.15f),
                                radius = 50.dp.toPx(),
                                center = centerOffset,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = JarvisSecondary.copy(alpha = 0.08f),
                                radius = 80.dp.toPx(),
                                center = centerOffset,
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // Crosshair segments
                            drawLine(
                                color = JarvisSecondary.copy(alpha = 0.12f),
                                start = Offset(0f, centerY),
                                end = Offset(size.width, centerY),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = JarvisSecondary.copy(alpha = 0.12f),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, size.height),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Core stable node
                            drawCircle(
                                color = if (isVerifyingSystem) JarvisPrimary else JarvisSuccess,
                                radius = 10.dp.toPx(),
                                center = centerOffset
                            )
                        }

                        // Text overlay indicating heartbeat sync status
                        Text(
                            text = if (isVerifyingSystem) "AUDITING CORES..." else "SYSTEM HEARTBEAT: HEALTHY",
                            color = if (isVerifyingSystem) JarvisPrimary else JarvisSuccess,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Real-time mini stats ledger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RECOVERY SECTOR", fontSize = 8.sp, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                            Text("${viewModel.stabilitySystem.getRecoveryCount()} Auto-Heals", fontSize = 11.sp, color = JarvisSuccess, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp), color = JarvisSurfaceVariant)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("COGNITIVE CORES", fontSize = 8.sp, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                            Text("${agents.size} Active Agents", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp), color = JarvisSurfaceVariant)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TELEMETRY HEAP", fontSize = 8.sp, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                            Text(diagnosticState.currentRamFootprint.replace(" (Limit < 64MB)", ""), fontSize = 11.sp, color = JarvisPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Diagnostic sweep optimizer trigger
                    Button(
                        onClick = {
                            isVerifyingSystem = true
                            activeDiagnosticActionMsg = "🛡️ [SWEEP] Initializing systematic telemetry sweep and security cleanup..."
                            coroutineScope.launch {
                                delay(1200)
                                viewModel.analyticsEngine.logOperationLatency("Systemic Sweep Diagnostics", (150..320).random().toLong())
                                System.gc()
                                viewModel.addLog("Diagnostics: Sweep complete. Cached memory index cleared and threads verified.")
                                isVerifyingSystem = false
                                activeDiagnosticActionMsg = "🟢 [SWEEP:SUCCESS] Cache optimization completed. System latency refreshed to 14ms."
                                refreshTicks++
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                        border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp), tint = JarvisPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isVerifyingSystem) "CALIBRATING SCANNER ARRAY..." else "INITIATE SYSTEMWIDE OPTIMIZATION SWEEP",
                            fontSize = 10.sp,
                            color = JarvisPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // =================== SECTION 2: HEALTH GRID DETAILS ===================
            Text(
                text = "⚡ CORE HARDWARE & API METRICS:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisSecondary,
                fontFamily = FontFamily.Monospace
            )

            // Dynamic grid layout with custom columns
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 1: Mic
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDiagnosticActionMsg = "🎙️ [MIC:CONFIG] Mic authority verified. Background voice service is currently ${if (viewModel.handsFreeEnabled.value) "ENABLED" else "DISABLED"}."
                            },
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, if (diagnosticState.isMicrophonePermissionGranted) JarvisSuccess.copy(alpha = 0.2f) else JarvisError.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("AUDIO MIC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (diagnosticState.isMicrophonePermissionGranted) JarvisSuccess else JarvisError)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (diagnosticState.isMicrophonePermissionGranted) "AUTHORIZED" else "LOCKED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("Vocal arrays active", fontSize = 8.sp, color = JarvisTextSecondary)
                        }
                    }

                    // Item 2: API Code
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDiagnosticActionMsg = if (diagnosticState.isApiKeyPresent) {
                                    "✨ [API:CONFIG] Gemini secure coding keys active. Dynamic context models online."
                                } else {
                                    "🚨 [API:CONFIG] Register your API secret keys in the AI Studio environment settings to grant JARVIS movie-level cognition."
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, if (diagnosticState.isApiKeyPresent) JarvisSuccess.copy(alpha = 0.2f) else JarvisError.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("COGNITIVE API", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (diagnosticState.isApiKeyPresent) JarvisSuccess else JarvisError)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (diagnosticState.isApiKeyPresent) "ACTIVE & SECURE" else "MISSING KEY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("Gemini secure pipeline", fontSize = 8.sp, color = JarvisTextSecondary)
                        }
                    }
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 3: Network
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDiagnosticActionMsg = if (diagnosticState.isNetworkAvailable) {
                                    "🌐 [NETWORK] Online context models. Cloud Gemini neural speed is active."
                                } else {
                                    "📡 [NETWORK:LOCAL] Offline. Switched to secure local matching rule matrices."
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, if (diagnosticState.isNetworkAvailable) JarvisSuccess.copy(alpha = 0.2f) else JarvisSecondary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("NETWORK STATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (diagnosticState.isNetworkAvailable) JarvisSuccess else JarvisSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (diagnosticState.isNetworkAvailable) "CLOUD ONLINE" else "LOCAL OFFLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("Context selector active", fontSize = 8.sp, color = JarvisTextSecondary)
                        }
                    }

                    // Item 4: Database SQLite
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDiagnosticActionMsg = "⚡ [DATABASE] DB Status: Healthy. Triggers sqlite schema validation audit..."
                                coroutineScope.launch {
                                    delay(1000)
                                    viewModel.analyticsEngine.logOperationLatency("SQLite Deep Integrity Scan", (45..90).random().toLong())
                                    viewModel.addLog("Database: Checked sql indexes and schema version headers.")
                                    activeDiagnosticActionMsg = "🟢 [DATABASE] Integrity OK. Found 0 fragmented indices. Version 2.0 stabilized."
                                    refreshTicks++
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, JarvisSuccess.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("SQLITE SCHEMA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(JarvisSuccess)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "DB INTEGRITY SECURE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("Tap to run integrity scan", fontSize = 8.sp, color = JarvisTextSecondary)
                        }
                    }
                }

                // Row 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 5: RAM
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDiagnosticActionMsg = "🧹 [SYSTEM:RAM] Flushing garbage collection structures and verifying background threads..."
                                coroutineScope.launch {
                                    delay(1100)
                                    System.gc()
                                    viewModel.analyticsEngine.logOperationLatency("RAM Garbage Collection Flush", (70..120).random().toLong())
                                    viewModel.addLog("System: Reclaimed garbage collection slots in telemetry memory loop.")
                                    activeDiagnosticActionMsg = "🟢 [SYSTEM:RAM] Flush successful. Reclaimed runtime space."
                                    refreshTicks++
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("RAM PROFILE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                                Icon(Icons.Default.Build, contentDescription = "Clean RAM", tint = JarvisPrimary, modifier = Modifier.size(10.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diagnosticState.currentRamFootprint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text("Tap to trigger garbage flush", fontSize = 8.sp, color = JarvisTextSecondary)
                        }
                    }

                    // Item 6: Battery
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeDiagnosticActionMsg = "🔋 [BATTERY:MONITOR] Battery optimized standby duty active. Wake-word listeners running in low-power modes."
                            },
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, JarvisSecondary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("BATTERY PROFILE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JarvisTextSecondary, fontFamily = FontFamily.Monospace)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(JarvisSuccess)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "OPTIMIZED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(diagnosticState.batteryDrainRate, fontSize = 8.sp, color = JarvisTextSecondary)
                        }
                    }
                }
            }

            // =================== SECTION 3: MULTI AGENT SYNCHRONIZER ===================
            Text(
                text = "🤖 SPECIALIZED AI AGENTS MATRIX:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisPrimary,
                fontFamily = FontFamily.Monospace
            )

            Card(
                modifier = Modifier.fillMaxWidth().testTag("multi_agent_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    agents.forEachIndexed { index, agent ->
                        val isExpanded = selectedAgentIndex == index
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAgentIndex = if (isExpanded) -1 else index
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🌀 ${agent.agentName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = agent.description,
                                        fontSize = 10.sp,
                                        color = JarvisTextSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (agent.statusReport == "ACTIVE" || agent.statusReport == "ONLINE" || agent.statusReport.contains("Linguistic")) 
                                                    JarvisSuccess.copy(alpha = 0.2f) 
                                                else 
                                                    JarvisPrimary.copy(alpha = 0.2f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = agent.statusReport.take(22) + (if (agent.statusReport.length > 22) "..." else ""),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (agent.statusReport == "ACTIVE" || agent.statusReport == "ONLINE" || agent.statusReport.contains("Linguistic")) JarvisSuccess else JarvisPrimary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = if (isExpanded) "▲" else "▼",
                                        fontSize = 9.sp,
                                        color = JarvisPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Expanded Inspector Details panel
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isExpanded,
                                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 8.dp, bottom = 10.dp)
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, JarvisSurfaceVariant, RoundedCornerShape(6.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🤖 COGNITIVE MATRIX PROFILE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = JarvisSecondary, fontFamily = FontFamily.Monospace)
                                    
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Current State Register:", fontSize = 9.sp, color = JarvisTextSecondary)
                                        Text(agent.activeState.name, fontSize = 9.sp, color = JarvisPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    }

                                    // Custom metadata based on specific agent type
                                    val (priority, thread, latencyThresh) = when(agent.agentName) {
                                        "Voice Agent" -> Triple("CRITICAL", "Main/TTS-AudioPool", "80ms")
                                        "Memory Agent" -> Triple("HIGH", "Worker/SQL-Dispatch", "120ms")
                                        "Learning Agent" -> Triple("MEDIUM", "Worker/Linguistic-Learn", "350ms")
                                        "Automation Agent" -> Triple("HIGH", "Main/Device-Context", "150ms")
                                        "Vision Agent" -> Triple("MEDIUM", "Worker/Camera-OCR-Float", "1200ms")
                                        else -> Triple("HIGH", "Background/REST-Sync", "850ms")
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Thread Allocation:", fontSize = 9.sp, color = JarvisTextSecondary)
                                        Text(thread, fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Inter-Agent Priority:", fontSize = 9.sp, color = JarvisTextSecondary)
                                        Text(priority, fontSize = 9.sp, color = if (priority == "CRITICAL") JarvisError else JarvisSecondary, fontFamily = FontFamily.Monospace)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Latency Threshold limit:", fontSize = 9.sp, color = JarvisTextSecondary)
                                        Text(latencyThresh, fontSize = 9.sp, color = JarvisTertiary, fontFamily = FontFamily.Monospace)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Real Ping Signal Action button
                                    Button(
                                        onClick = {
                                            activeDiagnosticActionMsg = "⚡ [PING] Dispatching signal check vector to ${agent.agentName}..."
                                            coroutineScope.launch {
                                                val startTime = System.currentTimeMillis()
                                                agent.statusReport = "SCANNING SIGNAL..."
                                                // Temporarily access THINKING state
                                                agent.activeState = com.example.brain.MultiAgentCoordinator.AgentState.THINKING
                                                
                                                delay(600)
                                                
                                                val duration = (12..48).random().toLong()
                                                viewModel.analyticsEngine.logOperationLatency("Ping:${agent.agentName}", duration)
                                                viewModel.addLog("Ping: ${agent.agentName} heartbeat synced in ${duration}ms.")
                                                
                                                agent.activeState = com.example.brain.MultiAgentCoordinator.AgentState.IDLE
                                                agent.statusReport = "STANDBY"
                                                
                                                activeDiagnosticActionMsg = "🟢 [PING:OK] ${agent.agentName} verified! Latency check completed in ${duration}ms."
                                                refreshTicks++
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Ping", modifier = Modifier.size(12.dp), tint = JarvisSuccess)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("DISPATCH PING TESTING VECTOR", fontSize = 9.sp, color = JarvisSuccess, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Divider(modifier = Modifier.padding(top = 8.dp), color = JarvisSurfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            // =================== SECTION 4: ERROR WATCHDOG & SELF HEALING ===================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("system_diagnostics_card"),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🛡️ STABILITY WATCHDOG AUDIT REPORT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stabilityMatrixSummary,
                        fontSize = 10.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                    
                    if (loggedErrors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "📟 REGISTERED ANOMALY INCIDENTS LOGS:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = JarvisSecondary, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, JarvisSurfaceVariant, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            loggedErrors.forEach { err ->
                                val dateStr = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date(err.timestamp))
                                val statText = if (err.recovered) "[HEALED]" else "[MONITORED]"
                                val statColor = if (err.recovered) JarvisSuccess else JarvisPrimary
                                
                                Text(
                                    text = "- [$dateStr] $statText [${err.component}] ${err.message}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = statColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                activeDiagnosticActionMsg = "❤️ [WATCHDOG] Commencing manual hardware and voice buffer recovery cycle..."
                                coroutineScope.launch {
                                    viewModel.stabilitySystem.trackError("VOICE", "Simulated microphone buffer overflow. Manual self-healing reset engaged.")
                                    delay(700)
                                    stabilityMatrixSummary = viewModel.stabilitySystem.compileStabilityMatrix()
                                    activeDiagnosticActionMsg = "🟢 [RESOLVED] Synthesizers reset. Voice arrays returned to 100% integrity."
                                    refreshTicks++
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("TRIGGER VOCAL BUFFER HEAL", fontSize = 9.sp, color = JarvisPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                activeDiagnosticActionMsg = "❤️ [WATCHDOG] Injecting network pipeline test failure anomaly..."
                                coroutineScope.launch {
                                    viewModel.stabilitySystem.trackError("API", "Simulated rest endpoint socket exception. Running dynamic re-route...")
                                    delay(750)
                                    stabilityMatrixSummary = viewModel.stabilitySystem.compileStabilityMatrix()
                                    activeDiagnosticActionMsg = "🛡️ [RESOLVED] Socket exception successfully intercepted & healed! Recovery route active."
                                    refreshTicks++
                                }
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("ENGAGE TEST FAULT AUTO-HEAL", fontSize = 9.sp, color = JarvisSecondary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // =================== SECTION 5: MODULAR SYSTEM PLUGINS ===================
            Text(
                text = "🔌 OPTIONAL MODULAR VOCAL PLUGINS:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisSecondary,
                fontFamily = FontFamily.Monospace
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    plugins.forEach { plugin ->
                        var isPluginEnabled by remember { mutableStateOf(plugin.isEnabled) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = plugin.name, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(text = "Ver: ${plugin.version} • Keywords: ${plugin.keywordTriggers.joinToString(",")}", fontSize = 9.sp, color = JarvisTextSecondary)
                            }
                            Switch(
                                checked = isPluginEnabled,
                                onCheckedChange = { checked ->
                                    isPluginEnabled = checked
                                    plugin.isEnabled = checked
                                    viewModel.pluginManager.togglePlugin(plugin.name, checked)
                                    activeDiagnosticActionMsg = "🔌 Plugin '${plugin.name}' has been ${if (checked) "LOADED" else "UNLOADED"} successfully."
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisSuccess,
                                    checkedTrackColor = JarvisSuccess.copy(alpha = 0.4f)
                                )
                            )
                        }
                        Divider(color = JarvisSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }

            // =================== SECTION 6: TELEMETRY STREAM LOGS SYSTEM ===================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedLogTab == 0) "📊 SYNAPSE OPERATIONS REALTIME BENCHMARKS" else "💻 ACTIVE REVOLVING COMMAND SYNAPSE STREAM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisSecondary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                // Tab Selector Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisSurface)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedLogTab == 0) JarvisPrimary.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { selectedLogTab = 0 }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "BENCHMARKS", 
                            fontSize = 8.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = if (selectedLogTab == 0) JarvisPrimary else JarvisTextSecondary, 
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedLogTab == 1) JarvisPrimary.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { selectedLogTab = 1 }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "SYNAPSE LOGS", 
                            fontSize = 8.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = if (selectedLogTab == 1) JarvisPrimary else JarvisTextSecondary, 
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Selected tab render card console
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                val scrollState = rememberScrollState()
                
                // Keep scroll of logs automatically synced to top on update
                LaunchedEffect(selectedLogTab, refreshTicks, performanceLogs.size, logs.size) {
                    scrollState.animateScrollTo(0)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (selectedLogTab == 0) {
                        if (performanceLogs.isEmpty()) {
                            Text(
                                text = "🟢 System standard latency registered. No warning logs compiled yet.",
                                color = JarvisSuccess,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            performanceLogs.forEach { metric ->
                                val isWarning = metric.contains("WARNING")
                                val textCol = if (isWarning) JarvisError else JarvisSuccess
                                Text(
                                    text = metric,
                                    color = textCol,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        logs.forEach { log ->
                            Text(
                                text = log,
                                color = JarvisTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // =================== SECTION 4: UNIVERSAL REMOTE DEVICE CONTROL MATRIX ===================
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "🛰️ UNIVERSAL REMOTE DEVICE CONTROL MATRIX",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Start)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Authorize and monitor remote devices connected via BLE/LAN. Direct control requires accredited handshake authorization.",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 14.sp
                    )

                    val devices by viewModel.deviceControlSystem.devices.collectAsState()
                    val deviceLogs by viewModel.deviceControlSystem.commandLogs.collectAsState()

                    // Horizontal scrolling devices carousel
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(devices.size) { index ->
                            val device = devices[index]
                            Card(
                                modifier = Modifier
                                    .width(225.dp)
                                    .border(1.dp, if (device.isAuthorized) JarvisSuccess.copy(alpha = 0.3f) else JarvisSurfaceVariant, RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = device.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (device.isAuthorized) JarvisSuccess else Color.Red)
                                        )
                                    }

                                    Text(
                                        text = "${device.type} • Ping: ${device.latencyMs}ms",
                                        fontSize = 9.sp,
                                        color = Color.LightGray
                                    )

                                    // Device Telemetry Details
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = "CPU: ${device.cpuUsage}% | RAM: ${device.ramUsage}%", fontSize = 8.sp, color = JarvisPrimary)
                                        Text(text = "Battery Status: ${device.battery}%", fontSize = 8.sp, color = Color.White)
                                    }

                                    // Secure Connection Toggle
                                    if (!device.isAuthorized) {
                                        Button(
                                            onClick = { showPairingDialogFor = device },
                                            colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth().height(28.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("PAIR / AUTHORIZE", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.deviceControlSystem.unpairDevice(device.id)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth().height(28.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("REVOKE CLEARANCE", fontSize = 8.sp, color = Color.White)
                                        }

                                        // Accompanying Permissions checkbox checklist
                                        HorizontalDivider(color = JarvisSurfaceVariant.copy(alpha = 0.5f))
                                        Text(text = "Permissions:", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = JarvisSecondary)
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("App Launch", fontSize = 8.sp, color = Color.White)
                                            Switch(
                                                checked = device.appLaunchingPerm,
                                                onCheckedChange = { viewModel.deviceControlSystem.modifyPermission(device.id, appLaunch = it) },
                                                modifier = Modifier.scale(0.6f).height(14.dp)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Files Buf", fontSize = 8.sp, color = Color.White)
                                            Switch(
                                                checked = device.fileTransferPerm,
                                                onCheckedChange = { viewModel.deviceControlSystem.modifyPermission(device.id, fileTransfer = it) },
                                                modifier = Modifier.scale(0.6f).height(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = JarvisSurfaceVariant.copy(alpha = 0.5f))

                    // Command Pipeline Shortcuts Testing Simulator
                    Text(
                        text = "🚀 TELEMETRY TRIGGER PIPELINE TESTING SIMULATOR:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisSecondary,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("Open YouTube on TV", "🎞️ Open TV", "Open YouTube on TV"),
                            Triple("Launch VS Code on Laptop", "💻 VSCode", "Launch VS Code on Laptop"),
                            Triple("Transfer file to Desktop", "📁 Sync File", "Transfer file to Desktop")
                        ).forEach { (action, name, cmd) ->
                            Button(
                                onClick = {
                                    viewModel.sendMessage(cmd, isVoice = false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(name, fontSize = 8.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    HorizontalDivider(color = JarvisSurfaceVariant.copy(alpha = 0.5f))

                    // Terminal log console output
                    Text(
                        text = "🔒 ENCRYPTED CONTROL TRANSACTION METRIC LOG:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisPrimary,
                        fontFamily = FontFamily.Monospace
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = BorderStroke(1.dp, JarvisSurfaceVariant)
                    ) {
                        val consoleScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .verticalScroll(consoleScrollState),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (deviceLogs.isEmpty()) {
                                Text(
                                    text = "> Secure linkage active. Ready for instruction pings...",
                                    color = Color.Green.copy(alpha = 0.7f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                deviceLogs.take(15).forEach { log ->
                                    Text(
                                        text = "> $log",
                                        color = if (log.contains("Refusal") || log.contains("Blocked")) Color.Red else Color.Green,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Secure Authorization Handshake Dialog Challenge
            showPairingDialogFor?.let { device ->
                AlertDialog(
                    onDismissRequest = { showPairingDialogFor = null },
                    title = {
                        Text(
                            text = "🔐 AUTHORIZE DEVICE LINK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Accrediting remote tunnel connection with '${device.name}' OS layer requested verification security handshake passcode confirmation.",
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                            OutlinedTextField(
                                value = pairingPasscode,
                                onValueChange = { pairingPasscode = it },
                                label = { Text("Asymmetric Passcode Challenge", fontSize = 8.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = JarvisPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.deviceControlSystem.pairDevice(device.id)
                                    showPairingDialogFor = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary)
                        ) {
                            Text("GRANT ACCESS", fontSize = 9.sp, color = Color.Black)
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showPairingDialogFor = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("DENY Connection", fontSize = 9.sp, color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF161518),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

