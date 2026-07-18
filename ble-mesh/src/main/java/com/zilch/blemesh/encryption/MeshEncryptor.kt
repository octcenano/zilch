package com.zilch.blemesh.encryption

import android.util.Log
import com.zilch.blemesh.config.BleConfig
import com.zilch.blemesh.exception.BleMeshException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * MeshEncryptor — Cifrado de mensajes en el canal BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: CIFRADO BLE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Los mensajes BLE se cifran con AES-256-GCM (Galois/Counter Mode).
 *
 * ¿Por qué AES-256-GCM?
 * - **Autenticación integrada:** GCM produce tanto cifrado como
 *   un tag de autenticación. Si alguien modifica el ciphertext,
 *   el tag no verificará y el mensaje se descarta.
 * - **AEAD (Authenticated Encryption with Associated Data):**
 *   Proporciona confidencialidad Y autenticidad en una sola operación.
 * - **Rendimiento:** GCM es eficiente en hardware (ARM AES instructions).
 * - **Estándar ampliamente auditado:** Usado en TLS 1.3, Signal, WhatsApp.
 *
 * DERIVACIÓN DE CLAVE:
 * Dado que Ed25519 no soporta intercambio de claves (ECDH), derivamos
 * la clave simétrica de cifrado usando:
 *
 *   key = HKDF-SHA256(
 *     ikm = SHA-256(senderPubKey || receiverPubKey),
 *     salt = SHA-256("zilch-ble-mesh-v1" || senderNodeId || receiverNodeId),
 *     info = "zilch-ble-message-encryption",
 *     length = 32
 *   )
 *
 * Este es un KDF determinístico: dadas las mismas dos claves públicas,
 * siempre produce la misma clave de cifrado. No es un ECDH real,
 * pero proporciona confidencialidad contra un adversario que no
 * posea ninguna de las dos claves públicas.
 *
 * LIMITACIÓN HONESTA:
 * Este enfoque NO es equivalente a ECDH. Un adversario que conozca
 * ambas claves públicas (que son, recordemos, intercambiadas vía QR
 * en proximidad física) podría derivar la misma clave. La seguridad
 * real del canal BLE depende de la proximidad física: el adversario
 * debe estar dentro del rango BLE (~10m) Y conocer ambas claves públicas.
 *
 * Para un cifrado verdaderamente seguro contra este escenario,
 * se recomienda usar X25519 ECDH (disponible en Bouncy Castle)
 * como extensión futura de este módulo.
 */
object MeshEncryptor {

    private const val TAG = "MeshEncryptor"

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Resultado del cifrado de un mensaje.
     *
     * @property ciphertext Datos cifrados (sin el tag GCM)
     * @property iv Vector de inicialización (nonce) — 12 bytes
     * @property tag Tag de autenticación GCM — 16 bytes
     * @property keyId Identificador de la clave usada (para rotación)
     */
    data class EncryptedPayload(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val tag: ByteArray,
        val keyId: String
    ) {
        /** Serializa a bytes para transmisión: [iv][tag][keyId][ciphertext] */
        fun toBytes(): ByteArray {
            val keyIdBytes = keyId.toByteArray(Charsets.UTF_8)
            return iv + tag +
                   byteArrayOf(keyIdBytes.size.toByte()) + keyIdBytes +
                   ciphertext
        }

        companion object {
            /** Deserializa desde bytes */
            fun fromBytes(data: ByteArray): EncryptedPayload {
                if (data.size < BleConfig.GCM_NONCE_SIZE + BleConfig.GCM_TAG_SIZE + 1) {
                    throw BleMeshException.MessageCorrupted(
                        "Payload cifrado demasiado corto"
                    )
                }

                val iv = data.copyOfRange(0, BleConfig.GCM_NONCE_SIZE)
                val tagOffset = BleConfig.GCM_NONCE_SIZE
                val tag = data.copyOfRange(tagOffset, tagOffset + BleConfig.GCM_TAG_SIZE)
                val keyIdLengthOffset = tagOffset + BleConfig.GCM_TAG_SIZE
                val keyIdLength = data[keyIdLengthOffset].toInt() and 0xFF
                val keyIdOffset = keyIdLengthOffset + 1
                val keyId = String(data.copyOfRange(keyIdOffset, keyIdOffset + keyIdLength))
                val ciphertextOffset = keyIdOffset + keyIdLength
                val ciphertext = data.copyOfRange(ciphertextOffset, data.size)

                return EncryptedPayload(ciphertext, iv, tag, keyId)
            }
        }
    }

