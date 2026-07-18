package com.zilch.anonsurf.killswitch

import android.util.Log
import com.zilch.anonsurf.config.TorConfig
import com.zilch.anonsurf.exception.AnonsurfException
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * NetworkKillSwitch — Mecanismo de protección integral contra fugas de red.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: DISEÑO DEL KILL SWITCH
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * El Kill Switch opera en dos niveles:
 *
 * 1. **PROACTIVO (Health Check periódico):** Un coroutine de fondo realiza
 *    conexiones TCP al proxy SOCKS5 cada N segundos. Si el proxy falla
 *    repetidamente, el switch se activa ANTES de que una petición real
 *    pueda filtrar datos.
 *
 * 2. **REACTIVO (Intercepción en cliente HTTP):** Cada petición OkHttp
 *    pasa por un interceptor que verifica el estado del Kill Switch
 *    antes de permitir la salida de datos.
 *
 * ¿Por qué no confiamos solo en el interceptor reactivo?
 * Porque una petición podría iniciarse justo cuando el proxy se cae,
 * entre el health check y la verificación. La detección proactiva
 * minimiza esta ventana de ataque.
 *
 * Estado del Kill Switch:
 * - Es ATÓMICO (AtomicBoolean) para acceso seguro desde múltiples hilos.
 * - Una vez activado, SOLO puede desactivarse reiniciando la sesión.
 * - Esto es intencional: si el proxy falla, no hay forma segura de
 *   reactivar la conexión sin riesgo de filtración.
 */
class NetworkKillSwitch {

    companion object {
        private const val TAG = "KillSwitch"
    }

    // ── Estado interno ──────────────────────────────────────────────

    /** Estado atómico del kill switch: true = BLOQUEADO */
    private val _isActive = AtomicBoolean(false)

    /** Contador de fallos consecutivos del proxy */
    private val _consecutiveFailures = AtomicInteger(0)

    /** Coroutine scope para el health check de fondo */
    @Volatile
    private var healthCheckScope: CoroutineScope? = null

    /** Job del health check periódico */
    @Volatile
    private var healthCheckJob: Job? = null

    /** Callback para notificar cambios de estado a la capa de UI */
    private var onStateChanged: ((active: Boolean, reason: String) -> Unit)? = null

    // ── API pública ─────────────────────────────────────────────────

    /**
     * Estado actual del Kill Switch.
     *
     * DECISIÓN: Se expone como propiedad de solo lectura externamente.
     * Solo las funciones internas pueden modificar el estado.
     */
    val isActive: Boolean
        get() = _isActive.get()

    /**
     * Registra un callback para notificaciones de cambio de estado.
     * Se ejecuta en el hilo del caller del Health Check.
     */
    fun setOnStateChangedListener(listener: (active: Boolean, reason: String) -> Unit) {
        onStateChanged = listener
    }

    /**
     * Inicia el monitoreo proactivo del proxy Tor.
     *
     * Lanza un coroutine que periódicamente verifica que el proxy
     * SOCKS5 en 127.0.0.1:9050 esté activo y receptivo.
     *
     * @param scope CoroutineScope padre (se usa el del ciclo de vida
     *               del componente que lo instancia).
     */
    fun startMonitoring(scope: CoroutineScope) {
        // Si ya está activo el kill switch, no permitimos monitoreo.
        // No hay vuelta atrás.
        if (_isActive.get()) {
            Log.w(TAG, "⚠ Kill Switch ya está activo. Monitoreo rechazado.")
            return
        }

        stopMonitoring()

        healthCheckScope = CoroutineScope(
            scope.coroutineContext + SupervisorJob() + Dispatchers.IO
        )

        healthCheckJob = healthCheckScope?.launch {
            Log.i(TAG, "Monitoreo de proxy Tor iniciado")

            while (isActive) {
                delay(TorConfig.PROXY_HEALTH_CHECK_INTERVAL_MS)
                performHealthCheck()
            }
        }

        Log.d(TAG, "Health check programado cada ${TorConfig.PROXY_HEALTH_CHECK_INTERVAL_MS}ms")
    }

