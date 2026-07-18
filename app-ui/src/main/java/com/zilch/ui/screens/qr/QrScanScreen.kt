package com.zilch.ui.screens.qr

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.components.FingerprintDisplay
import com.zilch.ui.theme.DarkPalette

/**
 * Estados del flujo de escaneo QR.
 */
sealed class QrScanState {
    /** Esperando escaneo */
    object Scanning : QrScanState()
    /** QR escaneado, mostrando fingerprint para verificación */
    data class Scanned(
        val fingerprint: String,
        val temporaryAddress: String
    ) : QrScanState()
    /** Contacto confirmado y añadido */
    object Confirmed : QrScanState()
    /** Error en el escaneo */
    data class Error(val message: String) : QrScanState()
}

/**
 * QrScanScreen — Cámara para escanear QR de otro dispositivo.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  FLUJO DE SEGURIDAD
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. Cámara activa → Detecta QR
 * 2. QR decodificado → Valida firma + expiración
 * 3. Muestra fingerprint → Usuario compara verbalmente
 * 4. Usuario confirma → Contacto añadido a la sesión
 *
 * ⚠ El paso 3 es CRÍTICO: sin verificación verbal del fingerprint,
 * un atacante podría colocar un QR falso en el punto de escaneo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    scanState: QrScanState,
    onConfirmContact: () -> Unit,
    onRetryScan: () -> Unit,
    onBack: () -> Unit,
    onEmergencyTriggered: (() -> Unit)? = null
) {
    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = { Text("Escanear QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkPalette.background,
                    titleContentColor = DarkPalette.onBackground
                )
            )
        },
        bottomBar = {
            onEmergencyTriggered?.let { trigger ->
                com.zilch.ui.components.EmergencyButton(
                    onEmergencyTriggered = trigger
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (scanState) {
                is QrScanState.Scanning -> {
                    // ═══ ESTADO: ESCANEANDO ═══
                    // En una app real, aquí iría CameraX preview
                    // Por ahora mostramos un placeholder
                    Card(
                        modifier = Modifier
                            .size(300.dp)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = DarkPalette.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CÁMARA ACTIVA\n\nApunta al QR del otro dispositivo",
                                color = DarkPalette.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Esperando código QR...",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkPalette.onSurfaceVariant
                    )
                }

                is QrScanState.Scanned -> {
                    // ═══ ESTADO: ESCANEADO — VERIFICAR FINGERPRINT ═══
                    Text(
                        text = "QR RECIBIDO — VERIFICA",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DarkPalette.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Pide a tu contacto que lea en voz alta\nel fingerprint que aparece en su pantalla.\n\nCompara con lo que ves aquí:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Fingerprint para comparar
                    FingerprintDisplay(
                        fingerprint = scanState.fingerprint,
                        label = "FINGERPRINT DEL CONTACTO"
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Botones de confirmación
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Rechazar
                        OutlinedButton(
                            onClick = onRetryScan,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DarkPalette.error
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("NO COINCIDE")
                        }

                        // Confirmar
                        Button(
                            onClick = onConfirmContact,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkPalette.secondary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COINCIDE", color = DarkPalette.onSecondary)
                        }
                    }
                }

                is QrScanState.Confirmed -> {
                    // ═══ ESTADO: CONFIRMADO ═══
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DarkPalette.secondary,
                        modifier = Modifier.size(72.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CONTACTO AÑADIDO",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DarkPalette.secondary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Ya puedes enviar mensajes cercanos\npor Bluetooth",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                is QrScanState.Error -> {
                    // ═══ ESTADO: ERROR ═══
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = DarkPalette.error,
                        modifier = Modifier.size(72.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ERROR DE VERIFICACIÓN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DarkPalette.error,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = scanState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onRetryScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkPalette.primary
                        )
                    ) {
                        Text("REINTENTAR", color = DarkPalette.onPrimary)
                    }
                }
            }
        }
    }
}
