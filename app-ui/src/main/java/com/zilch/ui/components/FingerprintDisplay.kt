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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

/**
 * FingerprintDisplay — Muestra un fingerprint de forma legible.
 *
 * El fingerprint se muestra en grupos de 4 caracteres separados
 * por guiones, optimizado para verificación verbal:
 *
 *   A3F2
 *   8B1C
 *   4D5E
 *
 * El usuario puede leer estos grupos al otro lado del teléfono
 * para verificar la identidad del contacto.
 */
@Composable
fun FingerprintDisplay(
    fingerprint: String,
    label: String = "FINGERPRINT",
    modifier: Modifier = Modifier
) {
    // Dividir en grupos de 4
    val groups = fingerprint
        .replace("-", "")
        .chunked(4)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkPalette.surfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Etiqueta
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DarkPalette.textMuted,
            letterSpacing = 2.sp,
            fontSize = 9.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Fingerprint en grupos legibles
        groups.forEach { group ->
            Text(
                text = group.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = DarkPalette.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}
