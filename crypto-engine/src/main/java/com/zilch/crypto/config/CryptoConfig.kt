package com.zilch.crypto.config

/**
 * CryptoConfig — Constantes del motor criptográfico.
 *
 * DECISIÓN DE SEGURIDAD: Todos los parámetros criptográficos están
 * hardcodeados. No se permite configuración dinámica de algoritmos
 * para prevenir ataques de downgrade (forzar un algoritmo más débil).
 */
object CryptoConfig {

    // ════════════════════════════════════════════════════════════════
    //  ALGORITMOS CRIPTOGRÁFICOS
    // ════════════════════════════════════════════════════════════════

    /** Algoritmo de curva elíptica para firmas digitales */
    const val SIGNATURE_ALGORITHM = "Ed25519"

    /** Algoritmo de hash para derivar identificadores de nodo */
    const val HASH_ALGORITHM = "SHA-256"

    /** Algoritmo para derivar claves de cifrado desde la semilla */
    const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"

    // ════════════════════════════════════════════════════════════════
    //  PARÁMETROS DE IDENTIDAD EFÍMERA
    // ════════════════════════════════════════════════════════════════

    /**
     * TTL de una identidad efímera en milisegundos.
     * Después de este tiempo, la identidad debe regenerarse.
     *
     * Valor: 1 hora. Razón: equilibrio entre practicidad
     * (no regenerar cada 5 minutos) y seguridad (no mantener
     * la misma identidad demasiado tiempo).
     */
    const val IDENTITY_TTL_MS = 3_600_000L // 1 hora

    /**
     * TTL de un código QR en milisegundos.
     * Después de este tiempo, el QR debe regenerarse.
     *
     * Valor: 5 minutos. Los QR son intencionalmente de corta
     * vida para prevenir replay attacks.
     */
    const val QR_TTL_MS = 300_000L // 5 minutos

    /**
     * Número de caracteres del fingerprint para verificación verbal.
     * Formato: aaaa-bbbb-cccc (12 hex chars = 48 bits de entropía).
     *
     * DECISIÓN: 12 caracteres son suficientes para verificación
     * verbal (4 grupos de 3) sin ser tediosos de comunicar.
     * 48 bits de entropía hacen que un ataque de fuerza bruta
     * del fingerprint sea inviable en la práctica.
     */
    const val FINGERPRINT_LENGTH = 12

    /**
     * Separador del fingerprint para presentación verbal.
     * Ejemplo: "a3f2-8b1c-4d5e"
     */
    const val FINGERPRINT_SEPARATOR = "-"

    /**
     * Longitud de cada grupo del fingerprint.
     */
    const val FINGERPRINT_GROUP_SIZE = 4

    // ════════════════════════════════════════════════════════════════
    //  PARÁMETROS QR
    // ════════════════════════════════════════════════════════════════

    /** Versión del protocolo QR. Para backward compatibility. */
    const val QR_PROTOCOL_VERSION = 1

    /**
     * Tamaño de imagen QR en píxeles.
     * 512px es un buen equilibrio entre legibilidad
     * y tamaño de archivo.
     */
    const val QR_IMAGE_SIZE = 512

    /**
     * Nivel de corrección de errores QR.
     * H (30%) permite que hasta el 30% del QR esté dañado
     * y aún así se pueda leer. Ideal para pantallas
     * con reflejos o suciedad física.
     */
    const val QR_ERROR_CORRECTION = 'H'

    /**
     * Margen del QR en módulos.
     */
    const val QR_MARGIN = 2

    /**
     * Número de iteraciones para PBKDF2 al derivar claves.
     * Alto intencionalmente: la derivación solo se hace una
     * vez al inicio de sesión, no en hot path.
     */
    const val PBKDF2_ITERATIONS = 100_000

    /**
     * Longitud de la salt para PBKDF2 en bytes.
     */
    const val PBKDF2_SALT_LENGTH = 32
}
