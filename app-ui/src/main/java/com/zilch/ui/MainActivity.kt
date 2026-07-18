package com.zilch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zilch.ui.navigation.Routes
import com.zilch.ui.screens.chat.NearbyChatScreen
import com.zilch.ui.screens.contacts.ContactsScreen
import com.zilch.ui.screens.home.HomeScreen
import com.zilch.ui.screens.home.HomeViewModel
import com.zilch.ui.screens.inbox.InboxScreen
import com.zilch.ui.screens.qr.QrReceiveScreen
import com.zilch.ui.screens.qr.QrScanScreen
import com.zilch.ui.screens.chat.ChatMessage
import com.zilch.ui.screens.contacts.ContactUi
import com.zilch.ui.screens.inbox.InboxMessage
import com.zilch.ui.screens.qr.QrScanState
import com.zilch.ui.theme.ZilchTheme

/**
 * MainActivity — Actividad principal de Zilch.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  ARQUITECTURA DE LA UI
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * La app usa un único Activity con Navigation Compose (Single Activity).
 * Esto es intencional:
 *
 * 1. **Sin fragments:** Los fragments mantienen estado en el back stack
 *    y pueden sobrevivir más de lo deseado. Compose Navigation es más
 *    predecible y eficiente en memoria.
 *
 * 2. **Sin transiciones de Activity:** Las transiciones entre pantallas
 *    revelan información sobre la navegación en los logs del sistema.
 *    Un solo Activity minimiza esta superficie.
 *
 * 3. **Back stack controlado:** La app puede destruir el back stack
 *    completo en caso de emergencia sin dejarActivities huérfanas.
 *
 * FLUJO DE NAVEGACIÓN:
 *
 *                    ┌────────┐
 *                    │  HOME  │
 *                    └───┬────┘
 *           ┌────────────┼────────────┐
 *           ↓            ↓            ↓
 *     ┌──────────┐ ┌──────────┐ ┌──────────┐
 *     │ QR RECIVE│ │ QR SCAN  │ │  INBOX   │
 *     └──────────┘ └────┬─────┘ └──────────┘
 *                       ↓
 *                 ┌──────────┐
 *                 │ CONTACTS │
 *                 └────┬─────┘
 *                      ↓
 *                 ┌──────────┐
 *                 │NEARBY CHAT│
 *                 └──────────┘
 */
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

/**
 * NavHost principal de la app.
 */
@Composable
fun ZilchNavGraph() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        // ═══ PANTALLA DE INICIO ═══
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToQrReceive = {
                    navController.navigate(Routes.QR_RECEIVE)
                },
                onNavigateToQrScan = {
                    navController.navigate(Routes.QR_SCAN)
                },
                onNavigateToInbox = {
                    navController.navigate(Routes.INBOX)
                },
                onNavigateToContacts = {
                    navController.navigate(Routes.CONTACTS)
                },
                onNavigateToChat = { peerNodeId ->
                    navController.navigate(Routes.nearbyChat(peerNodeId))
                }
            )
        }

        // ═══ Recibir QR ═══
        composable(Routes.QR_RECEIVE) {
            val fingerprint by homeViewModel.fingerprint.collectAsState()

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
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ═══ ESCANEAR QR ═══
        composable(Routes.QR_SCAN) {
            var scanState by remember { mutableStateOf<QrScanState>(QrScanState.Scanning) }
            var scannedData by remember { mutableStateOf<com.zilch.crypto.qr.QrDecoder.DecodedQr?>(null) }

            // Callback para cuando CameraX/ZXing detecta un QR
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
                            message = e.message ?: "Error desconocido al decodificar QR"
                        )
                    }
                }
            }

            // TODO: Integrar CameraX + ZXing para escaneo real de QR.
            // El callback onQrDetected se invocaría desde el analyzer de CameraX.
            // Por ahora, el flujo queda preparado pero requiere
            // permisos de cámara y dependencia CameraX para funcionar.

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
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ═══ BANDEJA .ONION ═══
        composable(Routes.INBOX) {
            // Datos de ejemplo — en una app real se conectaría al TorClient
            val sampleMessages = remember {
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

            InboxScreen(
                messages = sampleMessages,
                onMessageClick = { /* Abrir mensaje */ },
                onCompose = { /* Redactar nuevo */ },
                onBack = { navController.popBackStack() },
                onEmergencyTriggered = {
                    homeViewModel.emergencyDestroy()
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ═══ CONTACTOS ═══
        composable(Routes.CONTACTS) {
            // Datos de ejemplo
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
                    navController.navigate(Routes.HOME) {
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

            // Datos de ejemplo — en una app real se conectaría al BleMeshEngine
            val sampleMessages = remember {
                listOf(
                    ChatMessage("1", "Hola, estoy cerca", true, "14:30"),
                    ChatMessage("2", "¡Hola! ¿Puedes escuchar?", false, "14:31"),
                    ChatMessage("3", "Sí, perfecto. ¿Tienes el archivo?", true, "14:32"),
                    ChatMessage("4", "Sí, te lo envío ahora por BLE", false, "14:33"),
                )
            }

            NearbyChatScreen(
                peerFingerprint = "a3f2-8b1c-4d5e",
                messages = sampleMessages,
                isConnected = true,
                onSendMessage = { text ->
                    // En una app real: BleMeshEngine.sendMessage(peerNodeId, text)
                },
                onBack = { navController.popBackStack() },
                onEmergencyTriggered = {
                    homeViewModel.emergencyDestroy()
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
