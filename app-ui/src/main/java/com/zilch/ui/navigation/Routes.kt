package com.zilch.ui.navigation

/**
 * Rutas de navegación de la app.
 */
object Routes {
    /** Onboarding — primera vez que abre la app */
    const val ONBOARDING = "onboarding"

    /** Pantalla principal — lista de chats (estilo WhatsApp) */
    const val CHATS = "chats"

    /** Bandeja de correos .onion — Mensajes asíncronos por Tor */
    const val INBOX = "inbox"

    /** Ajustes — fingerprint, Tor, almacenamiento */
    const val SETTINGS = "settings"

    /** Recibir — Muestra el QR del nodo local */
    const val QR_RECEIVE = "qr_receive"

    /** Escanear — Cámara para escanear QR ajeno */
    const val QR_SCAN = "qr_scan"

    /** Chat cercano — Mensajería BLE por malla local */
    const val NEARBY_CHAT = "nearby_chat/{peerNodeId}"
    fun nearbyChat(peerNodeId: String) = "nearby_chat/$peerNodeId"

    /** Contactos verificados */
    const val CONTACTS = "contacts"
}
