package com.zilch.ui.navigation

/**
 * Rutas de navegación de la app.
 *
 * Cada ruta es un string plano para simplificar el deep linking
 * y la serialización de estado.
 */
object Routes {
    /** Pantalla de inicio — Tor status + botones principales */
    const val HOME = "home"

    /** Recibir — Muestra el QR del nodo local */
    const val QR_RECEIVE = "qr_receive"

    /** Escanear — Cámara para escanear QR ajeno */
    const val QR_SCAN = "qr_scan"

    /** Bandeja de correos .onion — Mensajes asíncronos por Tor */
    const val INBOX = "inbox"

    /** Chat cercano — Mensajería BLE por malla local */
    const val NEARBY_CHAT = "nearby_chat/{peerNodeId}"
    fun nearbyChat(peerNodeId: String) = "nearby_chat/$peerNodeId"

    /** Contactos verificados */
    const val CONTACTS = "contacts"
}
