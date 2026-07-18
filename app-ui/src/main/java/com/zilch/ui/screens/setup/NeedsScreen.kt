package com.zilch.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFF0D1117)
private val SurfaceColor = Color(0xFF161B22)
private val Primary = Color(0xFF58A6FF)
private val Green = Color(0xFF3FB950)
private val TextMuted = Color(0xFF8B949E)
private val OnBackground = Color(0xFFE6EDF3)

private enum class Status {
    GRANTED,
    AVAILABLE,
    CONDITIONAL,
    INFO,
}

private data class Requirement(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val status: Status,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsScreen(
    hasBluetooth: Boolean = false,
    hasCamera: Boolean = false,
    hasLocation: Boolean = false,
    isAndroid12OrAbove: Boolean = true,
    hasTor: Boolean = false,
    onComplete: () -> Unit,
) {
    val requirements = listOf(
        Requirement(
            icon = Icons.Default.Bluetooth,
            label = "Bluetooth LE",
            description = "Para conectar con dispositivos cercanos",
            status = if (hasBluetooth) Status.GRANTED else Status.AVAILABLE,
        ),
        Requirement(
            icon = Icons.Default.CameraAlt,
            label = "Cámara",
            description = "Para escanear códigos QR de contacto",
            status = if (hasCamera) Status.GRANTED else Status.AVAILABLE,
        ),
        Requirement(
            icon = Icons.Default.MyLocation,
            label = "Ubicación",
            description = if (isAndroid12OrAbove) {
                "No necesaria en Android 12+"
            } else {
                "Necesaria para escanear Bluetooth (solo Android 11 y anterior)"
            },
            status = when {
                isAndroid12OrAbove -> Status.CONDITIONAL
                hasLocation -> Status.GRANTED
                else -> Status.AVAILABLE
            },
        ),
        Requirement(
            icon = Icons.Default.Security,
            label = "Tor / Internet",
            description = "Para comunicaciones anónimas cifradas (opcional)",
            status = if (hasTor) Status.INFO else Status.INFO,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Title
            Text(
                text = "Zilch necesita acceso a:",
                color = OnBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Revisa que todo esté listo antes de empezar",
                color = TextMuted,
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Requirements list
            requirements.forEach { req ->
                RequirementRow(requirement = req)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // EMPEZAR button
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = Color.White,
                ),
                shape = MaterialTheme.shapes.large,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            ) {
                Text(
                    text = "EMPEZAR",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

@Composable
private fun RequirementRow(requirement: Requirement) {
    val dotColor = when (requirement.status) {
        Status.GRANTED -> Green
        Status.AVAILABLE -> Color(0xFFF0C543) // yellow
        Status.CONDITIONAL -> TextMuted
        Status.INFO -> Primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, shape = MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Icon
        Icon(
            imageVector = requirement.icon,
            contentDescription = null,
            tint = dotColor,
            modifier = Modifier.size(24.dp),
        )

        // Label + description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = requirement.label,
                color = OnBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = requirement.description,
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        // Status indicator dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}
