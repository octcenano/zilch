package com.zilch.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zilch.ui.components.BottomNavBar
import com.zilch.ui.navigation.Routes
import com.zilch.ui.screens.chat.NearbyChatScreen
import com.zilch.ui.screens.chat.SharedFile

import com.zilch.ui.screens.chatlist.ChatListScreen
import com.zilch.ui.screens.chatlist.ChatPreview
import com.zilch.ui.screens.contacts.ContactUi
import com.zilch.ui.screens.contacts.ContactsScreen
import com.zilch.ui.screens.home.HomeViewModel
import com.zilch.ui.screens.inbox.InboxMessage
import com.zilch.ui.screens.inbox.InboxScreen
import com.zilch.ui.screens.groups.GroupListScreen
import com.zilch.ui.screens.groups.GroupChatScreen
import com.zilch.ui.screens.onboarding.OnboardingScreen
import com.zilch.ui.screens.qr.QrReceiveScreen
import com.zilch.ui.screens.qr.QrScanScreen
import com.zilch.ui.screens.qr.QrScanState
import com.zilch.ui.screens.settings.SettingsScreen
import com.zilch.ui.screens.setup.NeedsScreen
import com.zilch.ui.screens.contacts.TrustedPerson
import com.zilch.ui.screens.contacts.TrustedPersonScreen
import com.zilch.ui.screens.voice.VoiceCallScreen
import com.zilch.ui.theme.DarkPalette
import com.zilch.ui.theme.ZilchTheme
import com.zilch.crypto.qr.QrDecoder
import com.zilch.crypto.CryptoEngine

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ZilchTheme {
                ZilchNavGraph()
            }
        }
    }
}

