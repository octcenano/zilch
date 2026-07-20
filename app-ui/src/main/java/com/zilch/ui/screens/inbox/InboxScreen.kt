package com.zilch.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

data class InboxMessage(
    val id: String,
    val senderFingerprint: String,
    val subject: String,
    val preview: String,
    val timestampMs: Long,
    val isRead: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    messages: List<InboxMessage>,
    onMessageClick: (String) -> Unit,
    onCompose: () -> Unit,
    onBack: () -> Unit,
    onEmergencyTriggered: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkPalette.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Bandeja .Onion")
                    Text("Mensajes por Tor", style = MaterialTheme.typography.labelSmall, color = DarkPalette.textMuted)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkPalette.background,
                titleContentColor = DarkPalette.onBackground
            )
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            tint = DarkPalette.textMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "Tu bandeja esta vacia",
                            style = MaterialTheme.typography.titleMedium,
                            color = DarkPalette.onSurfaceVariant
                        )
                        Text(
                            "Los mensajes que viajen por Tor\napareceran aqui",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkPalette.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        InboxMessageCard(message = message, onClick = { onMessageClick(message.id) })
                    }
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            ExtendedFloatingActionButton(
                onClick = onCompose,
                containerColor = DarkPalette.primary,
                contentColor = DarkPalette.onPrimary,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("Redactar", fontWeight = FontWeight.Bold) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxMessageCard(message: InboxMessage, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isRead) DarkPalette.surface else DarkPalette.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "De: ${message.senderFingerprint}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DarkPalette.primary,
                    fontFamily = FontFamily.Monospace
                )
                if (!message.isRead) {
                    Icon(
                        Icons.Default.FiberNew,
                        contentDescription = "Nuevo",
                        tint = DarkPalette.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = message.subject,
                style = MaterialTheme.typography.titleMedium,
                color = DarkPalette.onBackground,
                fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.SemiBold
            )
            Text(
                text = message.preview,
                style = MaterialTheme.typography.bodySmall,
                color = DarkPalette.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
