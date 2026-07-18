package com.zilch.crypto.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.zilch.crypto.config.CryptoConfig
import com.zilch.crypto.exception.CryptoEngineException
import com.zilch.crypto.identity.EphemeralIdentity
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * QrEncoder — Generación de códigos QR para intercambio de identidades.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: FORMATO QR
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El código QR contiene un payload JSON firmado criptográficamente.
 * La estructura es:
 *
 * ```json
 * {
 *   "v": 1,
 *   "pk": "<base64 Ed25519 public key>",
 *   "addr": "<temporary network address>",
 *   "ts": 1234567890,
 *   "exp": 1234568190,
 *   "sig": "<base64 Ed25519 signature>"
 * }
 * ```
 *
 * Campos:
 * - `v`: Versión del protocolo (para backward compatibility)
 * - `pk`: Clave pública del emisor (32 bytes, base64)
 * - `addr`: Dirección temporal del nodo (.onion o BLE UUID)
 * - `ts`: Timestamp de generación (Unix epoch)
 * - `exp`: Timestamp de expiración
 * - `sig`: Firma Ed25519 sobre los campos v+pk+addr+ts+exp
 *
 * SEGURIDAD DEL FORMATO:
 * - La firma previene que un atacante modifique la dirección (`addr`)
 *   manteniendo la misma clave pública (ataque MITM)
 * - El timestamp previene replay attacks (reutilizar un QR viejo)
 * - La versión del protocolo permite migraciones futuras
 * - El QR NO contiene la clave privada (obvio, pero explícito)
 *
 * Tamaño estimado del payload: ~250 chars
 * Capacidad máxima de un QR version 40: ~4296 chars
 * → Espacio sobrado para futuras extensiones
 */
object QrEncoder {

    private const val TAG = "QrEncoder"

    /**
     * Genera un Bitmap de código QR desde una identidad y dirección temporal.
     *
     * @param identity Identidad efímera local (usa su clave privada para firmar)
     * @param temporaryAddress Dirección temporal del nodo
     *                          (ej: "abc123.onion" o "ble:uuid-abc-def")
     * @return Bitmap del código QR listo para mostrar en pantalla
     * @throws CryptoEngineException.QrOperationFailed si la generación falla
     */
    fun generateQrBitmap(
        identity: EphemeralIdentity,
        temporaryAddress: String
    ): Bitmap {
        return try {
            val payload = buildSignedPayload(identity, temporaryAddress)
            encodeQrBitmap(payload)
        } catch (e: CryptoEngineException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al generar QR bitmap: ${e.message}")
            throw CryptoEngineException.QrOperationFailed(cause = e)
        }
    }

    /**
     * Genera el payload JSON firmado para el QR.
     *
     * @param identity Identidad del emisor
     * @param temporaryAddress Dirección temporal
     * @return String JSON firmado
     */
    fun buildSignedPayload(
        identity: EphemeralIdentity,
        temporaryAddress: String
    ): String {
        val now = System.currentTimeMillis()
        val expiration = now + CryptoConfig.QR_TTL_MS

        // ═══ Construir el payload sin firma ═══
        val payloadJson = JSONObject().apply {
            put("v", CryptoConfig.QR_PROTOCOL_VERSION)
            put("pk", identity.publicKeyBase64())
            put("addr", temporaryAddress)
            put("ts", now)
            put("exp", expiration)
        }

        // ═══ Firma del payload ═══
        // El payload que se firma incluye TODOS los campos excepto "sig"
        // La firma garantiza integridad: si alguien modifica "addr",
        // la firma no verificará
        val dataToSign = buildSignableString(
            version = CryptoConfig.QR_PROTOCOL_VERSION,
            publicKeyB64 = identity.publicKeyBase64(),
            address = temporaryAddress,
            timestamp = now,
            expiration = expiration
        )

        val signature = identity.sign(dataToSign.toByteArray(Charsets.UTF_8))
        val signatureB64 = Base64.getEncoder().encodeToString(signature)

        payloadJson.put("sig", signatureB64)

        val payload = payloadJson.toString()
        Log.d(TAG, "Payload QR generado (${payload.length} bytes)")

        return payload
    }

    /**
     * Construye la cadena canonical que se firma.
     *
     * DECISIÓN: Usamos una cadena delimitada por pipes en un orden
     * fijo. Esto evita ambiguidades en la serialización JSON
     * (orden de campos, espacios, etc.)
     */
    private fun buildSignableString(
        version: Int,
        publicKeyB64: String,
        address: String,
        timestamp: Long,
        expiration: Long
    ): String {
        return "zilch-qr|$version|$publicKeyB64|$address|$timestamp|$expiration"
    }

    /**
     * Codifica un string como imagen QR.
     *
     * @param content Contenido a codificar en el QR
     * @return Bitmap de la imagen QR
     */
    private fun encodeQrBitmap(content: String): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to CryptoConfig.QR_MARGIN,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val writer = QRCodeWriter()
        val bitMatrix: BitMatrix = writer.encode(
            content,
            BarcodeFormat.QR_CODE,
            CryptoConfig.QR_IMAGE_SIZE,
            CryptoConfig.QR_IMAGE_SIZE,
            hints
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    /**
     * Convierte un Bitmap QR a bytes PNG para almacenamiento o transmisión.
     *
     * @param bitmap Bitmap del QR
     * @param quality Calidad de compresión (0-100)
     * @return ByteArray con los bytes PNG
     */
    fun bitmapToPng(bitmap: Bitmap, quality: Int = 100): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }
}
