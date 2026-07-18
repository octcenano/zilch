package com.zilch.blemesh

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.zilch.blemesh.advertising.BleAdvertiser
import com.zilch.blemesh.config.BleConfig
import com.zilch.blemesh.encryption.MeshEncryptor
import com.zilch.blemesh.exception.BleMeshException
import com.zilch.blemesh.gatt.GattClientManager
import com.zilch.blemesh.gatt.GattServerManager
import com.zilch.blemesh.message.MeshMessage
import com.zilch.blemesh.message.MessageChunker
import com.zilch.blemesh.message.MessageType
import com.zilch.blemesh.mesh.MeshRouter
import com.zilch.blemesh.mesh.PeerNode
import com.zilch.blemesh.scanning.BleScanner
import com.zilch.blemesh.session.NearbyChatSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

/**
 * BleMeshEngine — Fachada principal del módulo BLE Mesh.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  ARQUITECTURA DEL MÓDULO BLE MESH
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Este orquestador integra todos los componentes BLE:
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                       BleMeshEngine                            │
 * │  (Fachada que orquesta scanner, advertiser, GATT y mesh)       │
 * ├──────────┬──────────┬──────────┬──────────┬────────────────────┤
 * │Advertiser│ Scanner  │GATT Srv  │GATT Cln  │ MeshRouter         │
 * │(Anuncia  │(Busca    │(Recibe   │(Envía    │(Enruta mensajes   │
 * │presencia)│ peers)   │conexión) │datos)    │entre nodos)       │
 * ├──────────┴──────────┴──────────┴──────────┴────────────────────┤
 * │  MeshEncryptor (cifra/descifra mensajes)                       │
 * │  MessageChunker (fragmenta para MTU BLE)                       │
 * │  NearbyChatSession (gestiona sesiones de chat cercano)         │
 * └────────────────────────────────────────────────────────────────┘
 *
 * USO:
 * ```kotlin
 * val mesh = BleMeshEngine.getInstance(context)
 *
 * // Iniciar (necesita permisos BLE)
 * mesh.start(lifecycleScope, myNodeId, publicKeyBytes)
 *
 * // Descubrir peers cercanos
 * mesh.startDiscovery()
 *
 * // Enviar mensaje a un peer
 * mesh.sendMessage(peerNodeId, "Hola, estoy cerca!")
 *
 * // Recibir mensajes
 * mesh.setOnMessageReceived { message ->
 *     // message.content = texto descifrado
 * }
 *
 * // Parada de emergencia
 * mesh.emergencyDestroy()
 * ```
 */
class BleMeshEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BleMeshEngine"

        @Volatile
        private var instance: BleMeshEngine? = null

        fun getInstance(context: Context): BleMeshEngine {
            return instance ?: synchronized(this) {
                instance ?: BleMeshEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }

        @Synchronized
        fun destroyInstance() {
            instance?.emergencyDestroy()
            instance = null
        }
    }

    // ── Componentes internos ────────────────────────────────────────

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bleAdvertiser = BleAdvertiser(context)
    private val bleScanner = BleScanner(context)
    private val gattServer = GattServerManager(context)
    private val gattClient = GattClientManager(context)

    // ── Estado ──────────────────────────────────────────────────────

    private var meshRouter: MeshRouter? = null
    private val knownPeers = ConcurrentHashMap<String, PeerNode>()
    private val _sessionMap = ConcurrentHashMap<String, NearbyChatSession>()
    private val sharedKeys = ConcurrentHashMap<String, SecretKey>()

    @Volatile
    private var myNodeId: String = ""

    @Volatile
    private var myPublicKeyBytes: ByteArray = ByteArray(0)

    private var engineScope: CoroutineScope? = null

    /** Estado de discovery */
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    /** Peers descubiertos y conectados */
    private val _peers = MutableStateFlow<Map<String, PeerNode>>(emptyMap())
    val peers: StateFlow<Map<String, PeerNode>> = _peers.asStateFlow()

    /** Sesiones de chat activas */
    private val _activeSessions = MutableStateFlow<List<NearbyChatSession>>(emptyList())
    val activeSessions: StateFlow<List<NearbyChatSession>> = _activeSessions.asStateFlow()

    /** Callbacks */
    private var onPeerDiscovered: ((PeerNode) -> Unit)? = null
    private var onMessageReceived: ((NearbyChatSession, String) -> Unit)? = null
    private var onSessionCreated: ((NearbyChatSession) -> Unit)? = null
    private var onSessionClosed: ((NearbyChatSession) -> Unit)? = null

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA — CICLO DE VIDA
    // ════════════════════════════════════════════════════════════════

    /**
     * Inicia el motor BLE Mesh.
     *
     * @param scope CoroutineScope del componente propietario
     * @param nodeId NodeId del nodo local (SHA-256 de su clave pública)
     * @param publicKeyBytes Clave pública Ed25519 del nodo local
     */
    fun start(scope: CoroutineScope, nodeId: String, publicKeyBytes: ByteArray) {
        myNodeId = nodeId
        myPublicKeyBytes = publicKeyBytes

        engineScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

        // Iniciar router mesh
        meshRouter = MeshRouter(nodeId)
        meshRouter?.start(engineScope!!)

        // Configurar callbacks del GATT server
        setupGattServerCallbacks()

        // Iniciar servidor GATT con información del peer local
        val peerInfo = buildPeerInfoData(nodeId, publicKeyBytes)
        gattServer.start(peerInfo)

        // Configurar callbacks del GATT client
        setupGattClientCallbacks()

        Log.i(TAG, "Motor BLE Mesh iniciado — nodeId: ${nodeId.take(16)}...")
    }

    /**
     * Detiene el motor BLE Mesh.
     */
    fun stop() {
        bleAdvertiser.stopAdvertising()
        bleScanner.stopScanning()
        gattServer.stop()
        gattClient.disconnectAll()
        meshRouter?.stop()

        knownPeers.values.forEach { it.wipe() }
        knownPeers.clear()
        _sessionMap.values.forEach { it.close() }
        _sessionMap.clear()
        sharedKeys.clear()

        _isDiscovering.value = false
        _peers.value = emptyMap()
        _activeSessions.value = emptyList()

        Log.i(TAG, "Motor BLE Mesh detenido")
    }

    /**
     * Parada de emergencia: destruye TODO.
     */
    fun emergencyDestroy() {
        Log.e(TAG, "EMERGENCIA: Destruyendo motor BLE Mesh")
        stop()
        instance = null
    }

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA — DESCUBRIMIENTO
    // ════════════════════════════════════════════════════════════════

    /**
     * Inicia el descubrimiento de peers cercanos.
     * Activa tanto el advertising como el escaneo simultáneamente.
     */
    fun startDiscovery() {
        bleAdvertiser.startAdvertising()
        bleScanner.startScanning { result ->
            handleDiscoveredDevice(result)
        }
        _isDiscovering.value = true
        Log.i(TAG, "Descubrimiento iniciado")
    }

    /**
     * Detiene el descubrimiento.
     */
    fun stopDiscovery() {
        bleAdvertiser.stopAdvertising()
        bleScanner.stopScanning()
        _isDiscovering.value = false
        Log.i(TAG, "Descubrimiento detenido")
    }

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA — MENSAJERÍA
    // ════════════════════════════════════════════════════════════════

    /**
     * Envía un mensaje de texto a un peer.
     *
     * El mensaje se cifra con AES-256-GCM, se fragmenta si es necesario,
     * y se envía a través de la conexión BLE GATT.
     *
     * @param peerNodeId NodeId del destinatario
     * @param text Contenido del mensaje en texto plano
     * @throws BleMeshException si el envío falla
     */
    suspend fun sendMessage(peerNodeId: String, text: String) {
        val peer = knownPeers[peerNodeId]
            ?: throw BleMeshException.UnknownPeer(peerNodeId)

        val session = _sessionMap[peerNodeId]
            ?: throw BleMeshException.SessionNotFound(peerNodeId)

        if (!session.isActive) {
            throw BleMeshException.SessionNotFound("Sesión no activa")
        }

        val encryptionKey = sharedKeys[peerNodeId]
            ?: throw BleMeshException.EncryptionError("Clave de sesión no disponible")

        withContext(Dispatchers.IO) {
            // ═══ 1. Cifrar el mensaje ═══
            val plaintext = text.toByteArray(Charsets.UTF_8)
            val headerBytes = "text".toByteArray() // Header simplificado para AAD

            val encrypted = MeshEncryptor.encrypt(plaintext, encryptionKey, headerBytes)

            // ═══ 2. Serializar ═══
            val serialized = encrypted.toBytes()

            // ═══ 3. Fragmentar para BLE ═══
            val chunks = MessageChunker.chunk(serialized, peer.negotiatedMtu)

            Log.d(TAG, "Mensaje cifrado: ${plaintext.size}B → ${serialized.size}B → ${chunks.size} chunks")

            // ═══ 4. Crear mensaje mesh ═══
            val meshMessage = MeshMessage(
                senderNodeId = myNodeId,
                recipientNodeId = peerNodeId,
                type = MessageType.TEXT,
                totalChunks = chunks.size,
                encryptedPayload = serialized
            )

            // ═══ 5. Enviar cada chunk ═══
            for ((index, chunk) in chunks.withIndex()) {
                val chunkMsg = meshMessage.copy(chunkIndex = index)
                val headerBytes2 = chunkMsg.serializeHeader()

                gattClient.writeCharacteristic(
                    peer.bleAddress,
                    BleConfig.MESSAGE_CHARACTERISTIC_UUID,
                    headerBytes2 + chunk
                )
            }

            // ═══ 6. Registrar en la sesión ═══
            session.addMessage(text, isFromLocal = true)

            Log.i(TAG, "Mensaje enviado a peer: ${peer.fingerprint}")
        }
    }

    /**
     * Añade un peer conocido (tras verificación QR).
     *
     * @param peerNodeId NodeId del peer
     * @param fingerprint Fingerprint del peer
     * @param publicKeyBytes Clave pública del peer
     * @param temporaryAddress Dirección temporal (BLE address o .onion)
     */
    fun addKnownPeer(
        peerNodeId: String,
        fingerprint: String,
        publicKeyBytes: ByteArray,
        temporaryAddress: String
    ) {
        val peer = PeerNode(
            nodeId = peerNodeId,
            fingerprint = fingerprint,
            publicKeyBytes = publicKeyBytes,
            bleAddress = temporaryAddress,
            temporaryAddress = temporaryAddress
        )

        knownPeers[peerNodeId] = peer
        _peers.value = knownPeers.toMap()

        // Derivar clave de cifrado compartida
        val sharedKey = MeshEncryptor.deriveSharedKey(
            myPublicKeyBytes, publicKeyBytes,
            myNodeId, peerNodeId
        )
        sharedKeys[peerNodeId] = sharedKey

        Log.i(TAG, "Peer conocido añadido: $fingerprint")
    }

    // ════════════════════════════════════════════════════════════════
    //  CALLBACKS
    // ════════════════════════════════════════════════════════════════

    fun setOnPeerDiscovered(listener: (PeerNode) -> Unit) {
        onPeerDiscovered = listener
    }

    fun setOnMessageReceived(listener: (NearbyChatSession, String) -> Unit) {
        onMessageReceived = listener
    }

    fun setOnSessionCreated(listener: (NearbyChatSession) -> Unit) {
        onSessionCreated = listener
    }

    fun setOnSessionClosed(listener: (NearbyChatSession) -> Unit) {
        onSessionClosed = listener
    }

    // ════════════════════════════════════════════════════════════════
    //  LÓGICA INTERNA
    // ════════════════════════════════════════════════════════════════

    private fun handleDiscoveredDevice(result: ScanResult) {
        val rssi = result.rssi

        // Crear PeerNode mínimo con la info del escaneo
        val peer = knownPeers.values.find { it.bleAddress == result.device.address }
        if (peer != null) {
            peer.rssi = rssi
            peer.markSeen()
            // Conectar si no estamos conectados
            if (peer.connectionState == PeerNode.ConnectionState.DISCOVERED) {
                peer.connectionState = PeerNode.ConnectionState.CONNECTING
                connectToPeer(peer)
            }
        }
    }

    private fun connectToPeer(peer: PeerNode) {
        engineScope?.launch {
            try {
                if (peer.bleAddress.isBlank()) {
                    Log.w(TAG, "Peer sin dirección BLE conocida")
                    return@launch
                }

                // Conectar a través de GattClientManager
                val adapter = bluetoothManager.adapter ?: run {
                    Log.w(TAG, "Bluetooth adapter no disponible")
                    return@launch
                }
                val device = adapter.getRemoteDevice(peer.bleAddress)

                peer.bluetoothDevice = device
                peer.connectionState = PeerNode.ConnectionState.CONNECTING

                // Establecer peer info local para handshake
                gattClient.setLocalPeerInfo(
                    buildPeerInfoData(myNodeId, myPublicKeyBytes)
                )

                Log.i(TAG, "Iniciando conexion BLE con peer")

                // La conexión real se gestiona a través de GattClientManager callbacks
                // onConnectionStateChanged → PeerNode.ConnectionState.CONNECTED
                // onServicesDiscovered → discovery de servicios
                // onMessageReceived → procesamiento de mensajes

            } catch (e: Exception) {
                peer.connectionState = PeerNode.ConnectionState.DISCONNECTED
                Log.e(TAG, "Error al conectar con peer: ${e.message}")
            }
        }
    }

    private fun setupGattServerCallbacks() {
        gattServer.onDeviceConnected = { device ->
            Log.d(TAG, "Dispositivo conectado como cliente")
        }

        gattServer.onDeviceDisconnected = { device ->
            Log.d(TAG, "Dispositivo cliente desconectado")
        }

        gattServer.onMessageReceived = { device, data ->
            handleIncomingMessage(device, data)
        }

        gattServer.onControlReceived = { device, data ->
            handleControlMessage(device, data)
        }
    }

    private fun setupGattClientCallbacks() {
        gattClient.onMessageReceived = { address, data ->
            handleIncomingDataFromPeer(address, data)
        }

        gattClient.onServicesDiscovered = { address, services ->
            Log.d(TAG, "Servicios descubiertos del peer")
        }
    }

    private fun handleIncomingMessage(device: BluetoothDevice, data: ByteArray) {
        Log.d(TAG, "Mensaje recibido (${data.size} bytes)")
        // El procesamiento completo se hace en handleIncomingDataFromPeer
        handleIncomingDataFromPeer(device.address, data)
    }

    private fun handleIncomingDataFromPeer(address: String, data: ByteArray) {
        engineScope?.launch {
            try {
                // Encontrar el peer por dirección BLE
                val peer = knownPeers.values.find { it.bleAddress == address }
                    ?: return@launch

                // ═══ PASO 1: Parsear el header del mensaje mesh ═══
                if (data.size < 8) {
                    Log.w(TAG, "Mensaje demasiado pequeño para procesar")
                    return@launch
                }
                val meshMessage = MeshMessage.deserialize(data)

                // ═══ PASO 2: Router mesh — deduplicación + TTL + enrutamiento ═══
                val router = meshRouter ?: return@launch
                val processed = router.processReceivedMessage(meshMessage, knownPeers)
                if (!processed) {
                    // El router reenvió el mensaje o lo descartó (duplicado/expirado)
                    return@launch
                }

                // ═══ PASO 3: Descifrar y entregar ═══
                val encryptionKey = sharedKeys[peer.nodeId] ?: return@launch

                val rawPayload = meshMessage.encryptedPayload ?: return@launch
                val encryptedPayload = MeshEncryptor.EncryptedPayload.fromBytes(rawPayload)
                val headerBytes = meshMessage.type.name.toByteArray(Charsets.UTF_8)

                val plaintext = MeshEncryptor.decrypt(encryptedPayload, encryptionKey, headerBytes)
                val text = String(plaintext, Charsets.UTF_8)

                // Entregar a la sesión
                val session = _sessionMap[peer.nodeId]
                if (session != null && session.isActive) {
                    session.addMessage(text, isFromLocal = false)
                    onMessageReceived?.invoke(session, text)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar mensaje entrante: ${e.message}")
            }
        }
    }

    private fun handleControlMessage(device: BluetoothDevice, data: ByteArray) {
        Log.d(TAG, "Mensaje de control recibido")
        // TODO: Implementar handshake y control del mesh
    }

    private fun buildPeerInfoData(nodeId: String, publicKeyBytes: ByteArray): ByteArray {
        val nodeIdBytes = nodeId.toByteArray(Charsets.UTF_8)
        val buffer = java.nio.ByteBuffer.allocate(4 + nodeIdBytes.size + publicKeyBytes.size)
        buffer.putInt(nodeIdBytes.size)
        buffer.put(nodeIdBytes)
        buffer.put(publicKeyBytes)
        return buffer.array()
    }

    private fun createSession(peer: PeerNode): NearbyChatSession {
        val session = NearbyChatSession(peer = peer)
        session.state = NearbyChatSession.SessionState.HANDSHAKING
        _sessionMap[peer.nodeId] = session
        _activeSessions.value = _sessionMap.values.toList()
        onSessionCreated?.invoke(session)
        return session
    }

    private fun closeSession(peerNodeId: String) {
        _sessionMap.remove(peerNodeId)?.let { session ->
            session.close()
            _activeSessions.value = _sessionMap.values.toList()
            onSessionClosed?.invoke(session)
        }
    }
}
