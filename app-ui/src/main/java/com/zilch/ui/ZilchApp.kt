package com.zilch.ui

import android.app.Application
import android.util.Log
import com.zilch.anonsurf.AnonsurfEngine
import com.zilch.blemesh.BleMeshEngine
import com.zilch.crypto.CryptoEngine
import com.zilch.crypto.storage.EncryptedStorage

/**
 * ZilchApp — Application class de Zilch.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: INICIALIZACIÓN
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Los motores se inicializan en onCreate() de la Application para
 * garantizar que existan antes de que cualquier Activity o Fragment
 * intente usarlos.
 *
 * La inicialización es perezosa dentro de cada motor: solo se
 * genera la identidad cuando se llama a start().
 */
class ZilchApp : Application() {

    companion object {
        private const val TAG = "ZilchApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "=== ZILCH INICIANDO ===")
        Log.i(TAG, "Build: FOSS (F-Droid)")

        // Inicializar singletons de motores
        // Nota: La identidad real se genera cuando se llama a start()
        // desde el ViewModel, no aquí. Esto es intencional para
        // minimizar el tiempo que las claves están en memoria
        // si la app no se usa.

        AnonsurfEngine.getInstance(this)
        CryptoEngine.getInstance(this)
        BleMeshEngine.getInstance(this)

        // Inicializar almacenamiento cifrado con la semilla de la identidad.
        // La DB se cifra con una passphrase derivada de la semilla.
        // Cuando la identidad se destruye, la passphrase se pierde
        // y los datos cifrados se vuelven inútiles.
        try {
            val crypto = CryptoEngine.getInstance(this)
            val identity = crypto.identityManager.currentIdentity
            EncryptedStorage.initialize(this, identity.seed)
            Log.i(TAG, "EncryptedStorage inicializado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al inicializar EncryptedStorage: ${e.message}")
        }

        Log.i(TAG, "Motores inicializados (Anonsurf + Crypto + BLE + Storage)")
    }

    override fun onTerminate() {
        super.onTerminate()

        // Limpieza al terminar el proceso
        AnonsurfEngine.destroyInstance()
        CryptoEngine.destroyInstance()
        BleMeshEngine.destroyInstance()

        // Destrucción forense de la base de datos cifrada
        try {
            EncryptedStorage.forensicDestroy(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error en destrucción forense: ${e.message}")
        }

        Log.w(TAG, "=== ZILCH TERMINADO ===")
    }
}
