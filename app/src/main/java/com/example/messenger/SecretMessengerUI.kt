package com.example.messenger

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog

// Emerald spy-inspired slate palette additions
val SecretBackground = Color(0xFF0F1113)
val SecretSurface = Color(0xFF161A1D)
val SecretSurfaceVariant = Color(0xFF1F2429)
val SecretEmeraldAccent = Color(0xFF10B981)
val SecretTextPrimary = Color(0xFFE2E8F0)
val SecretTextSecondary = Color(0xFF94A3B8)

@Composable
fun SecretMessengerRootContainer(
    viewModel: SecretMessengerViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val config by viewModel.configFlow.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val showPasscodeScreen by viewModel.showPasscodeScreen.collectAsStateWithLifecycle()

    if (!showPasscodeScreen) return

    val isProfileSetupNeeded = config == null || config?.passcodeHash?.isEmpty() == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SecretBackground)
            .testTag("secret_messenger_root")
    ) {
        if (isProfileSetupNeeded) {
            SecretProfileSetupScreen(
                onCompleted = { username, passcode, txtCode, voicePhrase, avatar ->
                    coroutineScope.launch {
                        viewModel.createSecretProfile(username, passcode, txtCode, voicePhrase, avatar)
                        Toast.makeText(context, "Secret Spy Network Profile Created!", Toast.LENGTH_SHORT).show()
                    }
                },
                onExit = {
                    viewModel.emergencyLock()
                    onClose()
                }
            )
        } else if (!isUnlocked) {
            SecretVerificationUnlockScreen(
                errorMessage = viewModel.errorMessage.collectAsStateWithLifecycle().value,
                onVerify = { passcodeAttempt ->
                    coroutineScope.launch {
                        val ok = viewModel.verifyAndUnlock(passcodeAttempt)
                        if (!ok) {
                            Toast.makeText(context, "Access Denied. Identity Unverified.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onBackAndHide = {
                    viewModel.emergencyLock()
                    onClose()
                }
            )
        } else {
            // Unlocked Main Messenger
            SecretMainMessengerHub(
                viewModel = viewModel,
                onEmergencyExit = {
                    viewModel.emergencyLock()
                    onClose()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretProfileSetupScreen(
    onCompleted: (username: String, passcode: String, textCode: String, voicePhrase: String, avatar: String) -> Unit,
    onExit: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    var textCode by remember { mutableStateOf("*#777#") }
    var voicePhrase by remember { mutableStateOf("open sesame") }
    var selectedAvatar by remember { mutableStateOf("spy_mask") }

    val spyAvatars = listOf("spy_mask", "agent_glasses", "sentinel_radar", "stealth_pulse")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "INITIALIZE STEALTH PROTOCOL",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = SecretEmeraldAccent
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Exit setup", tint = SecretTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SecretBackground)
            )
        },
        containerColor = SecretBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Establish your secure local agent profile. All information is cryptographically transformed and locked locally inside your dedicated device vault.",
                fontSize = 11.sp,
                color = SecretTextSecondary,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            // Setup Details Input fields
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Agent Identity (e.g. Neo)", color = SecretTextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SecretEmeraldAccent,
                    unfocusedBorderColor = SecretSurfaceVariant,
                    focusedTextColor = SecretTextPrimary,
                    unfocusedTextColor = SecretTextPrimary,
                    focusedContainerColor = SecretSurface,
                    unfocusedContainerColor = SecretSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_user")
            )

            OutlinedTextField(
                value = passcode,
                onValueChange = { passcode = it },
                label = { Text("Custom Access Key (Passcode)", color = SecretTextSecondary) },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SecretEmeraldAccent,
                    unfocusedBorderColor = SecretSurfaceVariant,
                    focusedTextColor = SecretTextPrimary,
                    unfocusedTextColor = SecretTextPrimary,
                    focusedContainerColor = SecretSurface,
                    unfocusedContainerColor = SecretSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_passcode")
            )

            OutlinedTextField(
                value = textCode,
                onValueChange = { textCode = it },
                label = { Text("Secret Text Dial-Trigger Code", color = SecretTextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SecretEmeraldAccent,
                    unfocusedBorderColor = SecretSurfaceVariant,
                    focusedTextColor = SecretTextPrimary,
                    unfocusedTextColor = SecretTextPrimary,
                    focusedContainerColor = SecretSurface,
                    unfocusedContainerColor = SecretSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_trigger_text")
            )

            OutlinedTextField(
                value = voicePhrase,
                onValueChange = { voicePhrase = it },
                label = { Text("Stealth Speech Voice Trigger", color = SecretTextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SecretEmeraldAccent,
                    unfocusedBorderColor = SecretSurfaceVariant,
                    focusedTextColor = SecretTextPrimary,
                    unfocusedTextColor = SecretTextPrimary,
                    focusedContainerColor = SecretSurface,
                    unfocusedContainerColor = SecretSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_trigger_voice")
            )

            Text("Select Agent Avatar Glyph:", fontSize = 12.sp, color = SecretTextPrimary, fontFamily = FontFamily.Monospace)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                spyAvatars.forEach { av ->
                    val isSelected = selectedAvatar == av
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) SecretEmeraldAccent else SecretSurface)
                            .border(1.dp, if (isSelected) Color.White else SecretSurfaceVariant, CircleShape)
                            .clickable { selectedAvatar = av }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (av) {
                                "spy_mask" -> Icons.Default.Lock
                                "agent_glasses" -> Icons.Default.Info
                                "sentinel_radar" -> Icons.Default.Refresh
                                else -> Icons.Default.Notifications
                            },
                            contentDescription = av,
                            tint = if (isSelected) Color.Black else SecretTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (username.isNotBlank() && passcode.isNotBlank() && textCode.isNotBlank() && voicePhrase.isNotBlank()) {
                        onCompleted(username, passcode, textCode, voicePhrase, selectedAvatar)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SecretEmeraldAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("setup_confirm_btn")
            ) {
                Text(
                    "ACTIVATE STANDALONE ENVELOPE",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SecretVerificationUnlockScreen(
    errorMessage: String?,
    onVerify: (String) -> Unit,
    onBackAndHide: () -> Unit
) {
    var passcodeInput by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SecretSurfaceVariant)
                .padding(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Shield Secure",
                tint = SecretEmeraldAccent,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "VERIFICATION OF CREDENTIALS REQUIRED",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = SecretTextPrimary
        )
        Text(
            text = "This channel remains isolated and zero-traced. Enter your secure passcode sequence.",
            fontSize = 11.sp,
            color = SecretTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = passcodeInput,
            onValueChange = { passcodeInput = it },
            label = { Text("Enter Passcode", color = SecretTextSecondary) },
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SecretEmeraldAccent,
                unfocusedBorderColor = SecretSurfaceVariant,
                focusedTextColor = SecretTextPrimary,
                unfocusedTextColor = SecretTextPrimary,
                focusedContainerColor = SecretSurface,
                unfocusedContainerColor = SecretSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.9f).testTag("verification_input")
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = JarvisError,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBackAndHide,
                modifier = Modifier.weight(1f).height(48.dp).testTag("verification_abort_btn"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SecretSurfaceVariant),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SecretTextPrimary)
            ) {
                Text("CLOSE", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            Button(
                onClick = {
                    if (passcodeInput.isNotEmpty()) {
                        onVerify(passcodeInput)
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("verification_unlock_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SecretEmeraldAccent)
            ) {
                Text("VERIFY", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun SecretMainMessengerHub(
    viewModel: SecretMessengerViewModel,
    onEmergencyExit: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0: CHATS, 1: PROTOCOL CONFIG
    val activeFriendId by viewModel.activeFriendId.collectAsStateWithLifecycle()
    val decryptedFriends by viewModel.decryptedFriends.collectAsStateWithLifecycle()

    if (activeFriendId != null) {
        // Show active chatroom instead of hub listings
        SecretChatRoomScreen(
            viewModel = viewModel,
            friendId = activeFriendId!!,
            onBack = { viewModel.setActiveFriend(null) },
            onEmergencyLock = onEmergencyExit
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SecretSurface,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(Icons.Default.Send, contentDescription = "Undercover Chats") },
                        label = { Text("Undercover Chats", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = SecretEmeraldAccent,
                            unselectedIconColor = SecretTextSecondary,
                            unselectedTextColor = SecretTextSecondary,
                            indicatorColor = SecretEmeraldAccent
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Triggers") },
                        label = { Text("Triggers", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = SecretEmeraldAccent,
                            unselectedIconColor = SecretTextSecondary,
                            unselectedTextColor = SecretTextSecondary,
                            indicatorColor = SecretEmeraldAccent
                        )
                    )
                }
            },
            containerColor = SecretBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    0 -> ChatsHubTab(
                        viewModel = viewModel,
                        friends = decryptedFriends,
                        onEmergencyHide = onEmergencyExit
                    )
                    1 -> TriggersConfigTab(
                        viewModel = viewModel,
                        onEmergencyHide = onEmergencyExit
                    )
                }
            }
        }
    }
}

@Composable
fun ChatsHubTab(
    viewModel: SecretMessengerViewModel,
    friends: List<DecryptedFriend>,
    onEmergencyHide: () -> Unit
) {
    var showAddFriendDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "SECURE ENVELOPE",
                    fontSize = 11.sp,
                    color = SecretEmeraldAccent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Secure Handshakes",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecretTextPrimary
                )
            }

            // Quick Emergency Hide
            Button(
                onClick = onEmergencyHide,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisError),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("emergency_hide_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Quick Hide", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text("QUICK HIDE", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Friends lists
        if (friends.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = "Empty", tint = SecretSurfaceVariant, modifier = Modifier.size(64.dp))
                    Text(
                        "No Handshakes Active",
                        color = SecretTextSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Button(
                        onClick = { showAddFriendDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SecretSurfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Invisible Friend", fontSize = 11.sp, color = SecretTextPrimary)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(friends) { friend ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveFriend(friend.id) }
                            .testTag("friend_card_${friend.id}"),
                        colors = CardDefaults.cardColors(containerColor = SecretSurface),
                        border = BorderStroke(1.dp, SecretSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(SecretSurfaceVariant)
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (friend.avatarName) {
                                        "spy_mask" -> Icons.Default.Lock
                                        "agent_glasses" -> Icons.Default.Info
                                        "sentinel_radar" -> Icons.Default.Refresh
                                        else -> Icons.Default.AccountCircle
                                    },
                                    contentDescription = friend.avatarName,
                                    tint = SecretEmeraldAccent
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    friend.name,
                                    fontWeight = FontWeight.Bold,
                                    color = SecretTextPrimary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Channel: " + if (friend.status == "FRIEND") "Secure peer connection [Verified]" else "Pending acceptance sequence...",
                                    color = if (friend.status == "FRIEND") SecretEmeraldAccent else JarvisSuccess.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            IconButton(
                                onClick = { viewModel.removeFriend(friend.id) },
                                modifier = Modifier.testTag("delete_friend_${friend.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove connection", tint = JarvisError.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
            
            Button(
                onClick = { showAddFriendDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SecretEmeraldAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("hub_add_friend_btn")
            ) {
                Text("INITIALIZE COVERT HANDSHAKE", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }

    if (showAddFriendDialog) {
        var friendName by remember { mutableStateOf("") }
        var chosenAvatar by remember { mutableStateOf("spy_mask") }

        Dialog(onDismissRequest = { showAddFriendDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SecretSurface),
                border = BorderStroke(1.dp, SecretSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "ADD OVER SECTIONS COORDINATE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = SecretEmeraldAccent
                    )
                    OutlinedTextField(
                        value = friendName,
                        onValueChange = { friendName = it },
                        placeholder = { Text("Friend Codename (e.g. Trinity)", color = SecretTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecretEmeraldAccent,
                            unfocusedBorderColor = SecretSurfaceVariant,
                            focusedTextColor = SecretTextPrimary,
                            unfocusedTextColor = SecretTextPrimary,
                            focusedContainerColor = SecretBackground,
                            unfocusedContainerColor = SecretBackground
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_friend_name_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("spy_mask", "agent_glasses", "sentinel_radar").forEach { av ->
                            val isSelected = chosenAvatar == av
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) SecretEmeraldAccent else SecretBackground)
                                    .clickable { chosenAvatar = av }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (av) {
                                        "spy_mask" -> Icons.Default.Lock
                                        "agent_glasses" -> Icons.Default.Info
                                        else -> Icons.Default.Refresh
                                    },
                                    contentDescription = av,
                                    tint = if (isSelected) Color.Black else SecretTextPrimary
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddFriendDialog = false }) {
                            Text("ABORT", color = SecretTextSecondary, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (friendName.isNotBlank()) {
                                    viewModel.initiateFriendRequest(friendName, chosenAvatar)
                                    showAddFriendDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecretEmeraldAccent)
                        ) {
                            Text("SYNC", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TriggersConfigTab(
    viewModel: SecretMessengerViewModel,
    onEmergencyHide: () -> Unit
) {
    val config by viewModel.configFlow.collectAsStateWithLifecycle()
    val passcode = viewModel.secretPasscode.collectAsStateWithLifecycle().value ?: ""

    var tempTextCode by remember { mutableStateOf("") }
    var tempVoicePhrase by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Decrypt on startup
    LaunchedEffect(config) {
        config?.let {
            tempTextCode = SecretCrypto.decryptAES(it.encryptedTextCode, passcode)
            tempVoicePhrase = SecretCrypto.decryptAES(it.encryptedVoicePhrase, passcode)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "CONVERT SETTINGS",
                    fontSize = 11.sp,
                    color = SecretEmeraldAccent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Triggers Node",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecretTextPrimary
                )
            }
            Button(
                onClick = onEmergencyHide,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisError),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("QUICK HIDE", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        Divider(color = SecretSurfaceVariant)

        Text(
            "Change dial-up code vectors and vocal trigger matrices. These control the exact commands used within normal mode to open your undercover safehouse.",
            fontSize = 11.sp,
            color = SecretTextSecondary,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace
        )

        OutlinedTextField(
            value = tempTextCode,
            onValueChange = { tempTextCode = it },
            label = { Text("Secret Text Dial-Trigger Code", color = SecretTextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SecretEmeraldAccent,
                unfocusedBorderColor = SecretSurfaceVariant,
                focusedTextColor = SecretTextPrimary,
                unfocusedTextColor = SecretTextPrimary,
                focusedContainerColor = SecretSurface,
                unfocusedContainerColor = SecretSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("config_text_code")
        )

        OutlinedTextField(
            value = tempVoicePhrase,
            onValueChange = { tempVoicePhrase = it },
            label = { Text("Stealth Speech Voice Trigger", color = SecretTextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SecretEmeraldAccent,
                unfocusedBorderColor = SecretSurfaceVariant,
                focusedTextColor = SecretTextPrimary,
                unfocusedTextColor = SecretTextPrimary,
                focusedContainerColor = SecretSurface,
                unfocusedContainerColor = SecretSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("config_voice_phrase")
        )

        Button(
            onClick = {
                config?.let { conf ->
                    coroutineScope.launch {
                        viewModel.createSecretProfile(
                            username = SecretCrypto.decryptAES(conf.encryptedUsername, passcode),
                            passcode = passcode,
                            textCode = tempTextCode,
                            voicePhrase = tempVoicePhrase,
                            avatar = conf.avatarName
                        )
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SecretEmeraldAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("config_save_btn")
        ) {
            Text("UPDATE GATEWAY TRIGGER VALUES", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Card(
            colors = CardDefaults.cardColors(containerColor = SecretSurface),
            border = BorderStroke(1.dp, SecretSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SECURE DESTRUCT NODE", fontSize = 12.sp, color = JarvisError, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("Purges all encrypted chats, attachments, friend handshakes, and configs off this device. This is irreversible.", fontSize = 10.sp, color = SecretTextSecondary)
                OutlinedButton(
                    onClick = { viewModel.wipeSecretMessenger() },
                    border = BorderStroke(1.dp, JarvisError),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisError),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("config_wipe_btn")
                ) {
                    Text("ERASE VAULT", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretChatRoomScreen(
    viewModel: SecretMessengerViewModel,
    friendId: Long,
    onBack: () -> Unit,
    onEmergencyLock: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val rawFriends by viewModel.rawFriendsFlow.collectAsStateWithLifecycle()
    val passcode = viewModel.secretPasscode.collectAsStateWithLifecycle().value ?: ""

    val friendName = remember(rawFriends, friendId) {
        val f = rawFriends.firstOrNull { it.id == friendId }
        if (f != null) SecretCrypto.decryptAES(f.encryptedName, passcode) else "Undercover Peer"
    }

    var draftText by remember { mutableStateOf("") }
    val drafts by viewModel.activeDraftMap.collectAsStateWithLifecycle()

    // Restore draft state when entering this room
    LaunchedEffect(friendId) {
        draftText = drafts[friendId] ?: ""
    }

    // Save draft state to ViewModel on character additions
    val onDraftChanged: (String) -> Unit = { updated ->
        draftText = updated
        viewModel.updateDraft(friendId, updated)
    }

    // Simulated recording states
    var isSimulatingRecording by remember { mutableStateOf(false) }
    var voiceRecordDuration by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(friendName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SecretTextPrimary)
                        Text("Peer Handshake Valid • AES-256-CBC Active", fontSize = 9.sp, color = SecretEmeraldAccent, fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SecretTextPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = onEmergencyLock,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisError),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("QUICK HIDE", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SecretSurface)
            )
        },
        containerColor = SecretBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen messages scroll list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activeMessages) { msg ->
                    val alignEnd = !msg.isIncoming
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (msg.isIncoming) 2.dp else 12.dp,
                                        bottomEnd = if (msg.isIncoming) 12.dp else 2.dp
                                    )
                                )
                                .background(if (msg.isIncoming) SecretSurface else SecretSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (msg.isIncoming) SecretEmeraldAccent.copy(alpha = 0.2f) else SecretSurfaceVariant,
                                    RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (msg.isIncoming) 2.dp else 12.dp,
                                        bottomEnd = if (msg.isIncoming) 12.dp else 2.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (msg.isIncoming) friendName else "YOU",
                                    fontSize = 10.sp,
                                    color = if (msg.isIncoming) SecretEmeraldAccent else SecretTextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "ENCRYPTED PACKET",
                                    fontSize = 8.sp,
                                    color = SecretTextSecondary.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Display appropriate layouts for Voice, Image, Files attachments
                            if (msg.attachmentType != null) {
                                when (msg.attachmentType) {
                                    "VOICE" -> {
                                        MockVoicePlayerCard()
                                    }
                                    "IMAGE" -> {
                                        MockImageAttachmentCard()
                                    }
                                    "FILE" -> {
                                        MockFileAttachmentCard()
                                    }
                                }
                            } else {
                                Text(
                                    text = msg.text,
                                    color = SecretTextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Animated voice recorder HUD overlays
            if (isSimulatingRecording) {
                RecordingHudBox(
                    seconds = voiceRecordDuration,
                    onStop = {
                        isSimulatingRecording = false
                        viewModel.sendSecretMessage(
                            friendId = friendId,
                            text = "",
                            attachmentType = "VOICE",
                            attachmentPath = "secure_audio_capture_${System.currentTimeMillis()}.aac"
                        )
                    }
                )
            } else {
                // NORMAL MESSAGE INPUT FIELD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SecretSurface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Attachment options button
                    var showAttachmentSheets by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showAttachmentSheets = true },
                        modifier = Modifier.testTag("attachment_menu_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Attachments", tint = SecretEmeraldAccent)
                    }

                    OutlinedTextField(
                        value = draftText,
                        onValueChange = onDraftChanged,
                        placeholder = { Text("Covert message envelope...", color = SecretTextSecondary, fontSize = 12.sp) },
                        maxLines = 3,
                        modifier = Modifier.weight(1f).testTag("chat_covert_text"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SecretEmeraldAccent,
                            unfocusedBorderColor = SecretSurfaceVariant,
                            focusedTextColor = SecretTextPrimary,
                            unfocusedTextColor = SecretTextPrimary,
                            focusedContainerColor = SecretBackground,
                            unfocusedContainerColor = SecretBackground
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )

                    if (draftText.trim().isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.sendSecretMessage(friendId, draftText.trim())
                                onDraftChanged("")
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SecretEmeraldAccent)
                                .size(40.dp)
                                .testTag("chat_covert_send_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Transmit", tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        // Mic Recording simulate launcher button
                        IconButton(
                            onClick = {
                                isSimulatingRecording = true
                                voiceRecordDuration = 0
                                coroutineScope.launch {
                                    while (isSimulatingRecording) {
                                        delay(1000)
                                        voiceRecordDuration += 1
                                    }
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(SecretSurfaceVariant)
                                .size(40.dp)
                                .testTag("record_voice_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Record Voice", tint = SecretEmeraldAccent)
                        }
                    }

                    // Attachment bottom selections sheet dialog simulation
                    if (showAttachmentSheets) {
                        Dialog(onDismissRequest = { showAttachmentSheets = false }) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SecretSurface),
                                border = BorderStroke(1.dp, SecretSurfaceVariant),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("SELECT STEALTH FILE PACKET", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SecretEmeraldAccent, fontFamily = FontFamily.Monospace)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.sendSecretMessage(friendId, "", "IMAGE", "blueprint_grid_v9.png")
                                            showAttachmentSheets = false
                                        }.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = "Image", tint = SecretTextPrimary)
                                        Text("Blueprints satellite_hq_view.png", color = SecretTextPrimary, fontSize = 13.sp)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.sendSecretMessage(friendId, "", "FILE", "intelligence_doc.pdf")
                                            showAttachmentSheets = false
                                        }.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = "File", tint = SecretTextPrimary)
                                        Text("Briefing nuclear_codes.txt (42 kb)", color = SecretTextPrimary, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = { showAttachmentSheets = false }, modifier = Modifier.align(Alignment.End)) {
                                        Text("ABORT", color = JarvisError, fontFamily = FontFamily.Monospace)
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

@Composable
fun MockVoicePlayerCard() {
    var isPlaying by remember { mutableStateOf(false) }
    var playPercent by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (playPercent < 1.0f) {
                delay(120)
                playPercent += 0.05f
            }
            isPlaying = false
            playPercent = 0f
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SecretSurfaceVariant)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SecretEmeraldAccent)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = "Speaker Toggle",
                tint = Color.Black,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("Voice Message (Secure Packet)", fontSize = 11.sp, color = SecretTextPrimary)
            LinearProgressIndicator(
                progress = playPercent,
                color = SecretEmeraldAccent,
                trackColor = SecretBackground,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(4.dp)
            )
            Text(if (isPlaying) "Streaming decrypt node..." else "0:14 Audio packet verified", fontSize = 9.sp, color = SecretTextSecondary, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun MockImageAttachmentCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SecretSurfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(SecretBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Star, contentDescription = "Satellite schematic diagram", tint = SecretEmeraldAccent, modifier = Modifier.size(36.dp))
                Text("INTELLIGENCE_SATELLITE_VIEW_HQ.PNG", fontSize = 10.sp, color = SecretTextPrimary, fontFamily = FontFamily.Monospace)
            }
        }
        Text("Covert Imagery: Synaptic encryption wrapper deployed.", fontSize = 11.sp, color = SecretTextSecondary)
    }
}

@Composable
fun MockFileAttachmentCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SecretSurfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = "Intel report", tint = SecretEmeraldAccent, modifier = Modifier.size(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("nuclear_codes.txt", fontSize = 13.sp, color = SecretTextPrimary, fontWeight = FontWeight.Bold)
            Text("42.8 KB • Plaintext Purged Off-Disk", fontSize = 10.sp, color = SecretTextSecondary, fontFamily = FontFamily.Monospace)
        }
        Icon(Icons.Default.Share, contentDescription = "Unlock action", tint = SecretTextPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun RecordingHudBox(
    seconds: Int,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SecretSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
            Text("VAULT RECORDING SECURE: $seconds s", color = SecretTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = SecretEmeraldAccent),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("TRANSMIT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
