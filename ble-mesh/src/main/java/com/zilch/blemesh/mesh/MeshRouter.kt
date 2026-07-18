package com.zilch.blemesh.mesh

import android.util.Log
import com.zilch.blemesh.config.BleConfig
import com.zilch.blemesh.message.MeshMessage
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * MeshRouter — Enrutamiento de mensajes en la red mesh BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: ENRUTAMIENTO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El enrutador implementa un protocolo de flooding simple con TTL:
 *
 * 1. Cuando un nodo recibe un mensaje, verifica:
 *    a. ¿Ya lo procesé? (deduplicación por messageId) → Si: descartar
 *    b. ¿Es para mí? (recipientNodeId == myNodeId) → Si: entregar
 *    c. ¿TTL > 0? → Si: reenviar a todos los peers conectados
 *    d. Si TTL == 0: descartar (evitar loops infinitos)
 *
 * 2. La deduplicación usa un cache con TTL:
 *    - Cada messageId se almacena por [MESSAGE_CACHE_TTL_MS]
 *    - Después de expirar, se purge para liberar memoria
 *
 * SEGURIDAD DEL ROUTER:
 * - Los nodos intermedios NO pueden leer el payload (está cifrado)
 * - Los nodos intermedios SÍ leen el header (recipientNodeId, ttl)
 * - La firma garantiza que nadie puede modificar el header sin ser detectado
 * - El TTL previene abusos (un nodo malicioso no puede amplificar mensajes)
 *
 * TOPOLOGÍA:
 * ┌──────┐    BLE     ┌──────┐    BLE     ┌──────┐
 * │Nodo A│ ←────────→ │Nodo B│ ←────────→ │Nodo C│
 * └──────┘            └──────┘            └──────┘
 *
 * Si A quiere enviar a C pero no está en rango directo,
 * B actúa como relay: recibe de A, reenvía a C.
 */
class MeshRouter(private val myNodeId: String) {

    companion object {
        private const val TAG = "MeshRouter"
    }

    /** Cache de mensajes procesados (para deduplicación) */
    private val processedMessages = ConcurrentHashMap<String, Long>()

    /** Callback para mensajes destinados a este nodo */
    private var onMessageReceived: ((MeshMessage) -> Unit)? = null

    /** Callback para mensajes reenviados (auditoría) */
    private var onMessageForwarded: ((MeshMessage) -> Unit)? = null

    /** Scope para tareas de limpieza */
    private var routerScope: CoroutineScope? = null

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Inicia el router con limpieza periódica del cache.
     */
    fun start(scope: CoroutineScope) {
        routerScope = scope

        // Programar limpieza periódica del cache de deduplicación
        scope.launch {
            while (isActive) {
                delay(BleConfig.MESSAGE_CACHE_TTL_MS)
                purgeExpiredMessages()
            }
        }

        Log.d(TAG, "Router iniciado para nodo: ${myNodeId.take(16)}...")
    }

    /**
     * Procesa un mensaje recibido.
     *
     * Este es el punto de entrada principal del router.
     * Se llama cuando se recibe un mensaje desde cualquier fuente
     * (conexión BLE directa, relay de otro nodo, etc.)
     *
     * @param message Mensaje recibido
     * @param peers Peers conectados para posibles reenvíos
     * @return true si el mensaje fue procesado/entregado,
     *         false si fue descartado (duplicado, expirado, etc.)
     */
    fun processReceivedMessage(
        message: MeshMessage,
        peers: Map<String, PeerNode>
    ): Boolean {
        // ═══ PASO 1: Deduplicación atómica (putIfAbsent) ═══
        // Usamos putIfAbsent para evitar la condición TOCTOU entre
        // isAlreadyProcessed() y markAsProcessed().
        val previousTimestamp = processedMessages.putIfAbsent(
            message.messageId, System.currentTimeMillis()
        )
        if (previousTimestamp != null &&
            System.currentTimeMillis() - previousTimestamp < BleConfig.MESSAGE_CACHE_TTL_MS
        ) {
            Log.d(TAG, "Mensaje duplicado descartado: ${message.messageId.take(8)}")
            return false
        }

        // Evitar que el cache crezca indefinidamente
        if (processedMessages.size > BleConfig.RECENT_MESSAGES_CACHE_SIZE) {
            purgeExpiredMessages()
        }

        // ═══ PASO 2: Verificar TTL ═══
        if (message.isExpired) {
            Log.d(TAG, "Mensaje expirado (TTL=0): ${message.messageId.take(8)}")
            return false
        }

        // ═══ PASO 4: ¿Es para mí? ═══
        if (message.recipientNodeId == null || message.recipientNodeId == myNodeId) {
            Log.i(TAG, "Mensaje entregado: ${message.messageId.take(8)}")
            onMessageReceived?.invoke(message)
            return true
        }

        // ═══ PASO 5: Reenviar (flooding) ═══
        Log.d(TAG, "Reenviando mensaje: ${message.messageId.take(8)}")
        val forwarded = message.decrementTtl()
        onMessageForwarded?.invoke(forwarded)

        return false
    }

    /**
     * Verifica si un messageId ya fue procesado.
     */
    fun isAlreadyProcessed(messageId: String): Boolean {
        val timestamp = processedMessages[messageId] ?: return false
        return System.currentTimeMillis() - timestamp < BleConfig.MESSAGE_CACHE_TTL_MS
    }

    /**
     * Elimina mensajes expirados del cache.
     */
    private fun purgeExpiredMessages() {
        val now = System.currentTimeMillis()
        val expired = processedMessages.entries.filter {
            now - it.value > BleConfig.MESSAGE_CACHE_TTL_MS
        }.map { it.key }

        expired.forEach { processedMessages.remove(it) }

        if (expired.isNotEmpty()) {
            Log.d(TAG, "Cache limpiado: ${expired.size} mensajes expirados")
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  CALLBACKS
    // ════════════════════════════════════════════════════════════════

    fun setOnMessageReceived(listener: (MeshMessage) -> Unit) {
        onMessageReceived = listener
    }

    fun setOnMessageForwarded(listener: (MeshMessage) -> Unit) {
        onMessageForwarded = listener
    }

    /**
     * Detiene el router y limpia el cache.
     */
    fun stop() {
        routerScope?.cancel()
        routerScope = null
        processedMessages.clear()
    }
}
