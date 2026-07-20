package com.zilch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

/**
 * EmergencyButton — Barra roja persistente de "Cerrar y Destruir".
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: BOTÓN DE PÁNICO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El botón de emergencia está SIEMPRE visible en la parte inferior
 * de la pantalla. No se puede ocultar, minimizar o cubrir.
 *
 * Flujo de activación (doble toque):
 * 1. Primer toque → Señal de confirmación ("Toque de nuevo para destruir")
 * 2. Segundo toque → Destruye identidad, contactos, Kill Switch
 *
 * El doble toque previene activaciones accidentales mientras mantiene
 * la accesibilidad: no requiere precisión fina, solo dos toques simples.
 *
 * El botón usa un gradiente rojo oscuro que se intensifica
 * con la interacción, proporcionando feedback visual claro.
 */
@Composable
fun EmergencyButton(
    onEmergencyTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirming by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (confirming) DarkPalette.emergency else DarkPalette.emergencyDark,
        animationSpec = tween(200),
        label = "bg_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        backgroundColor.copy(alpha = 0.8f),
                        backgroundColor
                    )
                )
            )
            .clickable {
                if (confirming) {
                    onEmergencyTriggered()
                } else {
                    confirming = true
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = DarkPalette.onError.copy(alpha = pulseAlpha),
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = if (confirming) "TOQUE DE NUEVO PARA DESTRUIR" else "CERRAR Y DESTRUIR",
                color = DarkPalette.onError,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
