package com.zilch.blemesh.gatt

import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.zilch.blemesh.config.BleConfig
import com.zilch.blemesh.exception.BleMeshException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * GattClientManager — Cliente GATT para conectarse a peers cercanos.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: GATT CLIENT
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El cliente GATT gestiona las conexiones salientes a dispositivos
 * cercanos que anuncian el servicio Zilch.
 *
 * FLUJO DE UNA CONEXIÓN SALIENTE:
 *
 * 1. connectToDevice(scanResult) → Inicia conexión GATT
 * 2. Esperar onConnectionStateChange(CONNECTED)
 * 3. discoverServices() → Descubrir servicio Zilch
 * 4. Leer PEER_INFO_CHARACTERISTIC → Obtener nodeId del peer
 * 5. Verificar que el peer es un contacto conocido
 * 6. Negotiate MTU → Asegurar MTU suficiente
 * 7. Escribir en MESSAGE_CHARACTERISTIC → Enviar handshake
 * 8. Registrar notificaciones de MESSAGE_CHARACTERISTIC
 * 9. Peer listo para intercambio de mensajes
 *
 * SEGURIDAD:
 * - Se limita el número de conexiones simultáneas
 * - Se verifica la identidad del peer antes de intercambiar mensajes
 * - Se timeout las conexiones que no completan el handshake
 * - Los errores de conexión se manejan sin exponer información
 */
class GattClientManager(private val context: Context) {

