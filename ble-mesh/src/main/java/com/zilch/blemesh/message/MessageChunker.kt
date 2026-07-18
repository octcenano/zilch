package com.zilch.blemesh.message

import com.zilch.blemesh.config.BleConfig

/**
 * MessageChunker — Fragmentación y reensamblaje de mensajes BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: CHUNKING
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * BLE tiene un MTU (Maximum Transmission Unit) limitado.
 * Aunque negociamos 512 bytes, el payload efectivo por chunk es
 * DESIRED_MTU - 3 bytes de overhead del protocolo.
 *
 * El chunker divide un mensaje grande en chunks que caben en
 * un solo envío BLE. Cada chunk contiene:
 *
 * ┌──────────────────────────────────────────────────┐
 * │  [seqNum:1][totalChunks:1][chunkData:variable]  │
 * └──────────────────────────────────────────────────┘
 *
 * - seqNum: Índice del chunk (0-based), 1 byte → máx 255 chunks
 * - totalChunks: Total de chunks, 1 byte → máx 255 chunks
 * - chunkData: Datos del chunk
 *
 * Con MTU de 512: cada chunk lleva 509 bytes de payload.
 * Un mensaje de 10KB se divide en ~20 chunks.
 *
 * ¿Por qué no usamos el protocolo de fragmentación de BLE (L2CAP)?
 * Porque no todos los dispositivos lo soportan y es más complejo.
 * Nuestro protocolo de aplicación es más portable y controlado.
 */
object MessageChunker {

    /** Overhead por chunk: 1 byte seqNum + 1 byte totalChunks + 1 byte reserved */
    private const val CHUNK_OVERHEAD = 3

    /**
     * Divide un mensaje en chunks que caben en el MTU BLE.
     *
     * @param data Datos a fragmentar
     * @param mtu MTU negociado con el dispositivo remoto
     * @return Lista de chunks serializados
     * @throws IllegalArgumentException si el mensaje excede 255 chunks
     */
    fun chunk(data: ByteArray, mtu: Int = BleConfig.DESIRED_MTU): List<ByteArray> {
        val maxPayload = mtu - CHUNK_OVERHEAD
        if (maxPayload <= 0) {
            throw IllegalArgumentException("MTU demasiado pequeño: $mtu")
        }

        val totalChunks = (data.size + maxPayload - 1) / maxPayload
        if (totalChunks > 255) {
            throw IllegalArgumentException(
                "Mensaje demasiado grande: $totalChunks chunks (máx 255)"
            )
        }

        val chunks = mutableListOf<ByteArray>()

        for (i in 0 until totalChunks) {
            val offset = i * maxPayload
            val length = minOf(maxPayload, data.size - offset)
            val chunkData = data.copyOfRange(offset, offset + length)

            val chunk = ByteArray(CHUNK_OVERHEAD + length)
            chunk[0] = i.toByte()              // seqNum
            chunk[1] = totalChunks.toByte()     // totalChunks
            chunk[2] = 0                        // reserved
            chunkData.copyInto(chunk, CHUNK_OVERHEAD)

            chunks.add(chunk)
        }

        return chunks
    }

    /**
     * Reensambla múltiples chunks en el mensaje original.
     *
     * @param chunks Lista de chunks recibidos
     * @param expectedSize Tamaño esperado del mensaje original (para validación)
     * @return ByteArray con el mensaje original reensamblado
     * @throws com.zilch.blemesh.exception.BleMeshException.MessageCorrupted
     *         si faltan chunks o están corruptos
     */
    fun reassemble(chunks: List<ByteArray>, expectedSize: Int? = null): ByteArray {
        if (chunks.isEmpty()) {
            throw com.zilch.blemesh.exception.BleMeshException.MessageCorrupted(
                "No hay chunks para reensamblar"
            )
        }

        // Leer metadata del primer chunk
        val totalChunks = chunks[0][1].toInt() and 0xFF
        if (chunks.size != totalChunks) {
            throw com.zilch.blemesh.exception.BleMeshException.MessageCorrupted(
                "Faltan chunks: esperados $totalChunks, recibidos ${chunks.size}"
            )
        }

        // Ordenar por seqNum
        val sortedChunks = chunks.sortedBy { it[0].toInt() and 0xFF }

        // Verificar secuencia
        for (i in sortedChunks.indices) {
            val seqNum = sortedChunks[i][0].toInt() and 0xFF
            if (seqNum != i) {
                throw com.zilch.blemesh.exception.BleMeshException.MessageCorrupted(
                    "Secuencia de chunks inválida: esperado $i, recibido $seqNum"
                )
            }
        }

        // Reensamblar
        val result = sortedChunks.fold(ByteArray(0)) { acc, chunk ->
            val chunkData = chunk.copyOfRange(CHUNK_OVERHEAD, chunk.size)
            acc + chunkData
        }

        // Validar tamaño si se proporcionó
        if (expectedSize != null && result.size != expectedSize) {
            throw com.zilch.blemesh.exception.BleMeshException.MessageCorrupted(
                "Tamaño reensamblado ($expectedSize) no coincide ($result.size)"
            )
        }

        return result
    }

    /**
     * Calcula el número de chunks que se generarán para un mensaje.
     *
     * @param dataSize Tamaño del mensaje en bytes
     * @param mtu MTU negociado
     * @return Número de chunks
     */
    fun calculateChunkCount(dataSize: Int, mtu: Int = BleConfig.DESIRED_MTU): Int {
        val maxPayload = mtu - CHUNK_OVERHEAD
        require(maxPayload > 0) { "MTU demasiado pequeño: $mtu (payload efectivo: $maxPayload)" }
        return (dataSize + maxPayload - 1) / maxPayload
    }
}
