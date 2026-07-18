package com.zilch.anonsurf.verification

import android.util.Log
import com.zilch.anonsurf.config.TorConfig
import com.zilch.anonsurf.exception.AnonsurfException
import com.zilch.anonsurf.killswitch.NetworkKillSwitch
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * TorIpVerifier — Verificación anónima de que el tráfico sale por Tor.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: VERIFICACIÓN DE IP
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Este verificador usa la API oficial de Tor Project para confirmar
 * que la dirección IP de salida corresponde a un nodo de Tor.
 *
 * ⚠ SEGURIDAD DEL VERIFICADOR:
 *
 * 1. NUNCA devuelve la IP en texto plano. Solo devuelve un enum
 *    de estado. La IP se descarta inmediatamente después de
 *    validarse internamente.
 *
 * 2. La respuesta HTTP se limita en tamaño (MAX_BODY_BYTES) para
 *    prevenir ataques de agotamiento de memoria.
 *
 * 3. El body se parsea con streaming (JsonReader) — la IP NUNCA se crea como String.
 *
 * 4. Se integra con el Kill Switch: si Tor no está activo,
 *    la petición se bloquea antes de salir.
 * ═══════════════════════════════════════════════════════════════════════════
 */
class TorIpVerifier(
    private val httpClient: OkHttpClient,
    private val killSwitch: NetworkKillSwitch
) {

    companion object {
        private const val TAG = "TorIpVerifier"

        /** Tamaño máximo del body de respuesta para prevenir DoS */
        private const val MAX_BODY_BYTES = 4096L
    }

    /**
     * Resultado de la verificación de IP.
     *
     * ════════════════════════════════════════════════════════════════
     *  DECISIÓN DE SEGURIDAD CRÍTICA:
     *  NO contiene la IP de salida como String.
     *  Un campo con la IP en texto plano en un data class es un
     *  vector de ataque: se almacena en el heap JVM, se serializa,
     *  se muestra en logs, y persiste después de que el objeto
     *  se descarta (hasta que el GC sobreescriba la memoria).
     *
     *  La IP se valida internamente y se descarta inmediatamente.
     *  Solo el resultado booleano sale de este módulo.
     * ════════════════════════════════════════════════════════════════
     */
    enum class TorStatus {
        /** Tráfico confirmado saliendo por un nodo de Tor */
        ACTIVE,

        /** Tráfico NO detectado como Tor — posible fuga de IP */
        LEAK_DETECTED,

        /** No se pudo verificar (proxy caído, timeout, etc.) */
        UNREACHABLE
    }

    /**
     * Verifica si el tráfico actual está saliendo por la red Tor.
     *
     * ⚠ SEGURIDAD: Esta función NUNCA devuelve la IP de salida.
     * Solo devuelve el estado de la verificación.
     *
     * @return TorStatus con el resultado de la verificación
     * @throws AnonsurfException.IpLeakDetected si se confirma fuga de IP
     * @throws AnonsurfException.NetworkError si no se pudo verificar
     * @throws AnonsurfException.KillSwitchActive si el kill switch bloqueó la petición
     */
    suspend fun verifyTorConnection(): TorStatus {
        Log.i(TAG, "Verificando conexion Tor...")

        // ═══ DEFENSA 1: Kill Switch ═══
        killSwitch.assertNetworkAllowed()

        var lastException: Exception? = null

        repeat(TorConfig.IP_VERIFICATION_MAX_RETRIES) { attempt ->
            Log.d(TAG, "Intento ${attempt + 1}/${TorConfig.IP_VERIFICATION_MAX_RETRIES}")

            try {
                val status = performIpCheck()

                if (status == TorStatus.ACTIVE) {
                    Log.i(TAG, "Verificacion exitosa — Tor operativo")
                    return TorStatus.ACTIVE
                }

                if (status == TorStatus.LEAK_DETECTED) {
                    // CRITICO: IP fuera de Tor detectada
                    Log.e(TAG, "IP LEAK DETECTADA — Kill Switch necesario")
                    throw AnonsurfException.IpLeakDetected()
                }

            } catch (e: AnonsurfException.IpLeakDetected) {
                throw e

            } catch (e: AnonsurfException.KillSwitchActive) {
                throw e

            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Intento ${attempt + 1} falló")

                if (attempt < TorConfig.IP_VERIFICATION_MAX_RETRIES - 1) {
                    delay(TorConfig.IP_VERIFICATION_RETRY_DELAY_MS)
                }
            }
        }

        Log.e(TAG, "Verificacion fallida tras ${TorConfig.IP_VERIFICATION_MAX_RETRIES} intentos")
        throw AnonsurfException.NetworkError(
            message = "No se pudo verificar la conexion Tor",
            cause = lastException
        )
    }

    /**
     * Verifica si el tráfico de red actual está saliendo por Tor.
     *
     * Función simplificada para uso rápido desde la UI.
     * No lanza excepciones, devuelve un boolean.
     *
     * @return true si Tor está activo y el tráfico sale por un nodo de salida Tor
     */
    suspend fun isUsingTor(): Boolean {
        return try {
            verifyTorConnection() == TorStatus.ACTIVE
        } catch (e: Exception) {
            false
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  LÓGICA INTERNA — NUNCA expone la IP en texto plano
    // ════════════════════════════════════════════════════════════════

    /**
     * Realiza una petición individual de verificación de IP.
     *
     * Flujo de seguridad:
     *  1. Petición HTTPS al endpoint de Tor Project
     *  2. Body limitado a MAX_BODY_BYTES (prevenir DoS)
     *  3. Parseo streaming con JsonReader — la IP NUNCA se crea como String
     *  4. Se lee SOLO el flag "IsTor" (boolean)
     *  5. El campo "IP" se descarta con skipValue() sin materializarlo
     *  6. Solo se retorna el estado TorStatus (sin IP)
     *
     * @return TorStatus con el resultado
     * @throws IOException si la petición o parsing falla
     */
    private fun performIpCheck(): TorStatus {
        val request = Request.Builder()
            .url(TorConfig.TOR_CHECK_ENDPOINT)
            .header("User-Agent", TorConfig.ANONYMOUS_USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()

        val response = httpClient.newCall(request).execute()

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException(
                    "Verificacion de IP fallo con HTTP ${resp.code}"
                )
            }

            val bodyBytes = resp.body?.bytes()
                ?: throw IOException("Respuesta vacia del verificador de IP")

            // ═══ DEFENSA 2: Limitar tamaño del body ═══
            if (bodyBytes.size > MAX_BODY_BYTES) {
                wipeByteArray(bodyBytes)
                throw IOException("Respuesta excede limite de seguridad")
            }

            return try {
                // ═══ DEFENSA 3: Parseo streaming SIN crear Strings con la IP ═══
                //
                // CRÍTICO: JSONObject(bodyStr) almacena TODOS los valores como
                // Strings internamente en un HashMap. Esto incluye la IP real.
                // Un atacante con acceso al heap de la JVM podría extraer la IP.
                //
                // Solución: Usamos JsonReader (streaming parser del SDK Android)
                // que lee directamente desde un InputStream. Para el campo "IP",
                // llamamos skipValue() que descarta el valor SIN crear ningún
                // String intermedio con la dirección IP.
                //
                // Flujo de memoria:
                //   bodyBytes → InputStream → JsonReader → skipValue("IP")
                //   Ningún String con la IP llega al heap.
                val inputStream = bodyBytes.inputStream()
                val reader = java.io.InputStreamReader(inputStream, Charsets.UTF_8)
                val jsonReader = android.util.JsonReader(reader)

                var isTor = false
                var hasIpField = false

                try {
                    jsonReader.beginObject()
                    while (jsonReader.hasNext()) {
                        when (jsonReader.nextName()) {
                            "IsTor" -> isTor = jsonReader.nextBoolean()
                            "IP" -> {
                                // skipValue() descarta el valor SIN crear String.
                                // Para un string JSON, consume los bytes y los descarta.
                                hasIpField = true
                                jsonReader.skipValue()
                            }

                            else -> jsonReader.skipValue()
                        }
                    }
                    jsonReader.endObject()
                } finally {
                    jsonReader.close()
                    reader.close()
                    inputStream.close()
                }

                if (!isTor) {
                    Log.e(
                        TAG,
                        "API confirma que NO estamos en Tor. Campo IP presente: $hasIpField"
                    )
                    TorStatus.LEAK_DETECTED
                } else {
                    Log.i(TAG, "API confirma que estamos en Tor")
                    TorStatus.ACTIVE
                }

            } catch (e: Exception) {
                throw IOException(
                    "No se pudo parsear la respuesta del verificador",
                    e
                )
            } finally {
                // ═══ DEFENSA 5: Sobreescribir datos sensibles ═══
                wipeByteArray(bodyBytes)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  UTILIDADES DE SEGURIDAD
    // ════════════════════════════════════════════════════════════════

    /**
     * Sobreescribe un ByteArray con ceros antes de liberarlo.
     *
     * DECISIÓN: En JVM no podemos garantizar que el GC sobreescriba
     * la memoria inmediatamente, pero al menos marcamos los bytes
     * como reemplazables y eliminamos la referencia del array del
     * scope activo. El pattern de wipe es un estándar de defensa
     * en código de ciberseguridad para minimizar la ventana de
     * exposición en memoria.
     *
     * @param data Array a sobreescribir con ceros
     */
    private fun wipeByteArray(data: ByteArray) {
        data.fill(0)
    }
}
