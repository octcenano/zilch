package com.zilch.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.zilch.ui.theme.DarkPalette

/**
 * Modelo de contacto para la UI.
 */
data class ContactUi(
    val nodeId: String,
    val fingerprint: String,
    val addresses: List<String>,
    val contactCount: Int
)

/**
 * ContactsScreen — Lista de contactos verificados.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  LAYOUT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ┌──────────────────────────────────┐
 * │  ← Contactos                     │
 * ├──────────────────────────────────┤
 * │                                  │
 * │  ┌──────────────────────────┐   │
 * │  │ 👤 a3f2-8b1c-4d5e        │   │
 * │  │    Dirección: ble:xx     │   │
 * │  │    Contactos: 3          │   │
 * │  └──────────────────────────┘   │
 * │                                  │
 * │  ┌──────────────────────────┐   │
 * │  │ 👤 7e2a-1f3b-9c0d        │   │
 * │  │    Dirección: .onion     │   │
 * │  │    Contactos: 1          │   │
 * │  └──────────────────────────┘   │
 * │                                  │
 * └──────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<ContactUi>,
    onContactClick: (String) -> Unit,
    onBack: () -> Unit,
    onEmergencyTriggered: (() -> Unit)? = null
) {
    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = { Text("Contactos") },
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
        if (contacts.isEmpty()) {
            // ═══ ESTADO VACÍO ═══
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = DarkPalette.textMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Sin contactos",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkPalette.onSurfaceVariant
                    )
                    Text(
                        text = "Escanea un QR de otro usuario\npara añadir un contacto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.textMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // ═══ LISTA DE CONTACTOS ═══
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts, key = { it.nodeId }) { contact ->
                    ContactCard(
                        contact = contact,
                        onClick = { onContactClick(contact.nodeId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactCard(
    contact: ContactUi,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
            // Avatar con inicial del fingerprint
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        Modifier.background(
                            DarkPalette.primary.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.medium
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.fingerprint.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkPalette.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Info del contacto
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.fingerprint,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = contact.addresses.firstOrNull() ?: "Sin dirección",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkPalette.textMuted
                )
            }

            // Flecha
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = DarkPalette.onSurfaceVariant
            )
        }
    }
}
