package com.zilch.crypto.keys

import java.security.SecureRandom

/**
 * SecureMemory — Utilidades para manejo seguro de datos sensibles en memoria.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: MANEJO DE MEMORIA
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * En JVM/Android, la memoria no se puede borrar de forma garantizada.
 * El garbage collector puede preservar copias en memoria o el runtime
 * puede hacer dumping del heap. Sin embargo, aplicamos las mejores
 * prácticas posibles:
 *
 * 1. **Wipe de arrays:** Sobreescribir con ceros antes de descartar.
 *    Aunque el GC pueda preservar la copia original, marcamos los
 *    datos como reemplazables en el scope activo.
 *
 * 2. **Evitar strings para claves:** Las claves se mantienen como
 *    ByteArray. Los Strings en JVM son inmutables y se cachean,
 *    lo que hace imposible borrarlos de memoria.
 *
 * 3. **Scope controlado:** Las claves se pasan como parámetros
 *    y se descartan al salir de la función que las necesita.
 *
 * 4. **SecureRandom:** Para toda generación de números aleatorios
 *    que afecten seguridad. Nunca usamos Random estándar.
 */
object SecureMemory {

    /** Generador de números aleatorios criptográficamente seguro */
    val secureRandom: SecureRandom = SecureRandom()

    /**
     * Sobreescribe un ByteArray con bytes aleatorios y luego con ceros.
     *
     * El patrón doble (aleatorios + ceros) es un estándar de
     * defensa en software de ciberseguridad. Los bytes aleatorios
     * eliminan patrones que un atacante podría usar para localizar
     * la memoria del array en un dump.
     *
     * @param data Array a sobreescribir
     */
    fun wipe(data: ByteArray) {
        // Paso 1: Sobreescribir con bytes aleatorios
        secureRandom.nextBytes(data)
        // Paso 2: Sobreescribir con ceros
        data.fill(0)
    }

    /**
     * Sobreescribe un ByteArray con ceros directamente.
     *
     * Más rápido que [wipe], pero menos seguro. Usar cuando
     * la prioridad es rendimiento y no se espera un ataque
     * de forense de memoria activo.
     *
     * @param data Array a sobreescribir
     */
    fun wipeFast(data: ByteArray) {
        data.fill(0)
    }

    /**
     * Copia un ByteArray de forma segura.
     *
     * El caller es responsable de limpiar el array copiado
     * cuando ya no lo necesite.
     *
     * @param source Array origen
     * @return Nuevo array con los mismos datos
     */
    fun secureCopy(source: ByteArray): ByteArray {
        return source.copyOf()
    }

    /**
     * Genera un ByteArray de bytes aleatorios criptográficamente seguros.
     *
     * @param size Número de bytes a generar
     * @return ByteArray con bytes aleatorios
     */
    fun generateRandomBytes(size: Int): ByteArray {
        return ByteArray(size).also {
            secureRandom.nextBytes(it)
        }
    }
}