    companion object {
        private const val TAG = "GattClientManager"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    /** Conexiones activas: address → BluetoothGatt */
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()

    /** Número de conexiones activas */
    private val _activeConnectionCount = MutableStateFlow(0)
    val activeConnectionCount: StateFlow<Int> = _activeConnectionCount.asStateFlow()

    /** Callback para datos recibidos via notificación */
    var onMessageReceived: ((String, ByteArray) -> Unit)? = null

    /** Callback para cambio de estado de conexión */
    var onConnectionStateChanged: ((address: String, state: Int) -> Unit)? = null

    /** Callback para servicios descubiertos */
    var onServicesDiscovered: ((address: String, services: List<BluetoothGattService>) -> Unit)? = null

    /** Pendientes de conexión: address → CompletableDeferred */
    private val pendingConnectionDeferreds = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** Datos del peer local para enviar en handshake */
    private var localPeerInfo: ByteArray = ByteArray(0)

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Configura los datos del peer local para el handshake.
     */
    fun setLocalPeerInfo(peerInfo: ByteArray) {
        localPeerInfo = peerInfo
    }

    /**
     * Conecta a un dispositivo descubierto por escaneo.
     *
     * @param scanResult Resultado del escaneo BLE
     * @return Deferred que se completa cuando la conexión está lista
     */
    fun connectToDevice(scanResult: ScanResult): Deferred<Boolean> {
        return CoroutineScope(Dispatchers.IO + SupervisorJob()).async {
            val device = scanResult.device
            val address = device.address

            // Verificar límite de conexiones
            if (activeConnections.size >= BleConfig.MAX_CONCURRENT_CONNECTIONS) {
                Log.w(TAG, "Máximo de conexiones alcanzado")
                throw BleMeshException.MaxConnectionsReached()
            }

            // Verificar si ya estamos conectados
            if (activeConnections.containsKey(address)) {
                Log.d(TAG, "Ya conectado al peer")
                return@async true
            }

            Log.i(TAG, "Iniciando conexion BLE...")

            // CompletableDeferred para esperar la conexión desde el callback GATT
            val connectionDeferred = CompletableDeferred<Boolean>()

            // Registrar temporalmente el callback para esta conexión
            pendingConnectionDeferreds[address] = connectionDeferred

            val gatt = device.connectGatt(context, false, gattClientCallback)

            // Esperar conexión con timeout
            val connected = withTimeoutOrNull(BleConfig.CONNECTION_TIMEOUT_MS) {
                connectionDeferred.await()
            }

            if (connected == null) {
                pendingConnectionDeferreds.remove(address)
                gatt.disconnect()
                gatt.close()
                throw BleMeshException.OperationTimeout("Timeout de conexión BLE")
            }

            activeConnections[address] = gatt
            _activeConnectionCount.value = activeConnections.size

            true
        }
    }

    /**
     * Descubre servicios del dispositivo remoto.
     */
    fun discoverServices(address: String): Boolean {
        val gatt = activeConnections[address] ?: return false
        return gatt.discoverServices()
    }

    /**
     * Solicita cambio de MTU.
     */
    fun requestMtu(address: String, mtu: Int = BleConfig.DESIRED_MTU): Boolean {
        val gatt = activeConnections[address] ?: return false
        return gatt.requestMtu(mtu)
    }

    /**
     * Escribe datos en una característica del dispositivo remoto.
     *
     * @param address Dirección del dispositivo
     * @param characteristicUuid UUID de la característica
     * @param data Datos a escribir
     */
    fun writeCharacteristic(
        address: String,
        characteristicUuid: java.util.UUID,
        data: ByteArray
    ): Boolean {
        val gatt = activeConnections[address] ?: return false
        val service = gatt.getService(BleConfig.SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return false

        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return gatt.writeCharacteristic(characteristic)
    }

    /**
     * Habilita notificaciones de una característica.
     */
    fun enableNotifications(address: String, characteristicUuid: java.util.UUID): Boolean {
        val gatt = activeConnections[address] ?: return false
        val service = gatt.getService(BleConfig.SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return false

        gatt.setCharacteristicNotification(characteristic, true)

        // Habilitar notificación en el descriptor
        val descriptor = characteristic.getDescriptor(
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        ) ?: return false

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt.writeDescriptor(descriptor)
    }

    /**
     * Desconecta de un dispositivo.
     */
    fun disconnect(address: String) {
        activeConnections.remove(address)?.let { gatt ->
            gatt.disconnect()
            gatt.close()
            _activeConnectionCount.value = activeConnections.size
            Log.i(TAG, "Desconectado del peer")
        }
    }

    /**
     * Desconecta de todos los dispositivos.
     */
    fun disconnectAll() {
        activeConnections.keys.toList().forEach { disconnect(it) }
    }

    // ════════════════════════════════════════════════════════════════
    //  CALLBACKS GATT CLIENT
    // ════════════════════════════════════════════════════════════════

    private val gattClientCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceHash = gatt.device.address.hashCode().toString(16)

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Conectado al peer [hash:$deviceHash]")
                    pendingConnectionDeferreds.remove(gatt.device.address)?.complete(true)
                    onConnectionStateChanged?.invoke(gatt.device.address, newState)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Peer desconectado [hash:$deviceHash]")
                    pendingConnectionDeferreds.remove(gatt.device.address)?.complete(false)
                    activeConnections.remove(gatt.device.address)
                    _activeConnectionCount.value = activeConnections.size
                    gatt.close()
                    onConnectionStateChanged?.invoke(gatt.device.address, newState)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                Log.d(TAG, "Servicios descubiertos: ${services.size}")
                onServicesDiscovered?.invoke(gatt.device.address, services)
            } else {
                Log.e(TAG, "Descubrimiento de servicios fallado: status=$status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU negociado: $mtu")
                if (mtu < BleConfig.MIN_MTU) {
                    Log.w(TAG, "MTU insuficiente: $mtu < ${BleConfig.MIN_MTU}")
                }
            } else {
                Log.w(TAG, "Cambio de MTU fallado: status=$status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BleConfig.MESSAGE_CHARACTERISTIC_UUID) {
                val value = characteristic.value
                if (value != null) {
                    onMessageReceived?.invoke(gatt.device.address, value)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Escritura fallida en ${characteristic.uuid}: $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor escrito: ${descriptor.uuid}")
            }
        }
    }
}
