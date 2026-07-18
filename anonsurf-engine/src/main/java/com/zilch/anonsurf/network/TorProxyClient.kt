package com.zilch.anonsurf.network

import android.util.Log
import com.zilch.anonsurf.config.TorConfig
import com.zilch.anonsurf.exception.AnonsurfException
import com.zilch.anonsurf.killswitch.NetworkKillSwitch
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * TorProxyClient — Cliente HTTP enrutado exclusivamente por Tor.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  ARQUITECTURA DEL CLIENTE HTTP
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Este cliente implementa la capa de red del sistema Anonsurf.
 * Todas las peticiones HTTP/S pasan por el proxy SOCKS5 local de
 * Tor (Orbot) en 127.0.0.1:9050.
 *
 * Cadena de seguridad del cliente:
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  App → [Kill Switch Check] → [Interceptor de Seguridad]│
 * │       → [SOCKS5 Proxy: Tor] → [Internet]              │
 * └─────────────────────────────────────────────────────────┘
 *
 * 1. **Kill Switch Check:** Antes de cada petición, se verifica
 *    que el Kill Switch no esté activo.
 *
 * 2. **Interceptor de Seguridad:** Headers anti-filtración,
 *    timeout agresivo, y detección de errores del proxy.
 *
 * 3. **SOCKS5 Proxy:** OkHttp envía todo el tráfico a través
 *    del proxy SOCKS5 de Tor. NO existe ruta directa a internet.
 *
 * DECISIÓN DE SEGURIDAD: El interceptor DESTRUYE la petición
 * si detecta cualquier anomalía en el proxy, en lugar de
 * intentar una ruta alternativa. El principio es:
 * "Si no puedes usar Tor, NO uses la red."
 *
 * DECISIÓN DE DISEÑO: Usamos connection pool mínimo y timeouts
 * cortos porque Tor es inherentemente más lento. Preferimos
 * fallos rápidos a conexiones zombi que consuman recursos.
 */
