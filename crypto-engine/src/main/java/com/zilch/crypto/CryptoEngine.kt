package com.zilch.crypto

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.zilch.crypto.config.CryptoConfig
import com.zilch.crypto.contact.Contact
import com.zilch.crypto.contact.ContactManager
import com.zilch.crypto.exception.CryptoEngineException
import com.zilch.crypto.hash.NodeIdentifier
import com.zilch.crypto.identity.EphemeralIdentity
import com.zilch.crypto.identity.IdentityManager
import com.zilch.crypto.qr.QrDecoder
import com.zilch.crypto.qr.QrEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.security.PrivateKey
import java.security.PublicKey

/**
 * CryptoEngine — Fachada principal del motor criptográfico.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  ARQUITECTURA DEL MÓDULO CRYPTO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Este orquestador integra todos los componentes criptográficos:
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │                     CryptoEngine                           │
 * │  (Fachada que expone la API pública segura)                 │
 * ├───────────────┬───────────────┬─────────────────────────────┤
 * │ IdentityMgr   │ QrEncoder/    │ ContactManager              │
 * │ (Ciclo de     │ QrDecoder     │ (Almacén de contactos       │
 * │  vida de la   │ (Generación y │  verificados, solo RAM)     │
 * │  identidad    │  validación   │                             │
 * │  efímera)     │  de QR)       │                             │
 * └───────────────┴───────────────┴─────────────────────────────┘
 *
 * USO:
 * ```kotlin
 * val crypto = CryptoEngine.getInstance(context)
 *
 * // Iniciar en una sesión
 * crypto.start(lifecycleScope)
 *
 * // Generar QR para que te escaneen
 * val qrBitmap = crypto.generateQr("abc123.onion")
 *
 * // Escanear QR de otro usuario
 * val scanResult = crypto.processScannedQr(qrPayload)
 * // scanResult.fingerprint → "a3f2-8b1c-4d5e" (verificar verbalmente)
 * crypto.confirmContact(scanResult)
 *
 * // Firma de datos
 * val signature = crypto.signWithIdentity(data)
 *
 * // Destruir todo
 * crypto.emergencyDestroy()
 * ```
 *
 * DECISIÓN DE SEGURIDAD: Singleton por proceso.
 * Garantiza una sola fuente de verdad para la identidad
 * y los contactos. No pueden existir múltiples managers
 * compitiendo por el mismo estado.
 */
class CryptoEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CryptoEngine"

        @Volatile
        private var instance: CryptoEngine? = null

        fun getInstance(context: Context): CryptoEngine {
            return instance ?: synchronized(this) {
                instance ?: CryptoEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }

        @Synchronized
        fun destroyInstance() {
            instance?.emergencyDestroy()
            instance = null
        }
    }

    // ── Componentes internos ────────────────────────────────────────

    val identityManager = IdentityManager()
    val contactManager = ContactManager()

    /** Estado observable de la identidad para la UI */
    val identity: EphemeralIdentity
        get() = identityManager.currentIdentity

    /** Contactos como StateFlow para observación en la UI */
    val contactsList: StateFlow<List<Contact>>
        get() = contactManager.contactsList

    /** Estado del motor */
    private var isStarted = false

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA — CICLO DE VIDA
    // ════════════════════════════════════════════════════════════════

    /**
     * Inicia el motor criptográfico.
     *
     * Genera la primera identidad efímera y configura los
     * timers de expiración.
     *
     * @param scope CoroutineScope del componente propietario
     */
    fun start(scope: CoroutineScope) {
        if (isStarted) {
            Log.w(TAG, "Motor ya está en ejecución")
            return
        }

        identityManager.setCallbacks(
            onIdentityChanged = { old, new ->
                Log.i(TAG, "Identidad cambiada: ${old?.fingerprint ?: "null"} → ${new.fingerprint}")
            },
            onIdentityExpired = {
                Log.w(TAG, "Identidad expirada — regenerando automáticamente")
            }
        )

        identityManager.start(scope)
        isStarted = true

        Log.i(TAG, "Motor criptográfico iniciado — identidad: ${identity.fingerprint}")
    }

    /**
     * Detiene el motor y limpia toda la memoria sensible.
     */
    fun stop() {
        identityManager.stop()
        contactManager.clearAll()
        isStarted = false
        Log.i(TAG, "Motor criptográfico detenido")
    }

    /**
     * Parada de emergencia: destruye TODO.
     *
     * Llamar cuando el usuario presiona el botón de pánico.
     * Borra identidad, contactos, y fuerza garbage collection.
     */
    fun emergencyDestroy() {
        Log.e(TAG, "EMERGENCIA: Destruyendo motor criptográfico")
        identityManager.destroyIdentity()
        contactManager.clearAll()
        isStarted = false
        System.gc() // Sugerir GC para limpiar memoria residual
    }

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA — QR Y EMPAREJAMIENTO
    // ════════════════════════════════════════════════════════════════

    /**
     * Genera un código QR con la identidad actual y una dirección temporal.
     *
     * El QR contiene la clave pública firmada, lista para ser
     * escaneada por otro dispositivo.
     *
     * @param temporaryAddress Dirección temporal (ej: "abc123.onion", "ble:uuid")
     * @return Bitmap del código QR
     * @throws CryptoEngineException si la generación falla
     */
    fun generateQr(temporaryAddress: String): Bitmap {
        val currentId = identityManager.currentIdentity
        return QrEncoder.generateQrBitmap(currentId, temporaryAddress)
    }

    /**
     * Decodifica un payload QR escaneado.
     *
     * Valida la firma, la expiración, y la versión del protocolo.
     * NO añade el contacto automáticamente — el usuario debe
     * confirmar el fingerprint verbalmente primero.
     *
     * @param qrPayload String contenido en el QR escaneado
     * @return DecodedQr con la información validada del contacto remoto
     * @throws CryptoEngineException si la validación falla
     */
    fun processScannedQr(qrPayload: String): QrDecoder.DecodedQr {
        return QrDecoder.decode(qrPayload)
    }

    /**
     * Confirma y añade un contacto después de la verificación verbal.
     *
     * El usuario debe:
     * 1. Escanear el QR → processScannedQr()
     * 2. Comparar fingerprints verbalmente
     * 3. Confirmar → confirmContact()
     *
     * @param decodedQr Resultado de processScannedQr()
     * @return El contacto añadido
     * @throws CryptoEngineException.ContactAlreadyExists si ya existe
     */
    fun confirmContact(decodedQr: QrDecoder.DecodedQr): Contact {
        return contactManager.addContact(decodedQr)
    }

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA — CRIPTOGRAFÍA
    // ════════════════════════════════════════════════════════════════

    /**
     * Firma datos con la identidad efímera actual.
     *
     * @param data Datos a firmar
     * @return Firma Ed25519 (64 bytes)
     */
    fun signWithIdentity(data: ByteArray): ByteArray {
        return identityManager.signWithCurrentIdentity(data)
    }

    /**
     * Obtiene la clave pública de la identidad actual.
     *
     * @return PublicKey de la identidad efímera
     */
    fun getCurrentPublicKey(): PublicKey {
        return identityManager.currentIdentity.publicKey
    }

    /**
     * Obtiene el fingerprint de la identidad actual.
     *
     * @return Fingerprint formateado (ej: "a3f2-8b1c-4d5e")
     */
    fun getCurrentFingerprint(): String {
        return identityManager.currentIdentity.fingerprint
    }

    /**
     * Obtiene el identificador de nodo de la identidad actual.
     *
     * @return NodeId hex de 64 caracteres
     */
    fun getCurrentNodeId(): String {
        return identityManager.currentIdentity.nodeId
    }

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA — GESTIÓN DE CONTACTOS
    // ════════════════════════════════════════════════════════════════

    /**
     * Obtiene un contacto conocido.
     */
    fun getContact(nodeId: String): Contact? {
        return contactManager.getContact(nodeId)
    }

    /**
     * Verifica si un nodo es un contacto conocido.
     */
    fun isKnownContact(nodeId: String): Boolean {
        return contactManager.isKnownContact(nodeId)
    }

    /**
     * Elimina un contacto.
     */
    fun removeContact(nodeId: String): Boolean {
        return contactManager.removeContact(nodeId)
    }

    /**
     * Obtiene el fingerprint de un contacto remoto por su nodeId.
     *
     * Útil para verificar un contacto por su identificador conocido.
     */
    fun getContactFingerprint(nodeId: String): String? {
        return contactManager.getContact(nodeId)?.fingerprint
    }
}
