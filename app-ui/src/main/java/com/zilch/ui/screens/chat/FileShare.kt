package com.zilch.ui.screens.chat

/**
 * SharedFile — Modelo de archivo compartido vía BLE.
 *
 * Los archivos se envían como chunks usando el MessageChunker del ble-mesh.
 */
data class SharedFile(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val filePath: String,
    val isSent: Boolean
) {
    companion object {
        /**
         * Formatea el tamaño del archivo de forma legible.
         */
        fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
                else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
            }
        }
    }
}
