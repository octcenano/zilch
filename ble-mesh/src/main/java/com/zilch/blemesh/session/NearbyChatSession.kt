package com.zilch.blemesh.session

import com.zilch.blemesh.message.MeshMessage
import com.zilch.blemesh.message.MessageType
import com.zilch.blemesh.mesh.PeerNode
import java.util.UUID

/**
 * NearbyChatSession — Sesión de chat cercano entre dos nodos.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: SESIÓN DE CHAT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Una sesión de chat cercano representa una conversación activa
 * entre el nodo local y un peer remoto a través de BLE.
 *
 * PROPIEDADES DE LA SESIÓN:
 *
 * 1. **Efímera:** La sesión existe solo mientras la conexión BLE
 *    está activa. Al desconectar, la sesión se destruye.
 *
 * 2. **No persistida:** Los mensajes se mantienen SOLO en RAM.
 *    No hay base de datos, no hay caché a disco.
 *
 * 3. **Autenticada:** Todos los mensajes están firmados con
 *    Ed25519 y cifrados con AES-256-GCM.
 *
 * 4. **Bidireccional:** Ambos peers pueden enviar y recibir.
 *
 * VIDA DE UNA SESIÓN:
 *
 * ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
 * │CREATED   │ ──→ │HANDSHAKE │ ──→ │ACTIVE    │ ──→ │CLOSED    │
 * └──────────┘     └──────────┘     └──────────┘     └──────────┘
 *      │                 │                │                 │
 *   PeerLocal         Intercambio      Mensajes          Limpieza
 *   conecta           de claves        normales          de memoria
 */
data class NearbyChatSession(
    /** ID único de la sesión */
    val sessionId: String = UUID.randomUUID().toString(),

    /** Peer con el que se establece la sesión */
    val peer: PeerNode,

    /** Timestamp de creación */
    val createdAtMs: Long = System.currentTimeMillis(),

    /** Estado actual de la sesión */
    @Volatile
    var state: SessionState = SessionState.CREATED,

    /** Mensajes intercambiados en la sesión (solo RAM) */
    private val _messages: MutableList<SessionMessage> = mutableListOf(),

    /** Timestamp del último mensaje */
    @Volatile
    var lastActivityMs: Long = createdAtMs
) {

    /** Estados de la sesión */
    enum class SessionState {
        /** Sesión creada, conexión BLE establecida */
        CREATED,

        /** Handshake en progreso — intercambio de claves */
        HANDSHAKING,

        /** Sesión activa, mensajería funcional */
        ACTIVE,

        /** Sesión cerrada por uno de los peers */
        CLOSING,

        /** Sesión destruida, memoria limpia */
        CLOSED
    }

    /** Mensajes como lista inmutable */
    val messages: List<SessionMessage>
        get() = _messages.toList()

    /** Número de mensajes intercambiados */
    val messageCount: Int
        get() = _messages.size

    /** Duración de la sesión en milisegundos */
    val durationMs: Long
        get() = System.currentTimeMillis() - createdAtMs

    /** La sesión está activa y lista para usar */
    val isActive: Boolean
        get() = state == SessionState.ACTIVE

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Añade un mensaje enviado o recibido a la sesión.
     *
     * @param content Contenido del mensaje (texto plano, ya descifrado)
     * @param isFromLocal true si el mensaje es del nodo local
     * @param type Tipo de mensaje
     * @return El mensaje registrado
     */
    fun addMessage(
        content: String,
        isFromLocal: Boolean,
        type: MessageType = MessageType.TEXT
    ): SessionMessage {
        val message = SessionMessage(
            content = content,
            isFromLocal = isFromLocal,
            type = type,
            timestampMs = System.currentTimeMillis()
        )
        _messages.add(message)
        lastActivityMs = message.timestampMs
        return message
    }

    /**
     * Registra un mensaje raw (cifrado) para auditoría.
     */
    fun addRawMessage(rawData: ByteArray, isFromLocal: Boolean) {
        lastActivityMs = System.currentTimeMillis()
        // No almacenamos el ciphertext en la sesión — solo en memoria volatile
    }

    /**
     * Cierra la sesión y limpia la memoria.
     *
     * ⚠ Llamar cuando:
     * - El peer se desconecta BLE
     * - El usuario cierra el chat
     * - Se presiona el botón de pánico
     */
    fun close() {
        state = SessionState.CLOSED
        _messages.clear()
        peer.wipe()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NearbyChatSession) return false
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int = sessionId.hashCode()
}

/**
 * SessionMessage — Mensaje individual dentro de una sesión.
 *
 * Solo almacena el contenido en texto plano (ya descifrado).
 * El ciphertext original se descarta después del descifrado.
 */
data class SessionMessage(
    /** Contenido del mensaje */
    val content: String,

    /** true si es del nodo local, false si es del peer */
    val isFromLocal: Boolean,

    /** Tipo de mensaje */
    val type: com.zilch.blemesh.message.MessageType,

    /** Timestamp en milisegundos */
    val timestampMs: Long = System.currentTimeMillis()
) {
    /** Hora formateada para UI (HH:mm) */
    val formattedTime: String
        get() {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = timestampMs
            return String.format(
                "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE)
            )
        }
}
