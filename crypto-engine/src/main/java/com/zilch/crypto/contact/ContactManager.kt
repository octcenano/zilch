package com.zilch.crypto.contact

import android.util.Log
import com.zilch.crypto.exception.CryptoEngineException
import com.zilch.crypto.hash.NodeIdentifier
import com.zilch.crypto.qr.QrDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * ContactManager — Gestión de contactos verificados.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: ALMACENAMIENTO DE CONTACTOS
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Los contactos se almacenan SOLO en memoria RAM durante la sesión.
 * No se persisten a disco, no se sincronizan a la nube.
 *
 * ¿Por qué no persistir contactos?
 * 1. **Plausible deniability:** Si te detienen, no hay evidencia
 *    de que alguna vez contactaste a nadie. Borrar la app borra
 *    todos los contactos.
 * 2. **Identidad efímera:** Los contactos se re-escanean en cada
 *    sesión. No hay "lista de amigos" permanente.
 * 3. **Reducir superficie de ataque:** Un disco cifrado con SQLCipher
 *    es más seguro que nada, pero nada es más seguro que RAM limpia.
 *
 * Excepción: En una futura iteración, se podrá exportar/importar
 * contactos de forma cifrada para persistencia opcional.
 *
 * ConcurrentHashMap se usa porque los contactos pueden ser
 * accedidos desde múltiples coroutines (UI, red, BLE).
 */
class ContactManager {

    companion object {
        private const val TAG = "ContactManager"
    }

    /** Almacén de contactos: nodeId → Contact */
    private val contacts = ConcurrentHashMap<String, Contact>()

    /** Estado observable de la lista de contactos para la UI */
    private val _contactsList = MutableStateFlow<List<Contact>>(emptyList())
    val contactsList: StateFlow<List<Contact>> = _contactsList.asStateFlow()

    /** Número total de contactos */
    val contactCount: Int
        get() = contacts.size

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Añade un contacto desde un payload QR decodificado.
     *
     * Flujo de seguridad:
     * 1. El QR ya fue validado por QrDecoder (firma, expiración, etc.)
     * 2. Se verifica que no sea un duplicado
     * 3. Se crea el Contact y se almacena
     *
     * @param decodedQr Resultado de QrDecoder.decode()
     * @return El contacto creado
     * @throws CryptoEngineException.ContactAlreadyExists si ya existe
     */
    fun addContact(decodedQr: QrDecoder.DecodedQr): Contact {
        val contact = Contact(
            nodeId = decodedQr.nodeId,
            fingerprint = decodedQr.fingerprint,
            publicKey = decodedQr.publicKey,
            publicKeyBytes = decodedQr.publicKeyBytes,
            addresses = mutableListOf(decodedQr.temporaryAddress)
        )

        // ═══ OPERACIÓN ATÓMICA ═══
        // putIfAbsent es atómico en ConcurrentHashMap:
        // si la clave ya existe, retorna el valor existente
        // SIN sobreescribir. Esto previene race conditions.
        val existing = contacts.putIfAbsent(contact.nodeId, contact)
        if (existing != null) {
            contact.wipe() // Limpiar el objeto que no se usará
            Log.w(TAG, "Contacto duplicado: ${decodedQr.fingerprint}")
            throw CryptoEngineException.ContactAlreadyExists(
                existingFingerprint = existing.fingerprint
            )
        }

        notifyContactsChanged()
        Log.i(TAG, "Contacto añadido: ${contact.fingerprint}")

        return contact
    }

