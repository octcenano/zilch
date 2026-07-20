package com.zilch.crypto.storage

import android.content.Context
import android.util.Log
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.security.SecureRandom

/**
 * EncryptedStorage — Almacenamiento Zero-Knowledge con SQLCipher.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE SEGURIDAD: ALMACENAMIENTO CIFRADO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Todo el almacenamiento pasa por SQLCipher (AES-256-CBC).
 * La passphrase se deriva de la identidad efímera actual:
 *
 *   passphrase = HKDF(identity.seed, "zilch-sqlcipher-v1", 32bytes)
 *
 * Cuando la identidad se destruye (botón de pánico), la passphrase
 * se pierde y TODOS los datos cifrados se vuelven basura inútil.
 *
 * PROPIEDADES:
 * - Cero archivos en directorios públicos de Android
 * - Cero datos en SharedPreferences
 * - La DB se almacena en getFilesDir() (privado a la app)
 * - Al panic: wipe de archivos + wipe de passphrase en RAM
 * - WAL mode para rendimiento
 * - Sin external storage NUNCA
 */
object EncryptedStorage {

    private const val TAG = "EncryptedStorage"
    private const val DB_NAME = "zilch_encrypted.db"
    private const val DB_VERSION = 1

    private var database: SQLiteDatabase? = null
    private var passphraseBytes: ByteArray? = null

    /**
     * Inicializa la base de datos cifrada.
     *
     * @param context Contexto Android
     * @param identitySeed Semilla de la identidad actual (32 bytes)
     *                     Se usa para derivar la passphrase de cifrado.
     */
    fun initialize(context: Context, identitySeed: ByteArray) {
        if (database?.isOpen == true) {
            Log.w(TAG, "DB ya inicializada")
            return
        }

        // Derivar passphrase de la semilla de identidad
        passphraseBytes = derivePassphrase(identitySeed)

        val dbPath = File(context.filesDir, DB_NAME)

        SQLiteDatabase.loadLibs(context)

        // SQLCipher 4.x: openDatabase con passphrase como String
        val passphraseStr = String(passphraseBytes!!, Charsets.UTF_8)
        database = SQLiteDatabase.openDatabase(
            dbPath.absolutePath,
            passphraseStr,
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY
        )

        database?.execSQL("PRAGMA cipher_compatibility = 4")
        database?.execSQL("PRAGMA cipher_page_size = 4096")
        database?.execSQL("PRAGMA journal_mode=WAL")

        createTables()
        Log.i(TAG, "Base de datos cifrada inicializada")
    }

    // ═══ Mensajes cifrados ════════════════════════════════════════

