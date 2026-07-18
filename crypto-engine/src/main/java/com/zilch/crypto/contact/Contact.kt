package com.zilch.crypto.contact

import com.zilch.crypto.keys.SecureMemory
import java.security.PublicKey
import java.util.Base64

/**
 * Contact — Representación de un contacto remoto.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: MODELO DE CONTACTO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Un contacto es la representación local de un nodo remoto
 * cuya clave pública fue verificada vía QR escaneado.
 *
 * El contacto NO almacena:
 * - Clave privada (la tiene solo el remoto)
 * - Nombre real (no hay registro)
 * - Número de teléfono (no hay registro)
 * - Correo electrónico (no hay registro)
 *
 * El contacto SÍ almacena:
 * - Clave pública Ed25519 del remoto (para cifrar/firmar)
 * - Identificador del nodo (SHA-256 de la clave pública)
 * - Fingerprint (para verificación verbal)
 * - Dirección(es) temporal(es) de red
 * - Timestamp de verificación
 *
 * ¿Por qué no se persiste la clave privada local en Contact?
 * Porque la clave privada vive exclusivamente en [EphemeralIdentity].
 * Los contactos solo necesitan la clave pública del remoto para
 * operaciones de cifrado y verificación.
 */
data class Contact(
    /** Identificador del nodo remoto = HEX(SHA-256(publicKey)) */
    val nodeId: String,

    /** Fingerprint para verificación verbal (12 hex chars) */
    val fingerprint: String,

    /** Clave pública Ed25519 del contacto remoto */
    val publicKey: PublicKey,

    /** Clave pública en bytes (32 bytes) */
    val publicKeyBytes: ByteArray,

    /** Dirección(es) temporal(es) conocida(s) del contacto */
    val addresses: MutableList<String> = mutableListOf(),

    /** Timestamp de cuando se verificó el contacto (escaneo QR) */
    val verifiedAtMs: Long = System.currentTimeMillis(),

    /** Número de veces que se ha contactado exitosamente */
    var contactCount: Int = 0,

    /** Último timestamp de comunicación exitosa */
    var lastContactMs: Long = 0
) {

    /**
     * Clave pública en Base64 para transmisión.
     *
     * Se calcula bajo demanda, no se cachea, para minimizar
     * el tiempo que el dato está como String en memoria.
     */
    fun publicKeyBase64(): String {
        return Base64.getEncoder().encodeToString(publicKeyBytes)
    }

    /**
     * Actualiza la dirección temporal del contacto.
     *
     * Un contacto puede cambiar su dirección (por ejemplo, al
     * regenerar su identidad Tor .onion). Esta función añade
     * la nueva dirección y elimina las antiguas.
     *
     * @param newAddress Nueva dirección temporal
     */
    fun updateAddress(newAddress: String) {
        addresses.clear()
        addresses.add(newAddress)
    }

    /**
     * Registra un contacto exitoso.
     */
    fun recordContact() {
        contactCount++
        lastContactMs = System.currentTimeMillis()
    }

    /**
     * Limpia la clave pública de memoria.
     */
    fun wipe() {
        SecureMemory.wipe(publicKeyBytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return nodeId == other.nodeId
    }

    override fun hashCode(): Int = nodeId.hashCode()

    override fun toString(): String {
        // toString() NO expone la clave pública — solo identificadores públicos
        return "Contact(nodeId=$nodeId, fingerprint=$fingerprint, " +
               "addresses=${addresses.size}, contacts=$contactCount)"
    }
}
