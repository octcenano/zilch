package com.zilch.crypto.keys

import android.util.Log
import com.zilch.crypto.config.CryptoConfig
import com.zilch.crypto.exception.CryptoEngineException
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.util.Base64

/**
 * Ed25519KeyGenerator — Generación y operaciones con claves Ed25519.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: ED25519
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Ed25519 (Curve25519) fue elegido por las siguientes razones:
 *
 * 1. **Resistencia a side-channel attacks:** A diferencia de ECDSA
 *    sobre NIST P-256, Ed25519 está diseñado para ser resistente
 *    a ataques de canal auxiliar (timing attacks, power analysis).
 *
 * 2. **Firma determinística:** No necesita un nonce aleatorio por
 *    firma (a diferencia de ECDSA), eliminando una clase entera
 *    de vulnerabilidades de generación de nonces.
 *
 * 3. **Claves pequeñas:** 32 bytes públicos, 64 bytes totales.
 *    Ideales para QR codes donde el espacio es limitado.
 *
 * 4. **Firma compacta:** 64 bytes. Mínimo espacio en el QR.
 *
 * 5. **Amplio auditado:** Curva con décadas de análisis criptográfico.
 *
 * IMPLEMENTACIÓN:
 * Usamos Bouncy Castle como proveedor criptográfico. Es 100% open source
 * (licencia MIT) y no tiene dependencias de Google Play Services.
 *
 * El proveedor se registra una sola vez al cargar la clase. En Android,
 * esto es seguro porque el ClassLoader es único por proceso.
 */
object Ed25519KeyGenerator {

    private const val TAG = "Ed25519KeyGen"

    // Tamaño de las claves en bytes
    private const val PUBLIC_KEY_SIZE = 32
    private const val PRIVATE_KEY_SIZE = 64 // seed (32) + public key (32)
    private const val SEED_SIZE = 32

