package com.zilch.ui.screens.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Theme colors
// ---------------------------------------------------------------------------

private val Background = Color(0xFF0D1117)
private val Surface = Color(0xFF161B22)
private val SurfaceVariant = Color(0xFF21262D)
private val Primary = Color(0xFF58A6FF)
private val Secondary = Color(0xFF3FB950)
private val OnBackground = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B949E)
private val Emergency = Color(0xFFDA3633)

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

data class ChatPreview(
    val contactNodeId: String,
    val fingerprint: String,
    val lastMessage: String,
    val lastMessageTimeMs: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val m = cal.get(java.util.Calendar.MINUTE)
            "%02d:%02d".format(h, m)
        }
    }
}

private fun initials(text: String): String {
    val clean = text.replace(" ", "")
    return if (clean.length >= 2) clean.take(2).uppercase() else clean.uppercase()
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatPreview>,
    onChatClick: (String) -> Unit,
    onScanQr: () -> Unit,
    onShowQr: () -> Unit
) {
    var fabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Zilch",
                        color = OnBackground,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: search */ }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = TextMuted
                        )
                    }
                    IconButton(onClick = onScanQr) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = Primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnBackground
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (fabExpanded) {
                    SmallFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            onScanQr()
                        },
                        containerColor = SurfaceVariant,
                        contentColor = Primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "Scan QR"
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            onShowQr()
                        },
                        containerColor = SurfaceVariant,
                        contentColor = Secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Show QR"
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = Primary,
                    contentColor = Background
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add contact"
                    )
                }
            }
        }
    ) { paddingValues ->
        if (chats.isEmpty()) {
            // ---- Empty state ----
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = SurfaceVariant,
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Sin contactos",
                        color = OnBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Escanea un QR para empezar",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // ---- Chat list ----
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Background),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(chats, key = { it.contactNodeId }) { chat ->
                    ChatListItem(
                        chat = chat,
                        onClick = { onChatClick(chat.contactNodeId) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat list item
// ---------------------------------------------------------------------------

@Composable
private fun ChatListItem(
    chat: ChatPreview,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ---- Avatar ----
        Box(modifier = Modifier.size(52.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials(chat.fingerprint),
                    color = Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Online indicator
            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Secondary)
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                )
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Surface)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Secondary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ---- Text content ----
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            // Fingerprint (name)
            Text(
                text = chat.fingerprint,
                color = OnBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Last message preview
            Text(
                text = chat.lastMessage,
                color = TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ---- Right column: time + badge ----
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = formatTime(chat.lastMessageTimeMs),
                color = TextMuted,
                fontSize = 12.sp
            )

            if (chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (chat.unreadCount > 99) "99+" else "${chat.unreadCount}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
