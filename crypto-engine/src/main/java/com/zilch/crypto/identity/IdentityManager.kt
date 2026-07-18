package com.zilch.crypto.identity

import android.util.Log
import com.zilch.crypto.keys.Ed25519KeyGenerator
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * IdentityManager — Ciclo de vida de la identidad efímera.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: GESTIÓN DE CICLO DE VIDA
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El IdentityManager es el propietario único de la identidad
 * efímera durante la sesión. Sus responsabilidades:
 *
 * 1. **Generar** una nueva identidad al inicio de la sesión
 * 2. **Regenerar** la identidad cuando expira (TTL)
 * 3. **Destruir** la identidad de forma segura en emergencia
 * 4. **Notificar** a la UI cuando la identidad cambia
 *
 * Patrón: AtomicReference + Generación perezosa
 * - La identidad se mantiene en un AtomicReference para acceso
 *   atómico desde cualquier hilo
 * - Se genera solo cuando se solicita (lazy)
 * - La regeneración automática se dispara por un timer
 *
 * ¿Por qué no un singleton?
 * Porque cada sesión puede necesitar múltiples identities
 * (ej: una para Tor, otra para BLE). El IdentityManager
 * es un componente reutilizable, no un singleton.
 */
class IdentityManager {

    companion object {
        private const val TAG = "IdentityManager"
    }

    /** Identidad actual — acceso atómico */
    private val _currentIdentity = AtomicReference<EphemeralIdentity?>(null)

    /** Scope para el timer de expiración */
    private var managerScope: CoroutineScope? = null

    /** Job del timer de expiración */
    private var expirationJob: Job? = null

    /** Callback cuando la identidad se regenera */
    private var onIdentityChanged: ((old: EphemeralIdentity?, new: EphemeralIdentity) -> Unit)? = null

    /** Callback cuando la identidad expira */
    private var onIdentityExpired: ((identity: EphemeralIdentity) -> Unit)? = null

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Identidad actual. Si no existe, la genera de forma lazy.
     *
     * El primer acceso a esta propiedad genera la identidad
     * si no se ha llamado a [start] previamente.
     */
    val currentIdentity: EphemeralIdentity
        get() = _currentIdentity.get() ?: generateNewIdentity()

    /**
     * Verifica si existe una identidad activa.
     */
    val hasIdentity: Boolean
        get() = _currentIdentity.get() != null

    /**
     * Inicia el IdentityManager con un scope para timers.
     *
     * @param scope CoroutineScope para el timer de expiración
     */
    fun start(scope: CoroutineScope) {
        managerScope = scope
        // Generar identidad inicial
        generateNewIdentity()
        Log.i(TAG, "IdentityManager iniciado")
    }

    /**
     * Registra callbacks para cambios de identidad.
     */
    fun setCallbacks(
        onIdentityChanged: ((old: EphemeralIdentity?, new: EphemeralIdentity) -> Unit)? = null,
        onIdentityExpired: ((identity: EphemeralIdentity) -> Unit)? = null
    ) {
        this.onIdentityChanged = onIdentityChanged
        this.onIdentityExpired = onIdentityExpired
    }

    /**
     * Genera una nueva identidad efímera, destruyendo la anterior.
     *
     * Esta función se llama cuando:
     * - Se inicia una nueva sesión
     * - La identidad anterior expiró
     * - El usuario solicita una nueva identidad manualmente
     *
     * @return La nueva identidad generada
     */
    fun generateNewIdentity(): EphemeralIdentity {
        // Destruir la identidad anterior si existe
        val oldIdentity = _currentIdentity.getAndSet(null)
        oldIdentity?.wipe()

        Log.i(TAG, "Generando nueva identidad efimera...")

        // Generar nuevo par de claves
        val keyPairResult = Ed25519KeyGenerator.generateKeyPair()

        // Crear la identidad
        val newIdentity = EphemeralIdentity.fromKeyPairResult(keyPairResult)

        // Almacenar
        _currentIdentity.set(newIdentity)

        // Programar regeneración automática al expirar
        scheduleExpiration(newIdentity)

        // Notificar
        onIdentityChanged?.invoke(oldIdentity, newIdentity)

        Log.i(TAG, "Identidad generada: ${newIdentity.fingerprint}")

        return newIdentity
    }

    /**
     * Firma datos con la identidad actual.
     *
     * Convenience method que delega en la identidad actual.
     *
     * @param data Datos a firmar
     * @return Firma Ed25519
     * @throws IllegalStateException si no hay identidad activa
     */
    fun signWithCurrentIdentity(data: ByteArray): ByteArray {
        val identity = _currentIdentity.get()
            ?: throw IllegalStateException("No hay identidad activa para firmar")
        return identity.sign(data)
    }

    /**
     * Destruye la identidad actual de forma segura.
     *
     * Llamar cuando:
     * - El usuario presiona el botón de pánico
     * - Se cierra la sesión
     * - Se detecta una amenaza de seguridad
     */
    fun destroyIdentity() {
        expirationJob?.cancel()
        val identity = _currentIdentity.getAndSet(null)
        identity?.wipe()
        Log.w(TAG, "Identidad destruida de forma segura")
    }

    /**
     * Detiene el IdentityManager y limpia recursos.
     */
    fun stop() {
        destroyIdentity()
        managerScope = null
    }

    // ════════════════════════════════════════════════════════════════
    //  LÓGICA INTERNA
    // ════════════════════════════════════════════════════════════════

    /**
     * Programa la regeneración automática de la identidad.
     *
     * Cuando la identidad alcanza el 80% de su TTL, se genera
     * una nueva de forma proactiva. Esto evita que la app quede
     * sin identidad válida durante una operación activa.
     *
     * Ejemplo con TTL de 1 hora:
     * - A los 48 minutos se genera nueva identidad
     * - La anterior se destruye limpiamente
     * - La UI se actualiza
     */
    private fun scheduleExpiration(identity: EphemeralIdentity) {
        expirationJob?.cancel()

        val ttlMs = identity.expiresAtMs - identity.createdAtMs
        val warningTimeMs = (ttlMs * 0.8).toLong() // 80% del TTL
        val remainingToWarning = warningTimeMs - (System.currentTimeMillis() - identity.createdAtMs)

        if (remainingToWarning <= 0) {
            // La identidad ya está en zona de expiración
            Log.w(TAG, "Identidad ya en zona de expiración — regenerando")
            regenerateIdentity()
            return
        }

        managerScope?.launch {
            delay(remainingToWarning)

            val current = _currentIdentity.get()
            if (current?.nodeId == identity.nodeId) {
                // La identidad sigue siendo la misma (no se regeneró manualmente)
                Log.w(TAG, "Identidad alcanzando expiración — regenerando automáticamente")
                onIdentityExpired?.invoke(identity)
                regenerateIdentity()
            }
        }
    }

    /**
     * Regenera la identidad manteniendo consistencia interna.
     */
    private fun regenerateIdentity() {
        // generateNewIdentity() ya maneja: wipe de la anterior,
        // generación de la nueva, programación del timer,
        // y notificación del callback onIdentityChanged.
        // NO necesitamos invocar el callback aquí de nuevo.
        generateNewIdentity()
    }
}
