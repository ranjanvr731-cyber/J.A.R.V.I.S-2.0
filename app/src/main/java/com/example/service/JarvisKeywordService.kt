package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class JarvisKeywordService : Service() {

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
    private var isWakeLockAcquired = false
    private var detector: LightweightWakeWordDetector? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        instance = this
        Log.i("JARVIS_SERVICE", "Always-on lightweight wake detector engine deploying.")
        createNotificationChannel()
        
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

        // 1. Initialize our lightweight Voice Activity detector
        detector = LightweightWakeWordDetector(
            context = this,
            onWakeWordDetected = {
                handleWakeTrigger()
            },
            onError = { error ->
                Log.e("JARVIS_SERVICE", "Acoustic hardware error: $error. Resetting...")
                VoiceStateManager.updateState(JarvisVoiceState.IDLE)
            }
        )

        // 2. Collect states from central VoiceStateManager to turn detector ON/OFF
        serviceScope.launch {
            VoiceStateManager.state.collectLatest { state ->
                Log.d("JARVIS_SERVICE", "Service received state change notification: $state")
                if (state == JarvisVoiceState.IDLE) {
                    detector?.start()
                } else {
                    detector?.stop()
                }
            }
        }

        // Initialize state to IDLE
        VoiceStateManager.updateState(JarvisVoiceState.IDLE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("JARVIS_SERVICE", "Service start command invocation received.")
        VoiceStateManager.updateState(JarvisVoiceState.IDLE)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleWakeTrigger() {
        Log.i("JARVIS_SERVICE", "Lightweight wake event matches parameters. Spinning up active recognition session.")
        
        // Transition to ACTIVE_LISTENING state
        VoiceStateManager.updateState(JarvisVoiceState.ACTIVE_LISTENING)

        // Bring MainActivity to the front or trigger STT directly on the active instance
        val mainActivity = com.example.MainActivity.instance
        if (mainActivity != null) {
            mainActivity.runOnUiThread {
                mainActivity.startSpeechToText()
            }
        } else {
            val launchIntent = Intent(applicationContext, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("START_SPEECH_RECOGNITION", true)
            }
            startActivity(launchIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Main Matrix Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Coordinates continuous voice diagnostics & commands."
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
            .setContentTitle("JARVIS Wake Detection Active")
            .setContentText("Monitoring voice activity in ultra low-power standby mode...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            val tag = "${applicationContext.packageName}:JARVIS::WakeLock"
            synchronized(this) {
                if (wakeLock == null) {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                        setReferenceCounted(false)
                    }
                }
                if (!isWakeLockAcquired) {
                    wakeLock?.acquire(5 * 60 * 1000L) // Safe limit to prevent severe battery leaks
                    isWakeLockAcquired = true
                    Log.d("JARVIS_SERVICE", "Partial WakeLock active.")
                }
            }
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Failed to acquire wake lock: ${e.localizedMessage}")
        }
    }

    private fun releaseWakeLock() {
        try {
            synchronized(this) {
                if (isWakeLockAcquired) {
                    wakeLock?.release()
                    isWakeLockAcquired = false
                    Log.d("JARVIS_SERVICE", "Partial WakeLock disengaged.")
                }
            }
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Failed to release wake lock: ${e.localizedMessage}")
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        instance = null
        releaseWakeLock()
        serviceJob.cancel()
        
        try {
            detector?.destroy()
            detector = null
        } catch (e: Exception) {
            Log.e("JARVIS_SERVICE", "Error disposing detector", e)
        }

        VoiceStateManager.updateState(JarvisVoiceState.IDLE)
        Log.d("JARVIS_SERVICE", "Always-on system service shutdown completely.")
        super.onDestroy()
    }
}
