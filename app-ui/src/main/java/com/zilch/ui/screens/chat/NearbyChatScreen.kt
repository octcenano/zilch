package com.zilch.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.components.ChatBubble
import com.zilch.ui.theme.DarkPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modelo de mensaje para el chat cercano.
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isFromLocal: Boolean,
    val timestamp: String
)

/**
 * NearbyChatScreen — Chat cercano por BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  LAYOUT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ┌──────────────────────────────────┐
 * │  ← a3f2-8b1c    [🔵 BLE]       │
 * ├──────────────────────────────────┤
 * │                                  │
 * │        "Hola, estoy cerca"       │  ← Mensajes locales (derecha)
 * │                        14:32    │
 * │                                  │
 * │  "¡Hola! ¿Puedes escuchar?"     │  ← Mensajes del peer (izquierda)
 * │  14:33                           │
 * │                                  │
 * │        "Sí, perfecto"            │
 * │                        14:33    │
 * │                                  │
 * ├──────────────────────────────────┤
 * │  [Escribe un mensaje...    ] [➤] │  ← Input
 * └──────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyChatScreen(
    peerFingerprint: String,
    messages: List<ChatMessage> = emptyList(),
    isConnected: Boolean,
    onBack: () -> Unit,
    onEmergencyTriggered: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // Initialize internal list from passed-in messages
    LaunchedEffect(messages) {
        if (chatMessages.isEmpty() && messages.isNotEmpty()) {
            chatMessages.addAll(messages)
        }
    }

    // Auto-scroll al último mensaje
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Indicador de conexión
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isConnected) DarkPalette.torActive
                                    else DarkPalette.torInactive
                                )
                        )
                        Column {
                            Text(
                                text = peerFingerprint,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (isConnected) "BLE Conectado" else "Desconectado",
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkPalette.textMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Navigate to voice call */ }) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Llamada de voz",
                            tint = DarkPalette.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkPalette.surface,
                    titleContentColor = DarkPalette.onBackground
                )
            )
        },
        bottomBar = {
            Column {
                // ═══ BARRA DE EMERGENCIA ═══
                onEmergencyTriggered?.let { trigger ->
                    com.zilch.ui.components.EmergencyButton(
                        onEmergencyTriggered = trigger
                    )
                }
                // ═══ BARRA DE INPUT ═══
                Surface(
                    color = DarkPalette.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Escribe un mensaje...",
                                    color = DarkPalette.textMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkPalette.primary,
                                unfocusedBorderColor = DarkPalette.border,
                                cursorColor = DarkPalette.primary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4
                        )

                        FilledIconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val userText = inputText.trim()
                                    inputText = ""

                                    val timeNow = java.text.SimpleDateFormat(
                                        "HH:mm", java.util.Locale.getDefault()
                                    ).format(java.util.Date())

                                    chatMessages.add(
                                        ChatMessage(
                                            id = "local_${System.currentTimeMillis()}",
                                            content = userText,
                                            isFromLocal = true,
                                            timestamp = timeNow
                                        )
                                    )

                                    // Simulate a reply after 2 seconds
                                    val msgId = "reply_${System.currentTimeMillis()}"
                                    val capturedTime = timeNow
                                    kotlinx.coroutines.MainScope().launch {
                                        delay(2000L)
                                        chatMessages.add(
                                            ChatMessage(
                                                id = msgId,
                                                content = "[BLE] Mensaje recibido: $userText",
                                                isFromLocal = false,
                                                timestamp = capturedTime
                                            )
                                        )
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && isConnected,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = DarkPalette.primary,
                                contentColor = DarkPalette.onPrimary,
                                disabledContainerColor = DarkPalette.surfaceVariant
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ═══ LISTA DE MENSAJES ═══
            if (chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Mensajes cifrados end-to-end\nvía BLE cercano",
                        color = DarkPalette.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(chatMessages, key = { it.id }) { message ->
                        ChatBubble(
                            text = message.content,
                            isFromLocal = message.isFromLocal,
                            timestamp = message.timestamp
                        )
                    }
                }
            }
        }
    }
}
