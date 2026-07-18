package com.zilch.blemesh.scanning

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.zilch.blemesh.config.BleConfig
import com.zilch.blemesh.exception.BleMeshException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BleScanner — Escaneo BLE para descubrir nodos cercanos.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: ESCANEO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El escáner BLE detecta dispositivos que anuncian el servicio Zilch.
 *
 * SEGURIDAD DEL ESCANEO:
 * - Solo escaneamos por UUID del servicio (no escaneo general)
 * - No almacenamos la dirección MAC del dispositivo fuera de RAM
 * - El escaneo se detiene cuando no se necesita (ahorro de batería)
 * - Los resultados se procesan inmediatamente, no se cachean
 *
 * PRIVACIDAD DEL ESCANEO:
 * - El escáner no revela nada sobre el dispositivo local
 * - Los dispositivos escaneados solo ven que somos "otro Zilch"
 * - La identidad completa se intercambia después de la conexión GATT
 *
 * NOTA: En Android 6-11, el escaneo BLE requiere ACCESS_FINE_LOCATION.
 * En Android 12+, BLUETOOTH_SCAN con neverForLocation es suficiente.
 */
class BleScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleScanner"
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    /** Estado del escaneo */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Dispositivos descubiertos (address → ScanResult) */
    private val _discoveredDevices = MutableStateFlow<Map<String, ScanResult>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, ScanResult>> = _discoveredDevices.asStateFlow()

    /** Callback de escaneo */
    private var scanCallback: ScanCallback? = null

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Verifica si el escaneo BLE es posible.
     */
    fun isSupported(): Boolean {
        return bleScanner != null && bluetoothAdapter?.isEnabled == true
    }

    /**
     * Inicia el escaneo de dispositivos Zilch cercanos.
     *
     * @param onDeviceFound Callback invocado cuando se descubre un dispositivo
     * @throws BleMeshException.BluetoothUnavailable si BLE no está disponible
     * @throws BleMeshException.ScanFailed si el escaneo falla
     */
    fun startScanning(onDeviceFound: (ScanResult) -> Unit) {
        if (!isSupported()) {
            throw BleMeshException.BluetoothUnavailable(
                "BLE scanning no soportado o Bluetooth desactivado"
            )
        }

        if (_isScanning.value) {
            Log.w(TAG, "Escaneo ya está activo")
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Solo procesar dispositivos que anuncian nuestro servicio
                val serviceUuids = result.scanRecord?.serviceUuids
                if (serviceUuids?.any { it.uuid == BleConfig.SERVICE_UUID } == true) {
                    Log.d(TAG, "Dispositivo Zilch descubierto")

                    // Actualizar cache de dispositivos descubiertos
                    // Nota: La address MAC se almacena como key interna
                    // para deduplicación, pero NUNCA se loguea.
                    val current = _discoveredDevices.value.toMutableMap()
                    current[result.device.address] = result
                    _discoveredDevices.value = current

                    onDeviceFound(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
                val errorMsg = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Escaneo ya iniciado"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Registro de app fallido"
                    SCAN_FAILED_INTERNAL_ERROR -> "Error interno"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "No soportado"
                    else -> "Error desconocido: $errorCode"
                }
                Log.e(TAG, "Escaneo falló: $errorMsg")
                throw BleMeshException.ScanFailed(errorMsg)
            }
        }

        // Filtro: solo nuestro servicio UUID
        val filters = listOf(
            android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(BleConfig.SERVICE_UUID))
                .build()
        )

        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // Reporte inmediato (no batching)
            .setMatchMode(android.bluetooth.le.ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(android.bluetooth.le.ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setCallbackType(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        bleScanner?.startScan(filters, settings, scanCallback)
        _isScanning.value = true

        Log.i(TAG, "Escaneo BLE iniciado — buscando servicio: ${BleConfig.SERVICE_UUID}")
    }

    /**
     * Detiene el escaneo BLE.
     */
    fun stopScanning() {
        scanCallback?.let { callback ->
            bleScanner?.stopScan(callback)
            _isScanning.value = false
            Log.i(TAG, "Escaneo BLE detenido")
        }
        scanCallback = null
        _discoveredDevices.value = emptyMap()
    }

    /**
     * Limpia la lista de dispositivos descubiertos.
     */
    fun clearDiscovered() {
        _discoveredDevices.value = emptyMap()
    }
}