    init {
        // Registrar Bouncy Castle como proveedor criptográfico
        // Solo se ejecuta una vez cuando se carga la clase
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
            Log.i(TAG, "Bouncy Castle provider registrado")
        }
    }

    /**
     * Resultado de la generación de un par de claves Ed25519.
     *
     * ════════════════════════════════════════════════════════════════
     *  SEGURIDAD: El caller DEBE limpiar privateKeyBytes y seed
     *  cuando ya no los necesite llamando a [SecureMemory.wipe].
     *
     *  Este objeto contiene datos sensibles. No se debe:
     *  - Serializar a JSON/String
     *  - Almacenar en log files
     *  - Pasar por Intent/Bundle
     *  - Almacenar en variables de tipo String
     * ════════════════════════════════════════════════════════════════
     */
    data class KeyPairResult(
        /** Clave pública en bytes (32 bytes) — SE PUEDE compartir */
        val publicKeyBytes: ByteArray,
        /** Clave privada en bytes (64 bytes) — NUNCA compartir */
        val privateKeyBytes: ByteArray,
        /** Semilla original (32 bytes) — NUNCA compartir */
        val seed: ByteArray,
        /** Clave pública en formato JCE — para operaciones estándar */
        val publicKey: PublicKey,
        /** Clave privada en formato JCE — para operaciones estándar */
        val privateKey: PrivateKey
    ) {
        /**
         * Limpia los bytes sensibles de memoria.
         * DEBE llamarse cuando el objeto ya no se necesite.
         *
         * Los campos JCE (publicKey/privateKey) no se pueden
         * limpiar directamente, pero serán recolectados por el GC.
         * Los ByteArray sí se sobreescriben de forma segura.
         */
        fun wipe() {
            SecureMemory.wipe(publicKeyBytes)
            SecureMemory.wipe(privateKeyBytes)
            SecureMemory.wipe(seed)
        }
    }

    /**
     * Genera un par de claves Ed25519 de forma aleatoria.
     *
     * Esta es la función principal de generación de identidad efímera.
     * Cada llamada produce un par completamente nuevo e impredecible.
     *
     * La semilla de generación proviene de [SecureRandom] del sistema,
     * que en Android usa `/dev/urandom` como fuente de entropía.
     *
     * @return KeyPairResult con las claves generadas
     * @throws CryptoEngineException.KeyGenerationFailed si la generación falla
     */
    fun generateKeyPair(): KeyPairResult {
        return try {
            // Generar semilla aleatoria criptográficamente segura
            val seed = SecureMemory.generateRandomBytes(SEED_SIZE)

            try {
                generateFromSeed(seed)
            } catch (e: Exception) {
                // Limpiar la semilla en caso de error
                SecureMemory.wipe(seed)
                throw e
            }

        } catch (e: CryptoEngineException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al generar par de claves: ${e.message}")
            throw CryptoEngineException.KeyGenerationFailed(cause = e)
        }
    }

    /**
     * Genera un par de claves Ed25519 desde una semilla específica.
     *
     * ⚠ USO PRINCIPAL: Testing y restauración de identidad.
     * Para uso normal, preferir [generateKeyPair].
     *
     * @param seed Semilla de 32 bytes
     * @return KeyPairResult con las claves derivadas de la semilla
     * @throws CryptoEngineException.InvalidSeed si la semilla no tiene 32 bytes
     * @throws CryptoEngineException.KeyGenerationFailed si la generación falla
     */
    fun generateFromSeed(seed: ByteArray): KeyPairResult {
        if (seed.size != SEED_SIZE) {
            throw CryptoEngineException.InvalidSeed(
                "Semilla debe tener $SEED_SIZE bytes, tiene ${seed.size}"
            )
        }

        return try {
            // ═══ Derivación determinística desde semilla ═══
            // El par de claves se genera DIRECTAMENTE desde la semilla
            // usando los parámetros Ed25519 de Bouncy Castle.
            // Esto garantiza que la misma semilla siempre produce
            // las mismas claves (determinístico).
            val privateKeyParams = Ed25519PrivateKeyParameters(seed)
            val publicKeyParams = privateKeyParams.generatePublicKey()

            // ═══ EXTRAER BYTES CRUDOS ═══
            // Ed25519PublicKeyParameters.getEncoded() puede retornar
            // bytes ASN.1 DER SubjectPublicKeyInfo (44 bytes) o bytes
            // crudos (32 bytes) dependiendo de la versión de BC.
            // Necesitamos SIEMPRE los 32 bytes crudos del punto público
            // para QR, hashing y derivación de claves compartidas.
            val asn1Header = byteArrayOf(
                0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70,
                0x03, 0x21, 0x00
            )
            val rawPublicEncoded = publicKeyParams.encoded
            val publicKeyBytes = if (
                rawPublicEncoded.size == PUBLIC_KEY_SIZE + asn1Header.size &&
                rawPublicEncoded.take(asn1Header.size) == asn1Header.toList()
            ) {
                rawPublicEncoded.copyOfRange(asn1Header.size, rawPublicEncoded.size)
            } else {
                rawPublicEncoded
            }
            require(publicKeyBytes.size == PUBLIC_KEY_SIZE) {
                "Clave pública tiene ${publicKeyBytes.size} bytes, esperados $PUBLIC_KEY_SIZE"
            }

            // Para la clave privada, extraer la semilla de 32 bytes
            // del wrapper PKCS#8 ASN.1 si es necesario.
            val rawPrivateEncoded = privateKeyParams.encoded
            val privateKeyBytes = if (rawPrivateEncoded.size > PRIVATE_KEY_SIZE) {
                rawPrivateEncoded.copyOfRange(
                    rawPrivateEncoded.size - SEED_SIZE,
                    rawPrivateEncoded.size
                )
            } else {
                rawPrivateEncoded
            }

            // Usar la representación ASN.1 completa para JCE
            val jcePublicKey = java.security.KeyFactory.getInstance("Ed25519")
                .generatePublic(java.security.spec.X509EncodedKeySpec(rawPublicEncoded))
            val jcePrivateKey = java.security.KeyFactory.getInstance("Ed25519")
                .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(rawPrivateEncoded))

            Log.i(TAG, "Par de claves Ed25519 derivado de semilla")

            val keyPairResult = KeyPairResult(
                publicKeyBytes = publicKeyBytes,
                privateKeyBytes = privateKeyBytes,
                seed = seed.copyOf(),
                publicKey = jcePublicKey,
                privateKey = jcePrivateKey
            )
            // Limpiar la semilla original (el copyOf ya está en KeyPairResult)
            SecureMemory.wipe(seed)
            keyPairResult

        } catch (e: CryptoEngineException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al generar claves desde semilla: ${e.message}")
            throw CryptoEngineException.KeyGenerationFailed(cause = e)
        }
    }

    /**
     * Firma datos con una clave privada Ed25519.
     *
     * La firma es determinística: los mismos datos + la misma clave
     * siempre producen la misma firma. Esto es una característica
     * de seguridad de Ed25519, no una debilidad.
     *
     * @param data Datos a firmar
     * @param privateKey Clave privada Ed25519
     * @return Array de bytes de la firma (64 bytes)
     * @throws CryptoEngineException.SigningFailed si la firma falla
     */
    fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        return try {
            val signer = Signature.getInstance(
                CryptoConfig.SIGNATURE_ALGORITHM,
                BouncyCastleProvider.PROVIDER_NAME
            )
            signer.initSign(privateKey)
            signer.update(data)
            signer.sign()
        } catch (e: CryptoEngineException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al firmar datos: ${e.message}")
            throw CryptoEngineException.SigningFailed(cause = e)
        }
    }

    /**
     * Verifica una firma Ed25519.
     *
     * @param data Datos originales
     * @param signature Firma a verificar
     * @param publicKey Clave pública Ed25519
     * @return true si la firma es válida
     * @throws CryptoEngineException.SignatureVerificationFailed si la verificación falla
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        return try {
            val verifier = Signature.getInstance(
                CryptoConfig.SIGNATURE_ALGORITHM,
                BouncyCastleProvider.PROVIDER_NAME
            )
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: CryptoEngineException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar firma: ${e.message}")
            throw CryptoEngineException.SignatureVerificationFailed()
        }
    }
}