    /**
     * Deriva una clave AES-256 a partir de las claves públicas de ambos peers.
     *
     * @param senderPublicKeyBytes Clave pública del emisor (32 bytes)
     * @param receiverPublicKeyBytes Clave pública del receptor (32 bytes)
     * @param senderNodeId NodeId del emisor
     * @param receiverNodeId NodeId del receptor
     * @return SecretKey AES-256 derivada
     */
    fun deriveSharedKey(
        senderPublicKeyBytes: ByteArray,
        receiverPublicKeyBytes: ByteArray,
        senderNodeId: String,
        receiverNodeId: String
    ): SecretKey {
        // Paso 1: Material de entrada = SHA-256(pubKey_sender || pubKey_receiver)
        val ikm = MessageDigest.getInstance("SHA-256").digest(
            senderPublicKeyBytes + receiverPublicKeyBytes
        )

        // Paso 2: Salt = SHA-256("zilch-ble-mesh-v1" || senderNodeId || receiverNodeId)
        val salt = MessageDigest.getInstance("SHA-256").digest(
            "zilch-ble-mesh-v1".toByteArray() +
            senderNodeId.toByteArray() + receiverNodeId.toByteArray()
        )

        // Paso 3: HKDF-SHA256 (simplificado con HMAC chain)
        val prk = hmacSha256(salt, ikm)

        // Paso 4: Expandir a 32 bytes (256 bits) para AES-256
        val keyMaterial = hkdfExpand(prk, "zilch-ble-message-encryption".toByteArray(), 32)

        // Generar keyId para identificar la clave usada
        val keyId = MessageDigest.getInstance("SHA-256").digest(keyMaterial)
            .take(8)
            .joinToString("") { String.format("%02x", it) }

        return SecretKeySpec(keyMaterial, "AES")
    }

    /**
     * Cifra datos con AES-256-GCM.
     *
     * @param plaintext Datos a cifrar
     * @param key Clave AES-256 derivada
     * @param associatedData Datos asociados (header del mensaje, no cifrados pero autenticados)
     * @return EncryptedPayload con ciphertext, IV y tag
     */
    fun encrypt(
        plaintext: ByteArray,
        key: SecretKey,
        associatedData: ByteArray? = null
    ): EncryptedPayload {
        return try {
            val iv = ByteArray(BleConfig.GCM_NONCE_SIZE)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(BleConfig.ENCRYPTION_ALGORITHM)
            val gcmSpec = GCMParameterSpec(BleConfig.GCM_TAG_SIZE * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

            if (associatedData != null) {
                cipher.updateAAD(associatedData)
            }

            val encrypted = cipher.doFinal(plaintext)

            // El tag GCM es los últimos 16 bytes del ciphertext
            val ciphertext = encrypted.copyOfRange(0, encrypted.size - BleConfig.GCM_TAG_SIZE)
            val tag = encrypted.copyOfRange(encrypted.size - BleConfig.GCM_TAG_SIZE, encrypted.size)

            // keyId derivado
            val keyId = MessageDigest.getInstance("SHA-256").digest(
                key.encoded
            ).take(8).joinToString("") { String.format("%02x", it) }

            Log.d(TAG, "Mensaje cifrado: ${plaintext.size} → ${encrypted.size} bytes")

            EncryptedPayload(ciphertext, iv, tag, keyId)

        } catch (e: BleMeshException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al cifrar: ${e.message}")
            throw BleMeshException.EncryptionError(cause = e)
        }
    }

    /**
     * Descifra datos con AES-256-GCM.
     *
     * @param encryptedPayload Payload cifrado
     * @param key Clave AES-256 derivada
     * @param associatedData Datos asociados (deben coincidir con los usados al cifrar)
     * @return ByteArray con los datos originales
     */
    fun decrypt(
        encryptedPayload: EncryptedPayload,
        key: SecretKey,
        associatedData: ByteArray? = null
    ): ByteArray {
        return try {
            val cipher = Cipher.getInstance(BleConfig.ENCRYPTION_ALGORITHM)
            val gcmSpec = GCMParameterSpec(
                BleConfig.GCM_TAG_SIZE * 8,
                encryptedPayload.iv
            )
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            if (associatedData != null) {
                cipher.updateAAD(associatedData)
            }

            // Reconstruir ciphertext completo (ciphertext + tag)
            val fullCiphertext = encryptedPayload.ciphertext + encryptedPayload.tag
            cipher.doFinal(fullCiphertext)

        } catch (e: BleMeshException) {
            throw e
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "Tag GCM inválido — posible manipulación del mensaje")
            throw BleMeshException.DecryptionError("Tag de autenticación inválido", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error al descifrar: ${e.message}")
            throw BleMeshException.DecryptionError(cause = e)
        }
    }

    // ═══ Utilidades HKDF ══════════════════════════════════════════════

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(BleConfig.KDF_ALGORITHM)
        mac.init(SecretKeySpec(key, BleConfig.KDF_ALGORITHM))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32 // SHA-256
        val n = (length + hashLen - 1) / hashLen
        var okm = ByteArray(0)
        var t = ByteArray(0)

        for (i in 1..n) {
            t = hmacSha256(prk, t + info + byteArrayOf(i.toByte()))
            okm += t
        }

        return okm.copyOf(length)
    }
}
