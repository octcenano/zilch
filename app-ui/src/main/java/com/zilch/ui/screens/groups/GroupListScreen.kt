package com.zilch.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

/**
 * GroupListScreen — Lista de grupos de chat estilo WhatsApp.
 *
 * Muestra los grupos existentes con su último mensaje,
 * y permite crear nuevos grupos seleccionando peers BLE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    groups: List<GroupInfo>,
    availablePeers: List<Pair<String, String>>, // (nodeId, fingerprint)
    onGroupClick: (String) -> Unit,
    onCreateGroup: (name: String, memberNodeIds: List<String>) -> Unit,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Grupos",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkPalette.surface,
                    titleContentColor = DarkPalette.onBackground,
                    navigationIconContentColor = DarkPalette.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = DarkPalette.primary,
                contentColor = DarkPalette.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crear grupo")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
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
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = DarkPalette.textMuted
                    )
                    Text(
                        text = "No hay grupos todavía",
                        style = MaterialTheme.typography.titleMedium,
                        color = DarkPalette.textMuted
                    )
                    Text(
                        text = "Toca + para crear un grupo",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkPalette.textMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    GroupListItem(
                        group = group,
                        onClick = { onGroupClick(group.groupId) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            availablePeers = availablePeers,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, members ->
                onCreateGroup(name, members)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun GroupListItem(
    group: GroupInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar del grupo
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DarkPalette.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = DarkPalette.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Información del grupo
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = DarkPalette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (group.lastMessage.isNotEmpty()) {
                Text(
                    text = group.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkPalette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "${group.members.size} miembros",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkPalette.textMuted
                )
            }
        }

        // Timestamp
        if (group.lastMessageTimeMs > 0) {
            Text(
                text = formatGroupTime(group.lastMessageTimeMs),
                style = MaterialTheme.typography.labelSmall,
                color = DarkPalette.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CreateGroupDialog(
    availablePeers: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCreate: (name: String, memberNodeIds: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkPalette.surface,
        titleContentColor = DarkPalette.onBackground,
        textContentColor = DarkPalette.onBackground,
        title = {
            Text(
                text = "Crear grupo",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Nombre del grupo") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkPalette.primary,
                        unfocusedBorderColor = DarkPalette.border,
                        cursorColor = DarkPalette.primary,
                        focusedLabelColor = DarkPalette.primary,
                        unfocusedLabelColor = DarkPalette.textMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (availablePeers.isEmpty()) {
                    Text(
                        text = "No hay peers BLE disponibles.\nDescubre peers para crear un grupo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkPalette.textMuted
                    )
                } else {
                    Text(
                        text = "Selecciona miembros:",
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkPalette.textMuted
                    )

                    availablePeers.forEach { (nodeId, fingerprint) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (nodeId in selectedMembers) {
                                        selectedMembers.remove(nodeId)
                                    } else {
                                        selectedMembers.add(nodeId)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = nodeId in selectedMembers,
                                onCheckedChange = { checked ->
                                    if (checked) selectedMembers.add(nodeId)
                                    else selectedMembers.remove(nodeId)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = DarkPalette.primary,
                                    uncheckedColor = DarkPalette.border,
                                    checkmarkColor = DarkPalette.onPrimary
                                )
                            )
                            Column {
                                Text(
                                    text = fingerprint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DarkPalette.onBackground,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                        onCreate(groupName.trim(), selectedMembers.toList())
                    }
                },
                enabled = groupName.isNotBlank() && selectedMembers.isNotEmpty()
            ) {
                Text(
                    text = "Crear",
                    color = if (groupName.isNotBlank() && selectedMembers.isNotEmpty())
                        DarkPalette.primary else DarkPalette.textMuted
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", color = DarkPalette.textMuted)
            }
        }
    )
}

private fun formatGroupTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000

    return when {
        minutes < 1 -> "Ahora"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> {
            val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            sdf.format(java.util.Date(timestampMs))
        }
    }
}
