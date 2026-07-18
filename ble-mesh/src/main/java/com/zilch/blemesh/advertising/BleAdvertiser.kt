package com.zilch.blemesh.advertising

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import com.zilch.blemesh.config.BleConfig
import com.zilch.blemesh.exception.BleMeshException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BleAdvertiser — Advertising BLE para hacer visible el nodo local.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: ADVERTISING
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El advertising BLE es la forma en que otros dispositivos te descubren.
 * Decidimos qué información incluir en el paquete de advertising:
 *
 * ✅ INCLUIMOS:
 * - UUID del servicio Zilch (para que solo dispositivos Zilch te descubran)
 *
 * ❌ NO INCLUIMOS:
 * - Nombre del dispositivo (evita tracking por nombre)
 * - NodeId (evita que un escáner pasivo identifique al nodo)
 * - Cualquier dato que permita correlacionar sesiones
 *
 * ¿Por qué no incluir el NodeId?
 * Porque el advertising es público (cualquier BLE scanner puede leerlo).
 * Si incluyéramos el NodeId, un adversario con un scanner BLE podría:
 * 1. Mapear la ubicación física del nodo (trackear movement)
 * 2. Correlacionar presencias en diferentes lugares
 * 3. Crear un grafo de contactos entre nodos
 *
 * El intercambio de identidad solo ocurre DESPUÉS de la conexión GATT,
 * que requiere que ambas partes estén dispuestas a conectarse.
 */
class BleAdvertiser(private val context: Context) {

    companion object {
        private const val TAG = "BleAdvertiser"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleAdvertiser: android.bluetooth.le.BluetoothLeAdvertiser? =
        bluetoothAdapter?.bluetoothLeAdvertiser

    /** Estado del advertising */
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    /** Callback de advertising */
    private var advertiseCallback: AdvertiseCallback? = null

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Verifica si el advertising BLE es posible.
     */
    fun isSupported(): Boolean {
        return bleAdvertiser != null && bluetoothAdapter?.isEnabled == true
    }

    /**
     * Inicia el advertising BLE.
     *
     * El dispositivo será visible para otros dispositivos Zilch
     * que estén escaneando.
     *
     * @throws BleMeshException.BluetoothUnavailable si BLE no está disponible
     * @throws BleMeshException.AdvertisingFailed si el advertising falla
     */
    fun startAdvertising() {
        if (!isSupported()) {
            throw BleMeshException.BluetoothUnavailable(
                "BLE advertising no soportado o Bluetooth desactivado"
            )
        }

        if (_isAdvertising.value) {
            Log.w(TAG, "Advertising ya está activo")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true) // Permitir conexiones GATT
            .setTimeout(0) // ADVERTISE_NO_TIMEOUT = 0 (removido en API 31+)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)  // SEGURIDAD: No incluir nombre
            .setIncludeTxPowerLevel(false) // SEGURIDAD: No incluir potencia
            .addServiceUuid(android.os.ParcelUuid(BleConfig.SERVICE_UUID))
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _isAdvertising.value = true
                Log.i(TAG, "Advertising BLE iniciado correctamente")
            }

            override fun onStartFailure(errorCode: Int) {
                _isAdvertising.value = false
                val errorMsg = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Datos demasiado grandes"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Demasiados advertisers"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Ya está iniciado"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Error interno del adaptador"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "No soportado por el hardware"
                    else -> "Error desconocido: $errorCode"
                }
                Log.e(TAG, "Advertising falló: $errorMsg")
                throw BleMeshException.AdvertisingFailed(errorMsg)
            }
        }

        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * Detiene el advertising BLE.
     */
    fun stopAdvertising() {
        advertiseCallback?.let { callback ->
            bleAdvertiser?.stopAdvertising(callback)
            _isAdvertising.value = false
            Log.i(TAG, "Advertising BLE detenido")
        }
        advertiseCallback = null
    }
}