    fun storeMessage(
        messageId: String,
        senderNodeId: String,
        recipientNodeId: String?,
        encryptedPayload: ByteArray,
        timestampMs: Long,
        ttl: Int,
        type: String = "TEXT"
    ) {
        val db = database ?: throw IllegalStateException("DB no inicializada")
        db.execSQL(
            """INSERT OR REPLACE INTO messages
               (message_id, sender_node_id, recipient_node_id,
                encrypted_payload, timestamp_ms, ttl, type)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            arrayOf(
                messageId, senderNodeId, recipientNodeId,
                encryptedPayload, timestampMs, ttl, type
            )
        )
    }

    fun getMessage(messageId: String): ByteArray? {
        val db = database ?: return null
        val cursor = db.rawQuery(
            "SELECT encrypted_payload FROM messages WHERE message_id = ?",
            arrayOf(messageId)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getBlob(0) else null
        }
    }

    fun getPendingMessages(recipientNodeId: String, maxAge: Long): List<PendingMessage> {
        val db = database ?: return emptyList()
        val cutoff = System.currentTimeMillis() - maxAge
        val cursor = db.rawQuery(
            """SELECT message_id, sender_node_id, encrypted_payload,
                      timestamp_ms, ttl, type
               FROM messages
               WHERE (recipient_node_id = ? OR recipient_node_id IS NULL)
                 AND timestamp_ms > ?
                 AND ttl > 0
               ORDER BY timestamp_ms ASC""",
            arrayOf(recipientNodeId, cutoff)
        )
        val messages = mutableListOf<PendingMessage>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(
                    PendingMessage(
                        messageId = it.getString(0),
                        senderNodeId = it.getString(1),
                        encryptedPayload = it.getBlob(2),
                        timestampMs = it.getLong(3),
                        ttl = it.getInt(4),
                        type = it.getString(5)
                    )
                )
            }
        }
        return messages
    }

    fun decrementTtl(messageId: String): Boolean {
        val db = database ?: return false
        db.execSQL(
            "UPDATE messages SET ttl = ttl - 1 WHERE message_id = ?",
            arrayOf(messageId)
        )
        return true
    }

    fun deleteMessage(messageId: String) {
        database?.execSQL(
            "DELETE FROM messages WHERE message_id = ?",
            arrayOf(messageId)
        )
    }

    fun purgeExpired() {
        val db = database ?: return
        val now = System.currentTimeMillis()
        db.execSQL("DELETE FROM messages WHERE ttl <= 0")
        db.execSQL(
            "DELETE FROM messages WHERE timestamp_ms < ?",
            arrayOf(now - 7 * 24 * 3600_000L)
        )
    }

    // ═══ Contactos cifrados ════════════════════════════════════════

    fun storeContact(
        nodeId: String,
        fingerprint: String,
        publicKeyBytes: ByteArray,
        temporaryAddress: String?
    ) {
        val db = database ?: throw IllegalStateException("DB no inicializada")
        db.execSQL(
            """INSERT OR REPLACE INTO contacts
               (node_id, fingerprint, public_key_bytes, temporary_address, verified_at_ms)
               VALUES (?, ?, ?, ?, ?)""",
            arrayOf(nodeId, fingerprint, publicKeyBytes, temporaryAddress, System.currentTimeMillis())
        )
    }

    fun getContact(nodeId: String): StoredContact? {
        val db = database ?: return null
        val cursor = db.rawQuery(
            "SELECT node_id, fingerprint, public_key_bytes, temporary_address FROM contacts WHERE node_id = ?",
            arrayOf(nodeId)
        )
        return cursor.use {
            if (it.moveToFirst()) StoredContact(
                nodeId = it.getString(0),
                fingerprint = it.getString(1),
                publicKeyBytes = it.getBlob(2),
                temporaryAddress = it.getString(3)
            ) else null
        }
    }

    fun getAllContacts(): List<StoredContact> {
        val db = database ?: return emptyList()
        val cursor = db.rawQuery(
            "SELECT node_id, fingerprint, public_key_bytes, temporary_address FROM contacts",
            null
        )
        val contacts = mutableListOf<StoredContact>()
        cursor.use {
            while (it.moveToNext()) {
                contacts.add(
                    StoredContact(
                        nodeId = it.getString(0),
                        fingerprint = it.getString(1),
                        publicKeyBytes = it.getBlob(2),
                        temporaryAddress = it.getString(3)
                    )
                )
            }
        }
        return contacts
    }

    fun deleteContact(nodeId: String) {
        database?.execSQL("DELETE FROM contacts WHERE node_id = ?", arrayOf(nodeId))
    }

    // ═══ Store-and-Forward: mensajes de mulas ══════════════════════

    fun storeRelayMessage(messageId: String, payload: ByteArray, ttl: Int) {
        val db = database ?: return
        db.execSQL(
            """INSERT OR IGNORE INTO relay_queue
               (message_id, payload, ttl, received_at_ms)
               VALUES (?, ?, ?, ?)""",
            arrayOf(messageId, payload, ttl, System.currentTimeMillis())
        )
    }

    fun getRelayMessages(excludeIds: Set<String>): List<RelayEntry> {
        val db = database ?: return emptyList()
        val cursor = db.rawQuery(
            "SELECT message_id, payload, ttl FROM relay_queue WHERE ttl > 0",
            null
        )
        val entries = mutableListOf<RelayEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                if (id !in excludeIds) {
                    entries.add(RelayEntry(id, it.getBlob(1), it.getInt(2)))
                }
            }
        }
        return entries
    }

    fun purgeRelayQueue() {
        database?.execSQL(
            "DELETE FROM relay_queue WHERE received_at_ms < ?",
            arrayOf(System.currentTimeMillis() - 30 * 60_000L)
        )
    }

    // ═══ IDs procesados (deduplicación) ════════════════════════════

    fun markProcessed(messageId: String) {
        database?.execSQL(
            "INSERT OR IGNORE INTO processed_ids (message_id, processed_at) VALUES (?, ?)",
            arrayOf(messageId, System.currentTimeMillis())
        )
    }

    fun isProcessed(messageId: String): Boolean {
        val db = database ?: return false
        val cursor = db.rawQuery(
            "SELECT 1 FROM processed_ids WHERE message_id = ?",
            arrayOf(messageId)
        )
        return cursor.use { it.moveToFirst() }
    }

    fun purgeProcessedIds() {
        database?.execSQL(
            "DELETE FROM processed_ids WHERE processed_at < ?",
            arrayOf(System.currentTimeMillis() - 24 * 3600_000L)
        )
    }

    // ═══ Cifrado de colas de mensajes ══════════════════════════════

    fun getConversation(peerNodeId: String, limit: Int = 100): List<ConversationMessage> {
        val db = database ?: return emptyList()
        val cursor = db.rawQuery(
            """SELECT message_id, sender_node_id, encrypted_payload, timestamp_ms, type
               FROM messages
               WHERE sender_node_id = ? OR recipient_node_id = ?
               ORDER BY timestamp_ms DESC LIMIT ?""",
            arrayOf(peerNodeId, peerNodeId, limit)
        )
        val messages = mutableListOf<ConversationMessage>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(
                    ConversationMessage(
                        messageId = it.getString(0),
                        senderNodeId = it.getString(1),
                        encryptedPayload = it.getBlob(2),
                        timestampMs = it.getLong(3),
                        type = it.getString(4)
                    )
                )
            }
        }
        return messages.reversed()
    }

    // ═══ Mensajes de chat (UI layer) ════════════════════════════════════

    fun storeChatMessage(
        messageId: String,
        peerNodeId: String,
        content: String,
        isFromLocal: Boolean,
        timestampMs: Long,
        status: String = "SENT"
    ) {
        val db = database ?: return
        db.execSQL(
            """INSERT OR REPLACE INTO chat_messages
               (message_id, peer_node_id, content, is_from_local, timestamp_ms, status)
               VALUES (?, ?, ?, ?, ?, ?)""",
            arrayOf(messageId, peerNodeId, content, isFromLocal, timestampMs, status)
        )
    }

    fun getChatMessages(peerNodeId: String, limit: Int = 200): List<ChatMessageRecord> {
        val db = database ?: return emptyList()
        val cursor = db.rawQuery(
            """SELECT message_id, peer_node_id, content, is_from_local, timestamp_ms, status
               FROM chat_messages
               WHERE peer_node_id = ?
               ORDER BY timestamp_ms ASC
               LIMIT ?""",
            arrayOf(peerNodeId, limit)
        )
        val messages = mutableListOf<ChatMessageRecord>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(
                    ChatMessageRecord(
                        messageId = it.getString(0),
                        peerNodeId = it.getString(1),
                        content = it.getString(2),
                        isFromLocal = it.getInt(3) == 1,
                        timestampMs = it.getLong(4),
                        status = it.getString(5)
                    )
                )
            }
        }
        return messages
    }

    fun getAllChatMessages(): Map<String, List<ChatMessageRecord>> {
        val db = database ?: return emptyMap()
        val cursor = db.rawQuery(
            """SELECT message_id, peer_node_id, content, is_from_local, timestamp_ms, status
               FROM chat_messages
               ORDER BY timestamp_ms ASC""",
            null
        )
        val result = mutableMapOf<String, MutableList<ChatMessageRecord>>()
        cursor.use {
            while (it.moveToNext()) {
                val record = ChatMessageRecord(
                    messageId = it.getString(0),
                    peerNodeId = it.getString(1),
                    content = it.getString(2),
                    isFromLocal = it.getInt(3) == 1,
                    timestampMs = it.getLong(4),
                    status = it.getString(5)
                )
                result.getOrPut(record.peerNodeId) { mutableListOf() }.add(record)
            }
        }
        return result
    }

    fun updateMessageStatus(messageId: String, status: String) {
        database?.execSQL(
            "UPDATE chat_messages SET status = ? WHERE message_id = ?",
            arrayOf(status, messageId)
        )
    }

    fun searchMessages(query: String): List<ChatMessageRecord> {
        val db = database ?: return emptyList()
        val cursor = db.rawQuery(
            """SELECT message_id, peer_node_id, content, is_from_local, timestamp_ms, status
               FROM chat_messages
               WHERE content LIKE ?
               ORDER BY timestamp_ms DESC
               LIMIT 50""",
            arrayOf("%$query%")
        )
        val messages = mutableListOf<ChatMessageRecord>()
        cursor.use {
            while (it.moveToNext()) {
                messages.add(
                    ChatMessageRecord(
                        messageId = it.getString(0),
                        peerNodeId = it.getString(1),
                        content = it.getString(2),
                        isFromLocal = it.getInt(3) == 1,
                        timestampMs = it.getLong(4),
                        status = it.getString(5)
                    )
                )
            }
        }
        return messages
    }

    // ═══ Limpieza Forense ══════════════════════════════════════════

    /**
     * Destrucción forense de toda la base de datos.
     * Sobreescribe archivos con bytes aleatorios antes de borrar.
     */
    fun forensicDestroy(context: Context) {
        Log.e(TAG, "DESTRUCCIÓN FORENSE DE BASE DE DATOS")

        try {
            // 1. Cerrar DB
            database?.close()
            database = null

            // 2. Wipe passphrase de memoria
            passphraseBytes?.let { SecureRandom().nextBytes(it) }
            passphraseBytes?.fill(0)
            passphraseBytes = null

            // 3. Sobreescribir archivos de DB
            val dbFile = File(context.filesDir, DB_NAME)
            val dbWal = File(context.filesDir, "$DB_NAME-wal")
            val dbShm = File(context.filesDir, "$DB_NAME-shm")
            val dbJournal = File(context.filesDir, "$DB_NAME-journal")

            listOf(dbFile, dbWal, dbShm, dbJournal).forEach { file ->
                if (file.exists()) {
                    // Sobreescribir con random bytes 3 veces
                    repeat(3) {
                        file.writeBytes(ByteArray(file.length().toInt()).also {
                            SecureRandom().nextBytes(it)
                        })
                    }
                    file.delete()
                }
            }

            Log.e(TAG, "DB destruida forensemente")
        } catch (e: Exception) {
            Log.e(TAG, "Error en destrucción forense: ${e.message}")
        }
    }

    // ═══ Schema ════════════════════════════════════════════════════

    private fun createTables() {
        val db = database ?: return

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                message_id TEXT PRIMARY KEY,
                sender_node_id TEXT NOT NULL,
                recipient_node_id TEXT,
                encrypted_payload BLOB NOT NULL,
                timestamp_ms INTEGER NOT NULL,
                ttl INTEGER NOT NULL DEFAULT 3,
                type TEXT NOT NULL DEFAULT 'TEXT'
            )
        """
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contacts (
                node_id TEXT PRIMARY KEY,
                fingerprint TEXT NOT NULL,
                public_key_bytes BLOB NOT NULL,
                temporary_address TEXT,
                verified_at_ms INTEGER NOT NULL
            )
        """
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS relay_queue (
                message_id TEXT PRIMARY KEY,
                payload BLOB NOT NULL,
                ttl INTEGER NOT NULL,
                received_at_ms INTEGER NOT NULL
            )
        """
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS processed_ids (
                message_id TEXT PRIMARY KEY,
                processed_at INTEGER NOT NULL
            )
        """
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_recipient ON messages(recipient_node_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp_ms)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_relay_ttl ON relay_queue(ttl)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                message_id TEXT PRIMARY KEY,
                peer_node_id TEXT NOT NULL,
                content TEXT NOT NULL,
                is_from_local INTEGER NOT NULL DEFAULT 0,
                timestamp_ms INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'SENT'
            )
        """
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_messages_peer ON chat_messages(peer_node_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_messages_timestamp ON chat_messages(timestamp_ms)")
    }

    // ═══ Derivación de passphrase ══════════════════════════════════

    private fun derivePassphrase(identitySeed: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec("zilch-sqlcipher-v1".toByteArray(), "HmacSHA256"))
        val prk = mac.doFinal(identitySeed)
        mac.init(javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal("sqlcipher-passphrase".toByteArray()).copyOf(32)
    }

    // ═══ Modelos de datos ══════════════════════════════════════════

    data class PendingMessage(
        val messageId: String,
        val senderNodeId: String,
        val encryptedPayload: ByteArray,
        val timestampMs: Long,
        val ttl: Int,
        val type: String
    )

    data class StoredContact(
        val nodeId: String,
        val fingerprint: String,
        val publicKeyBytes: ByteArray,
        val temporaryAddress: String?
    )

    data class RelayEntry(
        val messageId: String,
        val payload: ByteArray,
        val ttl: Int
    )

    data class ConversationMessage(
        val messageId: String,
        val senderNodeId: String,
        val encryptedPayload: ByteArray,
        val timestampMs: Long,
        val type: String
    )

    data class ChatMessageRecord(
        val messageId: String,
        val peerNodeId: String,
        val content: String,
        val isFromLocal: Boolean,
        val timestampMs: Long,
        val status: String = "SENT"  // SENT, DELIVERED, READ
    )
}
