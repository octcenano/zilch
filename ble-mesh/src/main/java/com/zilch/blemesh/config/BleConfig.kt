package com.zilch.blemesh.config

/**
 * BleConfig — Constantes del módulo BLE Mesh.
 *
 * DECISIÓN DE SEGURIDAD:
 * - Los UUIDs del servicio son específicos de Zilch para evitar
 *   que dispositivos no-Zilch intenten conectarse.
 * - El MTU se negocia pero se limita a un máximo razonable.
 * - Los timeouts son cortos para evitar conexiones zombi.
 */
object BleConfig {

    // ════════════════════════════════════════════════════════════════
    //  UUIDs DEL SERVICIO BLE
    // ════════════════════════════════════════════════════════════════

    /**
     * UUID del servicio BLE de Zilch.
     *
     * Solo dispositivos que anuncian este UUID serán descubiertos.
     * El UUID está derivado de "zilch" para ser único y memorable.
     */
    val SERVICE_UUID: java.util.UUID =
        java.util.UUID.fromString("7a696c63-0001-4008-8000-000000000001")

    /**
     * Característica para envío/recepción de mensajes cifrados.
     * Propiedades: WRITE + NOTIFY
     */
    val MESSAGE_CHARACTERISTIC_UUID: java.util.UUID =
        java.util.UUID.fromString("7a696c63-0001-4008-8000-000000000002")

    /**
     * Característica para intercambio de información del peer.
     * Propiedades: READ
     * Contiene: nodeId (SHA-256 de la clave pública) + fingerprint
     */
    val PEER_INFO_CHARACTERISTIC_UUID: java.util.UUID =
        java.util.UUID.fromString("7a696c63-0001-4008-8000-000000000003")

    /**
     * Característica de control del mesh.
     * Propiedades: WRITE + NOTIFY
     * Usada para: handshakes, ACKs, routing control.
     */
    val MESH_CONTROL_CHARACTERISTIC_UUID: java.util.UUID =
        java.util.UUID.fromString("7a696c63-0001-4008-8000-000000000004")

    // ════════════════════════════════════════════════════════════════
    //  PARÁMETROS BLE
    // ════════════════════════════════════════════════════════════════

    /**
     * MTU mínimo aceptable en bytes.
     * Si el dispositivo remoto no puede soportar este MTU,
     * la conexión se rechaza.
     */
    const val MIN_MTU = 512

    /**
     * MTU deseado (máximo a negociar).
     * BLE 4.2+ soporta hasta 517 bytes.
     */
    const val DESIRED_MTU = 512

    /**
     * Tamaño efectivo del payload por chunk.
     * 3 bytes de overhead del protocolo (ver MessageChunker).
     */
    const val CHUNK_PAYLOAD_SIZE = DESIRED_MTU - 3

    /**
     * Timeout de conexión BLE en milisegundos.
     */
    const val CONNECTION_TIMEOUT_MS = 10_000L

    /**
     * Timeout de operación GATT en milisegundos.
     */
    const val GATT_OPERATION_TIMEOUT_MS = 5_000L

    /**
     * Intervalo de advertising en milisegundos.
     * 1000ms = 1 segundo. Equilibrio entre discoverability
     * y consumo de batería.
     */
    const val ADVERTISE_INTERVAL_MS = 1000L

    /**
     * Duración máxima del advertising en milisegundos.
     * 0 = advertising continuo hasta que se cancele.
     */
    const val ADVERTISE_DURATION_MS = 0L

    // ════════════════════════════════════════════════════════════════
    //  PARÁMETROS DEL MESH
    // ════════════════════════════════════════════════════════════════

    /**
     * TTL máximo de un mensaje en el mesh.
     * Cada hop decrementa el TTL. Cuando llega a 0, el mensaje
     * se descarta. Previene loops infinitos.
     */
    const val MAX_MESH_TTL = 3

    /**
     * Tamaño del buffer de mensajes recientes.
     * Usado para deduplicar mensajes ya procesados.
     */
    const val RECENT_MESSAGES_CACHE_SIZE = 256

    /**
     * TTL de un mensaje en el cache de deduplicación (ms).
     * Después de este tiempo, el mensaje se purga del cache.
     */
    const val MESSAGE_CACHE_TTL_MS = 300_000L // 5 minutos

    /**
     * Número máximo de conexiones BLE simultáneas.
     * BLE tiene limitaciones de hardware; la mayoría de dispositivos
     * soportan 3-7 conexiones simultáneas.
     */
    const val MAX_CONCURRENT_CONNECTIONS = 3

    /**
     * Intervalo de limpieza de peers desconectados (ms).
     */
    const val PEER_CLEANUP_INTERVAL_MS = 30_000L

    /**
     * Tiempo máximo sin actividad antes de considerar un peer muerto (ms).
     */
    const val PEER_TIMEOUT_MS = 60_000L

    // ════════════════════════════════════════════════════════════════
    //  PARÁMETROS DE CIFRADO BLE
    // ════════════════════════════════════════════════════════════════

    /**
     * Algoritmo de cifrado para mensajes BLE.
     */
    const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Tamaño de la nonce GCM en bytes.
     */
    const val GCM_NONCE_SIZE = 12

    /**
     * Tamaño de la etiqueta GCM en bytes.
     */
    const val GCM_TAG_SIZE = 16

    /**
     * Algoritmo de derivación de claves.
     */
    const val KDF_ALGORITHM = "HmacSHA256"

    /**
     * Iteraciones del KDF.
     */
    const val KDF_ITERATIONS = 10_000
}
