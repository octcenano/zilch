package com.zilch.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palette ──────────────────────────────────────────────────────────────
private val BgColor = Color(0xFF0D1117)
private val SurfaceColor = Color(0xFF161B22)
private val SurfaceVariant = Color(0xFF21262D)
private val Primary = Color(0xFF58A6FF)
private val Secondary = Color(0xFF3FB950)
private val OnBackground = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B949E)
private val Emergency = Color(0xFFDA3633)
private val Warning = Color(0xFFD29922)

// ── Helpers ──────────────────────────────────────────────────────────────

/** Truncate the node-id to the first 8 + last 4 characters for compact display. */
private fun truncateNodeId(id: String): String {
    if (id.length <= 16) return id
    return "${id.take(8)}…${id.takeLast(4)}"
}

/** Split a long fingerprint into two visual lines for compact display. */
private fun fingerprintPreview(fingerprint: String): String {
    val clean = fingerprint.replace("\\s".toRegex(), "")
    if (clean.length <= 32) return clean
    val half = clean.length / 2
    return "${clean.substring(0, half)}\n${clean.substring(half)}"
}

// ── Section header ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

// ── Generic settings row ─────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    label: String,
    labelColor: Color = OnBackground,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val rowMod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowMod
            .background(SurfaceColor)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.width(14.dp))

        // Label
        Text(
            text = label,
            color = labelColor,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )

        // Trailing
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Status dot ───────────────────────────────────────────────────────────

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}

// ── Divider between rows in a section ────────────────────────────────────

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(horizontal = 20.dp)
            .background(SurfaceVariant.copy(alpha = 0.6f))
    )
}

// ── Main screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    fingerprint: String,
    nodeId: String,
    torStatusText: String,
    torStatusColor: Color,
    isKillSwitchActive: Boolean,
    onVerifyTor: () -> Unit,
    onShowQr: () -> Unit,
    onEmergencyDestroy: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var expandedFingerprint by remember { mutableStateOf(false) }
    var showDestroyConfirm by remember { mutableStateOf(false) }

    // ── Destroy confirmation dialog ──────────────────────────────────────
    if (showDestroyConfirm) {
        AlertDialog(
            onDismissRequest = { showDestroyConfirm = false },
            containerColor = SurfaceColor,
            titleContentColor = Emergency,
            title = { Text("Destruir todos los datos", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Esta acción es irreversible. Se eliminarán todas las llaves, mensajes y configuración de este dispositivo.",
                    color = OnBackground
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDestroyConfirm = false
                        onEmergencyDestroy()
                    }
                ) {
                    Text("Destruir", color = Emergency, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestroyConfirm = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            }
        )
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Configuración", color = OnBackground, fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BgColor
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ─────────────────────────────────────────────────────────────
            //  PROFILE CARD
            // ─────────────────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Compact fingerprint (2-line monospace)
                    Text(
                        text = fingerprintPreview(fingerprint),
                        color = Secondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                SurfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Copy button
                    TextButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(fingerprint)
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Copiar", color = Primary, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(4.dp))

                    // Node ID
                    Text(
                        text = "Node: ${truncateNodeId(nodeId)}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─────────────────────────────────────────────────────────────
            //  SECCIÓN — Seguridad
            // ─────────────────────────────────────────────────────────────
            SectionHeader("Seguridad")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
            ) {
                // Estado de Tor
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = torStatusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Estado de Tor",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(torStatusColor)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = torStatusText,
                                color = torStatusColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                )

                RowDivider()

                // Kill Switch
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (isKillSwitchActive) Secondary else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Kill Switch",
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isKillSwitchActive)
                                Secondary.copy(alpha = 0.15f)
                            else
                                SurfaceVariant
                        ) {
                            Text(
                                text = if (isKillSwitchActive) "Activo" else "Inactivo",
                                color = if (isKillSwitchActive) Secondary else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 4.dp
                                )
                            )
                        }
                    }
                )

                RowDivider()

                // Verificar conexión Tor
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.WifiFind,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Verificar conexión Tor",
                    labelColor = Primary,
                    onClick = onVerifyTor
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─────────────────────────────────────────────────────────────
            //  SECCIÓN — Identidad
            // ─────────────────────────────────────────────────────────────
            SectionHeader("Identidad")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
            ) {
                // Mostrar QR
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Mostrar QR",
                    onClick = onShowQr
                )

                RowDivider()

                // Ver fingerprint completo
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Ver fingerprint completo",
                    trailing = {
                        Icon(
                            imageVector = if (expandedFingerprint)
                                Icons.Filled.ExpandLess
                            else
                                Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { expandedFingerprint = !expandedFingerprint }
                )

                // Expanded fingerprint
                if (expandedFingerprint) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceVariant)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = fingerprint,
                            color = Secondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(fingerprint)
                                )
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Copiar fingerprint completo", color = Primary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─────────────────────────────────────────────────────────────
            //  SECCIÓN — Almacenamiento
            // ─────────────────────────────────────────────────────────────
            SectionHeader("Almacenamiento")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
            ) {
                // Base de datos cifrada
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Base de datos cifrada",
                    trailing = {
                        Text(
                            text = "Activa",
                            color = Secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )

                RowDivider()

                // Destruir todos los datos
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = Emergency,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Destruir todos los datos",
                    labelColor = Emergency,
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Emergency,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = { showDestroyConfirm = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─────────────────────────────────────────────────────────────
            //  SECCIÓN — Acerca de
            // ─────────────────────────────────────────────────────────────
            SectionHeader("Acerca de")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceColor)
            ) {
                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Zilch v0.1.0-alpha",
                    labelColor = TextMuted
                )

                RowDivider()

                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "FOSS — Código abierto",
                    labelColor = TextMuted
                )

                RowDivider()

                SettingsRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Gavel,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "Licencia MIT",
                    labelColor = TextMuted
                )
            }

            // Bottom spacing
            Spacer(Modifier.height(32.dp))
        }
    }
}
