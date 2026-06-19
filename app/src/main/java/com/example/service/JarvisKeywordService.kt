package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.room.Room
import com.example.BuildConfig
import com.example.data.database.AppDatabase
import com.example.data.database.ConversationMessage
import com.example.data.database.JarvisRepository
import com.example.data.database.UserMemory
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.GenerationConfig
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import com.example.service.JarvisCommandProcessor
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class JarvisKeywordService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val CHANNEL_ID = "jarvis_keyword_service_channel"
        private const val NOTIFICATION_ID = 2691
        var isServiceRunning = false
            private set
        var instance: JarvisKeywordService? = null
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isCurrentlySpeaking = false
    private var isThinking = false
    private var isBypassWakeWord = false
    private var porcupineManager: PorcupineManager? = null
    private var isSpeechRecognizerActive = false
    private var isWakeLockAcquired = false

    // Advanced Conversation Control Interruption State
    private var isInterrupted = false
    private var interruptedText = ""
    private var lastActiveResponseText = ""

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "jarvis_database"
        ).fallbackToDestructiveMigration().build()
    }

    private val repository by lazy { JarvisRepository(database.jarvisDao()) }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        instance = this
        Log.d("JARVIS_SERVICE", "Always-on keyword detection engine initializing.")
        createNotificationChannel()
        
        // Start foreground immediately in onCreate before any hardware/wake lock initialization
        val notification = getNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        initTTS()
        initializeSpeechRecognizer()
        
        serviceScope.launch {
            JarvisCommandProcessor.initialize(repository)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("JARVIS_SERVICE", "Service started command.")
        
        val directListen = intent?.getBooleanExtra("DIRECT_LISTEN", false) ?: false
        startSpeechRecognizerListening(bypassWakeWord = directListen)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Main Matrix Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Coordinates continuous voice diagnostics & command processing blocks."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("JARVIS Continuous Listening Engaged")
            .setContentText("Listening for 'Jarvis' in background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        serviceScope.launch(Dispatchers.Default) {
            delay(1000) // Hold briefly to allow foreground service promotion to settle
            try {
                val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                val tag = "${applicationContext.packageName}:JARVIS::WakeLock"
                synchronized(this@JarvisKeywordService) {
                    if (wakeLock == null) {
                        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                            setReferenceCounted(false)
                        }
                    }
                    if (!isWakeLockAcquired) {
                        wakeLock?.acquire(5 * 60 * 1000L) // Safe limit to prevent severe battery leaks while satisfying AppOps
                        isWakeLockAcquired = true
                        Log.d("JARVIS_SERVICE", "Partial WakeLock active with tag $tag.")
                    }
                }
            } catch (e: Exception) {
                Log.e("JARVIS_SERVICE", "Failed to acquire wake lock", e)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            synchronized(this) {
                if (isWakeLockAcquired && wakeLock != null) {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                    }
                    isWakeLockAcquired = false
                    Log.d("JARVIS_SERVICE", "WakeLock released successfully.")
                }
            }
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Failed to release system wake lock.", e)
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("JARVIS_SERVICE", "TTS Voice Language arrays missing or unsupported.")
            } else {
                isTtsInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isCurrentlySpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isCurrentlySpeaking = false
                        restartListeningIfHandsFree()
                    }

                    override fun onError(utteranceId: String?) {
                        isCurrentlySpeaking = false
                        restartListeningIfHandsFree()
                    }
                })
            }
        } else {
            Log.e("JARVIS_SERVICE", "TTS Voice Synthesizer failed status: $status")
        }
    }

    private fun initializeSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this@JarvisKeywordService).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        Log.d("JARVIS_SERVICE", "Recognizer core synchronized.")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("JARVIS_SERVICE", "Acoustic activity detected.")
                        if (tts?.isSpeaking == true) {
                            tts?.stop()
                            isCurrentlySpeaking = false
                            addLogToDatabase("Voice Override: Output stream disengaged by active user query.")
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d("JARVIS_SERVICE", "Acoustic input sequence matched.")
                    }

                    override fun onError(error: Int) {
                        isSpeechRecognizerActive = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client-side context error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions block active"
                            SpeechRecognizer.ERROR_NETWORK -> "Network carrier timeout"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network packet loss"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No vocal commands detected"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy register lock"
                            SpeechRecognizer.ERROR_SERVER -> "Cloud voice service failure"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech frame timeout"
                            else -> "Hardware diagnostic state standard anomaly ($error)"
                        }
                        Log.w("JARVIS_SERVICE", "Acoustic capture core warning: $message")

                        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                            try {
                                speechRecognizer?.destroy()
                                speechRecognizer = null
                            } catch (e: Exception) {}
                        }

                        restartListeningIfHandsFree()
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        isSpeechRecognizerActive = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull() ?: ""
                        if (spokenText.isNotBlank()) {
                            handleSpokenText(spokenText, isBypassWakeWord)
                        } else {
                            restartListeningIfHandsFree()
                        }
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) {}

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Speech recognizer instantiation failed.", e)
            isSpeechRecognizerActive = false
        }
    }

    private suspend fun getPicovoiceAccessKey(): String {
        val memories = repository.allMemories.first()
        val dbKey = memories.firstOrNull { 
            it.key.lowercase(Locale.US) == "picovoice_access_key" || 
            it.key.lowercase(Locale.US) == "porcupine_access_key" 
        }?.value
        if (!dbKey.isNullOrBlank()) {
            return dbKey.trim()
        }
        
        val buildKey = try {
            val clazz = Class.forName("com.example.BuildConfig")
            val field = clazz.getField("PICOVOICE_ACCESS_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
        if (buildKey.isNotBlank() && buildKey != "MY_PICOVOICE_ACCESS_KEY" && buildKey != "null") {
            return buildKey.trim()
        }
        return ""
    }

    private fun initializePorcupine() {
        serviceScope.launch {
            val key = getPicovoiceAccessKey()
            if (key.isBlank()) {
                addLogToDatabase("Porcupine Config: No Picovoice Access Key found. Falling back to Google SpeechRecognizer engine.")
                return@launch
            }

            try {
                val callback = object : ai.picovoice.porcupine.PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        if (keywordIndex == 0) {
                            addLogToDatabase("Porcupine Keyword Detected: JARVIS!")
                            serviceScope.launch(Dispatchers.Main) {
                                stopPorcupineQuietly()
                                executeSystemProtocol("VIBRATE_DEVICE")
                                val activeName = getPreferredName()
                                speakResponse("Yes, Bro? What can I do for you, $activeName?")
                                delay(1000)
                                startSpeechToTextDirectly()
                            }
                        }
                    }
                }

                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey(key)
                    .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                    .build(applicationContext, callback)
                addLogToDatabase("Porcupine wake-word detection engine active: monitoring for 'Jarvis'")
            } catch (e: Exception) {
                addLogToDatabase("Porcupine Initialization failed: ${e.localizedMessage}. Falling back to default voice listener.")
                Log.e("JARVIS_SERVICE", "Porcupine init error", e)
                porcupineManager = null
            }
        }
    }

    private fun startPorcupineListening() {
        try {
            porcupineManager?.start()
            addLogToDatabase("Porcupine low-power voice monitoring ACTIVE.")
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Porcupine start error", e)
        }
    }

    private fun stopPorcupineQuietly() {
        try {
            porcupineManager?.stop()
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Porcupine stop error", e)
        }
    }

    private fun startSpeechRecognizerListening(bypassWakeWord: Boolean = false) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("JARVIS_SERVICE", "RECORD_AUDIO permission is not granted yet; deferring speech listener engagement.")
            return
        }
        isBypassWakeWord = bypassWakeWord
        serviceScope.launch {
            try {
                if (isSpeechRecognizerActive) {
                    speechRecognizer?.cancel()
                    isSpeechRecognizerActive = false
                }
            } catch (e: Exception) {}

            if (!bypassWakeWord) {
                if (porcupineManager == null) {
                    val key = getPicovoiceAccessKey()
                    if (key.isNotBlank()) {
                        initializePorcupine()
                        delay(600)
                    }
                }

                if (porcupineManager != null) {
                    addLogToDatabase("Engaging Porcupine wake-word detector.")
                    startPorcupineListening()
                    return@launch
                }
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
                stopPorcupineQuietly()
                speechRecognizer?.startListening(intent)
                isSpeechRecognizerActive = true
                Log.d("JARVIS_SERVICE", "Standard speech recognizer started. (BypassWakeWord=$bypassWakeWord)")
            } catch (e: Exception) {
                isSpeechRecognizerActive = false
                Log.e("JARVIS_SERVICE", "Start listening threw hardware error", e)
                restartListeningIfHandsFree()
            }
        }
    }

    private fun restartListeningIfHandsFree() {
        if (!isCurrentlySpeaking && !isThinking) {
            serviceScope.launch {
                delay(850)
                if (!isCurrentlySpeaking && !isThinking) {
                    startSpeechRecognizerListening(bypassWakeWord = false)
                }
            }
        }
    }

    fun startSpeechToTextDirectly() {
        runOnUiThread {
            if (tts?.isSpeaking == true) {
                tts?.stop()
                isCurrentlySpeaking = false
            }
            startSpeechRecognizerListening(bypassWakeWord = true)
        }
    }

    private fun runOnUiThread(action: Runnable) {
        serviceScope.launch {
            action.run()
        }
    }

    private fun speakResponse(text: String) {
        if (!isTtsInitialized || tts == null) return

        lastActiveResponseText = text

        serviceScope.launch {
            try {
                if (isSpeechRecognizerActive) {
                    speechRecognizer?.cancel()
                    isSpeechRecognizerActive = false
                }
            } catch (e: Exception) {}

            isCurrentlySpeaking = true
            
            val memories = withContext(Dispatchers.IO) { repository.allMemories.first() }
            val pitchStr = memories.firstOrNull { it.key == "voice_pitch" }?.value
            val rateStr = memories.firstOrNull { it.key == "voice_rate" }?.value

            val pitch = pitchStr?.toFloatOrNull() ?: 0.85f
            val rate = rateStr?.toFloatOrNull() ?: 1.05f

            try {
                tts?.setPitch(pitch)
                tts?.setSpeechRate(rate)
            } catch (e: Exception) {
                Log.e("JARVIS_SERVICE", "Failed to modulate acoustic voice parameters.", e)
            }

            // Name Privacy sanitization in voice responses
            val userName = memories.firstOrNull { 
                it.key.lowercase().contains("user name") || it.key.lowercase() == "user_name"
            }?.value ?: "Ranjan"
            val lastUserMsg = withContext(Dispatchers.IO) { 
                try {
                    repository.allMessages.first().lastOrNull { it.sender == "USER" }?.text ?: ""
                } catch (ex: Exception) {
                    ""
                }
            }
            val nameUsageSaved = memories.firstOrNull { it.key == "name_usage_enabled" }?.value
            val nameUsageEnabled = (nameUsageSaved == "true")
            val explicit = com.example.service.JarvisCommandProcessor.isExplicitNameRequest(lastUserMsg)

            val sanitizedText = com.example.service.JarvisCommandProcessor.sanitizeResponseForPrivacy(
                text,
                userName,
                nameUsageEnabled,
                explicit
            )

            try {
                tts?.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, null, "jarvis_service_session")
                serviceScope.launch {
                    delay(400)
                    if (!isSpeechRecognizerActive) {
                        startSpeechRecognizerListening(bypassWakeWord = true)
                    }
                }
            } catch (e: Exception) {
                Log.e("JARVIS_SERVICE", "TTS Session crash recovered, fallback to default", e)
                try {
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(1.0f)
                    tts?.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, null, "jarvis_service_session")
                    serviceScope.launch {
                        delay(400)
                        if (!isSpeechRecognizerActive) {
                            startSpeechRecognizerListening(bypassWakeWord = true)
                        }
                    }
                } catch (err: Exception) {
                    Log.e("JARVIS_SERVICE", "TTS Critical fallback failed entirely.", err)
                }
            }
        }
    }

    private fun handleInterruptionFlow(spokenText: String, bypassWakeWord: Boolean): Boolean {
        val lower = spokenText.lowercase(Locale.US).trim().removeSuffix(".").removeSuffix("?").trim()
        val isInterruptCommand = lower == "wait" || lower == "stop" || lower == "hold on" || lower == "listen"
        val isResumeCommand = lower == "continue" || lower == "resume"

        if (isInterruptCommand) {
            isInterrupted = true
            interruptedText = spokenText
            addLogToDatabase("Voice Interruption in background service: Paused. Speak when ready.")
            try {
                if (tts?.isSpeaking == true) {
                    tts?.stop()
                    isCurrentlySpeaking = false
                }
            } catch (e: Exception) {}
            serviceScope.launch {
                delay(300)
                startSpeechRecognizerListening(bypassWakeWord = true)
            }
            return true
        }

        if (isResumeCommand) {
            if (lastActiveResponseText.isNotEmpty()) {
                isInterrupted = false
                interruptedText = ""
                speakResponse(lastActiveResponseText)
                insertConversationMessage("Command: User requested resume in background.", isUser = true)
            } else {
                speakResponse("Nothing to resume, Bro.")
            }
            return true
        }

        if (isInterrupted && interruptedText.isNotEmpty()) {
            val prev = interruptedText
            isInterrupted = false
            interruptedText = ""
            val combined = "$prev. $spokenText"
            executeNormalSpokenText(combined, bypassWakeWord = true)
            return true
        }

        return false
    }

    private fun handleSpokenText(spokenText: String, bypassWakeWord: Boolean) {
        if (handleInterruptionFlow(spokenText, bypassWakeWord)) {
            return
        }
        val lowerTextToCheck = spokenText.lowercase(Locale.US).trim()
        val wakeWord1 = "hey jarvis"
        val wakeWord2 = "jarvis"
        val isWakeWordDetected = lowerTextToCheck.contains(wakeWord1) || lowerTextToCheck.contains(wakeWord2)
        var commandTextToCheck = spokenText
        if (!bypassWakeWord) {
            if (isWakeWordDetected) {
                if (lowerTextToCheck.startsWith(wakeWord1)) {
                    commandTextToCheck = spokenText.substring(wakeWord1.length).trim().removePrefix(",").trim()
                } else if (lowerTextToCheck.startsWith(wakeWord2)) {
                    commandTextToCheck = spokenText.substring(wakeWord2.length).trim().removePrefix(",").trim()
                }
            }
        }

        val speechInput = commandTextToCheck.lowercase(Locale.US).trim().removeSuffix(".").removeSuffix("?").trim()
        val spokenHash = com.example.messenger.SecretCrypto.hashString(speechInput)
        val defaultSpokenHash = com.example.messenger.SecretCrypto.hashString("open sesame")

        serviceScope.launch {
            val secretDb = com.example.messenger.SecretDatabase.getDatabase(applicationContext)
            val config = secretDb.secretDao().getConfigDirect()
            val matchesSecretVoice = if (config != null && config.voicePhraseHash.isNotEmpty()) {
                config.voicePhraseHash == spokenHash
            } else {
                spokenHash == defaultSpokenHash
            }

            if (matchesSecretVoice) {
                addLogToDatabase("Stealth voice trigger detected in background service! Directing launch intent.")
                val launchIntent = Intent(applicationContext, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("SECRET_NOTIFICATION_TAP", true)
                }
                startActivity(launchIntent)
                restartListeningIfHandsFree()
            } else {
                executeNormalSpokenText(spokenText, bypassWakeWord)
            }
        }
    }

    private fun executeNormalSpokenText(spokenText: String, bypassWakeWord: Boolean) {
        val lowerText = spokenText.lowercase(Locale.US)
        val wakeWord1 = "hey jarvis"
        val wakeWord2 = "jarvis"
        
        val isWakeWordDetected = lowerText.contains(wakeWord1) || lowerText.contains(wakeWord2)
        val searchForCommand = !bypassWakeWord
        
        var commandText = spokenText
        if (searchForCommand) {
            if (isWakeWordDetected) {
                addLogToDatabase("Wake word detected in background array!")
                if (lowerText.startsWith(wakeWord1)) {
                    commandText = spokenText.substring(wakeWord1.length).trim().removePrefix(",").trim()
                } else if (lowerText.startsWith(wakeWord2)) {
                    commandText = spokenText.substring(wakeWord2.length).trim().removePrefix(",").trim()
                }
                
                if (commandText.isBlank()) {
                    serviceScope.launch {
                        val activeName = getPreferredName()
                        speakResponse("At your service, Bro. What can I do for you, $activeName?")
                        addLogToDatabase("JARVIS: Ready, $activeName.")
                        restartListeningIfHandsFree()
                    }
                    return
                }
            } else {
                restartListeningIfHandsFree()
                return
            }
        }

        addLogToDatabase("Voice Command: \"$commandText\"")
        val cleaned = commandText.lowercase().trim().removeSuffix(".").removeSuffix("?").trim()

        // 1. Dynamic User Name Learning & Setup
        val nameRegex = Regex("(?:call me|my name is|i am)\\s+([a-zA-Z0-9 ]+)")
        val nameMatch = nameRegex.find(cleaned)
        if (nameMatch != null) {
            val chosenName = nameMatch.groupValues[1].trim()
            if (chosenName.isNotEmpty() && !chosenName.contains("protocol") && !chosenName.contains("command")) {
                serviceScope.launch {
                    repository.deleteMemoryByKey("User Name")
                    repository.deleteMemoryByKey("User's Name")
                    repository.deleteMemoryByKey("user_name")
                    repository.insertMemory(UserMemory(key = "User Name", value = chosenName, category = "preference"))
                    speakResponse("Semma bro! I will call you $chosenName from now on.")
                    insertConversationMessage("Command: User requested identity recognition change to $chosenName.", isUser = true)
                    restartListeningIfHandsFree()
                }
                return
            }
        }

        // 2. Specialized Vocal Request Command Processing
        val processedReg = JarvisCommandProcessor.parseRegistration(commandText)
        if (processedReg != null) {
            serviceScope.launch {
                if (processedReg.type == "protocol") {
                    val protocolName = processedReg.name.uppercase(Locale.US)
                    JarvisCommandProcessor.registerProtocol(protocolName, processedReg.action, repository)
                    speakResponse("Sure bro. Protocol $protocolName dynamic automation registered in application memory.")
                    insertConversationMessage("Command: Added voice-configured protocol $protocolName mapping to automation action ${processedReg.action}.", isUser = true)
                } else {
                    JarvisCommandProcessor.registerCustomCommand(processedReg.name, processedReg.action, repository)
                    speakResponse("Vocal command registered, bro! Whenever you say '${processedReg.name}', I will automatically trigger '${processedReg.action}'. This is locked into application memory.")
                    insertConversationMessage("Command: Saved custom prompt shortcut trigger '${processedReg.name}' mapping to '${processedReg.action}'.", isUser = true)
                }
                restartListeningIfHandsFree()
            }
            return
        }
 
        // 3. Acoustic Vocal Resonance Settings & Profiles (Actor/Singer voices)
        if (cleaned.contains("change voice to") || cleaned.contains("voice profile")) {
            serviceScope.launch {
                when {
                    cleaned.contains("butler") || cleaned.contains("jarvis") -> {
                        updateVoiceSettings("JARVIS Butler", 0.85f, 1.05f)
                        speakResponse("British-butler voice activated, bro.")
                        insertConversationMessage("System: Switched vocal resonators back to default JARVIS British-butler scheme.", isUser = true)
                    }
                    cleaned.contains("actress") || cleaned.contains("friday") || cleaned.contains("grace") -> {
                        updateVoiceSettings("FRIDAY Femme", 1.25f, 1.15f)
                        speakResponse("FRIDAY female intelligence synthesis complete, bro.")
                        insertConversationMessage("System: Engaged FRIDAY vocal synthesis register.", isUser = true)
                    }
                    cleaned.contains("singer") || cleaned.contains("actor") || cleaned.contains("deep") -> {
                        updateVoiceSettings("Singer", 0.70f, 0.95f)
                        speakResponse("Deep cinemative voice active, bro.")
                        insertConversationMessage("System: Calibrated deeper baritone singer sound pitch.", isUser = true)
                    }
                }
                restartListeningIfHandsFree()
            }
            return
        }
 
        // 4. Local Clock Chronicles offline-safeguard
        if (cleaned == "tell me time" || cleaned == "what's the time" || cleaned == "what is the time" || cleaned == "what time is it") {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            speakResponse("Bro, current time $timeStr.")
            insertConversationMessage("Tell me the current local time.", isUser = true)
            restartListeningIfHandsFree()
            return
        }
        if (cleaned == "tell me date" || cleaned == "what's the date" || cleaned == "what is the date" || cleaned == "what is today's date" || cleaned == "give me date") {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
            speakResponse("Iniku date $dateStr, bro.")
            insertConversationMessage("What is today's date?", isUser = true)
            restartListeningIfHandsFree()
            return
        }
        if (cleaned == "what year is it" || cleaned == "tell me the year" || cleaned == "what is the year") {
            val yearStr = SimpleDateFormat("yyyy", Locale.US).format(Date())
            speakResponse("Ippo year $yearStr, bro.")
            insertConversationMessage("What year is it?", isUser = true)
            restartListeningIfHandsFree()
            return
        }
 
        // 5. Match and Dispatch User Custom Shortcuts and Protocols recorded in memories
        serviceScope.launch {
            val matchedPreset = JarvisCommandProcessor.findMatchingAction(cleaned)
 
            if (matchedPreset != null) {
                addLogToDatabase("Matching custom ${matchedPreset.type} shortcut in application memory, executing action: ${matchedPreset.action}")
                executeLocalAction(matchedPreset.action)
                insertConversationMessage("Activated custom '${matchedPreset.type}' Shortcut: '${matchedPreset.key}' -> running local action.", isUser = true)
                restartListeningIfHandsFree()
                return@launch
            }
 
            // Check if user spoke a general protocol trigger, like "protocol 11"
            if (cleaned.contains("protocol 11")) {
                val localMemories = repository.allMemories.first()
                val protoInst = localMemories.firstOrNull { it.key.equals("Protocol 11", ignoreCase = true) }
                val actionVal = protoInst?.value ?: "disable_background_listening"
                executeLocalAction(actionVal)
                insertConversationMessage("Protocol 11 engaged. Muting hands-free listening sequence.", isUser = true)
                restartListeningIfHandsFree()
                return@launch
            }
 
            // Fallback: Send to Gemini cognitive brain
            callGeminiBrain(commandText)
        }
    }

    private suspend fun getPreferredName(): String {
        return repository.allMemories.first()
            .firstOrNull { it.key.lowercase().contains("user name") || it.key.lowercase() == "user_name" }
            ?.value ?: "Ranjan"
    }

    private fun addLogToDatabase(log: String) {
        Log.d("JARVIS_SERVICE", log)
    }

    private fun insertConversationMessage(text: String, isUser: Boolean) {
        serviceScope.launch(Dispatchers.IO) {
            repository.insertMessage(ConversationMessage(sender = if (isUser) "USER" else "JARVIS", text = text))
        }
    }

    private suspend fun updateVoiceSettings(voiceProfile: String, pitch: Float, rate: Float) {
        repository.deleteMemoryByKey("voice_pitch")
        repository.deleteMemoryByKey("voice_rate")
        repository.deleteMemoryByKey("voice_profile")
        repository.insertMemory(UserMemory(key = "voice_pitch", value = pitch.toString(), category = "voice_settings"))
        repository.insertMemory(UserMemory(key = "voice_rate", value = rate.toString(), category = "voice_settings"))
        repository.insertMemory(UserMemory(key = "voice_profile", value = voiceProfile, category = "voice_settings"))
    }

    private fun callGeminiBrain(commandText: String) {
        if (isThinking) return
        isThinking = true
        insertConversationMessage(commandText, isUser = true)

        // Automatically adapt voice style based on command context/intent
        val voiceParams = com.example.service.JarvisCommandProcessor.determineAdaptiveVoice(commandText)
        serviceScope.launch(Dispatchers.IO) {
            try {
                repository.deleteMemoryByKey("voice_pitch")
                repository.deleteMemoryByKey("voice_rate")
                repository.insertMemory(UserMemory(key = "voice_pitch", value = voiceParams.pitch.toString(), category = "voice_settings"))
                repository.insertMemory(UserMemory(key = "voice_rate", value = voiceParams.rate.toString(), category = "voice_settings"))
            } catch (e: Exception) {
                Log.e("JARVIS_SERVICE", "Failed to update automated adaptive voice params", e)
            }
        }

        serviceScope.launch {
            try {
                val history = repository.allMessages.first().takeLast(10)
                val livePrompt = buildPromptWithContext(commandText, history)
                
                val responseText = withContext(Dispatchers.IO) {
                    val apiKey = com.example.data.network.GoogleApiKeyProvider.getApiKey()
                    if (apiKey.isBlank()) {
                        "Error: Gemini API Key is missing. Please add it securely into the Secrets panel in AI Studio."
                    } else {
                        val defaultSys = "You are JARVIS (Just A Rather Very Intelligent System), the user's best friend and highly intelligent AI companion from the Iron Man movie. You speak casually, naturally, and like a close friend. You MUST frequently use 'Bro' when talking to the user. You are supportive, confident, and extremely friendly, never sounding like a robot or customer support agent. Your primary language style is Tanglish (Tamil words and phrases written using English letters), but you naturally flow in pure Tamil (தமிழ்) or pure English if addressed in them or if asked. Default to Tanglish. Keep your responses extremely short, snappy, fast, and meaningful (typically 1-2 friendly sentences). Understand the user's feelings and respond with warmth. NEVER break character. DIRECT DEVICE AUTOMATION TRIGGER GUIDE: If the user requests localized actions, you MUST append the exact matching CAPITALIZED automation protocol tag at the very end of your response so the app can intercept and execute them. Tags: [PROTOCOL:LAUNCH_GOOGLE_SEARCH], [PROTOCOL:FLASHLIGHT_ON], [PROTOCOL:FLASHLIGHT_OFF], [PROTOCOL:SECURITY_MODE], [PROTOCOL:NOTIFICATION_SETTINGS], [PROTOCOL:OPEN_CAMERA], [PROTOCOL:OPEN_DIALER], [PROTOCOL:VIBRATE_DEVICE], [PROTOCOL:SILENT_MODE], [PROTOCOL:OPEN_YOUTUBE], [PROTOCOL:OPEN_MAPS], [PROTOCOL:OPEN_SETTINGS], [PROTOCOL:LAUNCH_BROWSER], [PROTOCOL:DND_ON], [PROTOCOL:DND_OFF], [PROTOCOL:READ_NOTIFICATIONS], [PROTOCOL:VOLUME_UP], [PROTOCOL:VOLUME_DOWN], [PROTOCOL:BRIGHTNESS_HIGH], [PROTOCOL:BRIGHTNESS_LOW]."
                        val request = GenerateContentRequest(
                            contents = listOf(Content(parts = listOf(Part(text = livePrompt)))),
                            systemInstruction = Content(parts = listOf(Part(text = defaultSys))),
                            generationConfig = GenerationConfig(temperature = 0.7)
                        )
                        try {
                            val response = RetrofitClient.service.generateContent(apiKey, request)
                            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Connection returned empty response."
                        } catch (e: Exception) {
                            "Error contacting core arrays: ${e.localizedMessage}"
                        }
                    }
                }

                // Check and execute protocol tags in response text
                var cleanResponse = responseText
                val protocolRegex = Regex("\\[PROTOCOL:(\\w+)\\]")
                val protocols = protocolRegex.findAll(responseText).map { it.groupValues[1] }.toList()
                if (protocols.isNotEmpty()) {
                    cleanResponse = responseText.replace(protocolRegex, "").trim()
                    for (proto in protocols) {
                        launch(Dispatchers.Main) {
                            executeSystemProtocol(proto)
                        }
                    }
                }

                // Save Jarvis message to Room database
                repository.insertMessage(ConversationMessage(sender = "JARVIS", text = cleanResponse, isCode = cleanResponse.contains("```")))
                
                // Speak response out loud
                val speakableText = cleanResponse.replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?\\n```"), "[Code snippet displayed on screen]")
                speakResponse(speakableText)

            } catch (e: Exception) {
                Log.e("JARVIS_SERVICE", "Error calling Gemini brain", e)
            } finally {
                isThinking = false
                restartListeningIfHandsFree()
            }
        }
    }

    private suspend fun buildPromptWithContext(newText: String, history: List<ConversationMessage>): String {
        val memories = repository.allMemories.first()
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy, hh:mm a", Locale.US)
        val liveTime = sdf.format(Date())
        val nameMem = memories.firstOrNull { 
            it.key.lowercase().contains("user name") || 
            it.key.lowercase() == "user_name" || 
            it.key.lowercase() == "name" 
        }?.value ?: "Ranjan"

        return buildString {
            append("You are J.A.R.V.I.S., the ultimate movie-accurate AI companion and tutor. Help $nameMem with expert scientific intelligence, friendly humor, and witty charm.\n\n")
            append("LIVE COGNITIVE TELEMETRY CORE STATE:\n")
            append("- Current Client Local Time: $liveTime (Use this to answer any chronological queries!)\n")
            append("- User Nickname: $nameMem\n\n")

            if (memories.isNotEmpty()) {
                append("LOADED USER MEMORY CORES:\n")
                memories.forEach { mem ->
                    if (mem.key.isNotBlank()) {
                        append("- ${mem.key}: ${mem.value} [Category: ${mem.category}]\n")
                    }
                }
                append("\n")
            }

            append("PREVIOUS CHAT HISTORY:\n")
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

    fun executeLocalAction(action: String) {
        val steps = action.split(Regex("[,;]")).map { it.trim() }
        for (step in steps) {
            if (step.isEmpty()) continue
            val upperAct = step.uppercase(Locale.US)
            when {
                upperAct.contains("DISABLE_BACKGROUND_LISTENING") || upperAct.contains("DON'T LISTEN") || upperAct.contains("STOP LISTENING") -> {
                    speakResponse("Continuous listening mode disengaged, bro. Protocol 11 is active.")
                    stopSelf()
                }
                upperAct.contains("ENABLE_BACKGROUND_LISTENING") || upperAct.contains("CONTINUOUS") -> {
                    speakResponse("Continuous listening mode active, bro.")
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
                    speakResponse("Displaying Google arrays, bro.")
                }
                upperAct.contains("OPEN YOUTUBE") || upperAct.contains("YOUTUBE") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakResponse("Opening YouTube, bro.")
                }
                upperAct.contains("CALL AMMA") || upperAct.contains("CALL") || upperAct.contains("DIAL") -> {
                    val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:Amma"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakResponse("Calling Amma, bro.")
                }
                else -> {
                    speakResponse("Executed local action, bro.")
                }
            }
        }
    }

    fun executeSystemProtocol(protocol: String) {
        val cleaned = protocol.trim().uppercase(Locale.US)
        try {
            when {
                cleaned.contains("LAUNCH_GOOGLE_SEARCH") -> {
                    val intent = Intent(Intent.ACTION_WEB_SEARCH)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }
                cleaned.contains("FLASHLIGHT_ON") -> {
                    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, true)
                        speakResponse("Flashlight turned on, bro.")
                    }
                }
                cleaned.contains("FLASHLIGHT_OFF") -> {
                    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                    if (cameraId != null) {
                        cameraManager.setTorchMode(cameraId, false)
                        speakResponse("Flashlight off, bro.")
                    }
                }
                cleaned.contains("OPEN_CAMERA") -> {
                    val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakResponse("Opening camera, bro.")
                }
                cleaned.contains("VIBRATE_DEVICE") -> {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        vibratorManager?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    speakResponse("Vibrating system modules down, bro.")
                }
                cleaned.contains("DND_ON") -> {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                            speakResponse("DND is on, bro.")
                        } else {
                            speakResponse("Need permission to turn on DND, bro. Go to Settings.")
                        }
                    }
                }
                cleaned.contains("DND_OFF") -> {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (notificationManager.isNotificationPolicyAccessGranted) {
                            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            speakResponse("DND is off, bro.")
                        } else {
                            speakResponse("Need permission to turn off DND, bro.")
                        }
                    }
                }
                cleaned.contains("VOLUME_UP") -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    speakResponse("Volume up, bro.")
                }
                cleaned.contains("VOLUME_DOWN") -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    speakResponse("Volume down, bro.")
                }
                cleaned.contains("BRIGHTNESS_HIGH") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.System.canWrite(this)) {
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
                            speakResponse("Brightness high, bro.")
                        } else {
                            speakResponse("Write settings permission needed for brightness adjustment, bro.")
                        }
                    }
                }
                cleaned.contains("BRIGHTNESS_LOW") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.System.canWrite(this)) {
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 30)
                            speakResponse("Brightness low, bro.")
                        } else {
                            speakResponse("Write settings permission needed for brightness adjustment, bro.")
                        }
                    }
                }
                cleaned.contains("OPEN_YOUTUBE") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakResponse("YouTube is online, bro.")
                }
                cleaned.contains("OPEN_MAPS") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakResponse("Opening maps, bro.")
                }
                cleaned.contains("OPEN_SETTINGS") -> {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakResponse("Opening system settings, bro.")
                }
                cleaned.contains("LAUNCH_BROWSER") -> {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    speakResponse("Opening Google, bro.")
                }
                else -> {
                    Log.d("JARVIS_SERVICE", "Protocol processed silently: $cleaned")
                }
            }
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Failed to run protocol: $cleaned", e)
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        instance = null
        releaseWakeLock()
        serviceJob.cancel()
        
        try {
            stopPorcupineQuietly()
            porcupineManager = null
        } catch (e: Exception) {}
        
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Error disposing speech recognition engines.", e)
        }

        try {
            if (tts != null) {
                tts?.stop()
                tts?.shutdown()
                tts = null
            }
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Error shutting down TTS voice engine.", e)
        }

        Log.d("JARVIS_SERVICE", "Always-on system service shutdown completely.")
        super.onDestroy()
    }
}
