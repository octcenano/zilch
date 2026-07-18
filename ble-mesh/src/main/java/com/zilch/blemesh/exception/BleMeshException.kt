package com.zilch.blemesh.exception

/**
 * BleMeshException — Excepciones del módulo BLE Mesh.
 */
sealed class BleMeshException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** El adaptador BLE no está disponible o está desactivado */
    class BluetoothUnavailable(
        message: String = "Bluetooth no disponible o desactivado",
        cause: Throwable? = null
    ) : BleMeshException(message, cause)

    /** Permisos BLE no concedidos */
    class PermissionsMissing(
        val missingPermissions: List<String>,
        message: String = "Permisos BLE faltantes"
    ) : BleMeshException(message)

    /** No se pudo iniciar el advertising */
    class AdvertisingFailed(
        message: String = "No se pudo iniciar el advertising",
        cause: Throwable? = null
    ) : BleMeshException(message, cause)

    /** No se pudo iniciar el escaneo */
    class ScanFailed(
        message: String = "No se pudo iniciar el escaneo",
        cause: Throwable? = null
    ) : BleMeshException(message, cause)

    /** Error en la conexión GATT */
    class GattConnectionError(
        message: String = "Error de conexión GATT",
        cause: Throwable? = null
    ) : BleMeshException(message, cause)

    /** El MTU negociado es menor que el mínimo requerido */
    class InsufficientMtu(
        val negotiatedMtu: Int,
        val requiredMtu: Int,
        message: String = "MTU insuficiente: $negotiatedMtu < $requiredMtu"
    ) : BleMeshException(message)

    /** Error al cifrar un mensaje */
    class EncryptionError(
        message: String = "Error al cifrar mensaje",
        cause: Throwable? = null
    ) : BleMeshException(message, cause)

    /** Error al descifrar un mensaje */
    class DecryptionError(
        message: String = "Error al descifrar mensaje — posible manipulación",
        cause: Throwable? = null
    ) : BleMeshException(message, cause)

    /** Mensaje corrupto o con formato inválido */
    class MessageCorrupted(
        message: String = "Mensaje corrupto o con formato inválido"
    ) : BleMeshException(message)

    /** Número máximo de conexiones alcanzado */
    class MaxConnectionsReached(
        message: String = "Número máximo de conexiones BLE alcanzado"
    ) : BleMeshException(message)

    /** El peer remoto no es un contacto conocido */
    class UnknownPeer(
        val peerNodeId: String,
        message: String = "Peer desconocido"
    ) : BleMeshException(message)

    /** Sesión de chat no encontrada */
    class SessionNotFound(
        val sessionId: String,
        message: String = "Sesión de chat no encontrada"
    ) : BleMeshException(message)

    /** Error de timeout en operación BLE */
    class OperationTimeout(
        message: String = "Timeout en operación BLE",
        cause: Throwable? = null
    ) : BleMeshException(message, cause)
}