class TorProxyClient(
    private val killSwitch: NetworkKillSwitch
) {

    companion object {
        private const val TAG = "TorProxyClient"
    }

    /** Listener para notificar eventos de conexión */
    interface ConnectionListener {
        fun onProxyConnected()
        fun onProxyDisconnected(reason: String)
        fun onKillSwitchTriggered(reason: String)
    }

    private var connectionListener: ConnectionListener? = null

    // ── Construcción del OkHttpClient ───────────────────────────────

    /**
     * Construye el OkHttpClient configurado estrictamente para Tor.
     *
     * CONFIGURACIÓN CRÍTICA:
     *
     * - **Proxy SOCKS5:** 127.0.0.1:9050 — Solo loopback, nunca
     *   se conecta directamente a internet.
     *
     * - **Connection Pool:** Mínimo (1 conexión, 5 min de vida).
     *   Evita que conexiones antiguas filtren información cuando
     *   los circuitos Tor se renuevan.
     *
     * - **Timeouts:** Moderados para Tor (30s). Demasiado corto
     *   falla en Tor lento; demasiado largo bloquea la UI.
     *
     * - **DNS:** Se resuelve a través de Tor (el proxy SOCKS5
     *   maneja la resolución DNS, protegiendo contra ataques
     *   de revelación de DNS).
     */
    fun buildClient(): OkHttpClient {
        // Proxy SOCKS5 exclusivo — aquí es donde ocurre la magia de Tor
        val torProxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress(TorConfig.PROXY_HOST, TorConfig.PROXY_PORT)
        )

        return OkHttpClient.Builder()
            // ═══ PROXY ═══
            // Todas las conexiones pasan por Tor. Sin excepciones.
            .proxy(torProxy)

            // ═══ TIMEOUTS ═══
            .connectTimeout(TorConfig.CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TorConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TorConfig.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            // ═══ CONNECTION POOL ═══
            // Pool mínimo para evitar conexiones persistentes a través de Tor
            // que podrían ser explotadas por un adversario que observe
            // los tiempos de conexión.
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 1,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES
                )
            )

            // ═══ INTERCEPTOR DE SEGURIDAD ═══
            // Este interceptor es la línea de defensa de red más importante.
            .addInterceptor(createSecurityInterceptor())

            // ═══ NETWORK INTERCEPTOR ═══
            // Intercepta la conexión a nivel de red para detectar
            // fallos específicos del proxy SOCKS5.
            .addNetworkInterceptor(createNetworkInterceptor())

            // ═══ DNS ═══
            // DNS se resuelve a través del proxy SOCKS5 de Tor.
            // Cuando OkHttp tiene un proxy SOCKS configurado, resuelve
            // DNS a través del proxy automáticamente. NO necesitamos
            // un resolver custom — crear uno que retorne loopback
            // rompe TODAS las peticiones HTTP.

            // ═══ SEGURIDAD ═══
            .followRedirects(false)
            .followSslRedirects(false)

            // No almacenar respuestas en cache que puedan
            // revelar historial de navegación
            .cache(null)

            .build()
    }

    // ── Interceptores de seguridad ──────────────────────────────────

    /**
     * Interceptor de aplicación: verifica el Kill Switch y añade
     * headers de seguridad antes de que la petición salga.
     */
    private fun createSecurityInterceptor(): Interceptor {
        return Interceptor { chain ->
            // ═══ PASO 1: Verificar Kill Switch ═══
            // Si el kill switch está activo, no hacemos NADA de red.
            // Esta es la defensa más importante del sistema.
            killSwitch.assertNetworkAllowed()

            val request = chain.request()

            // ═══ PASO 2: Construir petición segura ═══
            val secureRequest = request.newBuilder()
                .header("User-Agent", TorConfig.ANONYMOUS_USER_AGENT)
                .header("Accept", request.header("Accept") ?: "*/*")
                .header("Accept-Language", "en-US,en;q=0.5")
                // Eliminamos headers que podrían identificar al usuario
                .removeHeader("X-Forwarded-For")
                .removeHeader("X-Real-IP")
                .removeHeader("Authorization")
                .removeHeader("Cookie")
                .removeHeader("Referer")
                .build()

            // ═══ PASO 3: Ejecutar con monitoreo ═══
            try {
                val response = chain.proceed(secureRequest)
                killSwitch.reportProxySuccess()
                response

            } catch (e: IOException) {
                // Analizamos el tipo de error para determinar si es
                // un fallo del proxy Tor
                val isProxyError = analyzeProxyError(e)

                if (isProxyError) {
                    killSwitch.reportProxyFailure()
                    Log.e(TAG, "🚨 Error del proxy Tor: ${e.message}")
                }

                throw e
            }
        }
    }

    /**
     * Interceptor de red: captura errores específicos de la capa
     * de conexión SOCKS5/Tor.
     *
     * DECISIÓN: Este interceptor opera a nivel de conexión TCP,
     * lo que permite detectar errores que el interceptor de
     * aplicación no puede ver (como fallos en el handshake SOCKS5).
     */
    private fun createNetworkInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()

            try {
                val response = chain.proceed(request)

                // Verificar que no recibimos una respuesta que indica
                // que el proxy nos está mintiendo sobre estar en Tor
                if (response.code == 407) {
                    // Proxy Authentication Required — el proxy SOCKS5
                    // no debería pedir autenticación
                    Log.e(TAG, "Proxy pidió autenticación inesperada (407)")
                    killSwitch.reportProxyFailure()
                }

                response

            } catch (e: IOException) {
                // Analizamos el tipo de error para determinar si es
                // un fallo del proxy Tor
                val isProxyError = analyzeProxyError(e)

                if (isProxyError) {
                    killSwitch.reportProxyFailure()
                    Log.e(TAG, "Error del proxy Tor: ${e.message}")
                }

                throw AnonsurfException.TorProxyUnavailable(
                    cause = e
                )
            }
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────

    /**
     * Analiza un error de red para determinar si es un fallo
     * del proxy Tor vs. un error de la aplicación remota.
     *
     * @return true si el error es atribuible al proxy Tor
     */
    private fun analyzeProxyError(error: IOException): Boolean {
        val message = error.message?.lowercase() ?: return false

        // Patrones de error típicos de un proxy SOCKS5 caído
        val proxyErrorPatterns = listOf(
            "connection refused",      // Proxy no escuchando
            "connection timed out",    // Proxy no responde
            "connection reset",        // Proxy cerró la conexión
            "address unreachable",     // No se puede alcanzar el proxy
            "no route to host",        // Routing fallido
            "broken pipe",             // Conexión cortada
            "connect timed out",       // Timeout de conexión
            "unable to resolve proxy"  // No se resuelve el proxy
        )

        return proxyErrorPatterns.any { pattern ->
            message.contains(pattern)
        }
    }

    /**
     * Registra un listener para eventos de conexión.
     */
    fun setConnectionListener(listener: ConnectionListener) {
        connectionListener = listener
    }
}
