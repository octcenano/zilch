package com.zilch.anonsurf.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zilch.anonsurf.AnonsurfEngine
import com.zilch.anonsurf.exception.AnonsurfException
import com.zilch.anonsurf.verification.TorIpVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AnonsurfViewModel — Ejemplo de integración del motor Anonsurf.
 *
 * Este ViewModel demuestra cómo consumir el motor desde la capa
 * de presentación de la app. NO es parte del módulo base;
 * es un ejemplo de referencia para los desarrolladores.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  FLUJO DE ESTADOS DE LA UI
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   [INICIALIZANDO] → [TOR_ACTIVO] ←→ [SIN_TOR]
 *                          ↓
 *                    [KILL_SWITCH]
 *                          ↓
 *                     [DESTRUIDO]
 *
 *  - INICIALIZANDO: El motor está verificando la conexión Tor
 *  - TOR_ACTIVO: Tor funciona correctamente, se puede usar la app
 *  - SIN_TOR: Tor no está disponible, solo modo offline (BLE/WiFi Direct)
 *  - KILL_SWITCH: Kill Switch activado, toda red bloqueada
 *  - DESTRUIDO: Motor destruido, app cerrándose
 */
class AnonsurfViewModel(application: Application) : AndroidViewModel(application) {

    // ── Estados observables ─────────────────────────────────────────

    enum class EngineState {
        INITIALIZING,
        TOR_ACTIVE,
        TOR_NOT_AVAILABLE,
        KILL_SWITCH_ACTIVE,
        DESTROYED
    }

    private val _engineState = MutableStateFlow(EngineState.INITIALIZING)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _torStatus = MutableStateFlow<TorIpVerifier.TorStatus>(TorIpVerifier.TorStatus.UNREACHABLE)
    val torStatus: StateFlow<TorIpVerifier.TorStatus> = _torStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Verificando conexión Tor...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isKillSwitchActive = MutableStateFlow(false)
    val isKillSwitchActive: StateFlow<Boolean> = _isKillSwitchActive.asStateFlow()

    // ── Instancia del motor ─────────────────────────────────────────

    private val engine = AnonsurfEngine.getInstance(application)

    init {
        setupListeners()
        startEngine()
    }

    private fun setupListeners() {
        // Callback de estado de Tor
        engine.setOnTorStatusChanged { verified ->
            _engineState.value = if (verified) {
                EngineState.TOR_ACTIVE
            } else {
                EngineState.TOR_NOT_AVAILABLE
            }
        }

        // Callback del Kill Switch
        engine.setOnKillSwitchStateChanged { active, reason ->
            _isKillSwitchActive.value = active
            if (active) {
                _engineState.value = EngineState.KILL_SWITCH_ACTIVE
                _statusMessage.value = "Kill Switch activo"
            }
        }
    }

    private fun startEngine() {
        _engineState.value = EngineState.INITIALIZING
        _statusMessage.value = "Verificando conexión Tor..."

        // engine.start() ya lanza la verificación internamente
        // y notifica vía callbacks. No es necesario verificar de nuevo.
        engine.start(viewModelScope)
    }

    // ── Acciones de la UI ───────────────────────────────────────────

    /**
     * Verificación manual de Tor.
     * Llamado desde un botón "Verificar conexión" en la UI.
     */
    fun verifyTorConnection() {
        viewModelScope.launch {
            _statusMessage.value = "Verificando conexion Tor..."
            try {
                val status = engine.verifyTorConnection()
                _torStatus.value = status
                _statusMessage.value = when (status) {
                    TorIpVerifier.TorStatus.ACTIVE -> "Tor operativo"
                    TorIpVerifier.TorStatus.LEAK_DETECTED -> "LEAK DETECTADA"
                    TorIpVerifier.TorStatus.UNREACHABLE -> "Tor inalcanzable"
                }
            } catch (e: AnonsurfException.IpLeakDetected) {
                _statusMessage.value = "FUGA DE IP DETECTADA"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    /**
     * Activación de emergencia — Botón de Pánico.
     *
     * ⚠ Llamar SOLO cuando el usuario confirma explícitamente.
     * Esta acción es IRREVERSIBLE dentro de la sesión.
     */
    fun emergencyDestroy() {
        engine.emergencyStop()
        _engineState.value = EngineState.DESTROYED
        _isKillSwitchActive.value = true
        _statusMessage.value = "Motor destruido. Cerrando..."
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}
