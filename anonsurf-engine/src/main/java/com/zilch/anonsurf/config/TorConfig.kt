package com.zilch.anonsurf.config

/**
 * TorConfig — Configuración centralizada del motor Anonsurf.
 *
 * DECISIÓN DE SEGURIDAD: Estas constantes están hardcodeadas deliberadamente.
 * El proxy SOCKS5 de Tor (Orbot) escucha en el loopback 127.0.0.1:9050 por
 * defecto. No permitimos configuración remota de estos valores para evitar
 * que un atacante redirija el tráfico a un proxy malicioso.
 */
object TorConfig {

    /** Dirección del proxy SOCKS5 local de Tor (loopback exclusivo) */
    const val PROXY_HOST = "127.0.0.1"

    /** Puerto estándar del proxy SOCKS5 de Orbot */
    const val PROXY_PORT = 9050

    /**
     * Timeout general de conexión en milisegundos.
     * Valor moderado: Tor tiene latencia alta por los circuitos de 3 saltos,
     * pero no queremos que una conexión lenta bloquee la UI indefinidamente.
     */
    const val CONNECTION_TIMEOUT_MS = 30_000L

    /** Timeout de lectura de respuesta */
    const val READ_TIMEOUT_MS = 30_000L

    /** Timeout de escritura de petición */
    const val WRITE_TIMEOUT_MS = 30_000L

    /**
     * Número máximo de reintentos para verificación de IP.
     * En Tor, los circuitos se renuevan periódicamente; un intento
     * puede fallar por un circuito recién construido que aún no está listo.
     */
    const val IP_VERIFICATION_MAX_RETRIES = 3

    /** Tiempo entre reintentos de verificación en milisegundos */
    const val IP_VERIFICATION_RETRY_DELAY_MS = 2_000L

    /**
     * APIs para verificación de IP. Usamos múltiples endpoints
     * como fallback para mayor resiliencia.
     * Todos son servicios públicos de verificación de IP que NO requieren API key.
     */
    val IP_CHECK_ENDPOINTS: List<String> = listOf(
        "https://api.ipify.org?format=json",
        "https://check.torproject.org/api/ip",
        "https://httpbin.org/ip"
    )

    /** Endpoint primario para verificar si estamos en Tor */
    const val TOR_CHECK_ENDPOINT = "https://check.torproject.org/api/ip"

    /**
     * Intervalo de health check del proxy en milisegundos.
     * Se usa para el mecanismo de Kill Switch pasivo.
     */
    const val PROXY_HEALTH_CHECK_INTERVAL_MS = 10_000L

    /**
     * Número máximo de fallos consecutivos del proxy antes de
     * activar el Kill Switch automáticamente.
     */
    const val MAX_PROXY_FAILURES_BEFORE_KILL = 3

    /**
     * Tamaño del buffer para lectura de respuestas.
     * Limitado para evitar DoS por respuestas enormes.
     */
    const val MAX_RESPONSE_BODY_BYTES = 10L * 1024 * 1024 // 10 MB

    /**
     * User-Agent genérico y anónimo.
     * No revelamos información del dispositivo o la app.
     */
    const val ANONYMOUS_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0"
}
