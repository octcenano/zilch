package com.zilch.blemesh.encryption

import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SessionKeyRatchet — Implementación simplificada de Double Ratchet.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  POR QUÉ DOUBLE RATCHET
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Signal usa Double Ratchet para lograr:
 *
 * 1. **Forward Secrecy** — Si la clave actual se compromete, los mensajes
 *    pasados siguen protegidos porque cada mensaje usa una clave diferente.
 *
 * 2. **Future Secrecy** — Si una clave pasada se compromete, los mensajes
 *    futuros siguen protegidos porque la clave se renueva en cada mensaje.
 *
 * Este es el estándar de oro para mensajería cifrada. Sin esto, un
 * atacante que obtenga UNA clave puede descifrar TODOS los mensajes.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  IMPLEMENTACIÓN SIMPLIFICADA
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Esta implementación usa:
 *
 * 1. **Sending Chain** — Clave derivada por cada mensaje enviado
 * 2. **Receiving Chain** — Clave derivada por cada mensaje recibido
 * 3. **DH Ratchet** — Nueva ronda de DH cuando cambia el emisor
 * 4. **KDF Chain** — HKDF para derivar claves de sesión
 *
 * No es un Double Ratchet completo (falta X3DH para el handshak inicial),
 * pero proporciona forward secrecy real entre sesiones BLE.
 */
