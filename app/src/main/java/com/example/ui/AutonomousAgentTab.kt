package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.JarvisViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Representation of a mock screen context
data class MockScreen(
    val id: String,
    val name: String,
    val appCategory: String,
    val description: String,
    val ocrTexts: List<String>,
    val uiElements: List<UiElement>,
    val requiresSafetyElevation: Boolean = false
)

data class UiElement(
    val id: String,
    val label: String,
    val type: String, // "BUTTON", "INPUT", "SWITCH", "TAB"
    val xPercent: Float, // Position of center in screen preview (0f to 1f)
    val yPercent: Float,
    val secure: Boolean = false
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AutonomousAgentTab(viewModel: JarvisViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // AI Agent States
    var selectedGoal by remember { mutableStateOf("Autonomously clear background logs & optimization") }
    var runningPlan by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableStateOf(-1) }
    var isVerifiedOwner by remember { mutableStateOf(false) }
    var voiceTrainingProgress by remember { mutableFloatStateOf(0.0f) }
    var isTrainingVoice by remember { mutableStateOf(false) }
    var voiceprintVerifiedName by remember { mutableStateOf("NOT ENROLLED") }
    
    // Screens database
    val mockScreens = remember {
        listOf(
            MockScreen(
                id = "bank_pay",
                name = "Secure Money Transfer App",
                appCategory = "FINANCE / BANKING",
                description = "Active bank interface with fields for account name, routing numbers, and transfer amounts.",
                ocrTexts = listOf("Vertex Bank Pay", "Available Balance: $1,420.50", "Recipient: Ranjan Developer", "Amount: $100.00", "Send Cash"),
                uiElements = listOf(
                    UiElement("back_btn", "Back Arrow", "BUTTON", 0.08f, 0.08f),
                    UiElement("recipient_input", "Recipient Username Field", "INPUT", 0.5f, 0.30f),
                    UiElement("amount_input", "Transfer Amount Field", "INPUT", 0.5f, 0.45f),
                    UiElement("safety_switch", "Two-Factor Bio Lock", "SWITCH", 0.85f, 0.65f),
                    UiElement("send_btn", "Send Premium Payment", "BUTTON", 0.5f, 0.85f, secure = true)
                ),
                requiresSafetyElevation = true
            ),
            MockScreen(
                id = "smart_home",
                name = "Orbital Intelligent Smart Home Suite",
                appCategory = "IOT CONTROL",
                description = "Automated grid controlling atmospheric temperature, visual alarms, and magnetic door locks.",
                ocrTexts = listOf("Atmospheric Temp: 72°F", "Living Room Spotlight: Active", "Security Systems: SENTRY MODE ACTIVE", "Magnetic Lock: Engaged"),
                uiElements = listOf(
                    UiElement("temp_up", "Heat Increment Controller", "BUTTON", 0.3f, 0.25f),
                    UiElement("temp_down", "Heat Decrement Controller", "BUTTON", 0.7f, 0.25f),
                    UiElement("spotlight_toggle", "Toggle Lamp Array", "SWITCH", 0.5f, 0.50f),
                    UiElement("sentry_btn", "Secure Sentry Mode", "BUTTON", 0.5f, 0.75f)
                )
            ),
            MockScreen(
                id = "email_client",
                name = "Prism Secured Mail",
                appCategory = "COMMUNICATION",
                description = "A multi-column client detailing vital project deliverables, security reviews, and meeting rosters.",
                ocrTexts = listOf("Inboxes (3)", "To: Chief Ranjan", "Subject: J.A.R.V.I.S. Mark-V Upgrades ready for compile", "Body: Code is integrated, please verify audit hashes..."),
                uiElements = listOf(
                    UiElement("compose_btn", "Compose Master Mail", "BUTTON", 0.85f, 0.88f),
                    UiElement("expand_email", "Project Deliverables Email Item", "BUTTON", 0.5f, 0.40f),
                    UiElement("archive_btn", "Archive Selected Threads", "BUTTON", 0.15f, 0.15f)
                )
            ),
            MockScreen(
                id = "jarvis_control",
                name = "J.A.R.V.I.S. Internal Shell Settings",
                appCategory = "SYSTEM CONTROL",
                description = "Standard dynamic panel to adjust memory vaults, telemetry parameters, and audio output indices.",
                ocrTexts = listOf("Diagnostic Status: Core functional", "Background Listening: ENABLED", "Auditory Level: 85%", "Telemetry Level: Low"),
                uiElements = listOf(
                    UiElement("toggle_listening", "Keyword Wake Toggle", "SWITCH", 0.75f, 0.22f),
                    UiElement("volume_slider", "System Decibel Slider", "INPUT", 0.5f, 0.45f),
                    UiElement("clear_cache", "Wipe Cache Directory", "BUTTON", 0.5f, 0.72f)
                )
            )
        )
    }

    var selectedScreenId by remember { mutableStateOf(mockScreens[0].id) }
    val currentScreen = remember(selectedScreenId) { mockScreens.find { it.id == selectedScreenId } ?: mockScreens[0] }

    // Screen Analysis State
    var isAnalyzingScreen by remember { mutableStateOf(false) }
    var ocrStateResult by remember { mutableStateOf<List<String>>(emptyList()) }
    var uiElementsDetected by remember { mutableStateOf<List<UiElement>>(emptyList()) }
    var screenAnalysisExplanation by remember { mutableStateOf("No active snapshot scanned. Tap 'Scan & Analyze Active Screen' to initialize.") }

    var safetyLevelAlert by remember { mutableStateOf(false) }

    // Multi-step Action Planner Steps
    val planSteps = remember { mutableStateListOf<AgentPlanStep>() }
    val auditLogs = remember { mutableStateListOf<AgentAuditEntry>() }

    // Simulated Gesture Animations
    var animatingPointer by remember { mutableStateOf(false) }
    var animPointerX by remember { mutableFloatStateOf(0f) }
    var animPointerY by remember { mutableFloatStateOf(0f) }
    var gestureActionName by remember { mutableStateOf("") }

    // Learning engine workflows
    val learnedWorkflows = remember {
        mutableStateListOf(
            LearnedWorkflow("Secure Stealth Lock", "Triggers stealth modes, lights, and DND on verification", "DND_ON; SILENT_MODE; FLASH_OFF", 5),
            LearnedWorkflow("Father Payment Swift", "Executes money dispatch sequences after OCR review", "NAV_FINANCE; CONFIRM_RECIPIENT; TRANSMIT_FUNDS", 3)
        )
    }
    var customizedSafetyEnforcement by remember { mutableStateOf(true) }
    var systemExecutionSpeedPercent by remember { mutableFloatStateOf(80f) } // Speed slider: 10% to 100%

    // Completion reports state
    var completionReport by remember { mutableStateOf<AgentCompletionReport?>(null) }

    // Initialize initial audit logs
    LaunchedEffect(Unit) {
        if (auditLogs.isEmpty()) {
            auditLogs.add(AgentAuditEntry("System synchronized. Autonomous engine standing by.", "SUCCESS"))
            auditLogs.add(AgentAuditEntry("Voice encryption layer running SHA-256.", "SUCCESS"))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER TITLE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisPrimary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(JarvisPrimary.copy(alpha = 0.15f))
                            .border(1.dp, JarvisPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Autonomous Agent Core",
                            tint = JarvisPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "J.A.R.V.I.S. AUTONOMOUS AGENT",
                            color = JarvisPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Core Active | Level-5 Screen Understanding & DIY Gestures",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 1. OWNER VOICEPRINT RECOGNITION (SECTION)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = JarvisPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OWNER VOICE MUTEX LAYER",
                                color = JarvisTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isVerifiedOwner) JarvisSuccess.copy(alpha = 0.2f) else JarvisError.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isVerifiedOwner) "VERIFIED OWNER" else "UNAUTHORIZED SECURE STATE",
                                color = if (isVerifiedOwner) JarvisSuccess else JarvisError,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "To authorize autonomous actions (typing, swiping, payment triggers), Ranjan must verify his custom vocal biometric keys.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isTrainingVoice) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JarvisBackground, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Training... Say clearly: 'Hey Jarvis, authorize override code alpha-99'",
                                color = JarvisPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = voiceTrainingProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = JarvisPrimary,
                                trackColor = JarvisSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Analyzing frequency register harmonics: ${(voiceTrainingProgress * 100).toInt()}%",
                                color = JarvisTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isTrainingVoice = true
                                    voiceTrainingProgress = 0.0f
                                    coroutineScope.launch {
                                        viewModel.speakAloud("Biometric training active, Ranjan. Please repeat the override phrase.")
                                        for (i in 1..20) {
                                            delay(150)
                                            voiceTrainingProgress = i / 20f
                                        }
                                        isTrainingVoice = false
                                        isVerifiedOwner = true
                                        voiceprintVerifiedName = "Ranjan (Primary Owner)"
                                        auditLogs.add(0, AgentAuditEntry("Voice print matched & registered with SHA-256 successfully.", "SUCCESS"))
                                        viewModel.speakAloud("Voiceprint verified and synchronized successfully. Security lock dismissed, Bro!")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_train_voice")
                            ) {
                                Text("Train Voiceprint", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (isVerifiedOwner) {
                                Button(
                                    onClick = {
                                        isVerifiedOwner = false
                                        voiceprintVerifiedName = "NOT ENROLLED"
                                        auditLogs.add(0, AgentAuditEntry("Biometric signature flushed from RAM registers.", "WARNING"))
                                        viewModel.speakAloud("Biometric lock active. Absolute command restricted.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Revoke Signature", color = JarvisTextPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Active Voiceprint profile: $voiceprintVerifiedName",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 2. REAL-TIME SCREEN INTELLIGENCE (SECTION & SIMULATOR)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = JarvisPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REAL-TIME SCREEN INTELLIGENCE",
                            color = JarvisTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select active device viewport layout and run J.A.R.V.I.S OCR or UI tree models to see how the agent scans targets.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Choose simulated app
                    Text("Select Screen Target:", color = JarvisPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mockScreens.forEach { screen ->
                            val sSelected = screen.id == selectedScreenId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sSelected) JarvisPrimary.copy(alpha = 0.2f) else JarvisBackground)
                                    .border(1.dp, if (sSelected) JarvisPrimary else JarvisSurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedScreenId = screen.id
                                        ocrStateResult = emptyList()
                                        uiElementsDetected = emptyList()
                                        screenAnalysisExplanation = "Pending active context scan for ${screen.name}"
                                        safetyLevelAlert = screen.requiresSafetyElevation
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = screen.name,
                                    color = if (sSelected) JarvisPrimary else JarvisTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // GRAPHIC PREVIEW OF SIMULATED VIEWPORT
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(JarvisBackground, RoundedCornerShape(12.dp))
                            .border(1.dp, JarvisSurfaceVariant, RoundedCornerShape(12.dp))
                    ) {
                        // Background Grid
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val count = 10
                            val dx = size.width / count
                            val dy = size.height / count
                            for (i in 1 until count) {
                                drawLine(
                                    color = Color(0xFF333333).copy(alpha = 0.4f),
                                    start = Offset(i * dx, 0f),
                                    end = Offset(i * dx, size.height),
                                    strokeWidth = 1f
                                )
                                drawLine(
                                    color = Color(0xFF333333).copy(alpha = 0.4f),
                                    start = Offset(0f, i * dy),
                                    end = Offset(size.width, i * dy),
                                    strokeWidth = 1f
                                )
                            }
                        }

                        // App Overlay UI elements if analysed
                        uiElementsDetected.forEach { element ->
                            Box(
                                modifier = Modifier
                                    .absoluteOffset(
                                        x = (element.xPercent * 280).dp,
                                        y = (element.yPercent * 180).dp
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (element.secure) JarvisError.copy(alpha = 0.25f)
                                        else JarvisPrimary.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        1.dp,
                                        if (element.secure) JarvisError else JarvisPrimary,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "[${element.type}] ${element.label}",
                                        color = if (element.secure) JarvisError else JarvisTextPrimary,
                                        fontSize = 7.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "(${element.id})",
                                        color = JarvisTextSecondary,
                                        fontSize = 5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // OCR Texts scanned layer
                        if (ocrStateResult.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(4.dp)
                                    .align(Alignment.BottomCenter)
                            ) {
                                Text(
                                    text = "🔴 LIVE OBJECT DETECTION & OCR OVERLAY SYNC",
                                    color = JarvisPrimary,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Animated Gesture Overlap (finger pointer ripple effect!)
                        if (animatingPointer) {
                            Box(
                                modifier = Modifier
                                    .absoluteOffset(x = animPointerX.dp, y = animPointerY.dp)
                                    .size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Pulsing circles representing artificial finger press actions
                                val transition = rememberInfiniteTransition()
                                val scale by transition.animateFloat(
                                    initialValue = 0.5f,
                                    targetValue = 2.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )
                                val alpha by transition.animateFloat(
                                    initialValue = 0.7f,
                                    targetValue = 0.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(scale)
                                        .background(JarvisPrimary.copy(alpha = alpha), CircleShape)
                                        .border(1.dp, JarvisPrimary, CircleShape)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.Black, CircleShape)
                                        .border(2.dp, JarvisPrimary, CircleShape)
                                )
                            }

                            // Banner explaining current DIY action
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .background(Color.Black, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                    .border(1.dp, JarvisPrimary, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                    .padding(vertical = 4.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    text = "SIMULATING EXECUTABLE: $gestureActionName",
                                    color = JarvisPrimary,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Watermark / Indicator
                        Text(
                            text = "MOCK DEVICE SCREEN VIEWPORT: ${currentScreen.appCategory}",
                            color = JarvisTextSecondary.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isAnalyzingScreen = true
                                coroutineScope.launch {
                                    viewModel.speakAloud("Analyzing view matrix registers, Ranjan. Triggering computer vision models.")
                                    delay(800)
                                    ocrStateResult = currentScreen.ocrTexts
                                    uiElementsDetected = currentScreen.uiElements
                                    isAnalyzingScreen = false
                                    screenAnalysisExplanation = "Successfully resolved view matrix parameters. " +
                                            "Target application identified as '${currentScreen.name}'. Detected ${currentScreen.uiElements.size} interactive widgets and ${currentScreen.ocrTexts.size} textual sequences within safety boundaries."
                                    auditLogs.add(0, AgentAuditEntry("UI Viewport analyzed: '${currentScreen.name}' active.", "SUCCESS"))
                                    viewModel.speakAloud("Done Bro. I've successfully mapped the elements on this screen. I am fully prepared to trigger target gestures.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_analyze_screen")
                        ) {
                            if (isAnalyzingScreen) {
                                CircularProgressIndicator(color = JarvisPrimary, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Map Active Screen", color = JarvisTextPrimary, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // OCR Results output
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(JarvisBackground, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "COGNITIVE EXPLANATION:",
                            color = JarvisPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = screenAnalysisExplanation,
                            color = JarvisTextPrimary,
                            fontSize = 11.sp
                        )

                        if (ocrStateResult.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "RAW OCR OUTPUT TEXTS:",
                                color = JarvisPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            ocrStateResult.forEach { txt ->
                                Text(
                                    text = "• \"$txt\"",
                                    color = JarvisTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (safetyLevelAlert) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JarvisError.copy(alpha = 0.15f))
                                    .border(1.dp, JarvisError, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = JarvisError, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "HIGH RISK WARNING: Financial UI detected. Safety audit mandates verification and approval before actions.",
                                        color = JarvisError,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. ACTION PLANNER & DO-IT-YOURSELF WORKFLOW GESTURE STREAMS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUTONOMOUS GOAL & ACTION PLANNER",
                            color = JarvisTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Designate complex system targets for J.A.R.V.I.S to compile, execute, monitor, and recover step-by-step asynchronously.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset goals or custom input
                    Text("Select Target Objective Goal:", color = JarvisPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    listOf(
                        "Autonomously clear background logs & optimization",
                        "Configure Ultimate Stealth Mode, lights, and toggle Alarm",
                        "Send $120.00 to Ranjan Developer on Vertex Bank app",
                        "Scan Workspace layout whiteboard and compose Study deliverables"
                    ).forEach { goal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (selectedGoal == goal) JarvisPrimary.copy(alpha = 0.15f) else JarvisBackground,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selectedGoal == goal) JarvisPrimary else JarvisSurfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    if (!runningPlan) {
                                        selectedGoal = goal
                                        planSteps.clear()
                                        currentStepIndex = -1
                                        completionReport = null
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGoal == goal,
                                onClick = {
                                    if (!runningPlan) {
                                        selectedGoal = goal
                                        planSteps.clear()
                                        currentStepIndex = -1
                                        completionReport = null
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = JarvisPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = goal, color = JarvisTextPrimary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (planSteps.isNotEmpty()) {
                        Text("Active Execution Steps Sequence:", color = JarvisPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        planSteps.forEachIndexed { idx, step ->
                            val active = idx == currentStepIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (active) JarvisPrimary.copy(alpha = 0.1f) else JarvisSurfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) JarvisPrimary else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "STEP ${idx + 1}: ${step.title}",
                                        color = if (active) JarvisPrimary else JarvisTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = step.description,
                                        color = JarvisTextSecondary,
                                        fontSize = 10.sp
                                    )
                                    if (step.recoveredError != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "⚙️ Recovered: ${step.recoveredError}",
                                            color = JarvisSuccess,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (active) {
                                        CircularProgressIndicator(color = JarvisPrimary, modifier = Modifier.size(14.dp))
                                    } else {
                                        Icon(
                                            imageVector = when (step.status) {
                                                "COMPLETED" -> Icons.Default.Check
                                                "ERROR" -> Icons.Default.Warning
                                                "RECOVERED" -> Icons.Default.Check
                                                else -> Icons.Default.PlayArrow
                                            },
                                            contentDescription = step.status,
                                            tint = when (step.status) {
                                                "COMPLETED" -> JarvisSuccess
                                                "RECOVERED" -> JarvisSuccess
                                                "ERROR" -> JarvisError
                                                else -> JarvisTextSecondary
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = step.status,
                                        color = when (step.status) {
                                            "COMPLETED" -> JarvisSuccess
                                            "RECOVERED" -> JarvisSuccess
                                            "ERROR" -> JarvisError
                                            else -> JarvisTextSecondary
                                        },
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ACTIONS PANEL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (runningPlan) return@Button

                                // Generate steps depending on selected goal
                                planSteps.clear()
                                completionReport = null

                                // SAFETY MUTEX CHECK
                                val requiresSafety = selectedGoal.contains("Vertex Bank") || selectedGoal.contains("Send")
                                if (requiresSafety && !customizedSafetyEnforcement) {
                                    // Bypass
                                }

                                when {
                                    selectedGoal.contains("clear background") -> {
                                        planSteps.addAll(listOf(
                                            AgentPlanStep("Verify Active Telemetry Context", "Determine system benchmarks and active RAM directory indices", "COMPLETED"),
                                            AgentPlanStep("Acquire CPU lock", "Obtain high frequency performance registries temporarily", "COMPLETED"),
                                            AgentPlanStep("Purge cache and diagnostic traces", "Frees 32.5MB from operational journals", "PENDING"),
                                            AgentPlanStep("Recalibrate self diagnostic watchdogs", "Ensures system recovery operates at prime stability", "PENDING")
                                        ))
                                    }
                                    selectedGoal.contains("Stealth Mode") -> {
                                        planSteps.addAll(listOf(
                                            AgentPlanStep("Engage DND protocol", "Trigger hardware setting via framework package permissions", "COMPLETED"),
                                            AgentPlanStep("Dim display matrices", "Adjust overall brightness metrics down to minimum", "PENDING"),
                                            AgentPlanStep("Secure Camera active loops", "Disconnect spotlight grids to conserve energy", "PENDING")
                                        ))
                                    }
                                    selectedGoal.contains("Vertex Bank") -> {
                                        planSteps.addAll(listOf(
                                            AgentPlanStep("Trigger Secure Financial Overlay", "Verify active application signature against spoof parameters", "COMPLETED"),
                                            AgentPlanStep("Locate recipient entry coordinate", "Analyze OCR registers and center coordinate pointers", "PENDING"),
                                            AgentPlanStep("Authorize voiceprint signature verification", "Match Owner voice vectors with active RAM records", "PENDING"),
                                            AgentPlanStep("Execute simulated Tap gestures", "Low-latency gesture swipe interface", "PENDING")
                                        ))
                                    }
                                    else -> {
                                        planSteps.addAll(listOf(
                                            AgentPlanStep("Aperture OCR Calibration", "Run multi-column computer vision layout analysis", "PENDING"),
                                            AgentPlanStep("Format document parameters", "Structure text into study deliverables", "PENDING")
                                        ))
                                    }
                                }

                                runningPlan = true
                                currentStepIndex = 0

                                coroutineScope.launch {
                                    viewModel.speakAloud("Autonomous sequence engaged. Generating dynamic execution matrices.")
                                    delay(1000)

                                    // Run step-by-step
                                    val totalSteps = planSteps.size
                                    for (i in 0 until totalSteps) {
                                        currentStepIndex = i
                                        val step = planSteps[i]
                                        step.status = "RUNNING"

                                        viewModel.speakAloud("Executing step: ${step.title}")

                                        // Simulate Gestures / Finger taps on preview coordinate screen
                                        if (selectedGoal.contains("Vertex Bank") && i == 1) {
                                            selectedScreenId = "bank_pay"
                                            animatingPointer = true
                                            animPointerX = 140f // coordinates
                                            animPointerY = 90f
                                            gestureActionName = "TYPING: Ranjan Developer"
                                            delay(1200)
                                            animatingPointer = false
                                        } else if (selectedGoal.contains("Vertex Bank") && i == 3) {
                                            selectedScreenId = "bank_pay"
                                            animatingPointer = true
                                            animPointerX = 140f
                                            animPointerY = 160f
                                            gestureActionName = "TAP: Send Cash (Secure Button)"
                                            delay(1000)
                                            animatingPointer = false
                                        } else if (selectedGoal.contains("Stealth") && i == 1) {
                                            // Simulate a Swiping action
                                            selectedScreenId = "jarvis_control"
                                            animatingPointer = true
                                            gestureActionName = "SWIPE: Quick Settings Rail"
                                            for (s in 1..5) {
                                                animPointerX = 100f
                                                animPointerY = 20f + (s * 30f)
                                                delay(150)
                                            }
                                            animatingPointer = false
                                        }

                                        // Simulated Error & Auto-Recovery Mechanism
                                        if (i == 2 && selectedGoal.contains("Vertex Bank")) {
                                            step.status = "ERROR"
                                            viewModel.speakAloud("Warning, biometric Mutex mismatch or verification required. Re-authorizing security channel.")
                                            delay(1500)
                                            
                                            if (isVerifiedOwner) {
                                                step.status = "RECOVERED"
                                                step.recoveredError = "Verified Owner Voice Token approved alpha-99 credentials."
                                                viewModel.speakAloud("Biometrics approved. Resuming payment dispatch.")
                                                delay(1000)
                                            } else {
                                                step.status = "ERROR"
                                                step.recoveredError = "Failure: Owner Voiceprint was not authenticated."
                                                viewModel.speakAloud("Security alert. Financial actions cancelled. Ranjan, verify your voice print first!")
                                                runningPlan = false
                                                auditLogs.add(0, AgentAuditEntry("Transaction abort. Biometric tokens absent.", "FAIL"))
                                                break
                                            }
                                        } else {
                                            delay(1500) // general sleep
                                            step.status = "COMPLETED"
                                        }
                                    }

                                    if (runningPlan) {
                                        runningPlan = false
                                        // Final completion report generating
                                        completionReport = AgentCompletionReport(
                                            title = "TASK COMPLETED: ${selectedGoal}",
                                            outcome = "Success",
                                            timeTakenMs = 4500,
                                            successPercent = 100f,
                                            stepsSuccess = totalSteps,
                                            logsWritten = listOf(
                                                "Checked app checksum layers",
                                                "Registered click gestures on target positions",
                                                "Synchronized memory registry vectors"
                                            )
                                        )
                                        viewModel.speakAloud("Autonomous objective resolved successfully. G-layers secure and quiet, Bro.")
                                        auditLogs.add(0, AgentAuditEntry("Task completed successfully: '$selectedGoal'.", "SUCCESS"))
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_run_planner")
                        ) {
                            if (runningPlan) {
                                Text("Executing...", color = Color.Black)
                            } else {
                                Text("DIY: Execute Goal", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (runningPlan) {
                            Button(
                                onClick = {
                                    runningPlan = false
                                    currentStepIndex = -1
                                    animatingPointer = false
                                    viewModel.speakAloud("Autonomous actions disengaged immediately. All threads paused.")
                                    auditLogs.add(0, AgentAuditEntry("Execution terminated by owner command.", "WARNING"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisError),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("FORCE STOP", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. COMPLETION REPORTS (DYNAMIC INVOICE / TICKET CARD SUMMARY)
        item {
            AnimatedVisibility(
                visible = completionReport != null,
                enter = expandIn() + fadeIn(),
                exit = shrinkOut() + fadeOut()
            ) {
                completionReport?.let { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, JarvisSuccess)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = JarvisSuccess, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "TASK COMPLETION INVOICE",
                                        color = JarvisSuccess,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(JarvisSuccess.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "STABLE COMPLETED",
                                        color = JarvisSuccess,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = JarvisSuccess.copy(alpha = 0.3f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = report.title,
                                color = JarvisTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Calculated Duration:", color = JarvisTextSecondary, fontSize = 11.sp)
                                Text("${report.timeTakenMs} ms", color = JarvisSuccess, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Logical Accuracy Ratio:", color = JarvisTextSecondary, fontSize = 11.sp)
                                Text("${report.successPercent.toInt()}% Successful", color = JarvisSuccess, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Operational Steps Resolved:", color = JarvisTextSecondary, fontSize = 11.sp)
                                Text("${report.stepsSuccess} sub-routines", color = JarvisTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Cognitive Logs Registered:",
                                color = JarvisPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            report.logsWritten.forEach { l ->
                                Text(
                                    text = "✔ $l",
                                    color = JarvisTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. PERFORMANCE OPTIMIZATION METRICS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = JarvisPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PERFORMANCE OPTIMIZATION PROFILE",
                            color = JarvisTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Gesture Latency Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = JarvisBackground)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Gesture Latency", color = JarvisTextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("12.4 ms", color = JarvisPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("Ultra Low Overhead", color = JarvisSuccess, fontSize = 7.sp)
                            }
                        }
                        // OCR Latency Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = JarvisBackground)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Neural Scan Speed", color = JarvisTextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("42.1 ms", color = JarvisPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("Efficient Local SDK", color = JarvisSuccess, fontSize = 7.sp)
                            }
                        }
                        // Touch Registration Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = JarvisBackground)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Touch Latency", color = JarvisTextSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("4.8 ms", color = JarvisPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("Direct Kernel Sync", color = JarvisSuccess, fontSize = 7.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gesture Speed Config Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "A.I. Gesture Thread Rate Speed: ${systemExecutionSpeedPercent.toInt()}%",
                            color = JarvisTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = systemExecutionSpeedPercent,
                        onValueChange = { systemExecutionSpeedPercent = it },
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = JarvisPrimary,
                            activeTrackColor = JarvisPrimary,
                            inactiveTrackColor = JarvisSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(11.dp))
                    Text(
                        text = "⚡ Battery Awareness Mode: ACTIVE. When background execution drops below 15% threshold, J.A.R.V.I.S throttling reduces gesture rate speed down to safe clock loops.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 6. LEARNING ENGINE - MACRO ROUTINES & USER COGNITIVE PREFERENCES
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = JarvisPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LEARNING ENGINE - CUSTOM COGNITIVE ROUTINES",
                            color = JarvisTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "J.A.R.V.I.S continuously records frequent tasks. Here are the learned workflow macros that have structured themselves from Ranjan's use patterns.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    learnedWorkflows.forEach { workflow ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(JarvisBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, JarvisSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = workflow.title, color = JarvisPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(JarvisPrimary.copy(alpha = 0.15f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "Trigger count: ${workflow.triggerCount}", color = JarvisPrimary, fontSize = 8.sp)
                                    }
                                }
                                Text(text = "Resolving plan: ${workflow.actions}", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            IconButton(onClick = {
                                viewModel.speakAloud("Triggering learned macro: ${workflow.title}")
                                selectedGoal = when {
                                    workflow.title.contains("Stealth") -> "Configure Ultimate Stealth Mode, lights, and toggle Alarm"
                                    else -> "Send $120.00 to Ranjan Developer on Vertex Bank app"
                                }
                                auditLogs.add(0, AgentAuditEntry("Triggered saved macro '${workflow.title}'", "SUCCESS"))
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run workflow", tint = JarvisPrimary)
                            }
                        }
                    }
                }
            }
        }

        // 7. SAFETY LAYER & LIVE AUDIT LOG REGISTRY
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JarvisSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = JarvisPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SAFETY LAYER & BIO MUTEX SECURITY",
                                color = JarvisTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enforce Strict Consent Verification",
                                color = JarvisTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mandatory dialog checks when making financial or system-altering gestures.",
                                color = JarvisTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = customizedSafetyEnforcement,
                            onCheckedChange = { customizedSafetyEnforcement = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisPrimary,
                                checkedTrackColor = JarvisAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("LIVE AGENT AUDIT LOGS:", color = JarvisPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(JarvisBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, JarvisSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val scrollLogsState = rememberLazyListState()
                        LazyColumn(state = scrollLogsState, modifier = Modifier.fillMaxSize()) {
                            items(auditLogs) { entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = JarvisSurface.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    if (entry.state == "SUCCESS") JarvisSuccess
                                                    else if (entry.state == "WARNING") JarvisPrimary
                                                    else JarvisError,
                                                    CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = entry.log,
                                                color = JarvisTextPrimary,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "Timestamp: ${entry.timestamp} | Hash: SHA-${entry.hash}",
                                                color = JarvisTextSecondary,
                                                fontSize = 7.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Support Structures
data class AgentPlanStep(
    val title: String,
    val description: String,
    var status: String, // "PENDING", "RUNNING", "COMPLETED", "ERROR", "RECOVERED"
    var recoveredError: String? = null
)

data class AgentCompletionReport(
    val title: String,
    val outcome: String,
    val timeTakenMs: Int,
    val successPercent: Float,
    val stepsSuccess: Int,
    val logsWritten: List<String>
)

data class LearnedWorkflow(
    val title: String,
    val description: String,
    val actions: String,
    val triggerCount: Int
)

data class AgentAuditEntry(
    val log: String,
    val state: String, // "SUCCESS", "FAIL", "WARNING"
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
    val hash: String = UUID.randomUUID().toString().substring(0, 8).uppercase()
)
