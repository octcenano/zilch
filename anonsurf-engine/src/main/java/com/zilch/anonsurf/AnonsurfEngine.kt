package com.zilch.anonsurf

import android.content.Context
import android.util.Log
import com.zilch.anonsurf.config.TorConfig
import com.zilch.anonsurf.exception.AnonsurfException
import com.zilch.anonsurf.killswitch.NetworkKillSwitch
import com.zilch.anonsurf.network.TorProxyClient
import com.zilch.anonsurf.tor.TorManager
import com.zilch.anonsurf.verification.TorIpVerifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AnonsurfEngine — Punto de entrada y orquestador del motor de red.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  ARQUITECTURA DEL MÓDULO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Este orquestador integra los tres componentes del módulo de red:
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                         AnonsurfEngine                           │
 * │   (Fachada que expone la API pública para el resto de           │
 * │    la aplicación y coordina los componentes)                    │
 * ├──────────┬──────────┬──────────────┬────────────────────────────┤
 * │TorManager│ TorIpVer │ KillSwitch   │  TorProxyClient            │
 * │ (Starts  │ (Checks  │ (Blocks all  │  (Routes all HTTP/S        │
 * │  local   │  we're   │  network if  │   through SOCKS5/Tor)      │
 * │  Tor or  │  really  │  Tor drops)  │                            │
 * │  Orbot)  │  on Tor) │              │                            │
 * └──────────┴──────────┴──────────────┴────────────────────────────┘
 *
 * USO:
 * ```kotlin
 * // In your ViewModel or main Activity
 * val engine = AnonsurfEngine.getInstance(context)
 *
 * // Start the engine (launches local Tor or falls back to Orbot,
 * // then verifies the connection and activates the Kill Switch)
 * engine.start()
 *
 * // Observe Tor process state (optional, for UI)
 * engine.torManagerState.collect { state ->
 *     when (state) {
 *         TorManager.TorState.READY -> { /* show connected */ }
 *         TorManager.TorState.BOOTSTRAPPING -> { /* show progress */ }
 *         TorManager.TorState.ERROR -> { /* show error */ }
 *         else -> { /* ... */ }
 *     }
 * }
 *
 * // Make a secure request through Tor
 * val response = engine.executeSecureRequest(request)
 *
 * // Check status
 * if (engine.isReady) { ... }
 *
 * // Emergency: destroy everything
 * engine.emergencyStop()
 * ```
 *
 * DECISIÓN DE SEGURIDAD: El engine es un singleton por proceso.
 * Esto garantiza que no existan múltiples instancias del Kill Switch
 * o del cliente HTTP que puedan quedar fuera de sync.
 */
class AnonsurfEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AnonsurfEngine"

        @Volatile
        private var instance: AnonsurfEngine? = null

        /**
         * Obtiene la instancia singleton del motor Anonsurf.
         *
         * @param context Contexto Android (se usa applicationContext
         *                para evitar memory leaks)
         * @return Instancia singleton del motor
         */
        fun getInstance(context: Context): AnonsurfEngine {
            return instance ?: synchronized(this) {
                instance ?: AnonsurfEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * Reset forzado de la instancia singleton.
         * Solo para testing o para destrucción completa del motor.
         */
        @Synchronized
        fun destroyInstance() {
            instance?.emergencyStop()
            instance = null
        }
    }

    // ── Componentes internos ────────────────────────────────────────

    /**
     * TorManager — manages the embedded Tor process or Orbot fallback.
     * This is the component that makes Tor work without requiring Orbot
     * to be pre-installed. It starts a local Tor process if a binary is
     * available, or probes for Orbot as a fallback.
     */
    val torManager = TorManager(context)

    val killSwitch = NetworkKillSwitch()
    private val proxyClient = TorProxyClient(killSwitch)
    private val httpClient = proxyClient.buildClient()
    private val ipVerifier = TorIpVerifier(httpClient, killSwitch)

    // ── Estado ──────────────────────────────────────────────────────

    /** Motor inicializado y listo para usar */
    private val _isReady = AtomicBoolean(false)
    val isReady: Boolean get() = _isReady.get()

    /** Verificación de Tor completada con éxito */
    private val _isTorVerified = AtomicBoolean(false)
    val isTorVerified: Boolean get() = _isTorVerified.get()

    /** Estado actual de Tor */
    private val _torStatus = MutableStateFlow(TorIpVerifier.TorStatus.UNREACHABLE)
    val torStatus: StateFlow<TorIpVerifier.TorStatus> = _torStatus.asStateFlow()

    /** Observable state of the embedded Tor process */
    val torManagerState: StateFlow<TorManager.TorState>
        get() = torManager.state

    /** Observable bootstrap progress (0–100) of the local Tor process */
    val torBootstrapProgress: StateFlow<Int>
        get() = torManager.bootstrapProgress

    /** Which Tor source is currently in use (LOCAL, ORBOT, NONE) */
    val torSource: StateFlow<TorManager.TorSource>
        get() = torManager.source

    /** Scope de coroutines propio del motor */
    @Volatile
    private var engineScope: CoroutineScope? = null

    /** Callback para estado de Tor en la UI */
    private var onTorStatusChanged: ((verified: Boolean) -> Unit)? = null

    // ── API pública ─────────────────────────────────────────────────

    /**
     * Inicia el motor Anonsurf.
     *
     * Este es el punto de entrada que debe llamarse en la inicialización
     * de la app (o al crear la Activity principal). Realiza:
     *
     * Startup sequence:
     *  1. Launch embedded Tor (or detect Orbot) via TorManager
     *  2. Wait for the SOCKS5 proxy to become available
     *  3. Verify traffic is actually routing through Tor
     *  4. Activate proactive proxy monitoring (Kill Switch)
     *
     * @param scope CoroutineScope for async operations
     *               (typically viewModelScope of the Activity)
     */
    fun start(scope: CoroutineScope) {
        if (_isReady.get()) {
            Log.w(TAG, "Motor ya está en ejecución")
            return
        }

        engineScope = CoroutineScope(
            scope.coroutineContext + SupervisorJob() + Dispatchers.IO
        )

        Log.i(TAG, "🚀 Iniciando motor Anonsurf...")

        // Step 1: Start TorManager (local binary or Orbot fallback)
        val usedLocalTor = torManager.start(scope)
        val torSourceLabel = if (usedLocalTor) "local" else "Orbot"
        Log.i(TAG, "Tor source: $torSourceLabel")

        // Step 2: Wait for TorManager to reach READY, then verify
        engineScope?.launch {
            try {
                // Wait for the proxy to become available
                // (TorManager handles the timeout internally)
                waitForProxyReady()

                // Step 3: Verify we're actually on Tor
                val status = ipVerifier.verifyTorConnection()
                if (status == TorIpVerifier.TorStatus.ACTIVE) {
                    _isTorVerified.set(true)
                    _torStatus.value = TorIpVerifier.TorStatus.ACTIVE
                    onTorStatusChanged?.invoke(true)
                }

                // Step 4: Activate proxy monitoring
                startProxyMonitoring()

                // Step 5: Mark as ready
                _isReady.set(true)
                Log.i(TAG, "✅ Motor Anonsurf listo — Tor ($torSourceLabel) verificado, Kill Switch activo")

            } catch (e: AnonsurfException.IpLeakDetected) {
                // CRITICO: IP expuesta fuera de Tor
                Log.e(TAG, "EMERGENCIA: Fuga de IP detectada")
                _torStatus.value = TorIpVerifier.TorStatus.LEAK_DETECTED
                killSwitch.activate("IP leak detectada")
                onTorStatusChanged?.invoke(false)

            } catch (e: AnonsurfException.TorProxyUnavailable) {
                // CRITICO: Tor no disponible
                Log.e(TAG, "Proxy Tor no disponible — Kill Switch activado")
                _torStatus.value = TorIpVerifier.TorStatus.UNREACHABLE
                killSwitch.activate("Proxy Tor inactivo")
                onTorStatusChanged?.invoke(false)

            } catch (e: Exception) {
                Log.e(TAG, "Error fatal al iniciar motor: ${e.message}")
                killSwitch.activate("Error de inicialización")
                onTorStatusChanged?.invoke(false)
            }
        }
    }

    /**
     * Detiene el motor y libera recursos.
     * Also stops the embedded Tor process if we started one.
     */
    fun stop() {
        Log.i(TAG, "Deteniendo motor Anonsurf...")
        engineScope?.cancel()
        engineScope = null
        torManager.stop()
        _isReady.set(false)
        _isTorVerified.set(false)
    }

    /**
     * Activación de emergencia: destruye el motor y activa
     * el Kill Switch de forma irreversible.
     *
     * ⚠ Esto debe llamarse cuando:
     * - El usuario presiona el botón de pánico
     * - Se detecta una amenaza de seguridad
     * - La app necesita cerrar de forma segura
     */
    fun emergencyStop() {
        Log.e(TAG, "🚨 PARADA DE EMERGENCIA")
        torManager.stop()
        stop()
        killSwitch.activate("Parada de emergencia activada")
    }

    /**
     * Obtiene el cliente HTTP configurado para Tor.
     *
     * Útil para hacer peticiones HTTP específicas que
     * no sean las operaciones estándar del motor.
     *
     * @return OkHttpClient configurado con proxy SOCKS5
     */
    fun getSecureHttpClient(): OkHttpClient = httpClient

    /**
     * Obtiene el verificador de IP.
     */
    fun getIpVerifier(): TorIpVerifier = ipVerifier

    /**
     * Estado de Tor como Flow para observación reactiva en la UI.
     */
    val torStatusFlow: StateFlow<TorIpVerifier.TorStatus>
        get() = _torStatus

    /**
     * Verifica manualmente la conexión Tor.
     *
     * Útil para un botón "Verificar conexión" en la UI.
     *
     * @return TorStatus con el resultado (NO incluye la IP)
     */
    suspend fun verifyTorConnection(): TorIpVerifier.TorStatus {
        val status = ipVerifier.verifyTorConnection()

        _torStatus.value = status
        _isTorVerified.set(status == TorIpVerifier.TorStatus.ACTIVE)
        onTorStatusChanged?.invoke(status == TorIpVerifier.TorStatus.ACTIVE)

        return status
    }

    /**
     * Ejecuta una petición HTTP de forma segura a través de Tor.
     *
     * Esta es la función principal que el resto de la app debe usar
     * para cualquier comunicación de red.
     *
     * @param request La petición HTTP a ejecutar
     * @return Response de OkHttp
     * @throws AnonsurfException.KillSwitchActive si el Kill Switch está activo
     * @throws AnonsurfException.TorProxyUnavailable si Tor no está disponible
     */
    suspend fun executeSecureRequest(request: okhttp3.Request): okhttp3.Response {
        // Verificar que el motor está operativo
        killSwitch.assertNetworkAllowed()

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute()

            } catch (e: AnonsurfException.KillSwitchActive) {
                throw e

            } catch (e: java.io.IOException) {
                if (isProxyRelatedError(e)) {
                    throw AnonsurfException.TorProxyUnavailable(cause = e)
                }
                throw AnonsurfException.NetworkError(
                    message = "Error de red: ${e.message}",
                    cause = e
                )
            }
        }
    }

    // ── Configuración de callbacks ──────────────────────────────────

    /**
     * Registra callback para cambios en el estado de Tor.
     * Se ejecuta en el hilo del coroutine que hace la verificación.
     */
    fun setOnTorStatusChanged(listener: (verified: Boolean) -> Unit) {
        onTorStatusChanged = listener
    }

    /**
     * Registra callback para cambios del Kill Switch.
     * Útil para mostrar una alerta roja en la UI.
     */
    fun setOnKillSwitchStateChanged(listener: (active: Boolean, reason: String) -> Unit) {
        killSwitch.setOnStateChangedListener(listener)
    }

    // ── Lógica interna ──────────────────────────────────────────────

    /**
     * Wait for the TorManager to reach READY state, or throw on failure.
     *
     * This polls the TorManager state with a short delay, giving the
     * embedded process (or Orbot detection) time to become ready.
     *
     * @throws AnonsurfException.TorProxyUnavailable if TorManager enters ERROR
     * @throws CancellationException if the engine scope is cancelled
     */
    private suspend fun waitForProxyReady() {
        val deadline = System.currentTimeMillis() + TorConfig.CONNECTION_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            when (torManager.state.value) {
                TorManager.TorState.READY -> {
                    Log.d(TAG, "Tor proxy is ready")
                    return
                }

                TorManager.TorState.ERROR -> {
                    val errMsg = torManager.errorMessage.value ?: "Unknown error"
                    throw AnonsurfException.TorProxyUnavailable(
                        message = "TorManager error: $errMsg"
                    )
                }

                TorManager.TorState.STOPPED -> {
                    throw AnonsurfException.TorProxyUnavailable(
                        message = "TorManager stopped unexpectedly"
                    )
                }
                // STARTING or BOOTSTRAPPING — keep waiting
                else -> { /* continue polling */
                }
            }
            delay(500)
        }

        throw AnonsurfException.TorProxyUnavailable(
            message = "Timed out waiting for Tor proxy to become available"
        )
    }

    /**
     * Inicia el monitoreo proactivo del proxy Tor.
     */
    private fun startProxyMonitoring() {
        engineScope?.let { scope ->
            killSwitch.startMonitoring(scope)
        }
        Log.d(TAG, "Monitoreo de proxy activo — intervalo: ${TorConfig.PROXY_HEALTH_CHECK_INTERVAL_MS}ms")
    }

    /**
     * Analiza si un error de IO es atribuible al proxy Tor.
     */
    private fun isProxyRelatedError(error: java.io.IOException): Boolean {
        val msg = error.message?.lowercase() ?: return false
        return listOf(
            "connection refused",
            "connection timed out",
            "socks",
            "proxy"
        ).any { msg.contains(it) }
    }
}
