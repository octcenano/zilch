package com.zilch.anonsurf

import android.content.Context
import android.util.Log
import com.zilch.anonsurf.config.TorConfig
import com.zilch.anonsurf.exception.AnonsurfException
import com.zilch.anonsurf.killswitch.NetworkKillSwitch
import com.zilch.anonsurf.network.TorProxyClient
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
 * ┌──────────────────────────────────────────────────────────┐
 * │                    AnonsurfEngine                        │
 * │  (Fachada que expone la API pública para el resto de     │
 * │   la aplicación y coordina los componentes)              │
 * ├──────────────┬──────────────┬────────────────────────────┤
 * │  TorIpVerifier│ KillSwitch   │  TorProxyClient           │
 * │  (Verifica    │ (Bloquea    │  (Enruta todo el           │
 * │   que somos   │  la red si  │   tráfico HTTP/S           │
 * │   Tor)        │  Tor cae)   │   por SOCKS5/Tor)          │
 * └──────────────┴──────────────┴────────────────────────────┘
 *
 * USO:
 * ```kotlin
 * // En tu ViewModel o Activity principal
 * val engine = AnonsurfEngine.getInstance(context)
 *
 * // Iniciar el motor (verifica Tor + activa Kill Switch)
 * engine.start()
 *
 * // Hacer una petición segura a través de Tor
 * val response = engine.executeSecureRequest(request)
 *
 * // Verificar estado
 * if (engine.isReady) { ... }
 *
 * // En emergencia: destruir todo
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
     * 1. Verificación de que el proxy Tor está activo
     * 2. Verificación de que el tráfico sale por un nodo Tor
     * 3. Activación del monitoreo proactivo del proxy
     *
     * @param scope CoroutineScope para las operaciones asíncronas
     *               (usualmente el viewModelScope de la Activity)
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

        engineScope?.launch {
            try {
                // Paso 1: Verificar conexión Tor
                val status = ipVerifier.verifyTorConnection()
                if (status == TorIpVerifier.TorStatus.ACTIVE) {
                    _isTorVerified.set(true)
                    _torStatus.value = TorIpVerifier.TorStatus.ACTIVE
                    onTorStatusChanged?.invoke(true)
                }

                // Paso 2: Activar monitoreo del proxy
                startProxyMonitoring()

                // Paso 3: Marcar como listo
                _isReady.set(true)
                Log.i(TAG, "✅ Motor Anonsurf listo — Tor verificado, Kill Switch activo")

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
     */
    fun stop() {
        Log.i(TAG, "Deteniendo motor Anonsurf...")
        engineScope?.cancel()
        engineScope = null
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