@Composable
fun ZilchNavGraph() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Screens with bottom bar
    val bottomBarRoutes = listOf(Routes.CHATS, Routes.GROUP_LIST, Routes.SETTINGS)
    val showBottomBar = currentRoute in bottomBarRoutes

    // Datos reales de los motores BLE y crypto
    val peers by homeViewModel.peers.collectAsState()
    val allMessages by homeViewModel.messages.collectAsState()
    val realChats by homeViewModel.chatPreviews.collectAsState()


    val sampleInboxMessages = remember {
        listOf(
            InboxMessage(
                id = "1",
                senderFingerprint = "a3f2-8b1c-4d5e",
                subject = "Documento sensible",
                preview = "Aquí tienes el archivo que pediste...",
                timestampMs = System.currentTimeMillis() - 300_000,
                isRead = false
            ),
            InboxMessage(
                id = "2",
                senderFingerprint = "7e2a-1f3b-9c0d",
                subject = "Confirmación de reunión",
                preview = "Nos vemos en el punto acordado...",
                timestampMs = System.currentTimeMillis() - 7_200_000,
                isRead = true
            )
        )
    }

    val fingerprint by homeViewModel.fingerprint.collectAsState()
    val torStatus by homeViewModel.torStatus.collectAsState()
    val isKillSwitchActive by homeViewModel.isKillSwitchActive.collectAsState()
    val notificationsEnabled by homeViewModel.notificationsEnabled.collectAsState()

    Scaffold(
        containerColor = Color(0xFF0D1117),
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.CHATS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        // Manejar deep link desde notificaciones
        val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        LaunchedEffect(activity?.intent) {
            val intent = activity?.intent
            val navigateTo = intent?.getStringExtra("navigate_to")
            val peerNodeId = intent?.getStringExtra("peerNodeId")
            if (navigateTo == "nearby_chat" && !peerNodeId.isNullOrEmpty()) {
                navController.navigate(Routes.nearbyChat(peerNodeId))
                intent?.removeExtra("navigate_to")
                intent?.removeExtra("peerNodeId")
            }
        }

        NavHost(
            navController = navController,
            startDestination = Routes.NEEDS,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ═══ ONBOARDING ═══
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // ═══ NECESIDADES (pantalla de permisos/setup) ═══
            composable(Routes.NEEDS) {
                NeedsScreen(
                    onComplete = {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.NEEDS) { inclusive = true }
                        }
                    }
                )
            }

            // ═══ CHATS (principal — estilo WhatsApp) ═══
            composable(Routes.CHATS) {
                ChatListScreen(
                    chats = realChats,
                    onChatClick = { nodeId ->
                        navController.navigate(Routes.nearbyChat(nodeId))
                    },
                    onScanQr = {
                        navController.navigate(Routes.QR_SCAN)
                    },
                    onShowQr = {
                        navController.navigate(Routes.QR_RECEIVE)
                    }
                )
            }

            // ═══ GRUPOS (lista de grupos) ═══
            composable(Routes.GROUP_LIST) {
                val groups by homeViewModel.groups.collectAsState()
                val peersList by homeViewModel.peers.collectAsState()
                val peerOptions = peersList.map { it.nodeId to it.fingerprint }

                GroupListScreen(
                    groups = groups,
                    availablePeers = peerOptions,
                    onGroupClick = { groupId ->
                        navController.navigate(Routes.groupChat(groupId))
                    },
                    onCreateGroup = { name, members ->
                        homeViewModel.createGroup(name, members)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // ═══ CHAT DE GRUPO ═══
            composable(
                route = Routes.GROUP_CHAT,
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
                val groupsList by homeViewModel.groups.collectAsState()
                val allGroupMessages by homeViewModel.groupMessages.collectAsState()
                val group = groupsList.find { it.groupId == groupId }
                    ?: com.zilch.ui.screens.groups.GroupInfo(
                        groupId = groupId,
                        name = "Grupo",
                        members = emptyList()
                    )
                val groupMsgs = allGroupMessages[groupId] ?: emptyList()

                GroupChatScreen(
                    group = group,
                    messages = groupMsgs,
                    onBack = { navController.popBackStack() },
                    onSendMessage = { text ->
                        homeViewModel.sendGroupMessage(groupId, text)
                    }
                )
            }

            // ═══ BANDEJA (correo P2P) ═══
            composable(Routes.INBOX) {
                InboxScreen(
                    messages = sampleInboxMessages,
                    onMessageClick = { /* Abrir mensaje */ },
                    onCompose = { /* Redactar nuevo */ },
                    onBack = { navController.popBackStack() },
                    onEmergencyTriggered = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // ═══ AJUSTES ═══
            composable(Routes.SETTINGS) {
                val torStatusText = when (torStatus) {
                    com.zilch.ui.components.TorStatus.ACTIVE -> "Activo"
                    com.zilch.ui.components.TorStatus.INACTIVE -> "Inactivo"
                    com.zilch.ui.components.TorStatus.CHECKING -> "Verificando..."
                    com.zilch.ui.components.TorStatus.KILL_SWITCH -> "Kill Switch"
                }
                val torStatusColor = when (torStatus) {
                    com.zilch.ui.components.TorStatus.ACTIVE -> Color(0xFF3FB950)
                    com.zilch.ui.components.TorStatus.INACTIVE -> Color(0xFF8B949E)
                    com.zilch.ui.components.TorStatus.CHECKING -> Color(0xFFD29922)
                    com.zilch.ui.components.TorStatus.KILL_SWITCH -> Color(0xFFDA3633)
                }

                SettingsScreen(
                    fingerprint = fingerprint,
                    nodeId = homeViewModel.nodeId.collectAsState().value,
                    torStatusText = torStatusText,
                    torStatusColor = torStatusColor,
                    isKillSwitchActive = isKillSwitchActive,
                    notificationsEnabled = notificationsEnabled,
                    onVerifyTor = { /* Verificar Tor */ },
                    onShowQr = { navController.navigate(Routes.QR_RECEIVE) },
                    onEmergencyDestroy = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onShowTrustedPersons = { navController.navigate(Routes.TRUSTED_PERSONS) },
                    onToggleNotifications = { homeViewModel.toggleNotifications() }
                )
            }

            // ═══ QR RECIBIR ═══
            composable(Routes.QR_RECEIVE) {
                var timeRemaining by remember { mutableIntStateOf(300) }
                LaunchedEffect(Unit) {
                    while (timeRemaining > 0) {
                        kotlinx.coroutines.delay(1000L)
                        timeRemaining--
                    }
                }

                QrReceiveScreen(
                    fingerprint = fingerprint,
                    timeRemainingSeconds = timeRemaining,
                    onBack = { navController.popBackStack() },
                    onEmergencyTriggered = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // ═══ QR ESCANEAR ═══
            composable(Routes.QR_SCAN) {
                var scanState by remember { mutableStateOf<QrScanState>(QrScanState.Scanning) }
                var lastQrText by remember { mutableStateOf<String?>(null) }
                var lastDecodedQr by remember { mutableStateOf<com.zilch.crypto.qr.QrDecoder.DecodedQr?>(null) }

                QrScanScreen(
                    scanState = scanState,
                    onQrDetected = { qrText ->
                        lastQrText = qrText
                        try {
                            val decoded = QrDecoder.decode(qrText)
                            lastDecodedQr = decoded
                            scanState = QrScanState.Scanned(
                                fingerprint = decoded.fingerprint,
                                temporaryAddress = decoded.temporaryAddress
                            )
                        } catch (e: Exception) {
                            scanState = QrScanState.Error(
                                message = e.message ?: "Error al decodificar QR"
                            )
                        }
                    },
                    onConfirmContact = {
                        lastQrText?.let { qrText ->
                            homeViewModel.addContactFromQr(qrText)
                        }
                        scanState = QrScanState.Confirmed
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.QR_SCAN) { inclusive = true }
                        }
                    },
                    onRetryScan = {
                        scanState = QrScanState.Scanning
                        lastQrText = null
                        lastDecodedQr = null
                    },
                    onBack = { navController.popBackStack() },
                    onEmergencyTriggered = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // ═══ CONTACTOS ═══
            composable(Routes.CONTACTS) {
                val sampleContacts = remember {
                    listOf(
                        ContactUi(
                            nodeId = "a3f2b81c4d5e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a",
                            fingerprint = "a3f2-8b1c-4d5e",
                            addresses = listOf("ble:AA:BB:CC:DD:EE:FF"),
                            contactCount = 3
                        )
                    )
                }

                ContactsScreen(
                    contacts = sampleContacts,
                    onContactClick = { nodeId ->
                        navController.navigate(Routes.nearbyChat(nodeId))
                    },
                    onBack = { navController.popBackStack() },
                    onEmergencyTriggered = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // ═══ PERSONAS DE CONFIANZA ═══
            composable(Routes.TRUSTED_PERSONS) {
                val contactsList by homeViewModel.trustedContacts.collectAsState()
                TrustedPersonScreen(
                    contacts = contactsList,
                    onBack = { navController.popBackStack() },
                    onFingerprintChange = { fp, nick ->
                        homeViewModel.updateTrustedContactNickname(fp, nick)
                    },
                    onTrustToggle = { fp, trusted ->
                        homeViewModel.toggleTrustedContact(fp, trusted)
                    },
                    onAddContact = {
                        navController.navigate(Routes.QR_SCAN)
                    }
                )
            }

            // ═══ CHAT CERCANO ═══
            composable(
                route = Routes.NEARBY_CHAT,
                arguments = listOf(
                    navArgument("peerNodeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val peerNodeId = backStackEntry.arguments?.getString("peerNodeId") ?: ""
                val peer = peers.find { it.nodeId == peerNodeId }
                val peerMessages = allMessages[peerNodeId] ?: emptyList()
                val fileContext = androidx.compose.ui.platform.LocalContext.current

                NearbyChatScreen(
                    peerFingerprint = peer?.fingerprint ?: peerNodeId.take(8),
                    messages = peerMessages,
                    isConnected = peer?.isReachable == true,
                    onBack = { navController.popBackStack() },
                    onCallClick = { navController.navigate(Routes.voiceCall(peerNodeId)) },
                    onEmergencyTriggered = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onSendMessage = { text ->
                        homeViewModel.sendMessage(peerNodeId, text)
                    },
                    onSendFile = { uri ->
                        try {
                            val resolver = fileContext.contentResolver
                            val cursor = resolver.query(uri, null, null, null, null)
                            val fileName = cursor?.use {
                                if (it.moveToFirst()) {
                                    it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                                } else "archivo"
                            } ?: "archivo"
                            val fileSize = cursor?.use {
                                if (it.moveToFirst()) {
                                    it.getLong(it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE))
                                } else 0L
                            } ?: 0L
                            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                            val sharedFile = SharedFile(
                                id = java.util.UUID.randomUUID().toString(),
                                fileName = fileName,
                                fileSize = fileSize,
                                mimeType = mimeType,
                                filePath = uri.toString(),
                                isSent = true
                            )
                            homeViewModel.registerSentFile(peerNodeId, sharedFile)
                            // Leer bytes del archivo y enviar vía BLE
                            val inputStream = resolver.openInputStream(uri)
                            if (inputStream != null) {
                                val fileBytes = inputStream.readBytes()
                                inputStream.close()
                                homeViewModel.sendFileToPeer(peerNodeId, fileName, mimeType, fileBytes)
                            }
                        } catch (e: Exception) {
                            Log.e("NavGraph", "Error procesando archivo: ${e.message}")
                        }
                    }
                )
            }

            // ═══ LLAMADA DE VOZ BLE ═══
            composable(
                route = Routes.VOICE_CALL,
                arguments = listOf(
                    navArgument("peerNodeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val peerNodeId = backStackEntry.arguments?.getString("peerNodeId") ?: ""
                val peer = peers.find { it.nodeId == peerNodeId }
                val peerFingerprint = peer?.fingerprint ?: peerNodeId.take(8)

                // Generate a session key for the call
                val callKey = remember {
                    javax.crypto.KeyGenerator.getInstance("AES").apply {
                        init(256)
                    }.generateKey()
                }

                val ctx = androidx.compose.ui.platform.LocalContext.current
                val callScope = rememberCoroutineScope()

                val voiceCall = remember {
                    com.zilch.blemesh.voice.BleVoiceCall(
                        context = ctx.applicationContext,
                        encryptionKey = callKey,
                        scope = callScope
                    )
                }

                val callState by voiceCall.state.collectAsState()
                val callDuration by voiceCall.duration.collectAsState()
                val isMuted by voiceCall.isMuted.collectAsState()
                val isSpeakerOn by voiceCall.isSpeakerOn.collectAsState()

                LaunchedEffect(peerNodeId) {
                    try {
                        voiceCall.startCall()
                    } catch (e: Exception) {
                        Log.e("NavGraph", "Error starting call: ${e.message}")
                    }
                }

                VoiceCallScreen(
                    peerFingerprint = peerFingerprint,
                    isConnected = callState == com.zilch.blemesh.voice.BleVoiceCall.CallState.ACTIVE,
                    callDuration = callDuration,
                    isMuted = isMuted,
                    isSpeakerOn = isSpeakerOn,
                    onToggleMute = { voiceCall.toggleMute() },
                    onToggleSpeaker = { voiceCall.toggleSpeaker() },
                    onEndCall = {
                        voiceCall.endCall()
                        navController.popBackStack()
                    },
                    onBack = {
                        voiceCall.endCall()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
