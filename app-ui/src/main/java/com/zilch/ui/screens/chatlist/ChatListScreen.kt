package com.zilch.ui.screens.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

data class ChatPreview(
    val contactNodeId: String,
    val fingerprint: String,
    val lastMessage: String,
    val lastMessageTimeMs: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

private fun formatTime(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "ahora"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
            "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
        }
    }
}

private fun initials(text: String): String {
    val clean = text.replace(" ", "")
    return if (clean.length >= 2) clean.take(2).uppercase() else clean.uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatPreview>,
    onChatClick: (String) -> Unit,
    onScanQr: () -> Unit,
    onShowQr: () -> Unit
) {
    var fabExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkPalette.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Zilch",
                    color = DarkPalette.onBackground,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 22.sp
                )
            },
            actions = {
                IconButton(onClick = onScanQr) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR", tint = DarkPalette.primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkPalette.surface,
                titleContentColor = DarkPalette.onBackground
            )
        )

        // ═══ BARRA DE BÚSQUEDA ═══
        if (isSearchActive || chats.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = DarkPalette.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = DarkPalette.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Buscar chats...", color = DarkPalette.textMuted, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = DarkPalette.primary
                        )
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = DarkPalette.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (chats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = DarkPalette.surfaceVariant,
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Sin contactos",
                            color = DarkPalette.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Escanea un QR para empezar", color = DarkPalette.textMuted, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onScanQr,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkPalette.primary)
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ESCANEAR QR", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val filteredChats = if (searchQuery.isBlank()) chats
                else chats.filter {
                    it.fingerprint.contains(searchQuery, ignoreCase = true) ||
                            it.lastMessage.contains(searchQuery, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(filteredChats, key = { it.contactNodeId }) { chat ->
                        ChatListItem(chat = chat, onClick = { onChatClick(chat.contactNodeId) })
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (fabExpanded) {
                    SmallFloatingActionButton(
                        onClick = { fabExpanded = false; onScanQr() },
                        containerColor = DarkPalette.surfaceVariant,
                        contentColor = DarkPalette.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR") }
                    SmallFloatingActionButton(
                        onClick = { fabExpanded = false; onShowQr() },
                        containerColor = DarkPalette.surfaceVariant,
                        contentColor = DarkPalette.secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) { Icon(Icons.Filled.Shield, contentDescription = "Show QR") }
                }
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = DarkPalette.primary,
                    contentColor = DarkPalette.background
                ) { Icon(Icons.Filled.Add, contentDescription = "Add contact") }
            }
        }
    }
}

@Composable
private fun ChatListItem(chat: ChatPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(DarkPalette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(DarkPalette.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials(chat.fingerprint),
                color = DarkPalette.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(DarkPalette.background)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(DarkPalette.secondary))
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                chat.fingerprint,
                color = DarkPalette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                chat.lastMessage,
                color = DarkPalette.textMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(chat.lastMessageTimeMs), color = DarkPalette.textMuted, fontSize = 12.sp)
            if (chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(DarkPalette.secondary),
                    contentAlignment = Alignment.Center
                ) { Text("${chat.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
