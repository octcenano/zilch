package com.zilch.ui.screens.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.zilch.ui.components.FingerprintDisplay
import com.zilch.ui.theme.DarkPalette
import kotlinx.coroutines.delay

/**
 * QrReceiveScreen — Muestra el QR del nodo local para que lo escaneen.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  LAYOUT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * ┌──────────────────────────────────┐
 * │  ← [Tu Identidad]               │
 * ├──────────────────────────────────┤
 * │                                  │
 * │  ┌──────────────────────────┐   │
 * │  │                          │   │
 * │  │    [CÓDIGO QR GRANDE]    │   │  ← QR con clave pública firmada
 * │  │                          │   │
 * │  └──────────────────────────┘   │
 * │                                  │
 * │  Expira en: 04:32               │  ← Countdown del TTL
 * │                                  │
 * │  [Fingerprint Display]          │  ← Para verificar verbalmente
 * │   a3f2                          │
 * │   8b1c                          │
 * │   4d5e                          │
 * │                                  │
 * │  "Compara el fingerprint con    │
 * │   tu contacto"                  │
 * └──────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrReceiveScreen(
    fingerprint: String,
    timeRemainingSeconds: Int,
    onBack: () -> Unit,
    onEmergencyTriggered: (() -> Unit)? = null
) {
    // ═══ QR generation via ZXing ═══
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrGenerating by remember { mutableStateOf(true) }

    LaunchedEffect(fingerprint) {
        qrGenerating = true
        qrBitmap = try {
            val payload = org.json.JSONObject().apply {
                put("fingerprint", fingerprint)
                put("ts", System.currentTimeMillis())
            }.toString()
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(
                payload, BarcodeFormat.QR_CODE, 512, 512, hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    )
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
        qrGenerating = false
    }

    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = { Text("Tu Identidad") },
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
        },
        bottomBar = {
            onEmergencyTriggered?.let { trigger ->
                com.zilch.ui.components.EmergencyButton(
                    onEmergencyTriggered = trigger
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ═══ QR CODE ═══
            Card(
                modifier = Modifier.size(280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkPalette.qrBackground
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Código QR de identidad",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = if (qrGenerating) "Generando QR..." else "Error generando QR",
                            color = DarkPalette.textMuted
                        )
                    }
                }
            }

            // ═══ COUNTDOWN ═══
            val minutes = timeRemainingSeconds / 60
            val seconds = timeRemainingSeconds % 60

            Text(
                text = "Expira en %02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.titleMedium,
                color = if (timeRemainingSeconds < 60) DarkPalette.error
                else DarkPalette.onSurfaceVariant,
                letterSpacing = 2.sp
            )

            // ═══ INSTRUCCIONES ═══
            Text(
                text = "Muestra este código para que tu contacto lo escanee.\n" +
                        "Después compara el fingerprint verbalmente.",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkPalette.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ FINGERPRINT ═══
            FingerprintDisplay(
                fingerprint = fingerprint,
                label = "TU FINGERPRINT"
            )
        }
    }
}
