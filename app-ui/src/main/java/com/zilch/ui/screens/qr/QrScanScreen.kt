package com.zilch.ui.screens.qr

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.zilch.ui.components.FingerprintDisplay
import com.zilch.ui.theme.DarkPalette
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Estados del flujo de escaneo QR.
 */
sealed class QrScanState {
    /** Esperando escaneo */
    object Scanning : QrScanState()

    /** QR escaneado, mostrando fingerprint para verificación */
    data class Scanned(
        val fingerprint: String,
        val temporaryAddress: String
    ) : QrScanState()

    /** Contacto confirmado y añadido */
    object Confirmed : QrScanState()

    /** Error en el escaneo */
    data class Error(val message: String) : QrScanState()
}

private const val TAG = "QrScanScreen"

/**
 * QrScanScreen — Cámara para escanear QR de otro dispositivo.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  FLUJO DE SEGURIDAD
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. Cámara activa → Detecta QR
 * 2. QR decodificado → Valida firma + expiración
 * 3. Muestra fingerprint → Usuario compara verbalmente
 * 4. Usuario confirma → Contacto añadido a la sesión
 *
 * ⚠ El paso 3 es CRÍTICO: sin verificación verbal del fingerprint,
 * un atacante podría colocar un QR falso en el punto de escaneo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    scanState: QrScanState,
    onQrDetected: (String) -> Unit = {},
    onConfirmContact: () -> Unit,
    onRetryScan: () -> Unit,
    onBack: () -> Unit,
    onEmergencyTriggered: (() -> Unit)? = null
) {
    Scaffold(
        containerColor = DarkPalette.background,
        topBar = {
            TopAppBar(
                title = { Text("Escanear QR") },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (scanState) {
                is QrScanState.Scanning -> {
                    QrCameraPreview(onQrDetected = onQrDetected)
                }

                is QrScanState.Scanned -> {
                    // ═══ ESTADO: ESCANEADO — VERIFICAR FINGERPRINT ═══
                    Text(
                        text = "QR RECIBIDO — VERIFICA",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DarkPalette.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Pide a tu contacto que lea en voz alta\nel fingerprint que aparece en su pantalla.\n\nCompara con lo que ves aquí:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Fingerprint para comparar
                    FingerprintDisplay(
                        fingerprint = scanState.fingerprint,
                        label = "FINGERPRINT DEL CONTACTO"
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Botones de confirmación
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Rechazar
                        OutlinedButton(
                            onClick = onRetryScan,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DarkPalette.error
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("NO COINCIDE")
                        }

                        // Confirmar
                        Button(
                            onClick = onConfirmContact,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkPalette.secondary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COINCIDE", color = DarkPalette.onSecondary)
                        }
                    }
                }

                is QrScanState.Confirmed -> {
                    // ═══ ESTADO: CONFIRMADO ═══
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DarkPalette.secondary,
                        modifier = Modifier.size(72.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CONTACTO AÑADIDO",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DarkPalette.secondary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Ya puedes enviar mensajes cercanos\npor Bluetooth",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                is QrScanState.Error -> {
                    // ═══ ESTADO: ERROR ═══
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = DarkPalette.error,
                        modifier = Modifier.size(72.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ERROR DE VERIFICACIÓN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DarkPalette.error,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = scanState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkPalette.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onRetryScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkPalette.primary
                        )
                    ) {
                        Text("REINTENTAR", color = DarkPalette.onPrimary)
                    }
                }
            }
        }
    }
}

/**
 * QrCameraPreview — Cámara con análisis de QR en hilo dedicado.
 *
 * Correge los problemas del analyzer original:
 * 1. Usa un executor de fondo en lugar del main executor
 * 2. Resetea el MultiFormatReader entre cada frame
 * 3. Usa un flag atómico para evitar decodificaciones concurrentes
 * 4. Maneja correctamente el stride del ImageProxy
 */
@Composable
private fun QrCameraPreview(onQrDetected: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Flag atómico: solo un frame a la vez se decodifica
    val isProcessing = remember { AtomicBoolean(false) }

    // Executor dedicado para análisis de imagen (NO el main thread)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Estado para mostrar guía visual
    var scanLineOffset by remember { mutableFloatStateOf(0f) }

    // Animación de la línea de escaneo
    LaunchedEffect(Unit) {
        while (true) {
            scanLineOffset = (scanLineOffset + 0.02f) % 1f
            kotlinx.coroutines.delay(16L) // ~60fps
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══ MARCO DE CÁMARA CON GUÍA VISUAL ═══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkPalette.surfaceVariant)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            // Preview
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            // Image Analysis — configurado para QR
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                processQrFrame(imageProxy, isProcessing, onQrDetected)
                            }

                            // Vincular al lifecycle
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )

                            Log.d(TAG, "Cámara vinculada correctamente")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al vincular cámara: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // ═══ GUÍA VISUAL — Marco central ═══
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Marco semi-transparente
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkPalette.primary.copy(alpha = 0.1f))
                        .padding(2.dp)
                )

                // Instrucciones
                Text(
                    text = "Centra el código QR aquí",
                    color = DarkPalette.primary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Apunta al QR del otro dispositivo",
            style = MaterialTheme.typography.titleMedium,
            color = DarkPalette.onSurfaceVariant
        )
    }
}

/**
 * Procesa un frame de la cámara en busca de códigos QR.
 *
 * Ejecuta en el executor de análisis (NO en el main thread).
 * Usa un flag atómico para evitar que múltiples frames se procesen
 * simultáneamente.
 *
 * El MultiFormatReader se crea nuevo por cada intento de decode
 * para evitar estados inconsistentes internos de ZXing.
 */
private fun processQrFrame(
    imageProxy: ImageProxy,
    isProcessing: AtomicBoolean,
    onQrDetected: (String) -> Unit
) {
    // Solo procesar si no hay otro frame en proceso
    if (isProcessing.getAndSet(true)) {
        imageProxy.close()
        return
    }

    try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // ═══ STRIDE-AWARE: el stride puede ser > width ═══
        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val rowStride = imageProxy.planes[0].rowStride

        // Usar el ancho real de datos por fila (rowStride o imageWidth)
        val dataWidth = if (rowStride > imageWidth) rowStride else imageWidth

        val source = PlanarYUVLuminanceSource(
            bytes,
            dataWidth,
            imageHeight,
            0,
            0,
            imageWidth,
            imageHeight,
            false
        )

        // ═══ Crear reader NUEVO por cada frame ═══
        // MultiFormatReader tiene estado interno que se corrompe
        // después de un decode fallido. Crearlo nuevo garantiza
        // un estado limpio.
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to false,
                    DecodeHintType.PURE_BARCODE to false
                )
            )
        }

        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)

            if (result.text.isNotEmpty()) {
                Log.i(TAG, "QR detectado: ${result.text.take(50)}...")
                onQrDetected(result.text)
            }
        } catch (_: com.google.zxing.NotFoundException) {
            // Normal: no hay QR en este frame
        } catch (_: com.google.zxing.ChecksumException) {
            // QR detectado pero con errores de checksum
        } catch (_: com.google.zxing.FormatException) {
            // Formato no reconocido
        } finally {
            reader.reset()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error procesando frame: ${e.message}")
    } finally {
        isProcessing.set(false)
        imageProxy.close()
    }
}
