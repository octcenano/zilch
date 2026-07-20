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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult

/**
 * Modelo de mensaje para el chat cercano.
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isFromLocal: Boolean,
    val timestamp: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val sharedFile: SharedFile? = null
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
    onCallClick: (() -> Unit)? = null,
    onEmergencyTriggered: (() -> Unit)? = null,
    onSendMessage: ((String) -> Unit)? = null,
    onSendFile: ((Uri) -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    // Sync external messages into local list (handles initial load and new incoming)
    LaunchedEffect(messages) {
        val currentIds = chatMessages.map { it.id }.toSet()
        messages.filter { it.id !in currentIds }.forEach { chatMessages.add(it) }
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
                    onCallClick?.let { callClick ->
                        IconButton(onClick = callClick) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = "Llamada de voz",
                                tint = DarkPalette.secondary
                            )
                        }
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
                        // Botón de adjuntar archivo
                        val filePickerLauncher = rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                        ) { uri ->
                            uri?.let {
                                onSendFile?.invoke(it)
                            }
                        }

                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = isConnected
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Adjuntar archivo",
                                tint = if (isConnected) DarkPalette.primary else DarkPalette.textMuted
                            )
                        }

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
                                    onSendMessage?.invoke(userText)
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
                        if (message.sharedFile != null) {
                            FileBubble(
                                file = message.sharedFile,
                                isFromLocal = message.isFromLocal,
                                timestamp = message.timestamp
                            )
                        } else {
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
}

/**
 * FileBubble — Burbuja de archivo compartido en el chat.
 *
 * Muestra icono del tipo de archivo, nombre, tamaño y timestamp.
 */
@Composable
private fun FileBubble(
    file: SharedFile,
    isFromLocal: Boolean,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isFromLocal) {
        DarkPalette.primary.copy(alpha = 0.15f)
    } else {
        DarkPalette.surfaceVariant
    }

    val alignment = if (isFromLocal) Alignment.End else Alignment.Start

    // Icono según tipo de archivo
    val fileIcon = when {
        file.mimeType.startsWith("image/") -> Icons.Default.Image
        file.mimeType.startsWith("video/") -> Icons.Default.VideoFile
        file.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        file.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
        else -> Icons.Default.InsertDriveFile
    }

    val iconTint = when {
        file.mimeType.startsWith("image/") -> DarkPalette.secondary
        file.mimeType.startsWith("video/") -> DarkPalette.tertiary
        file.mimeType.contains("pdf") -> DarkPalette.error
        else -> DarkPalette.primary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isFromLocal) 18.dp else 4.dp,
                        bottomEnd = if (isFromLocal) 4.dp else 18.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Icono del archivo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = fileIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Info del archivo
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.onBackground,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = SharedFile.formatFileSize(file.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkPalette.textMuted
                    )
                }
            }
        }

        // Timestamp debajo de la burbuja
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = if (isFromLocal) Arrangement.End else Arrangement.Start
        ) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = DarkPalette.textMuted,
                fontSize = 10.sp
            )
        }
    }
}