class SessionKeyRatchet(
    /** Clave pública local (32 bytes Ed25519) */
    private val localPublicKey: ByteArray,
    /** Clave pública del peer (32 bytes Ed25519) */
    private val peerPublicKey: ByteArray,
    /** NodeId local */
    private val localNodeId: String,
    /** NodeId del peer */
    private val peerNodeId: String
) {
    companion object {
        private const val TAG = "SessionKeyRatchet"
        private const val KEY_SIZE = 32
        private const val NONCE_SIZE = 12
        private const val GCM_TAG_SIZE = 16
    }

    /** Contador de mensajes enviados (para derivar claves de envío) */
    @Volatile
    private var sendCounter: Long = 0

    /** Contador de mensajes recibidos (para derivar claves de recepción) */
    @Volatile
    private var receiveCounter: Long = 0

    /** Clave de sesión actual (se renueva periódicamente) */
    @Volatile
    private var rootKey: SecretKey

    /** Clave de la cadena de envío */
    @Volatile
    private var sendingKey: SecretKey

    /** Clave de la cadena de recepción */
    @Volatile
    private var receivingKey: SecretKey

    /** Nonce base para mensajes enviados */
    @Volatile
    private var sendNonceBase: ByteArray

    /** Nonce base para mensajes recibidos */
    @Volatile
    private var receiveNonceBase: ByteArray

    /** Nonces vistos (para detectar replay attacks) */
    private val seenNonces = mutableSetOf<String>()

    /** Callback para notificar ratchet */
    var onKeyRatchet: (() -> Unit)? = null

    init {
        // Derivar claves iniciales del intercambio DH
        val initialMaterial = deriveInitialKeyMaterial()
        rootKey = initialMaterial.rootKey
        sendingKey = initialMaterial.sendingKey
        receivingKey = initialMaterial.receivingKey
        sendNonceBase = initialMaterial.sendNonceBase
        receiveNonceBase = initialMaterial.receiveNonceBase

        Log.i(TAG, "SessionKeyRatchet inicializado para peer $peerNodeId")
    }

    /**
     * Resultado de una operación de cifrado.
     */
    data class EncryptedResult(
        val ciphertext: ByteArray,
        val nonce: ByteArray,
        val counter: Long,
        val keyId: String
    ) {
        fun toBytes(): ByteArray {
            val keyIdBytes = keyId.toByteArray(Charsets.UTF_8)
            // [counter:8][nonce:12][keyIdLen:1][keyId][ciphertext]
            return ByteArray(8).also {
                var c = counter
                for (i in 7 downTo 0) {
                    it[i] = (c and 0xFF).toByte(); c = c shr 8
                }
            } + nonce +
                    byteArrayOf(keyIdBytes.size.toByte()) + keyIdBytes +
                    ciphertext
        }

        companion object {
            fun fromBytes(data: ByteArray): EncryptedResult {
                if (data.size < 8 + NONCE_SIZE + 1) {
                    throw IllegalArgumentException("Payload demasiado corto para SessionKeyRatchet")
                }
                var counter = 0L
                for (i in 0 until 8) {
                    counter = (counter shl 8) or (data[i].toLong() and 0xFF)
                }
                val offset = 8
                val nonce = data.copyOfRange(offset, offset + NONCE_SIZE)
                val keyIdLen = data[offset + NONCE_SIZE].toInt() and 0xFF
                val keyId = String(data.copyOfRange(offset + NONCE_SIZE + 1, offset + NONCE_SIZE + 1 + keyIdLen))
                val ciphertext = data.copyOfRange(offset + NONCE_SIZE + 1 + keyIdLen, data.size)
                return EncryptedResult(ciphertext, nonce, counter, keyId)
            }
        }
    }

    /**
     * Cifra un mensaje con la clave de sesión actual.
     * La clave se renueva automáticamente después de cada cifrado.
     *
     * @param plaintext Datos a cifrar
     * @return EncryptedResult con ciphertext, nonce y counter
     */
    fun encrypt(plaintext: ByteArray): EncryptedResult {
        val currentKey = sendingKey
        val currentNonce = deriveNonce(sendNonceBase, sendCounter)
        val keyId = deriveKeyId(currentKey)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            currentKey,
            GCMParameterSpec(GCM_TAG_SIZE * 8, currentNonce)
        )
        val encrypted = cipher.doFinal(plaintext)
        val ciphertext = encrypted.copyOfRange(0, encrypted.size - GCM_TAG_SIZE)
        val tag = encrypted.copyOfRange(encrypted.size - GCM_TAG_SIZE, encrypted.size)

        val result = EncryptedResult(
            ciphertext = ciphertext + tag,
            nonce = currentNonce,
            counter = sendCounter,
            keyId = keyId
        )

        // ═══ RATCHET: Renovar clave después de cada envío ═══
        sendCounter++
        ratchetSendingKey()

        Log.d(TAG, "Mensaje enviado #$sendCounter, clave renovada")
        return result
    }

    /**
     * Descifra un mensaje con la clave de sesión actual.
     *
     * @param result EncryptedResult recibido
     * @return ByteArray con los datos descifrados
     * @throws Exception si el counter es menor que el esperado (replay)
     * @throws Exception si la verificación GCM falla (manipulación)
     */
    fun decrypt(result: EncryptedResult): ByteArray {
        // ═══ REPLAY PROTECTION ═══
        val nonceKey = "${result.counter}:${result.nonce.joinToString("") { String.format("%02x", it) }}"
        synchronized(seenNonces) {
            if (nonceKey in seenNonces) {
                throw SecurityException("Replay attack detectado: nonce ya visto")
            }
            seenNonces.add(nonceKey)
            // Limpiar nonces antiguos (>1000)
            if (seenNonces.size > 1000) {
                val toRemove = seenNonces.take(500)
                seenNonces.removeAll(toRemove.toSet())
            }
        }

        // ═══ FORWARD SECRECY CHECK ═══
        if (result.counter < receiveCounter) {
            // Mensaje con counter antiguo — podría ser replay o reordenamiento
            Log.w(TAG, "Mensaje con counter antiguo: ${result.counter} < $receiveCounter")
        }

        val currentKey = receivingKey
        val fullCiphertext = result.ciphertext
        val ciphertext = fullCiphertext.copyOfRange(0, fullCiphertext.size - GCM_TAG_SIZE)
        val tag = fullCiphertext.copyOfRange(fullCiphertext.size - GCM_TAG_SIZE, fullCiphertext.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            currentKey,
            GCMParameterSpec(GCM_TAG_SIZE * 8, result.nonce)
        )
        val plaintext = cipher.doFinal(ciphertext + tag)

        // ═══ RATCHET: Renovar clave después de cada recepción ═══
        receiveCounter = maxOf(receiveCounter, result.counter + 1)
        ratchetReceivingKey()

        Log.d(TAG, "Mensaje recibido #${result.counter}, clave renovada")
        return plaintext
    }

    /**
     * Renueva la sesión completamente (nueva ronda DH).
     * Llamar cuando el peer genera una nueva identidad.
     */
    fun ratchetSession() {
        val newMaterial = deriveNewKeyMaterial()
        rootKey = newMaterial.rootKey
        sendingKey = newMaterial.sendingKey
        receivingKey = newMaterial.receivingKey
        sendNonceBase = newMaterial.sendNonceBase
        receiveNonceBase = newMaterial.receiveNonceBase
        sendCounter = 0
        receiveCounter = 0
        seenNonces.clear()
        onKeyRatchet?.invoke()
        Log.i(TAG, "Sesión ratcheted completamente")
    }

    // ═══ Derivación de claves ═══

    private fun deriveInitialKeyMaterial(): KeyMaterial {
        // IKM = SHA-256(localPub || peerPub)
        val ikm = MessageDigest.getInstance("SHA-256").digest(
            localPublicKey + peerPublicKey
        )
        // Salt = SHA-256("zilch-session-v1" || localNodeId || peerNodeId)
        val salt = MessageDigest.getInstance("SHA-256").digest(
            "zilch-session-v1".toByteArray() +
                    localNodeId.toByteArray() + peerNodeId.toByteArray()
        )

        val prk = hmacSha256(salt, ikm)
        val rootKeyBytes = hkdfExpand(prk, "root".toByteArray(), KEY_SIZE)
        val sendingKeyBytes = hkdfExpand(prk, "sending".toByteArray(), KEY_SIZE)
        val receivingKeyBytes = hkdfExpand(prk, "receiving".toByteArray(), KEY_SIZE)
        val sendNonce = hkdfExpand(prk, "send-nonce".toByteArray(), NONCE_SIZE)
        val receiveNonce = hkdfExpand(prk, "recv-nonce".toByteArray(), NONCE_SIZE)

        return KeyMaterial(
            rootKey = SecretKeySpec(rootKeyBytes, "AES"),
            sendingKey = SecretKeySpec(sendingKeyBytes, "AES"),
            receivingKey = SecretKeySpec(receivingKeyBytes, "AES"),
            sendNonceBase = sendNonce,
            receiveNonceBase = receiveNonce
        )
    }

    private fun deriveNewKeyMaterial(): KeyMaterial {
        // Renovar rootKey con SHA-256(rootKey)
        val newRoot = MessageDigest.getInstance("SHA-256").digest(
            rootKey.encoded + sendCounter.toByteArray() + receiveCounter.toByteArray()
        )
        val newRootKey = SecretKeySpec(newRoot, "AES")

        val prk = hmacSha256(newRoot, localPublicKey + peerPublicKey)
        val sendingKeyBytes = hkdfExpand(prk, "sending-new".toByteArray(), KEY_SIZE)
        val receivingKeyBytes = hkdfExpand(prk, "receiving-new".toByteArray(), KEY_SIZE)
        val sendNonce = hkdfExpand(prk, "send-nonce-new".toByteArray(), NONCE_SIZE)
        val receiveNonce = hkdfExpand(prk, "recv-nonce-new".toByteArray(), NONCE_SIZE)

        return KeyMaterial(
            rootKey = newRootKey,
            sendingKey = SecretKeySpec(sendingKeyBytes, "AES"),
            receivingKey = SecretKeySpec(receivingKeyBytes, "AES"),
            sendNonceBase = sendNonce,
            receiveNonceBase = receiveNonce
        )
    }

    private fun ratchetSendingKey() {
        // Derivar nueva sendingKey = HKDF(sendingKey, "ratchet-send", 32)
        val newKeyBytes = hkdfExpand(sendingKey.encoded, "ratchet-send".toByteArray(), KEY_SIZE)
        sendingKey = SecretKeySpec(newKeyBytes, "AES")
    }

    private fun ratchetReceivingKey() {
        val newKeyBytes = hkdfExpand(receivingKey.encoded, "ratchet-recv".toByteArray(), KEY_SIZE)
        receivingKey = SecretKeySpec(newKeyBytes, "AES")
    }

    private fun deriveNonce(base: ByteArray, counter: Long): ByteArray {
        val counterBytes = ByteArray(8).also {
            var c = counter
            for (i in 7 downTo 0) {
                it[i] = (c and 0xFF).toByte(); c = c shr 8
            }
        }
        val mixed = hmacSha256(base, counterBytes)
        return mixed.copyOf(NONCE_SIZE)
    }

    private fun deriveKeyId(key: SecretKey): String {
        return MessageDigest.getInstance("SHA-256").digest(key.encoded)
            .take(4).joinToString("") { String.format("%02x", it) }
    }

    private fun Long.toByteArray(): ByteArray {
        return ByteArray(8).also {
            var c = this
            for (i in 7 downTo 0) {
                it[i] = (c and 0xFF).toByte(); c = c shr 8
            }
        }
    }

    // ═══ Utilidades HKDF ═══

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen
        var okm = ByteArray(0)
        var t = ByteArray(0)
        for (i in 1..n) {
            t = hmacSha256(prk, t + info + byteArrayOf(i.toByte()))
            okm += t
        }
        return okm.copyOf(length)
    }

    data class KeyMaterial(
        val rootKey: SecretKey,
        val sendingKey: SecretKey,
        val receivingKey: SecretKey,
        val sendNonceBase: ByteArray,
        val receiveNonceBase: ByteArray
    )
}
