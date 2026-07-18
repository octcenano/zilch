package com.zilch.anonsurf.exception

/**
 * AnonsurfException — Jerarquía de excepciones del motor de red.
 *
 * DECISIÓN DE SEGURIDAD: Nunca exponemos datos sensibles (IPs, claves, etc.)
 * en los mensajes de excepción. Los mensajes son descriptivos del fallo
 * pero no contienen información que pueda filtrarse en logs.
 */

/**
 * Excepción base del módulo Anonsurf.
 * Todas las excepciones del motor heredan de esta para facilitar
 * el manejo centralizado de errores.
 */
sealed class AnonsurfException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /**
     * El proxy SOCKS5 de Tor no está disponible o no responde.
     * Esto activa el Kill Switch inmediatamente.
     */
    class TorProxyUnavailable(
        message: String = "El proxy Tor no está disponible",
        cause: Throwable? = null
    ) : AnonsurfException(message, cause)

    /**
     * El proxy respondió pero la conexión a través de Tor falló
     * (timeout, circuito roto, etc.).
     */
    class TorCircuitFailure(
        message: String = "Fallo en el circuito Tor",
        cause: Throwable? = null
    ) : AnonsurfException(message, cause)

    /**
     * La verificación de IP reveló que NO estamos saliendo por Tor.
     * Esto es una emergencia: puede indicar un ataque o fuga de red.
     */
    class IpLeakDetected(
        message: String = "IP detectada fuera de la red Tor"
    ) : AnonsurfException(message)

    /**
     * El Kill Switch ha sido activado. Todas las operaciones de red
     * deben fallar inmediatamente con esta excepción.
     */
    class KillSwitchActive(
        reason: String = "Kill Switch activado"
    ) : AnonsurfException(reason)

    /**
     * Error genérico de red dentro del contexto de Tor.
     */
    class NetworkError(
        message: String,
        cause: Throwable? = null
    ) : AnonsurfException(message, cause)
}
