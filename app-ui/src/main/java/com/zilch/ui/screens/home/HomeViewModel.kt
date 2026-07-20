package com.zilch.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.zilch.blemesh.BleMeshEngine
import com.zilch.blemesh.mesh.PeerNode
import com.zilch.crypto.CryptoEngine
import com.zilch.ui.components.TorStatus
import com.zilch.ui.screens.chat.ChatMessage
import com.zilch.ui.screens.chat.SharedFile
import com.zilch.ui.screens.chatlist.ChatPreview
import com.zilch.ui.screens.groups.GroupInfo
import com.zilch.ui.screens.groups.GroupMessage
import com.zilch.ui.screens.contacts.TrustedPerson
import com.zilch.crypto.storage.EncryptedStorage
import kotlinx.coroutines.delay
import com.zilch.ui.notifications.MessageNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * HomeViewModel — Estado y lógica de la pantalla de inicio.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val cryptoEngine = CryptoEngine.getInstance(application)
    private val bleEngine = BleMeshEngine.getInstance(application)
    private val notificationManager = MessageNotificationManager(application.applicationContext)

    // ═══ Estado observable ═══

    private val _torStatus = MutableStateFlow(TorStatus.CHECKING)
    val torStatus: StateFlow<TorStatus> = _torStatus.asStateFlow()

    private val _fingerprint = MutableStateFlow("")
    val fingerprint: StateFlow<String> = _fingerprint.asStateFlow()

    private val _nodeId = MutableStateFlow("")
    val nodeId: StateFlow<String> = _nodeId.asStateFlow()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    /** Peers descubiertos vía BLE */
    private val _peers = MutableStateFlow<List<PeerNode>>(emptyList())
    val peers: StateFlow<List<PeerNode>> = _peers.asStateFlow()

    /** Mensajes por peer (nodeId -> lista de mensajes) */
    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    /** Lista de conversaciones para la pantalla principal */
    private val _chatPreviews = MutableStateFlow<List<ChatPreview>>(emptyList())
    val chatPreviews: StateFlow<List<ChatPreview>> = _chatPreviews.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _isKillSwitchActive = MutableStateFlow(false)
    val isKillSwitchActive: StateFlow<Boolean> = _isKillSwitchActive.asStateFlow()

    private val _statusMessage = MutableStateFlow("Inicializando...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    /** Personas de confianza */
    private val _trustedContacts = MutableStateFlow<List<com.zilch.ui.screens.contacts.TrustedPerson>>(emptyList())
    val trustedContacts: StateFlow<List<com.zilch.ui.screens.contacts.TrustedPerson>> = _trustedContacts.asStateFlow()

    /** Notificaciones locales habilitadas */
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    /** Grupos de chat */
    private val _groups = MutableStateFlow<List<GroupInfo>>(emptyList())
    val groups: StateFlow<List<GroupInfo>> = _groups.asStateFlow()

    /** Mensajes por grupo (groupId -> lista de mensajes) */
    private val _groupMessages = MutableStateFlow<Map<String, List<GroupMessage>>>(emptyMap())
    val groupMessages: StateFlow<Map<String, List<GroupMessage>>> = _groupMessages.asStateFlow()

    /** Archivos compartidos por peer (nodeId -> lista de archivos) */
    private val _sharedFiles = MutableStateFlow<Map<String, List<SharedFile>>>(emptyMap())
    val sharedFiles: StateFlow<Map<String, List<SharedFile>>> = _sharedFiles.asStateFlow()

    init {
        // Iniciar el motor criptográfico con el scope del ViewModel
        // para que los temporizadores de regeneración de identidad
        // se activen correctamente
        // Crear canal de notificaciones
        notificationManager.createChannel()

        try {
            cryptoEngine.start(viewModelScope)
        } catch (_: Exception) {
            // Motor ya iniciado o en proceso de inicialización
        }
        loadIdentity()
        startBleEngine()

        // Initialize encrypted storage for message persistence
        try {
            val seed = cryptoEngine.identityManager.currentIdentity.publicKeyBytes
            EncryptedStorage.initialize(application, seed)
            loadPersistedMessages()
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error init storage: ${e.message}")
        }
    }

    private fun loadIdentity() {
        viewModelScope.launch {
            // Esperar un frame para que CryptoEngine se inicialice completamente
            delay(500L)
            refreshIdentity()
        }
    }

    /**
     * Actualiza el estado de Tor.
     */
    fun updateTorStatus(status: TorStatus) {
        _torStatus.value = status
    }

    /**
     * Actualiza el fingerprint desde CryptoEngine.
     * Si falla, reintenta después de un breve delay.
     */
    fun refreshIdentity() {
        viewModelScope.launch {
            try {
                _fingerprint.value = cryptoEngine.getCurrentFingerprint()
                _nodeId.value = cryptoEngine.getCurrentNodeId()
            } catch (e: Exception) {
                delay(1_000L)
                try {
                    _fingerprint.value = cryptoEngine.getCurrentFingerprint()
                    _nodeId.value = cryptoEngine.getCurrentNodeId()
                } catch (_: Exception) {
                    _fingerprint.value = "Generando..."
                    _nodeId.value = "Generando..."
                }
            }
        }
    }

    /**
     * Parada de emergencia — destruye TODO.
     *
     * ⚠ IRREVERSIBLE dentro de la sesión actual.
     */
    fun emergencyDestroy() {
        cryptoEngine.emergencyDestroy()
        bleEngine.emergencyDestroy()
        _isKillSwitchActive.value = true
        _torStatus.value = TorStatus.KILL_SWITCH
        _statusMessage.value = "TODOS LOS DATOS DESTRUIDOS"

        try {
            EncryptedStorage.forensicDestroy(getApplication<Application>())
        } catch (_: Exception) {
        }
    }

    // ═══ BLE MESH ═══

    /**
     * Inicia el motor BLE Mesh tras generar la identidad.
     */
    private fun startBleEngine() {
        viewModelScope.launch {
            delay(600L) // Esperar a que CryptoEngine genere la identidad
            try {
                val nodeId = cryptoEngine.getCurrentNodeId()
                val publicKeyBytes = cryptoEngine.identityManager.currentIdentity.publicKeyBytes
                bleEngine.start(viewModelScope, nodeId, publicKeyBytes)

                // Observar peers descubiertos
                launch {
                    bleEngine.peers.collect { peerMap ->
                        _peers.value = peerMap.values.toList()
                        _peerCount.value = peerMap.size
                        rebuildChatPreviews()
                    }
                }

                // Callback: peer descubierto
                bleEngine.setOnPeerDiscovered { peer ->
                    Log.i("HomeViewModel", "Peer descubierto: ${peer.fingerprint}")
                }

                // Callback: mensaje recibido
                bleEngine.setOnMessageReceived { session, text ->
                    val peerNodeId = session.peer.nodeId
                    val timeNow = java.text.SimpleDateFormat(
                        "HH:mm", java.util.Locale.getDefault()
                    ).format(java.util.Date())
                    val msg = ChatMessage(
                        id = "recv_${System.currentTimeMillis()}",
                        content = text,
                        isFromLocal = false,
                        timestamp = timeNow
                    )
                    val current = _messages.value.toMutableMap()
                    val peerMessages = (current[peerNodeId] ?: emptyList()).toMutableList()
                    peerMessages.add(msg)
                    current[peerNodeId] = peerMessages
                    _messages.value = current
                    rebuildChatPreviews()

                    // Mostrar notificación local si está habilitada
                    if (_notificationsEnabled.value) {
                        notificationManager.showMessageNotification(
                            peerFingerprint = session.peer.fingerprint,
                            messagePreview = text,
                            peerNodeId = peerNodeId
                        )
                    }

                    try {
                        EncryptedStorage.storeChatMessage(
                            messageId = msg.id,
                            peerNodeId = peerNodeId,
                            content = text,
                            isFromLocal = false,
                            timestampMs = System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "Error persisting received: ${e.message}")
                    }
                }

                // Iniciar discovery automáticamente
                bleEngine.startDiscovery()
                _statusMessage.value = "BLE activo — escaneando"
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error iniciando BLE: ${e.message}")
                _statusMessage.value = "BLE no disponible"
            }
        }
    }

    /**
     * Inicia el descubrimiento de peers cercanos.
     */
    fun startDiscovery() {
        try {
            bleEngine.startDiscovery()
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error starting discovery: ${e.message}")
        }
    }

    /**
     * Detiene el descubrimiento de peers.
     */
    fun stopDiscovery() {
        try {
            bleEngine.stopDiscovery()
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error stopping discovery: ${e.message}")
        }
    }

    /**
     * Envía un mensaje de texto a un peer vía BLE.
     */
    fun sendMessage(peerNodeId: String, text: String) {
        val timeNow = java.text.SimpleDateFormat(
            "HH:mm", java.util.Locale.getDefault()
        ).format(java.util.Date())
        val msg = ChatMessage(
            id = "local_${System.currentTimeMillis()}",
            content = text,
            isFromLocal = true,
            timestamp = timeNow
        )

        // Añadir al mapa de mensajes localmente
        val current = _messages.value.toMutableMap()
        val peerMessages = (current[peerNodeId] ?: emptyList()).toMutableList()
        peerMessages.add(msg)
        current[peerNodeId] = peerMessages
        _messages.value = current
        rebuildChatPreviews()
        try {
            EncryptedStorage.storeChatMessage(
                messageId = msg.id,
                peerNodeId = peerNodeId,
                content = text,
                isFromLocal = true,
                timestampMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error persisting message: ${e.message}")
        }

        // Enviar vía BLE
        viewModelScope.launch {
            try {
                bleEngine.sendMessage(peerNodeId, text)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error enviando mensaje: ${e.message}")
            }
        }
    }

    /**
     * Procesa un QR escaneado y añade el contacto al BLE engine.
     */
    fun addContactFromQr(qrPayload: String) {
        viewModelScope.launch {
            try {
                val decoded = cryptoEngine.processScannedQr(qrPayload)
                val contact = cryptoEngine.confirmContact(decoded)
                bleEngine.addKnownPeer(
                    peerNodeId = contact.nodeId,
                    fingerprint = contact.fingerprint,
                    publicKeyBytes = contact.publicKeyBytes,
                    temporaryAddress = contact.addresses.firstOrNull() ?: ""
                )
                _peers.value = bleEngine.peers.value.values.toList()
                Log.i("HomeViewModel", "Contacto añadido: ${contact.fingerprint}")

                // Añadir a personas de confianza
                val existing = _trustedContacts.value.find { it.fingerprint == contact.fingerprint }
                if (existing == null) {
                    _trustedContacts.value = _trustedContacts.value + TrustedPerson(
                        fingerprint = contact.fingerprint,
                        nickname = "",
                        isTrusted = false
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error procesando QR: ${e.message}")
            }
        }
    }

    fun updateTrustedContactNickname(fingerprint: String, nickname: String) {
        _trustedContacts.value = _trustedContacts.value.map {
            if (it.fingerprint == fingerprint) it.copy(nickname = nickname) else it
        }
    }

    fun toggleTrustedContact(fingerprint: String, isTrusted: Boolean) {
        _trustedContacts.value = _trustedContacts.value.map {
            if (it.fingerprint == fingerprint) it.copy(isTrusted = isTrusted) else it
        }
    }

    /** Alterna el estado de notificaciones locales */
    fun toggleNotifications() {
        _notificationsEnabled.value = !_notificationsEnabled.value
        if (!_notificationsEnabled.value) {
            notificationManager.cancelAll()
        }
    }

    // ═══ GRUPOS ═══

    /**
     * Crea un nuevo grupo de chat con los miembros seleccionados.
     */
    fun createGroup(name: String, memberNodeIds: List<String>) {
        val groupId = "group_${System.currentTimeMillis()}"
        val group = GroupInfo(
            groupId = groupId,
            name = name,
            members = memberNodeIds,
            createdAtMs = System.currentTimeMillis()
        )
        _groups.value = _groups.value + group
        _groupMessages.value = _groupMessages.value + (groupId to emptyList())
        Log.i("HomeViewModel", "Grupo creado: $name con ${memberNodeIds.size} miembros")
    }

    /**
     * Envía un mensaje de texto a todos los miembros de un grupo vía BLE.
     */
    fun sendGroupMessage(groupId: String, text: String) {
        val group = _groups.value.find { it.groupId == groupId } ?: return
        val msg = GroupMessage(
            id = "gmsg_${System.currentTimeMillis()}",
            groupId = groupId,
            senderNodeId = _nodeId.value,
            senderName = "Yo",
            content = text,
            timestampMs = System.currentTimeMillis(),
            isFromLocal = true
        )

        // Añadir al mapa de mensajes del grupo
        val current = _groupMessages.value.toMutableMap()
        val groupMsgs = (current[groupId] ?: emptyList()).toMutableList()
        groupMsgs.add(msg)
        current[groupId] = groupMsgs
        _groupMessages.value = current

        // Actualizar último mensaje del grupo
        _groups.value = _groups.value.map {
            if (it.groupId == groupId) it.copy(
                lastMessage = text,
                lastMessageTimeMs = System.currentTimeMillis()
            ) else it
        }

        // Enviar vía BLE a cada miembro del grupo
        viewModelScope.launch {
            for (memberNodeId in group.members) {
                try {
                    bleEngine.sendMessage(memberNodeId, "[GRP:${groupId}]$text")
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Error enviando a grupo[$memberNodeId]: ${e.message}")
                }
            }
        }
    }

    // ═══ ARCHIVOS ═══

    /**
     * Registra un archivo enviado a un peer.
     */
    fun registerSentFile(peerNodeId: String, file: SharedFile) {
        val current = _sharedFiles.value.toMutableMap()
        val files = (current[peerNodeId] ?: emptyList()).toMutableList()
        files.add(file)
        current[peerNodeId] = files
        _sharedFiles.value = current
    }

    /**
     * Registra un archivo recibido de un peer.
     */
    fun registerReceivedFile(peerNodeId: String, file: SharedFile) {
        val current = _sharedFiles.value.toMutableMap()
        val files = (current[peerNodeId] ?: emptyList()).toMutableList()
        files.add(file)
        current[peerNodeId] = files
        _sharedFiles.value = current
    }

    /**
     * Envía un archivo a un peer vía BLE como Base64 chunks.
     */
    fun sendFileToPeer(peerNodeId: String, fileName: String, mimeType: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            try {
                val encoded = java.util.Base64.getEncoder().encodeToString(fileBytes)
                bleEngine.sendMessage(peerNodeId, "[FILE:$fileName:$mimeType:${fileBytes.size}]$encoded")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error enviando archivo: ${e.message}")
            }
        }
    }

    private fun loadPersistedMessages() {
        try {
            val allMessages = EncryptedStorage.getAllChatMessages()
            val mapped = allMessages.mapValues { (_, records) ->
                records.map { record ->
                    ChatMessage(
                        id = record.messageId,
                        content = record.content,
                        isFromLocal = record.isFromLocal,
                        timestamp = java.text.SimpleDateFormat(
                            "HH:mm", java.util.Locale.getDefault()
                        ).format(java.util.Date(record.timestampMs)),
                        timestampMs = record.timestampMs
                    )
                }
            }
            _messages.value = mapped
            rebuildChatPreviews()
        } catch (e: Exception) {
            Log.e("HomeViewModel", "Error loading messages: ${e.message}")
        }
    }

    /** Reconstruye la lista de chats para la pantalla principal */
    private fun rebuildChatPreviews() {
        val peerMap = _peers.value.associateBy { it.nodeId }
        val msgMap = _messages.value

        // Conversaciones con mensajes persistidos
        val fromMessages = msgMap.map { (peerNodeId, msgs) ->
            val lastMsg = msgs.lastOrNull()
            val peer = peerMap[peerNodeId]
            ChatPreview(
                contactNodeId = peerNodeId,
                fingerprint = peer?.fingerprint ?: peerNodeId.take(8),
                lastMessage = lastMsg?.content ?: "", lastMessageTimeMs = lastMsg?.timestampMs ?: 0L,
                unreadCount = 0,
                isOnline = peer?.isReachable == true
            )
        }

        // Peers BLE sin mensajes aún
        val peersWithoutMsgs = _peers.value.filter { it.nodeId !in msgMap }
            .map { peer ->
                ChatPreview(
                    contactNodeId = peer.nodeId,
                    fingerprint = peer.fingerprint,
                    lastMessage = "Contacto descubierto", lastMessageTimeMs = peer.lastSeenMs,
                    unreadCount = 0,
                    isOnline = peer.isReachable
                )
            }

        // Fusionar, evitando duplicados, ordenados por timestamp
        val allChats = (fromMessages + peersWithoutMsgs)
            .distinctBy { it.contactNodeId }
            .sortedByDescending { it.lastMessageTimeMs }

        _chatPreviews.value = allChats
    }
}
