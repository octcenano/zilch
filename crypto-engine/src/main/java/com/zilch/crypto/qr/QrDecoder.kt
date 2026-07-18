package com.zilch.crypto.qr

import android.util.Log
import com.zilch.crypto.config.CryptoConfig
import com.zilch.crypto.exception.CryptoEngineException
import com.zilch.crypto.hash.NodeIdentifier
import com.zilch.crypto.keys.Ed25519KeyGenerator
import org.json.JSONObject
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * QrDecoder — Decodificación y validación de códigos QR escaneados.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: VALIDACIÓN QR
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Al escanear un QR, el decodificador ejecuta una cadena de
 * validaciones ANTES de aceptar la identidad remota:
 *
 * ┌─────────────────────────────────────────────────┐
 * │  1. Parseo del JSON                             │
 * │  2. Validación de campos obligatorios           │
 * │  3. Verificación de versión del protocolo       │
 * │  4. Verificación de expiración                  │
 * │  5. Validación de formato de clave pública       │
 * │  6. Verificación de la firma Ed25519            │
 * │  7. Derivación del identificador de nodo         │
 * │  8. Retorno de la identidad remota validada      │
 * └─────────────────────────────────────────────────┘
 *
 * Si CUALQUIER paso falla, se lanza una excepción y el contacto
 * NO se acepta. No hay "degradación graceful" en seguridad:
 * o es seguro, o no se usa.
 *
 * La verificación de firma (paso 6) es la defensa contra MITM:
 * aunque un atacante intercepte el QR y modifique la dirección,
 * no puede re-firmar sin la clave privada del emisor.
 */
object QrDecoder {

    private const val TAG = "QrDecoder"

    /**
     * Resultado de la decodificación QR.
     *
     * Contiene toda la información validada del contacto remoto.
     *
     * SEGURIDAD: La clave pública se mantiene como PublicKey (JCE),
     * NO como String. Esto minimiza el tiempo que los datos
     * sensibles están en formato String en memoria.
     */
    data class DecodedQr(
        /** Clave pública Ed25519 del emisor */
        val publicKey: java.security.PublicKey,
        /** Clave pública en bytes (32 bytes) */
        val publicKeyBytes: ByteArray,
        /** Identificador del nodo remoto */
        val nodeId: String,
        /** Fingerprint para verificación verbal */
        val fingerprint: String,
        /** Dirección temporal del contacto */
        val temporaryAddress: String,
        /** Timestamp de generación del QR */
        val generatedAtMs: Long,
        /** Timestamp de expiración del QR */
        val expiresAtMs: Long,
        /** Versión del protocolo */
        val protocolVersion: Int
    ) {
        /** Verifica si el QR ha expirado */
        val isExpired: Boolean
            get() = System.currentTimeMillis() > expiresAtMs
    }

    /**
     * Decodifica y valida un string de payload QR.
     *
     * @param payload String JSON contenido en el QR escaneado
     * @return DecodedQr con la identidad validada del contacto
     * @throws CryptoEngineException.QrPayloadInvalid si el formato es inválido
     * @throws CryptoEngineException.QrExpired si el QR ha expirado
     * @throws CryptoEngineException.QrUnsupportedVersion si la versión no es soportada
     * @throws CryptoEngineException.SignatureVerificationFailed si la firma no verifica
     */
    fun decode(payload: String): DecodedQr {
        Log.d(TAG, "Decodificando payload QR (${payload.length} bytes)")

        // ═══ PASO 1: Parseo del JSON ═══
        val json = try {
            JSONObject(payload)
        } catch (e: Exception) {
            throw CryptoEngineException.QrPayloadInvalid(
                "No se pudo parsear el QR como JSON"
            )
        }

        // ═══ PASO 2: Validación de campos obligatorios ═══
        val requiredFields = listOf("v", "pk", "addr", "ts", "exp", "sig")
        for (field in requiredFields) {
            if (!json.has(field) || json.isNull(field)) {
                throw CryptoEngineException.QrPayloadInvalid(
                    "Campo obligatorio ausente: $field"
                )
            }
        }

        // ═══ PASO 3: Verificación de versión ═══
        val version = json.getInt("v")
        if (version > CryptoConfig.QR_PROTOCOL_VERSION) {
            throw CryptoEngineException.QrUnsupportedVersion(version)
        }

        // ═══ PASO 4: Verificación de expiración ═══
        val expiration = json.getLong("exp")
        val now = System.currentTimeMillis()
        if (now > expiration) {
            Log.w(TAG, "QR expirado: exp=$expiration, now=$now")
            throw CryptoEngineException.QrExpired()
        }

        // ═══ PASO 5: Validación de clave pública ═══
        val publicKeyB64 = json.getString("pk")
        val publicKeyBytes: ByteArray
        val publicKey: java.security.PublicKey

        try {
            publicKeyBytes = Base64.getDecoder().decode(publicKeyB64)
            if (publicKeyBytes.size != 32) {
                throw CryptoEngineException.QrPayloadInvalid(
                    "Clave pública debe tener 32 bytes, tiene ${publicKeyBytes.size}"
                )
            }

            val keySpec = X509EncodedKeySpec(
                // Prefijo ASN.1 para Ed25519 X.509
                byteArrayOf(0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00) +
                publicKeyBytes
            )
            publicKey = KeyFactory.getInstance("Ed25519").generatePublic(keySpec)

        } catch (e: CryptoEngineException) {
            throw e
        } catch (e: Exception) {
            throw CryptoEngineException.QrPayloadInvalid(
                "Clave pública inválida"
            )
        }

        // ═══ PASO 6: Verificación de firma ═══
        val signatureB64 = json.getString("sig")
        val signature: ByteArray

        try {
            signature = Base64.getDecoder().decode(signatureB64)
            if (signature.size != 64) {
                throw CryptoEngineException.QrPayloadInvalid(
                    "Firma debe tener 64 bytes, tiene ${signature.size}"
                )
            }
        } catch (e: CryptoEngineException) {
            throw e
        } catch (e: Exception) {
            throw CryptoEngineException.QrPayloadInvalid("Firma inválida")
        }

        // Reconstruir la cadena canonical que fue firmada
        val timestamp = json.getLong("ts")
        val address = json.getString("addr")

        val dataToVerify = "zilch-qr|$version|$publicKeyB64|$address|$timestamp|$expiration"

        val isValid = Ed25519KeyGenerator.verify(
            data = dataToVerify.toByteArray(Charsets.UTF_8),
            signature = signature,
            publicKey = publicKey
        )

        if (!isValid) {
            Log.e(TAG, "FIRMA QR INVALIDA — posible tampering o QR falso")
            throw CryptoEngineException.SignatureVerificationFailed(
                "La firma del QR no coincide — datos posiblemente manipulados"
            )
        }

        Log.i(TAG, "Firma QR verificada correctamente")

        // ═══ PASO 7: Derivación del identificador ═══
        val nodeId = NodeIdentifier.derive(publicKeyBytes)
        val fingerprint = NodeIdentifier.fingerprint(publicKeyBytes)

        Log.i(TAG, "Contacto remoto: fingerprint=$fingerprint")

        // ═══ PASO 8: Retorno de identidad validada ═══
        return DecodedQr(
            publicKey = publicKey,
            publicKeyBytes = publicKeyBytes,
            nodeId = nodeId,
            fingerprint = fingerprint,
            temporaryAddress = address,
            generatedAtMs = timestamp,
            expiresAtMs = expiration,
            protocolVersion = version
        )
    }
}
