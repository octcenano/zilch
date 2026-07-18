package com.zilch.ui.screens.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

/**
 * VoiceCallScreen — Llamada peer-to-peer por BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  FLUJO DE SEGURIDAD
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. Conexión BLE establecida → CipherSuite negociada
 * 2. Audio encriptado end-to-end con ChaCha20-Poly1305
 * 3. Fingerprint visible para verificación de identidad
 * 4. Sin grabación, sin metadata, sin servidor intermediario
 *
 * ⚠ La pantalla muestra el fingerprint del peer para que ambos
 * participantes puedan verificar que están hablando con la persona correcta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallScreen(
    peerFingerprint: String,
    isConnected: Boolean,
    callDuration: Int,  // seconds
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    onBack: () -> Unit
) {
    // ═══ Derivar iniciales del fingerprint ═══
    val initials = peerFingerprint
        .filter { it.isLetterOrDigit() }
        .take(2)
        .uppercase()

    // ═══ Formatear duración como MM:SS ═══
    val minutes = callDuration / 60
    val seconds = callDuration % 60
    val durationText = "%02d:%02d".format(minutes, seconds)

    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = { Text("Llamada BLE") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkPalette.background,
                    titleContentColor = DarkPalette.onBackground,
                    navigationIconContentColor = DarkPalette.onBackground
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ═══ CONTENIDO CENTRAL ═══
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ═══ AVATAR CIRCULAR — Fingerprint initials ═══
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(DarkPalette.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineLarge,
                        color = DarkPalette.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ═══ FINGERPRINT — Monospace ═══
                Text(
                    text = peerFingerprint,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkPalette.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ═══ ESTADO DE CONEXIÓN — Dot + text ═══
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Indicador de estado (punto verde/gris)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) DarkPalette.secondary
                                else DarkPalette.onSurfaceVariant
                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isConnected) "BLE Conectado" else "Conectando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) DarkPalette.secondary
                        else DarkPalette.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ═══ DURACIÓN DE LLAMADA ═══
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = DarkPalette.onBackground,
                    fontWeight = FontWeight.Light,
                    fontSize = 40.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ═══ CIFRADO END-TO-END ═══
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = DarkPalette.tertiary,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "Cifrada end-to-end",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkPalette.tertiary
                    )
                }
            }

            // ═══ BOTONES DE ACCIÓN — Parte inferior ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ═══ BOTÓN MUTE ═══
                FilledIconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isMuted) DarkPalette.primary
                        else DarkPalette.surfaceVariant,
                        contentColor = if (isMuted) DarkPalette.onPrimary
                        else DarkPalette.onSurfaceVariant
                    )
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Activar micrófono" else "Silenciar"
                    )
                }

                // ═══ BOTÓN SPEAKER ═══
                FilledIconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isSpeakerOn) DarkPalette.primary
                        else DarkPalette.surfaceVariant,
                        contentColor = if (isSpeakerOn) DarkPalette.onPrimary
                        else DarkPalette.onSurfaceVariant
                    )
                ) {
                    Icon(
                        if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp
                        else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isSpeakerOn) "Silenciar altavoz" else "Activar altavoz"
                    )
                }

                // ═══ BOTÓN FIN DE LLAMADA ═══
                FilledIconButton(
                    onClick = onEndCall,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DarkPalette.emergency,
                        contentColor = DarkPalette.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Finalizar llamada"
                    )
                }
            }
        }
    }
}
