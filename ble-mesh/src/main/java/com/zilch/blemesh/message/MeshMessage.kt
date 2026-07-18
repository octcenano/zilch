package com.zilch.blemesh.message

import java.util.UUID

/**
 * MeshMessage — Modelo de mensaje en la red mesh BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: ESTRUCTURA DEL MENSAJE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Cada mensaje en el mesh contiene:
 *
 * ┌──────────────────────────────────────────────────────────┐
 * │  Header (no cifrado — necesario para routing)           │
 * │  ├── messageId (UUID) — deduplicación                   │
 * │  ├── senderNodeId — identificador del emisor            │
 * │  ├── recipientNodeId — identificador del destinatario   │
 * │  ├── timestamp — previene replay attacks                │
 * │  ├── ttl — time-to-live del mesh                        │
 * │  ├── type — tipo de mensaje (TEXT, HANDSHAKE, ACK, etc) │
 * │  └── chunkIndex / totalChunks — para mensajes large     │
 * ├──────────────────────────────────────────────────────────┤
 * │  Payload (CIFRADO con AES-256-GCM)                     │
 * │  └── contenido del mensaje (texto, archivo, control)    │
 * ├──────────────────────────────────────────────────────────┤
 * │  Firma (Ed25519 — 64 bytes)                            │
 * │  └── firma del header + payload cifrado                 │
 * └──────────────────────────────────────────────────────────┘
 *
 * ¿Por qué el header no está cifrado?
 * Porque los nodos intermedios del mesh necesitan leer el
 * recipientNodeId y el ttl para reenviar el mensaje.
 * La confidencialidad del contenido está en el payload cifrado.
 * La autenticidad está en la firma.
 */
data class MeshMessage(
    /** ID único del mensaje (UUID) — para deduplicación */
    val messageId: String = UUID.randomUUID().toString(),

    /** Identificador del nodo emisor */
    val senderNodeId: String,

    /** Identificador del nodo destinatario (o broadcast) */
    val recipientNodeId: String?,

    /** Timestamp de creación (Unix epoch ms) */
    val timestamp: Long = System.currentTimeMillis(),

    /** Time-to-live — se decrementa en cada hop */
    var ttl: Int = com.zilch.blemesh.config.BleConfig.MAX_MESH_TTL,

    /** Tipo de mensaje */
    val type: MessageType = MessageType.TEXT,

    /** Índice del chunk actual (para mensajes fragmentados) */
    val chunkIndex: Int = 0,

    /** Total de chunks del mensaje */
    val totalChunks: Int = 1,

    /** Payload cifrado (si es null, el mensaje está vacío) */
    val encryptedPayload: ByteArray? = null,

    /** Firma Ed25519 del mensaje (64 bytes) */
    val signature: ByteArray? = null
) {

    /** El mensaje ha agotado su TTL y no debe reenviarse */
    val isExpired: Boolean
        get() = ttl <= 0

    /** Es un mensaje fragmentado (compuesto de múltiples chunks) */
    val isFragmented: Boolean
        get() = totalChunks > 1

    /** Es el primer chunk del mensaje */
    val isFirstChunk: Boolean
        get() = chunkIndex == 0

    /** Es el último chunk del mensaje */
    val isLastChunk: Boolean
        get() = chunkIndex >= totalChunks - 1

    /**
     * Decrementa el TTL para reenvío.
     * Retorna una copia con el TTL decrementado.
     */
    fun decrementTtl(): MeshMessage {
        return copy(ttl = ttl - 1)
    }

    companion object {
        /**
         * Deserializa un ByteArray en un MeshMessage.
         *
         * El formato es el mismo que produce serializeHeader() + encryptedPayload:
         * [header bytes] + [encryptedPayload bytes]
         */
        fun deserialize(data: ByteArray): MeshMessage {
            val buffer = java.nio.ByteBuffer.wrap(data)

            // messageId
            val idLen = buffer.int
            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val messageId = String(idBytes, Charsets.UTF_8)

            // senderNodeId
            val senderLen = buffer.int
            val senderBytes = ByteArray(senderLen)
            buffer.get(senderBytes)
            val senderNodeId = String(senderBytes, Charsets.UTF_8)

            // recipientNodeId
            val recipientLen = buffer.int
            val recipientBytes = ByteArray(recipientLen)
            buffer.get(recipientBytes)
            val recipientNodeId = String(recipientBytes, Charsets.UTF_8)
                .ifBlank { null }

            // timestamp
            val timestamp = buffer.long

            // ttl
            val ttl = buffer.get().toInt() and 0xFF

            // type
            val typeOrdinal = buffer.get().toInt() and 0xFF
            val type = MessageType.entries.getOrElse(typeOrdinal) { MessageType.TEXT }

            // chunkIndex
            val chunkIndex = buffer.short.toInt() and 0xFFFF

            // totalChunks
            val totalChunks = buffer.short.toInt() and 0xFFFF

            // encryptedPayload (resto del buffer)
            val remaining = if (buffer.hasRemaining()) {
                val payload = ByteArray(buffer.remaining())
                buffer.get(payload)
                payload
            } else null

            return MeshMessage(
                messageId = messageId,
                senderNodeId = senderNodeId,
                recipientNodeId = recipientNodeId,
                timestamp = timestamp,
                ttl = ttl,
                type = type,
                chunkIndex = chunkIndex,
                totalChunks = totalChunks,
                encryptedPayload = remaining
            )
        }
    }

    /**
     * Serializa el header a bytes para transmisión.
     *
     * Formato:
     * [messageId:36][senderNodeId:64][recipientNodeId:64 or ""]:8]
     * [timestamp:8][ttl:1][type:1][chunkIndex:2][totalChunks:2]
     *
     * Total header: ~186 bytes fijo (con recipientNodeId de 64 chars)
     */
    fun serializeHeader(): ByteArray {
        val idBytes = messageId.toByteArray(Charsets.UTF_8)
        val senderBytes = senderNodeId.toByteArray(Charsets.UTF_8)
        val recipientBytes = (recipientNodeId ?: "").toByteArray(Charsets.UTF_8)

        val buffer = java.nio.ByteBuffer.allocate(
            4 + idBytes.size +
            4 + senderBytes.size +
            4 + recipientBytes.size +
            8 + 1 + 1 + 2 + 2
        )

        // messageId
        buffer.putInt(idBytes.size)
        buffer.put(idBytes)
        // senderNodeId
        buffer.putInt(senderBytes.size)
        buffer.put(senderBytes)
        // recipientNodeId
        buffer.putInt(recipientBytes.size)
        buffer.put(recipientBytes)
        // timestamp (8 bytes long)
        buffer.putLong(timestamp)
        // ttl (1 byte)
        buffer.put(ttl.toByte())
        // type (1 byte)
        buffer.put(type.ordinal.toByte())
        // chunkIndex (2 bytes)
        buffer.putShort(chunkIndex.toShort())
        // totalChunks (2 bytes)
        buffer.putShort(totalChunks.toShort())

        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshMessage) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}

/**
 * Tipos de mensaje en el mesh.
 *
 * El orden importa: se serializa como ordinal byte.
 */
enum class MessageType {
    /** Mensaje de texto cifrado */
    TEXT,

    /** Handshake inicial entre dos peers */
    HANDSHAKE,

    /** Acknowledgment de recepción */
    ACK,

    /** Mensaje de routing (control del mesh) */
    ROUTING,

    /** Solicitud de eliminación de mensaje */
    REVOKE,

    /** Ping de keepalive */
    PING,

    /** Pong de respuesta a ping */
    PONG
}
