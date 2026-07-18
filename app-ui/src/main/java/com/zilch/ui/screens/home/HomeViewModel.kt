package com.zilch.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zilch.blemesh.BleMeshEngine
import com.zilch.crypto.CryptoEngine
import com.zilch.ui.components.TorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HomeViewModel — Estado y lógica de la pantalla de inicio.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val cryptoEngine = CryptoEngine.getInstance(application)
    private val bleEngine = BleMeshEngine.getInstance(application)

    // ═══ Estado observable ═══

    private val _torStatus = MutableStateFlow(TorStatus.CHECKING)
    val torStatus: StateFlow<TorStatus> = _torStatus.asStateFlow()

    private val _fingerprint = MutableStateFlow("")
    val fingerprint: StateFlow<String> = _fingerprint.asStateFlow()

    private val _nodeId = MutableStateFlow("")
    val nodeId: StateFlow<String> = _nodeId.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _isKillSwitchActive = MutableStateFlow(false)
    val isKillSwitchActive: StateFlow<Boolean> = _isKillSwitchActive.asStateFlow()

    private val _statusMessage = MutableStateFlow("Inicializando...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        // Cargar identidad después de un frame para permitir
        // que CryptoEngine se inicialice completamente
        loadIdentity()
    }

    private fun loadIdentity() {
        try {
            refreshIdentity()
        } catch (e: Exception) {
            _fingerprint.value = "error"
            _nodeId.value = "error"
        }
    }

    /**
     * Actualiza el estado de Tor.
     */
    fun updateTorStatus(status: TorStatus) {
        _torStatus.value = status
    }

    /**
     * Actualiza el fingerprint desde CryptoEngine.
     */
    fun refreshIdentity() {
        try {
            _fingerprint.value = cryptoEngine.getCurrentFingerprint()
            _nodeId.value = cryptoEngine.getCurrentNodeId()
        } catch (e: Exception) {
            _fingerprint.value = "error"
            _nodeId.value = "error"
        }
    }

    /**
     * Parada de emergencia — destruye TODO.
     *
     * ⚠ IRREVERSIBLE dentro de la sesión actual.
     */
    fun emergencyDestroy() {
        cryptoEngine.emergencyDestroy()
        bleEngine.emergencyDestroy()
        _isKillSwitchActive.value = true
        _torStatus.value = TorStatus.KILL_SWITCH
        _statusMessage.value = "TODOS LOS DATOS DESTRUIDOS"
    }
}
