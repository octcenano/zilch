package com.zilch.crypto.hash

import com.zilch.crypto.config.CryptoConfig
import java.security.MessageDigest
import java.util.Base64

/**
 * NodeIdentifier — Derivación del identificador único del nodo.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: IDENTIFICADOR DE NODO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El identificador del nodo se deriva como:
 *   nodeId = HEX(SHA-256(publicKey))
 *
 * ¿Por qué SHA-256 del hash y no la clave pública directamente?
 *
 * 1. **Longitud fija:** Un hash siempre tiene 64 caracteres hex,
 *    independientemente del algoritmo de clave subyacente.
 *
 * 2. **Privacidad parcial:** El hash no revela la clave pública
 *    directamente (resistencia de preimagen). Un atacante no puede
 *    reconstruir la clave pública desde el identificador.
 *
 * 3. **Compatibilidad con .onion:** El formato de 64 chars hex
 *    se parece a una dirección .onion v3 (56 chars base32), lo que
 *    facilita la integración con la UI y el protocolo.
 *
 * 4. **Unicidad global:** Dado que la probabilidad de colisión de
 *    SHA-256 es prácticamente nula, el identificador es único
 *    en todo el sistema descentralizado.
 */
object NodeIdentifier {

    /**
     * Deriva el identificador de nodo desde una clave pública.
     *
     * @param publicKeyBytes Clave pública Ed25519 en bytes (32 bytes)
     * @return String hexadecimal de 64 caracteres
     */
    fun derive(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance(CryptoConfig.HASH_ALGORITHM)
        val hash = digest.digest(publicKeyBytes)
        return bytesToHex(hash)
    }

    /**
     * Deriva el fingerprint para verificación verbal.
     *
     * Formato: aaaa-bbbb-cccc (12 hex chars del nodeId)
     *
     * El fingerprint es una porción truncada del nodeId, formateada
     * para facilitar la verificación verbal entre dos usuarios.
     * Al escanear un QR, ambas partes deben comparar sus fingerprints
     * para confirmar que no hubo MITM.
     *
     * @param publicKeyBytes Clave pública Ed25519 en bytes
     * @return Fingerprint formateado, ej: "a3f2-8b1c-4d5e"
     */
    fun fingerprint(publicKeyBytes: ByteArray): String {
        val nodeId = derive(publicKeyBytes)
        val shortId = nodeId.take(CryptoConfig.FINGERPRINT_LENGTH)

        return shortId.chunked(CryptoConfig.FINGERPRINT_GROUP_SIZE)
            .joinToString(CryptoConfig.FINGERPRINT_SEPARATOR)
            .lowercase()
    }

    /**
     * Deriva el identificador de nodo desde una clave pública en Base64.
     *
     * @param publicKeyBase64 Clave pública en Base64
     * @return String hexadecimal del identificador
     */
    fun deriveFromBase64(publicKeyBase64: String): String {
        val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
        return derive(publicKeyBytes)
    }

    /**
     * Formatea un nodeId para visualización con separación de grupos.
     *
     * Útil para mostrar en la UI: "a3f2b81c...4d5e9f0a"
     *
     * @param nodeId Identificador hex de 64 caracteres
     * @return String formateado
     */
    fun formatForDisplay(nodeId: String): String {
        return nodeId.chunked(8).joinToString(" ")
    }

    /**
     * Convierte un array de bytes a string hexadecimal.
     *
     * @param bytes Array de bytes
     * @return String hexadecimal en minúsculas
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
