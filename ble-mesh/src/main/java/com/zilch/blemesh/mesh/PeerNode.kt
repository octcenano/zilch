package com.zilch.blemesh.mesh

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import java.util.concurrent.atomic.AtomicReference

/**
 * PeerNode — Representación de un nodo cercano en la red mesh BLE.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  SEGURIDAD: PeerNode solo almacena información pública.
 *  La clave pública del peer se almacena como ByteArray y se
 *  limpia al descartar el nodo. El BluetoothDevice se usa
 *  solo para la capa BLE, no para identidad.
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class PeerNode(
    /** NodeId del peer (SHA-256 de su clave pública) */
    val nodeId: String,

    /** Fingerprint para verificación verbal */
    val fingerprint: String,

    /** Clave pública Ed25519 del peer (32 bytes) */
    val publicKeyBytes: ByteArray,

    /** Dirección BLE del dispositivo (MAC address) */
    val bleAddress: String,

    /** Referencia al dispositivo BluetoothAndroid */
    @Transient
    var bluetoothDevice: BluetoothDevice? = null,

    /** Referencia a la conexión GATT activa (si existe) */
    @Transient
    val gattConnection: AtomicReference<BluetoothGatt?> = AtomicReference(null),

    /** Timestamp de la última actividad */
    @Volatile
    var lastSeenMs: Long = System.currentTimeMillis(),

    /** RSSI (señal) medida en el último escaneo */
    var rssi: Int = 0,

    /** MTU negociado con este peer */
    var negotiatedMtu: Int = 23, // Default BLE 4.0 MTU

    /** Estado de la conexión con este peer */
    var connectionState: ConnectionState = ConnectionState.DISCOVERED,

    /** Dirección temporal conocida (.onion o BLE) */
    var temporaryAddress: String? = null
) {

    /** Estados posibles de un peer en el mesh */
    enum class ConnectionState {
        /** Descubierto por escaneo pero no conectado */
        DISCOVERED,

        /** Conexión GATT en progreso */
        CONNECTING,

        /** Conectado y listo para intercambiar mensajes */
        CONNECTED,

        /** Handshake completado, listo para chat */
        PAIRED,

        /** Desconectado recientemente */
        DISCONNECTED
    }

    /** Verifica si el peer es alcanzable actualmente */
    val isReachable: Boolean
        get() = connectionState == ConnectionState.CONNECTED ||
                connectionState == ConnectionState.PAIRED

    /** Tiempo desde la última actividad en milisegundos */
    val timeSinceLastSeenMs: Long
        get() = System.currentTimeMillis() - lastSeenMs

    /** Actualiza el timestamp de última actividad */
    fun markSeen() {
        lastSeenMs = System.currentTimeMillis()
    }

    /**
     * Libera la conexión GATT.
     */
    fun disconnect() {
        gattConnection.getAndSet(null)?.close()
        connectionState = ConnectionState.DISCONNECTED
    }

    /**
     * Limpia datos sensibles de memoria.
     */
    fun wipe() {
        // Sobreescribir bytes sensibles con ceros
        java.util.Arrays.fill(publicKeyBytes, 0.toByte())
        // Desconectar para liberar referencias BLE y GATT
        disconnect()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerNode) return false
        return nodeId == other.nodeId
    }

    override fun hashCode(): Int = nodeId.hashCode()
}
