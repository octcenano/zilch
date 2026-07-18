package com.zilch.crypto.identity

import com.zilch.crypto.config.CryptoConfig
import com.zilch.crypto.hash.NodeIdentifier
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64

/**
 * EphemeralIdentity — Identidad efímera del nodo local.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: IDENTIDAD EFÍMERA
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Una identidad efímera es un par de claves Ed25519 que existe
 * SOLO durante la sesión actual de la app. Cuando la app se cierra
 * o el usuario presiona el botón de pánico, la identidad se destruye.
 *
 * Propiedades de una identidad efímera:
 *
 * 1. **No persistente:** Las claves viven en memoria RAM. Al cerrar
 *    la app, la memoria es liberada por el OS.
 *
 * 2. **Temporal:** Tiene un TTL (Time To Live). Después de expirar,
 *    se genera una nueva identidad automáticamente.
 *
 * 3. **Sin registro:** No hay servidor central, no hay base de datos
 *    de usuarios. Tu identidad ES tu clave pública.
 *
 * 4. **Un solo uso:** Un escaneo de QR = una identidad. Escanear
 *    otro QR genera una nueva identidad.
 *
 * ¿Por qué no persistir las claves?
 * Porque la persistencia crea superficie de ataque:
 * - Un atacante con acceso al dispositivo puede extraer claves
 *   de almacenamiento persistente
 * - Las claves en disco sobreviven al borrado de la app
 * - Las claves en RAM se borran cuando el OS reasigna la memoria
 *
 * La identidad efímera es el pilar de la "desvinculación" entre
 * sesiones: cada sesión es un nodo completamente nuevo e
 * insospechable que no tiene relación con sesiones anteriores.
 */
data class EphemeralIdentity(
    /** Clave pública — SE PUEDE compartir via QR */
    val publicKey: PublicKey,

    /** Clave privada — NUNCA sale de la memoria */
    val privateKey: PrivateKey,

    /** Clave pública en bytes (32 bytes) — para hashing y firma */
    val publicKeyBytes: ByteArray,

    /** Clave privada en bytes (64 bytes) — para firma */
    val privateKeyBytes: ByteArray,

    /** Semilla original (32 bytes) — para regeneración controlada */
    val seed: ByteArray,

    /** Identificador del nodo = HEX(SHA-256(publicKey)) */
    val nodeId: String,

    /** Fingerprint para verificación verbal (12 hex chars) */
    val fingerprint: String,

    /** Timestamp de creación de la identidad */
    val createdAtMs: Long = System.currentTimeMillis(),

    /** Timestamp de expiración */
    val expiresAtMs: Long = createdAtMs + CryptoConfig.IDENTITY_TTL_MS
) {

    companion object {
        /**
         * Crea una nueva identidad desde los datos del generador.
         *
         * @param keyPairResult Resultado del generador Ed25519
         * @return Nueva identidad efímera
         */
        fun fromKeyPairResult(
            keyPairResult: com.zilch.crypto.keys.Ed25519KeyGenerator.KeyPairResult
        ): EphemeralIdentity {
            val nodeId = NodeIdentifier.derive(keyPairResult.publicKeyBytes)
            val fingerprint = NodeIdentifier.fingerprint(keyPairResult.publicKeyBytes)

            return EphemeralIdentity(
                publicKey = keyPairResult.publicKey,
                privateKey = keyPairResult.privateKey,
                publicKeyBytes = keyPairResult.publicKeyBytes,
                privateKeyBytes = keyPairResult.privateKeyBytes,
                seed = keyPairResult.seed,
                nodeId = nodeId,
                fingerprint = fingerprint
            )
        }
    }

    /**
     * Verifica si la identidad ha expirado.
     */
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAtMs

    /**
     * Tiempo restante de vida en milisegundos.
     */
    val remainingTimeMs: Long
        get() = maxOf(0, expiresAtMs - System.currentTimeMillis())

    /**
     * Clave pública en formato Base64 (para QR y transmisión).
     *
     * DECISIÓN: Se calcula bajo demanda y NO se cachea como String
     * para minimizar el tiempo que los datos sensibles permanecen
     * como String en memoria.
     */
    fun publicKeyBase64(): String {
        return Base64.getEncoder().encodeToString(publicKeyBytes)
    }

    /**
     * Firma datos con la clave privada de esta identidad.
     *
     * @param data Datos a firmar
     * @return Firma Ed25519 (64 bytes)
     */
    fun sign(data: ByteArray): ByteArray {
        return com.zilch.crypto.keys.Ed25519KeyGenerator.sign(data, privateKey)
    }

    /**
     * Limpia todas las claves sensibles de memoria.
     *
     * ⚠ DEBE llamarse cuando la identidad ya no se necesite:
     * - Al cambiar de sesión
     * - Al presionar el botón de pánico
     * - Al cerrar la app
     */
    fun wipe() {
        com.zilch.crypto.keys.SecureMemory.wipe(publicKeyBytes)
        com.zilch.crypto.keys.SecureMemory.wipe(privateKeyBytes)
        com.zilch.crypto.keys.SecureMemory.wipe(seed)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EphemeralIdentity) return false
        return nodeId == other.nodeId
    }

    override fun hashCode(): Int = nodeId.hashCode()

    override fun toString(): String {
        // toString() no expone claves — solo información pública
        return "EphemeralIdentity(nodeId=$nodeId, fingerprint=$fingerprint, " +
               "expired=$isExpired)"
    }
}
