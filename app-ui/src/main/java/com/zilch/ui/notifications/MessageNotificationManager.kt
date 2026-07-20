package com.zilch.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zilch.ui.MainActivity
import com.zilch.ui.R

/**
 * Gestor de notificaciones locales para mensajes BLE.
 *
 * Solo notificaciones locales, sin Firebase ni servicios cloud.
 * Muestra el fingerprint del emisor y una vista previa del mensaje.
 */
class MessageNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "zilch_messages"
        private const val CHANNEL_NAME = "Mensajes"
        private const val GROUP_KEY = "zilch_messages"
        private const val NOTIFICATION_ID_BASE = 10_000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Crea el canal de notificaciones (requerido para Android 8+).
     * Importancia DEFAULT: sonido + vibración pero sin heads-up.
     */
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones de nuevos mensajes BLE"
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Muestra una notificación de mensaje recibido.
     *
     * @param peerFingerprint Fingerprint del emisor (se trunca para el título)
     * @param messagePreview  Vista previa del mensaje (se trunca a 100 caracteres)
     * @param peerNodeId      NodeId del peer, usado para abrir el chat al tocar
     */
    fun showMessageNotification(
        peerFingerprint: String,
        messagePreview: String,
        peerNodeId: String
    ) {
        // Truncar fingerprint a los primeros 16 caracteres para el título
        val titleFingerprint = if (peerFingerprint.length > 16) {
            "${peerFingerprint.take(16)}…"
        } else {
            peerFingerprint
        }

        // Truncar vista previa a 100 caracteres
        val truncatedPreview = if (messagePreview.length > 100) {
            "${messagePreview.take(100)}…"
        } else {
            messagePreview
        }

        // Intent para abrir el chat al tocar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "nearby_chat")
            putExtra("peerNodeId", peerNodeId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            peerNodeId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ID único por peer para evitar solapamiento
        val notificationId = NOTIFICATION_ID_BASE + abs(peerNodeId.hashCode() % 1000)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_zilch_message)
            .setContentTitle(titleFingerprint)
            .setContentText(truncatedPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(truncatedPreview))
            .setGroup(GROUP_KEY)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancela todas las notificaciones de mensajes.
     */
    fun cancelAll() {
        notificationManager.cancelAll()
    }

    private fun abs(value: Int): Int = if (value < 0) -value else value
}
