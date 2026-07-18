package com.zilch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zilch.ui.theme.DarkPalette

/**
 * Estados del indicador de Tor.
 */
enum class TorStatus {
    ACTIVE,         // Tor funciona correctamente
    INACTIVE,       // Tor no disponible
    CHECKING,       // Verificando conexión
    KILL_SWITCH     // Kill Switch activado
}

/**
 * TorStatusIndicator — Indicador visual del estado de Tor.
 *
 * Se muestra en la parte superior de la pantalla de inicio.
 * Cambia de color y texto según el estado actual.
 *
 * CHECKING se muestra como una barra horizontal sutil en lugar
 * de una tarjeta completa para no alarmar al usuario.
 */
@Composable
fun TorStatusIndicator(
    status: TorStatus,
    modifier: Modifier = Modifier
) {
    // CHECKING: subtle info bar, not a full card
    if (status == TorStatus.CHECKING) {
        val animatedColor by animateColorAsState(
            targetValue = DarkPalette.torChecking,
            animationSpec = tween(300),
            label = "tor_checking_color"
        )

        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkPalette.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Small animated dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
            )

            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = animatedColor,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = "Verificando conexión...",
                style = MaterialTheme.typography.bodySmall,
                color = DarkPalette.textMuted,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    // All other states: full card (original behavior)
    val (color, icon, text) = when (status) {
        TorStatus.ACTIVE -> Triple(
            DarkPalette.torActive,
            Icons.Default.CheckCircle,
            "Tor Activo"
        )

        TorStatus.INACTIVE -> Triple(
            DarkPalette.torInactive,
            Icons.Default.Cancel,
            "Tor Inactivo"
        )

        TorStatus.CHECKING -> Triple(
            DarkPalette.torChecking,
            Icons.Default.Sync,
            "Verificando..."
        )

        TorStatus.KILL_SWITCH -> Triple(
            DarkPalette.emergency,
            Icons.Default.Shield,
            "KILL SWITCH ACTIVO"
        )
    }

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "tor_color"
    )

    // Pulse animation for active state
    val infiniteTransition = rememberInfiniteTransition(label = "tor_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkPalette.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Indicador pulsante
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        animatedColor.copy(
                            alpha = if (status == TorStatus.ACTIVE) pulseScale else 1f
                        )
                    )
            )

            // Icono
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = animatedColor,
                modifier = Modifier.size(20.dp)
            )

            // Texto de estado
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = animatedColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            // Indicador de Kill Switch
            if (status == TorStatus.KILL_SWITCH) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alerta",
                    tint = DarkPalette.emergency,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