    /**
     * Añade un contacto directamente desde datos verificados manualmente.
     *
     * Usado cuando el usuario confirma el fingerprint verbalmente
     * después de escanear el QR.
     *
     * @param publicKeyBytes Clave pública del contacto
     * @param address Dirección temporal
     * @return El contacto creado
     */
    fun addContactDirect(
        publicKeyBytes: ByteArray,
        address: String
    ): Contact {
        val nodeId = NodeIdentifier.derive(publicKeyBytes)
        val fingerprint = NodeIdentifier.fingerprint(publicKeyBytes)

        val keyFactory = java.security.KeyFactory.getInstance("Ed25519")
        val keySpec = java.security.spec.X509EncodedKeySpec(
            byteArrayOf(0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00) +
            publicKeyBytes
        )
        val publicKey = keyFactory.generatePublic(keySpec)

        val contact = Contact(
            nodeId = nodeId,
            fingerprint = fingerprint,
            publicKey = publicKey,
            publicKeyBytes = publicKeyBytes,
            addresses = mutableListOf(address)
        )

        // ═══ OPERACIÓN ATÓMICA ═══
        val existing = contacts.putIfAbsent(contact.nodeId, contact)
        if (existing != null) {
            contact.wipe()
            throw CryptoEngineException.ContactAlreadyExists(fingerprint)
        }

        notifyContactsChanged()
        Log.i(TAG, "Contacto añadido directamente: $fingerprint")
        return contact
    }

    /**
     * Obtiene un contacto por su identificador de nodo.
     *
     * @param nodeId Identificador del nodo
     * @return El contacto o null si no existe
     */
    fun getContact(nodeId: String): Contact? {
        return contacts[nodeId]
    }

    /**
     * Obtiene un contacto por su fingerprint.
     *
     * Útil para verificar si un QR escaneado corresponde a un
     * contacto conocido.
     *
     * @param fingerprint Fingerprint del contacto
     * @return El contacto o null
     */
    fun getContactByFingerprint(fingerprint: String): Contact? {
        return contacts.values.find { it.fingerprint == fingerprint }
    }

    /**
     * Elimina un contacto por su identificador.
     *
     * La eliminación es limpiia: se sobreescriben los bytes
     * de la clave pública antes de descartar el objeto.
     *
     * @param nodeId Identificador del nodo a eliminar
     * @return true si se eliminó, false si no existía
     */
    fun removeContact(nodeId: String): Boolean {
        val contact = contacts.remove(nodeId)
        if (contact != null) {
            contact.wipe()
            notifyContactsChanged()
            Log.i(TAG, "Contacto eliminado: ${contact.fingerprint}")
            return true
        }
        return false
    }

    /**
     * Verifica si un identificador de nodo pertenece a un contacto conocido.
     *
     * @param nodeId Identificador a verificar
     * @return true si el contacto existe
     */
    fun isKnownContact(nodeId: String): Boolean {
        return contacts.containsKey(nodeId)
    }

    /**
     * Obtiene todos los contactos como lista inmutable.
     */
    fun getAllContacts(): List<Contact> {
        return contacts.values.toList()
    }

    /**
     * Busca contactos por coincidencia parcial del fingerprint.
     *
     * Útil para la UI: el usuario escribe los primeros caracteres
     * del fingerprint y se filtran los contactos.
     *
     * @param query Texto a buscar (case-insensitive)
     * @return Lista de contactos que coinciden
     */
    fun searchByFingerprint(query: String): List<Contact> {
        val normalizedQuery = query.lowercase().replace("-", "")
        return contacts.values.filter { contact ->
            val normalizedFingerprint = contact.fingerprint.replace("-", "")
            normalizedFingerprint.contains(normalizedQuery)
        }
    }

    /**
     * Limpia todos los contactos de memoria.
     *
     * Cada contacto tiene sus bytes de clave pública sobrescritos
     * antes de descartarse.
     */
    fun clearAll() {
        contacts.values.forEach { it.wipe() }
        contacts.clear()
        notifyContactsChanged()
        Log.w(TAG, "Todos los contactos eliminados")
    }

    // ════════════════════════════════════════════════════════════════
    //  LÓGICA INTERNA
    // ════════════════════════════════════════════════════════════════

    /**
     * Notifica a la UI que la lista de contactos cambió.
     */
    private fun notifyContactsChanged() {
        _contactsList.value = contacts.values.toList()
    }
}