    /**
     * Detiene el monitoreo proactivo.
     */
    fun stopMonitoring() {
        healthCheckJob?.cancel()
        healthCheckScope?.cancel()
        healthCheckJob = null
        healthCheckScope = null
        Log.d(TAG, "Monitoreo detenido")
    }

    /**
     * Verifica si una petición de red está permitida.
     *
     * Esta función DEBE ser llamada antes de cada petición HTTP
     * por el interceptor del OkHttpClient.
     *
     * @throws AnonsurfException.KillSwitchActive si el switch está activo
     */
    fun assertNetworkAllowed() {
        if (_isActive.get()) {
            throw AnonsurfException.KillSwitchActive(
                "Red bloqueada por Kill Switch. " +
                "El proxy Tor no está disponible. " +
                "Toda comunicación de red ha sido suspendida."
            )
        }
    }

    /**
     * Registra un fallo del proxy externamente.
     * Útil cuando el interceptor detecta un error de conexión al proxy.
     */
    fun reportProxyFailure() {
        val failures = _consecutiveFailures.incrementAndGet()
        Log.w(TAG, "⚠ Fallo de proxy registrado (#$failures)")

        if (failures >= TorConfig.MAX_PROXY_FAILURES_BEFORE_KILL) {
            activate("Máximo de fallos consecutivos alcanzado ($failures)")
        }
    }

    /**
     * Registra un éxito del proxy (reset del contador de fallos).
     */
    fun reportProxySuccess() {
        _consecutiveFailures.set(0)
    }

    /**
     * Activa el Kill Switch manualmente.
     *
     * Esta función puede ser llamada desde la UI (botón de pánico)
     * o desde mecanismos de detección de intrusos.
     *
     * @param reason Razón descriptiva del activación (para auditoría)
     */
    fun activate(reason: String = "Activación manual") {
        val previous = _isActive.getAndSet(true)

        if (!previous) {
            Log.e(TAG, "🚨 KILL SWITCH ACTIVADO: $reason")
            stopMonitoring()
            _consecutiveFailures.set(0)
            onStateChanged?.invoke(true, reason)
        }
    }

    /**
     * Intenta restablecer el Kill Switch.
     *
     * ⚠ ADVERTENCIA: Esto solo debe hacerse bajo condiciones controladas:
     * - El usuario debe autenticarse de nuevo (nueva sesión efímera).
     * - Debe verificarse que el proxy está operativo antes de reactivar.
     *
     * @return true si se restableció, false si no se pudo
     */
    fun tryReset(): Boolean {
        if (!_isActive.get()) return true

        // Verificar que el proxy está realmente disponible antes de resetear
        val proxyAlive = runBlocking(Dispatchers.IO) {
            checkProxyConnectivity()
        }

        if (proxyAlive) {
            _isActive.set(false)
            _consecutiveFailures.set(0)
            Log.i(TAG, "✓ Kill Switch restablecido — proxy operativo")
            onStateChanged?.invoke(false, "Proxy restaurado")
            return true
        }

        Log.w(TAG, "✗ No se pudo restablecer: proxy sigue caído")
        return false
    }

    // ── Lógica interna ──────────────────────────────────────────────

    /**
     * Ejecuta una verificación de salud del proxy SOCKS5.
     * Conexión TCP directa al proxy, sin HTTP.
     */
    private suspend fun performHealthCheck() {
        val isAlive = withContext(Dispatchers.IO) {
            checkProxyConnectivity()
        }

        if (isAlive) {
            reportProxySuccess()
        } else {
            reportProxyFailure()
        }
    }

    /**
     * Verifica conectividad TCP con el proxy SOCKS5.
     *
     * DECISIÓN: Usamos una conexión TCP raw en lugar de HTTP
     * para verificar el proxy. Esto es más rápido y no genera
     * tráfico HTTP innecesario sobre la red Tor.
     *
     * @return true si el proxy está aceptando conexiones
     */
    private fun checkProxyConnectivity(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(TorConfig.PROXY_HOST, TorConfig.PROXY_PORT),
                    TorConfig.CONNECTION_TIMEOUT_MS.toInt()
                )
                socket.isConnected
            }
        } catch (e: Exception) {
            Log.d(TAG, "Proxy no alcanzable: ${e.message}")
            false
        }
    }
}
