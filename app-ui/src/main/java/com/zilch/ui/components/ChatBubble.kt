package com.zilch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

/**
 * ChatBubble — Burbuja de mensaje para el chat cercano (BLE).
 *
 * Los mensajes locales aparecen a la derecha (cian).
 * Los mensajes del peer aparecen a la izquierda (gris).
 */
@Composable
fun ChatBubble(
    text: String,
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
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isFromLocal) 16.dp else 4.dp,
                        bottomEnd = if (isFromLocal) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkPalette.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkPalette.textMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
