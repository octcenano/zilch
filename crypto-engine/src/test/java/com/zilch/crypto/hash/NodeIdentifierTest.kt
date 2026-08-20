package com.zilch.crypto.hash

import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pruebas unitarias del [NodeIdentifier].
 *
 * `NodeIdentifier` solo depende de la JDK (`MessageDigest` + `Base64`), por lo
 * que estas pruebas se ejecutan al 100% en la JVM sin necesidad de emulador.
 */
class NodeIdentifierTest {

    private val rng = SecureRandom()

    private fun randomPublicKey(size: Int = 32): ByteArray =
        ByteArray(size).also { rng.nextBytes(it) }

    @Test
    fun `derive produces a 64 char lowercase hex node id`() {
        val nodeId = NodeIdentifier.derive(randomPublicKey())
        assertEquals(64, nodeId.length)
        assertTrue(nodeId.all { it in "0123456789abcdef" })
    }

    @Test
    fun `derive es deterministico para la misma clave publica`() {
        val publicKey = randomPublicKey()
        assertEquals(
            NodeIdentifier.derive(publicKey),
            NodeIdentifier.derive(publicKey)
        )
    }

    @Test
    fun `claves publicas distintas producen node ids distintos`() {
        assertNotEquals(
            NodeIdentifier.derive(randomPublicKey()),
            NodeIdentifier.derive(randomPublicKey())
        )
    }

    @Test
    fun `deriveFromBase64 equivale a derive`() {
        val publicKey = randomPublicKey()
        val base64 = Base64.getEncoder().encodeToString(publicKey)
        assertEquals(
            NodeIdentifier.derive(publicKey),
            NodeIdentifier.deriveFromBase64(base64)
        )
    }

    @Test
    fun `fingerprint es el prefijo de 12 chars formateado del node id`() {
        val publicKey = randomPublicKey()
        val nodeId = NodeIdentifier.derive(publicKey)
        val fingerprint = NodeIdentifier.fingerprint(publicKey)

        val expected = nodeId.take(12).chunked(4).joinToString("-").lowercase()
        assertEquals(expected, fingerprint)
        assertEquals(14, fingerprint.length) // 12 chars + 2 separadores
    }

    @Test
    fun `formatForDisplay agrupa el node id en bloques de 8 chars`() {
        val nodeId = NodeIdentifier.derive(randomPublicKey())
        val formatted = NodeIdentifier.formatForDisplay(nodeId)
        assertTrue(formatted.split(" ").all { it.length == 8 })
    }
}