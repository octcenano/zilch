package com.zilch.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.components.*
import com.zilch.ui.theme.DarkPalette

/**
 * HomeScreen — Pantalla principal de Zilch.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  LAYOUT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ┌──────────────────────────────────┐
 * │  [Indicador Tor Status]          │  ← Siempre visible arriba
 * ├──────────────────────────────────┤
 * │                                  │
 * │       [FINGERPRINT DISPLAY]      │  ← Tu identidad visual
 * │        a3f2                       │
 * │        8b1c                       │
 * │        4d5e                       │
 * │                                  │
 * │  ┌──────────┐  ┌──────────┐     │
 * │  │ RECIBIR  │  │ ESCANEAR │     │  ← Acciones principales
 * │  │   📱     │  │   📷    │     │
 * │  └──────────┘  └──────────┘     │
 * │                                  │
 * │  [Estado: X peers cercanos]      │  ← Info del mesh
 * │  [Sesiones activas: X]           │
 * │                                  │
 * ├──────────────────────────────────┤
 * │  [████ CERRAR Y DESTRUIR ████]  │  ← SIEMPRE visible abajo
 * └──────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToQrReceive: () -> Unit,
    onNavigateToQrScan: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToContacts: () -> Unit
) {
    val torStatus by viewModel.torStatus.collectAsState()
    val fingerprint by viewModel.fingerprint.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()
    val sessionCount by viewModel.sessionCount.collectAsState()

    // Dialog de confirmación de emergencia
    var showEmergencyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ZILCH",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkPalette.background,
                    titleContentColor = DarkPalette.onBackground
                ),
                actions = {
                    // Botón de contactos
                    IconButton(onClick = onNavigateToContacts) {
                        Icon(
                            Icons.Default.Contacts,
                            contentDescription = "Contactos",
                            tint = DarkPalette.onSurfaceVariant
                        )
                    }
                    // Botón de inbox
                    IconButton(onClick = onNavigateToInbox) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = "Bandeja",
                            tint = DarkPalette.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            // ═══ BARRA DE EMERGENCIA — SIEMPRE VISIBLE ═══
            EmergencyButton(
                onEmergencyTriggered = { showEmergencyDialog = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ═══ INDICADOR TOR ═══
            TorStatusIndicator(status = torStatus)

            Spacer(modifier = Modifier.height(12.dp))

            // ═══ FINGERPRINT ═══
            if (fingerprint.isNotEmpty() && fingerprint != "cargando...") {
                FingerprintDisplay(fingerprint = fingerprint)
            }

            Spacer(modifier = Modifier.weight(1f))

            // ═══ BOTONES PRINCIPALES ═══
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botón RECIBIR
                Button(
                    onClick = onNavigateToQrReceive,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkPalette.primary.copy(alpha = 0.15f),
                        contentColor = DarkPalette.primary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "RECIBIR",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Botón ESCANEAR
                Button(
                    onClick = onNavigateToQrScan,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkPalette.secondary.copy(alpha = 0.15f),
                        contentColor = DarkPalette.secondary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "ESCANEAR",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // ═══ INFO DEL MESH ═══
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DarkPalette.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$peerCount",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DarkPalette.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "PEERS CERCANOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkPalette.textMuted,
                            letterSpacing = 1.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$sessionCount",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DarkPalette.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CHATS ACTIVOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkPalette.textMuted,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // ═══ DIAGÁLOGO DE CONFIRMACIÓN DE EMERGENCIA ═══
    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            containerColor = DarkPalette.errorContainer,
            titleContentColor = DarkPalette.emergency,
            textContentColor = DarkPalette.onBackground,
            title = {
                Text(
                    text = "DESTRUIR TODO",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Esta acción destruirá tu identidad, todos los contactos, " +
                            "sesiones activas y activará el Kill Switch de red. " +
                            "Esta acción es IRREVERSIBLE."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyDialog = false
                        viewModel.emergencyDestroy()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkPalette.emergency
                    )
                ) {
                    Text("DESTRUIR", color = DarkPalette.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false }) {
                    Text("CANCELAR", color = DarkPalette.onSurfaceVariant)
                }
            }
        )
    }
}
