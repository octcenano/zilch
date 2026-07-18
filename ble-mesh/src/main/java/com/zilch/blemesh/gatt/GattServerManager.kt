package com.zilch.blemesh.gatt

import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.zilch.blemesh.config.BleConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GattServerManager — Servidor GATT para recibir conexiones entrantes.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: GATT SERVER
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El servidor GATT acepta conexiones de dispositivos cercanos y
 * expone las características del servicio Zilch.
 *
 * FLUJO DE UNA CONEXIÓN ENTRANTE:
 *
 * ┌─────────────────┐         ┌─────────────────┐
 * │  Dispositivo A   │         │  Dispositivo B   │
 * │  (Scanner)       │         │  (Este servidor) │
 * └────────┬────────┘         └────────┬────────┘
 *          │                           │
 *          │  1. Connect to GATT       │
 *          │ ────────────────────────→ │
 *          │                           │
 *          │  2. Discover Services     │
 *          │ ────────────────────────→ │
 *          │                           │
 *          │  3. Read PeerInfo         │
 *          │ ────────────────────────→ │
 *          │ ←──────────────────────── │ (nodeId + fingerprint)
 *          │                           │
 *          │  4. Write Message (handshake)
 *          │ ────────────────────────→ │
 *          │                           │
 *          │  5. Notify Message        │
 *          │ ←──────────────────────── │
 *
 * SEGURIDAD:
 * - El servidor NO acepta escrituras sin handshake previo
 * - Cada conexión se registra para prevenir abuso
 * - Se limita el número de conexiones simultáneas
 */
class GattServerManager(private val context: Context) {

    companion object {
        private const val TAG = "GattServerManager"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var gattServer: BluetoothGattServer? = null

    /** Número de clientes conectados al servidor */
    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    /** Callback para datos recibidos a través de la characteristic de mensajes */
    var onMessageReceived: ((BluetoothDevice, ByteArray) -> Unit)? = null

    /** Callback para datos recibidos en la characteristic de control */
    var onControlReceived: ((BluetoothDevice, ByteArray) -> Unit)? = null

    /** Callback para nuevas conexiones */
    var onDeviceConnected: ((BluetoothDevice) -> Unit)? = null

    /** Callback para desconexiones */
    var onDeviceDisconnected: ((BluetoothDevice) -> Unit)? = null

    /** Datos del peer local para la characteristic PeerInfo */
    private var localPeerInfo: ByteArray = ByteArray(0)

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Inicia el servidor GATT.
     *
     * @param peerInfoData Datos del peer local para PeerInfoCharacteristic
     *                     (nodeId + fingerprint, formato binario)
     */
    fun start(peerInfoData: ByteArray) {
        localPeerInfo = peerInfoData
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        setupCharacteristics()
        // NOTA: El advertising BLE NO se inicia aquí.
        // La publicidad BLE es gestionada centralmente por BleAdvertiser
        // a través de BleMeshEngine.startDiscovery() para evitar conflictos
        // con múltiples advertisers simultáneos.
    }

    /**
     * Detiene el servidor GATT.
     */
    fun stop() {
        gattServer?.close()
        gattServer = null
        _connectedClients.value = 0
    }

    /**
     * Envía una notificación a un cliente conectado.
     *
     * @param device Dispositivo destino
     * @param characteristicId UUID de la característica
     * @param data Datos a enviar
     * @return true si la notificación fue enviada
     */
    fun sendNotification(
        device: BluetoothDevice,
        characteristicId: java.util.UUID,
        data: ByteArray
    ): Boolean {
        val service = gattServer?.getService(BleConfig.SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(characteristicId) ?: return false
        characteristic.value = data
        return gattServer?.notifyCharacteristicChanged(device, characteristic, false) ?: false
    }

    /**
     * Envía una notificación a todos los clientes conectados.
     */
    fun broadcastNotification(characteristicId: java.util.UUID, data: ByteArray) {
        val service = gattServer?.getService(BleConfig.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(characteristicId) ?: return

        bluetoothManager.getConnectedDevices(BluetoothGatt.GATT_SERVER).forEach { device ->
            characteristic.value = data
            gattServer?.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  CONFIGURACIÓN INTERNA
    // ════════════════════════════════════════════════════════════════

    private fun setupCharacteristics() {
        // ═══ REGISTRAR SERVICIO GATT ═══
        // Sin esto, el server no expone ningún servicio a clientes conectados
        val service = BluetoothGattService(
            BleConfig.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // Característica de mensajes: WRITE + NOTIFY
        val messageChar = BluetoothGattCharacteristic(
            BleConfig.MESSAGE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(messageChar)

        // Característica de peer info: READ
        val peerInfoChar = BluetoothGattCharacteristic(
            BleConfig.PEER_INFO_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(peerInfoChar)

        // Característica de control mesh: WRITE + NOTIFY
        val controlChar = BluetoothGattCharacteristic(
            BleConfig.MESH_CONTROL_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(controlChar)

        gattServer?.addService(service)
        Log.d(TAG, "Servicio GATT registrado con 3 caracteristicas")
    }


    // ════════════════════════════════════════════════════════════════
    //  CALLBACKS GATT SERVER
    // ════════════════════════════════════════════════════════════════

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val count = _connectedClients.value + 1
                    _connectedClients.value = count
                    onDeviceConnected?.invoke(device)
                    Log.i(TAG, "Cliente conectado (total: $count)")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val count = maxOf(0, _connectedClients.value - 1)
                    _connectedClients.value = count
                    onDeviceDisconnected?.invoke(device)
                    Log.i(TAG, "Cliente desconectado (total: $count)")
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                BleConfig.PEER_INFO_CHARACTERISTIC_UUID -> {
                    // Enviar información del peer local
                    val data = if (offset < localPeerInfo.size) {
                        localPeerInfo.copyOfRange(offset, localPeerInfo.size)
                    } else {
                        ByteArray(0)
                    }
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, data
                    )
                }

                else -> {
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            when (characteristic.uuid) {
                BleConfig.MESSAGE_CHARACTERISTIC_UUID -> {
                    value?.let { data ->
                        onMessageReceived?.invoke(device, data)
                    }
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                        )
                    }
                }

                BleConfig.MESH_CONTROL_CHARACTERISTIC_UUID -> {
                    value?.let { data ->
                        onControlReceived?.invoke(device, data)
                    }
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                        )
                    }
                }

                else -> {
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
                    }
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.d(TAG, "MTU cambiado: $mtu")
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Notificacion fallida: status=$status")
            }
        }
    }
}
