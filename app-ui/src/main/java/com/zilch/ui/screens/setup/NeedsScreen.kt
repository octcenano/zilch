package com.zilch.ui.screens.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.zilch.ui.theme.DarkPalette

private enum class Status {
    GRANTED,
    AVAILABLE,
    CONDITIONAL,
    INFO,
    REQUESTING,
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
    onComplete: () -> Unit,
) {
    // ═══ Estado de permisos en tiempo real ═══
    var bluetoothGranted by remember { mutableStateOf(false) }
    var cameraGranted by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(false) }

    val isAndroid12OrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // ═══ Launchers de permisos ═══
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        bluetoothGranted = permissions.values.any { it }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationGranted = granted
    }

    // ═══ Función para solicitar permisos ═══
    fun requestBluetooth() {
        if (isAndroid12OrAbove) {
            bluetoothLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                )
            )
        } else {
            bluetoothLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                )
            )
        }
    }

    fun requestCamera() {
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    fun requestLocation() {
        if (!isAndroid12OrAbove) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // ═══ Lista de requisitos ═══
    val requirements = listOf(
        Requirement(
            icon = Icons.Default.Bluetooth,
            label = "Bluetooth LE",
            description = "Para conectar con dispositivos cercanos",
            status = when {
                bluetoothGranted -> Status.GRANTED
                else -> Status.AVAILABLE
            },
        ),
        Requirement(
            icon = Icons.Default.CameraAlt,
            label = "Cámara",
            description = "Para escanear códigos QR de contacto",
            status = when {
                cameraGranted -> Status.GRANTED
                else -> Status.AVAILABLE
            },
        ),
        Requirement(
            icon = Icons.Default.MyLocation,
            label = "Ubicación",
            description = if (isAndroid12OrAbove) {
                "No necesaria en Android 12+"
            } else {
                "Necesaria para escanear Bluetooth"
            },
            status = when {
                isAndroid12OrAbove -> Status.CONDITIONAL
                locationGranted -> Status.GRANTED
                else -> Status.AVAILABLE
            },
        ),
        Requirement(
            icon = Icons.Default.Security,
            label = "Tor / Internet",
            description = "Para comunicaciones anónimas (opcional)",
            status = Status.INFO,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkPalette.background)
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
                color = DarkPalette.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Toca cada permiso para concederlo",
                color = DarkPalette.textMuted,
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Requirements list — cada uno es clickeable
            requirements.forEachIndexed { index, req ->
                val onClickAction: () -> Unit = when (index) {
                    0 -> {
                        {
                            if (!bluetoothGranted) {
                                if (isAndroid12OrAbove) {
                                    bluetoothLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_ADVERTISE,
                                        )
                                    )
                                } else {
                                    bluetoothLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH,
                                            Manifest.permission.BLUETOOTH_ADMIN,
                                        )
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        {
                            if (!cameraGranted) {
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }

                    2 -> {
                        {
                            if (!isAndroid12OrAbove && !locationGranted) {
                                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    }

                    else -> {
                        {}
                    }
                }

                RequirementRow(
                    requirement = req,
                    onClick = onClickAction,
                    isClickable = req.status == Status.AVAILABLE
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info de privacidad
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkPalette.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = DarkPalette.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Zilch NO almacena ningún dato en la nube. " +
                                "Todo queda en tu dispositivo. Puedes saltarte " +
                                "los permisos y darlos después desde Ajustes.",
                        color = DarkPalette.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // EMPEZAR button
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkPalette.primary,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequirementRow(
    requirement: Requirement,
    onClick: () -> Unit = {},
    isClickable: Boolean = false,
) {
    val dotColor = when (requirement.status) {
        Status.GRANTED -> DarkPalette.secondary
        Status.AVAILABLE -> Color(0xFFF0C543)
        Status.CONDITIONAL -> DarkPalette.textMuted
        Status.INFO -> DarkPalette.primary
        Status.REQUESTING -> Color(0xFFF0C543)
    }

    Card(
        onClick = if (isClickable) onClick else {
            {}
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DarkPalette.surface,
        ),
        enabled = isClickable
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = requirement.icon,
                contentDescription = null,
                tint = dotColor,
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = requirement.label,
                    color = DarkPalette.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = requirement.description,
                    color = DarkPalette.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            // Status indicator
            when (requirement.status) {
                Status.GRANTED -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Concedido",
                        tint = DarkPalette.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Status.AVAILABLE -> {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "Toca para conceder",
                        tint = Color(0xFFF0C543),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Status.CONDITIONAL -> {
                    Text(
                        text = "N/A",
                        color = DarkPalette.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Status.INFO -> {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = DarkPalette.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Status.REQUESTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = dotColor,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}
