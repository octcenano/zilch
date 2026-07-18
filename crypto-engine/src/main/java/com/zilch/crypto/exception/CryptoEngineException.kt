package com.zilch.crypto.exception

/**
 * CryptoEngineException — Jerarquía de excepciones del motor criptográfico.
 *
 * DECISIÓN DE SEGURIDAD: Las excepciones nunca contienen claves,
 * semillas, ni datos sensibles en sus mensajes.
 */
sealed class CryptoEngineException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** Error al generar el par de claves Ed25519 */
    class KeyGenerationFailed(
        message: String = "No se pudo generar el par de claves",
        cause: Throwable? = null
    ) : CryptoEngineException(message, cause)

    /** La semilla no tiene la longitud correcta (32 bytes) */
    class InvalidSeed(
        message: String = "Semilla inválida: longitud incorrecta"
    ) : CryptoEngineException(message)

    /** Error al firmar datos */
    class SigningFailed(
        message: String = "No se pudieron firmar los datos",
        cause: Throwable? = null
    ) : CryptoEngineException(message, cause)

    /** La firma no coincide — posible tampering */
    class SignatureVerificationFailed(
        message: String = "Verificación de firma fallida — datos manipulados"
    ) : CryptoEngineException(message)

    /** Error al generar o decodificar un código QR */
    class QrOperationFailed(
        message: String = "Operación QR fallida",
        cause: Throwable? = null
    ) : CryptoEngineException(message, cause)

    /** El payload QR tiene formato inválido o campos faltantes */
    class QrPayloadInvalid(
        message: String = "Payload QR inválido"
    ) : CryptoEngineException(message)

    /** El código QR ha expirado */
    class QrExpired(
        message: String = "El código QR ha expirado"
    ) : CryptoEngineException(message)

    /** El payload QR tiene una versión de protocolo no soportada */
    class QrUnsupportedVersion(
        val version: Int,
        message: String = "Versión de protocolo QR no soportada: $version"
    ) : CryptoEngineException(message)

    /** Error al acceder al almacenamiento seguro */
    class SecureStorageError(
        message: String = "Error en el almacenamiento seguro",
        cause: Throwable? = null
    ) : CryptoEngineException(message, cause)

    /** La identidad ha expirado y debe regenerarse */
    class IdentityExpired(
        message: String = "Identidad expirada — regenerar sesión"
    ) : CryptoEngineException(message)

    /** Error en la derivación de claves */
    class KeyDerivationFailed(
        message: String = "Error al derivar claves",
        cause: Throwable? = null
    ) : CryptoEngineException(message, cause)

    /** El contacto ya existe en el almacenamiento */
    class ContactAlreadyExists(
        val existingFingerprint: String,
        message: String = "El contacto ya existe"
    ) : CryptoEngineException(message)

    /** El contacto no fue encontrado */
    class ContactNotFound(
        val nodeId: String,
        message: String = "Contacto no encontrado"
    ) : CryptoEngineException(message)
}
