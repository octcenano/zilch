package com.zilch.ui

import android.os.Bundle
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
import com.zilch.ui.screens.chat.ChatMessage
import com.zilch.ui.screens.chatlist.ChatListScreen
import com.zilch.ui.screens.chatlist.ChatPreview
import com.zilch.ui.screens.contacts.ContactUi
import com.zilch.ui.screens.contacts.ContactsScreen
import com.zilch.ui.screens.home.HomeViewModel
import com.zilch.ui.screens.inbox.InboxMessage
import com.zilch.ui.screens.inbox.InboxScreen
import com.zilch.ui.screens.onboarding.OnboardingScreen
import com.zilch.ui.screens.qr.QrReceiveScreen
import com.zilch.ui.screens.qr.QrScanScreen
import com.zilch.ui.screens.qr.QrScanState
import com.zilch.ui.screens.settings.SettingsScreen
import com.zilch.ui.theme.ZilchTheme

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
    val bottomBarRoutes = listOf(Routes.CHATS, Routes.INBOX, Routes.SETTINGS)
    val showBottomBar = currentRoute in bottomBarRoutes

    // Datos de ejemplo (en una app real vendrían de los motores)
    val sampleChats = remember {
        listOf(
            ChatPreview(
                contactNodeId = "a3f2b81c",
                fingerprint = "a3f2-8b1c-4d5e",
                lastMessage = "Sí, te lo envío ahora por BLE",
                lastMessageTimeMs = System.currentTimeMillis() - 180_000,
                unreadCount = 2,
                isOnline = true
            ),
            ChatPreview(
                contactNodeId = "7e2a1f3b",
                fingerprint = "7e2a-1f3b-9c0d",
                lastMessage = "Nos vemos en el punto acordado...",
                lastMessageTimeMs = System.currentTimeMillis() - 7_200_000,
                unreadCount = 0,
                isOnline = false
            )
        )
    }

    val sampleMessages = remember {
        listOf(
            ChatMessage("1", "Hola, estoy cerca", true, "14:30"),
            ChatMessage("2", "¡Hola! ¿Puedes escuchar?", false, "14:31"),
            ChatMessage("3", "Sí, perfecto. ¿Tienes el archivo?", true, "14:32"),
            ChatMessage("4", "Sí, te lo envío ahora por BLE", false, "14:33"),
        )
    }

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
        NavHost(
            navController = navController,
            startDestination = Routes.ONBOARDING,
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

            // ═══ CHATS (principal — estilo WhatsApp) ═══
            composable(Routes.CHATS) {
                ChatListScreen(
                    chats = sampleChats,
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
                    else -> "Desconocido"
                }
                val torStatusColor = when (torStatus) {
                    com.zilch.ui.components.TorStatus.ACTIVE -> Color(0xFF3FB950)
                    com.zilch.ui.components.TorStatus.INACTIVE -> Color(0xFF8B949E)
                    com.zilch.ui.components.TorStatus.CHECKING -> Color(0xFFD29922)
                    com.zilch.ui.components.TorStatus.KILL_SWITCH -> Color(0xFFDA3633)
                    else -> Color(0xFF8B949E)
                }

                SettingsScreen(
                    fingerprint = fingerprint,
                    nodeId = homeViewModel.nodeId.collectAsState().value,
                    torStatusText = torStatusText,
                    torStatusColor = torStatusColor,
                    isKillSwitchActive = isKillSwitchActive,
                    onVerifyTor = { /* Verificar Tor */ },
                    onShowQr = { navController.navigate(Routes.QR_RECEIVE) },
                    onEmergencyDestroy = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
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
                    qrBitmap = null,
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
                var scannedData by remember { mutableStateOf<com.zilch.crypto.qr.QrDecoder.DecodedQr?>(null) }

                val onQrDetected: (String) -> Unit = remember {
                    { rawPayload: String ->
                        try {
                            val decoded = com.zilch.crypto.qr.QrDecoder.decode(rawPayload)
                            scannedData = decoded
                            scanState = QrScanState.Scanned(
                                fingerprint = decoded.fingerprint,
                                temporaryAddress = decoded.temporaryAddress
                            )
                        } catch (e: Exception) {
                            scanState = QrScanState.Error(
                                message = e.message ?: "Error al decodificar QR"
                            )
                        }
                    }
                }

                QrScanScreen(
                    scanState = scanState,
                    onConfirmContact = {
                        scanState = QrScanState.Confirmed
                    },
                    onRetryScan = {
                        scanState = QrScanState.Scanning
                        scannedData = null
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

            // ═══ CHAT CERCANO ═══
            composable(
                route = Routes.NEARBY_CHAT,
                arguments = listOf(
                    navArgument("peerNodeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val peerNodeId = backStackEntry.arguments?.getString("peerNodeId") ?: ""

                NearbyChatScreen(
                    peerFingerprint = "a3f2-8b1c-4d5e",
                    messages = sampleMessages,
                    isConnected = true,
                    onSendMessage = { text -> },
                    onBack = { navController.popBackStack() },
                    onEmergencyTriggered = {
                        homeViewModel.emergencyDestroy()
                        navController.navigate(Routes.CHATS) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
