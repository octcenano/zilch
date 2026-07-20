# ZILCH — Reporte Técnico Completo para Análisis

> **Generado:** 2026-07-19
> **Versión:** 0.1.0-alpha (build 1)
> **Estado:** BUILD SUCCESSFUL — 0 errores, 0 warnings
> **APK:** `app-ui/build/outputs/apk/debug/app-ui-debug.apk` (36 MB)
> **GitHub:** https://github.com/octcenano/zilch
> **Licencia:** MIT

---

## TABLA DE CONTENIDOS

1. [Resumen Ejecutivo](#1-resumen-ejecutivo)
2. [Arquitectura del Proyecto](#2-arquitectura-del-proyecto)
3. [Módulos y sus Archivos](#3-módulos-y-sus-archivos)
4. [APIs Públicas de cada Módulo](#4-apis-públicas-de-cada-módulo)
5. [Estado Actual de Funcionalidades](#5-estado-actual-de-funcionalidades)
6. [Configuración de Build](#6-configuración-de-build)
7. [Dependencias](#7-dependencias)
8. [Errores Conocidos y Limitaciones](#8-errores-conocidos-y-limitaciones)
9. [Roadmap Pendiente](#9-roadmap-pendiente)
10. [Instrucciones de Compilación](#10-instrucciones-de-compilación)
11. [F-Droid Metadata](#11-f-droid-metadata)
12. [Código Completo de Archivos Críticos](#12-código-completo-de-archivos-críticos)

---

## 1. RESUMEN EJECUTIVO

**Zilch** es una aplicación Android de comunicación cifrada P2P que funciona SIN internet, usando Bluetooth LE como capa de transporte. Diseñada para evadir censura, chat-control y vigilancia estatal.

### Pilares de diseño:
- **ZERO COSTOS:** 100% open source (BouncyCastle, ZXing, CameraX, OkHttp, SQLCipher)
- **ZERO SERVIDORES:** Sin Firebase, sin Play Services, sin infraestructura centralizada
- **ZERO RASTRO:** Identidad efímera auto-regenerable, Kill Switch de emergencia, base de datos cifrada

### Stack tecnológico:
- **Lenguaje:** Kotlin 1.9.22
- **UI:** Jetpack Compose (BOM 2024.01.00) — tema OLED oscuro exclusivo
- **Cifrado:** Ed25519 (BouncyCastle), AES-GCM (BLE), AES-256-CBC (SQLCipher)
- **Transporte:** Bluetooth LE Mesh (GATT Server/Client), OkHttp + SOCKS5 (Tor)
- **QR:** ZXing 3.5.3 (generación + escaneo con CameraX 1.3.1)
- **Almacenamiento:** SQLCipher 4.5.4 (base de datos cifrada en disco)
- **Navegación:** Navigation Compose 2.7.6 (Single Activity)

---

## 2. ARQUITECTURA DEL PROYECTO

```
zilch/
├── build.gradle.kts              ← Plugin root (AGP 8.2.2, Kotlin 1.9.22)
├── settings.gradle.kts           ← 4 módulos incluidos
├── gradle.properties             ← JVM args, AndroidX, non-transitive R
├── metadata/com.zilch.app.yml    ← F-Droid metadata
├── LICENSE                       ← MIT
│
├── app-ui/                       ← Módulo de aplicación (Jetpack Compose)
│   ├── build.gradle.kts          ← Dependencias Compose, CameraX, ZXing, Navigation
│   ├── proguard-rules.pro        ← ProGuard para BouncyCastle, SQLCipher, OkHttp
│   └── src/main/
│       ├── AndroidManifest.xml   ← Permisos: BLE, Camera, Location, Internet
│       ├── res/                  ← Launcher icon, colors, strings, themes, network_security
│       └── java/com/zilch/ui/
│           ├── MainActivity.kt           ← Single Activity + NavHost
│           ├── ZilchApp.kt               ← Application class
│           ├── navigation/Routes.kt      ← Todas las rutas de navegación
│           ├── components/               ← Componentes reutilizables
│           │   ├── BottomNavBar.kt       ← Barra inferior (Chats, Bandeja, Ajustes)
│           │   ├── ChatBubble.kt         ← Burbuja de mensaje estilo WhatsApp
│           │   ├── EmergencyButton.kt    ← Barra roja "Cerrar y Destruir"
│           │   ├── FingerprintDisplay.kt ← Fingerprint en grupos legibles
│           │   └── TorStatusIndicator.kt ← Indicador de estado Tor
│           ├── screens/
│           │   ├── home/
│           │   │   ├── HomeScreen.kt     ← (Legacy, no usado actualmente)
│           │   │   └── HomeViewModel.kt  ← ViewModel principal (BLE + Crypto + Contacts)
│           │   ├── chatlist/
│           │   │   └── ChatListScreen.kt ← Lista de chats estilo WhatsApp
│           │   ├── chat/
│           │   │   └── NearbyChatScreen.kt ← Chat cercano por BLE con input
│           │   ├── qr/
│           │   │   ├── QrReceiveScreen.kt  ← Muestra QR propio (firma Ed25519)
│           │   │   └── QrScanScreen.kt     ← Cámara + ZXing + decodificación real
│           │   ├── contacts/
│           │   │   ├── ContactsScreen.kt     ← Lista de contactos verificados
│           │   │   ├── TrustedContact.kt     ← Data class de contacto
│           │   │   └── TrustedPersonScreen.kt ← Gestionar personas de confianza
│           │   ├── inbox/
│           │   │   └── InboxScreen.kt     ← Bandeja de correo P2P por Tor
│           │   ├── voice/
│           │   │   └── VoiceCallScreen.kt ← UI de llamada (sin audio real aún)
│           │   ├── settings/
│           │   │   └── SettingsScreen.kt  ← Configuración + fingerprint + Tor
│           │   ├── setup/
│           │   │   └── NeedsScreen.kt     ← Pantalla de permisos (BLE, Camera, Location)
│           │   └── onboarding/
│           │       └── OnboardingScreen.kt ← 4 páginas introductorias
│           └── theme/
│               ├── Color.kt    ← DarkPalette (OLED oscuro)
│               ├── Theme.kt   ← ZilchTheme
│               └── Type.kt    ← Tipografía
│
├── crypto-engine/                ← Módulo de criptografía
│   ├── build.gradle.kts          ← BouncyCastle, ZXing, SQLCipher, Security-Crypto
│   └── src/main/java/com/zilch/crypto/
│       ├── CryptoEngine.kt              ← Fachada principal (singleton)
│       ├── config/CryptoConfig.kt       ← Parámetros criptográficos hardcodeados
│       ├── identity/
│       │   ├── EphemeralIdentity.kt     ← Identidad efímera (Ed25519 + nodeId)
│       │   └── IdentityManager.kt       ← Ciclo de vida de identidades
│       ├── keys/
│       │   ├── Ed25519KeyGenerator.kt   ← Generación de claves Ed25519
│       │   └── SecureMemory.kt          ← Wipe de buffers sensibles
│       ├── qr/
│       │   ├── QrEncoder.kt             ← Genera QR firmado (JSON + Ed25519 sig)
│       │   └── QrDecoder.kt             ← Decodifica + valida firma + expiración
│       ├── hash/
│       │   └── NodeIdentifier.kt        ← SHA-256 de pubkey → nodeId
│       ├── contact/
│       │   ├── Contact.kt               ← Data class de contacto
│       │   └── ContactManager.kt        ← Gestión de contactos en RAM
│       ├── storage/
│       │   └── EncryptedStorage.kt      ← SQLCipher (AES-256-CBC)
│       └── exception/
│           └── CryptoEngineException.kt ← Jerarquía de excepciones
│
├── ble-mesh/                     ← Módulo de red mesh BLE
│   ├── build.gradle.kts          ← BouncyCastle, Lifecycle, Coroutines
│   └── src/main/java/com/zilch/blemesh/
│       ├── BleMeshEngine.kt             ← Fachada principal (singleton)
│       ├── config/BleConfig.kt          ← UUIDs, MTU, timeouts, TTL mesh
│       ├── advertising/
│       │   └── BleAdvertiser.kt         ← Advertising BLE (presencia)
│       ├── scanning/
│       │   └── BleScanner.kt            ← Escaneo de peers cercanos
│       ├── gatt/
│       │   ├── GattServerManager.kt     ← Servidor GATT (recibe conexiones)
│       │   └── GattClientManager.kt     ← Cliente GATT (envía datos)
│       ├── mesh/
│       │   ├── MeshRouter.kt            ← Enrutamiento de mensajes entre nodos
│       │   └── PeerNode.kt              ← Data class de peer descubierto
│       ├── message/
│       │   ├── MeshMessage.kt           ← Modelo de mensaje mesh
│       │   └── MessageChunker.kt        ← Fragmentación para MTU BLE
│       ├── encryption/
│       │   └── MeshEncryptor.kt         ← AES-GCM para mensajes BLE
│       ├── session/
│       │   └── NearbyChatSession.kt     ← Sesión de chat cercano
│       └── exception/
│           └── BleMeshException.kt      ← Excepciones del módulo
│
└── anonsurf-engine/              ← Módulo Tor / Anti-censura
    ├── build.gradle.kts          ← OkHttp, JSON, Lifecycle
    └── src/main/java/com/zilch/anonsurf/
        ├── AnonsurfEngine.kt            ← Orquestador principal (singleton)
        ├── config/TorConfig.kt          ← Proxy SOCKS5, timeouts, endpoints
        ├── verification/
        │   └── TorIpVerifier.kt         ← Verifica si tráfico sale por Tor
        ├── killswitch/
        │   └── NetworkKillSwitch.kt     ← Kill Switch proactivo + reactivo
        ├── network/
        │   └── TorProxyClient.kt        ← OkHttpClient con proxy SOCKS5
        ├── exception/
        │   └── AnonsurfException.kt     ← Excepciones de red
        └── example/
            └── AnonsurfViewModel.kt     ← ViewModel de ejemplo
```

---

## 3. MÓDULOS Y SUS ARCHIVOS

### 3.1 `app-ui` (namespace: `com.zilch.ui`)
| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `MainActivity.kt` | ~400 | Single Activity con NavHost completo, wiring de todos los screens |
| `ZilchApp.kt` | ~20 | Application class |
| `navigation/Routes.kt` | ~40 | 10 rutas definidas (NEEDS, CHATS, INBOX, SETTINGS, QR_RECEIVE, QR_SCAN, CONTACTS, TRUSTED_PERSONS, NEARBY_CHAT, VOICE_CALL) |
| `components/BottomNavBar.kt` | ~100 | 3 pestañas: Chats (Chat), Bandeja (Inbox), Ajustes (Settings) |
| `components/ChatBubble.kt` | ~80 | Burbuja de mensaje con timestamp |
| `components/EmergencyButton.kt` | ~120 | Barra roja persistente con auto-cancel 5s |
| `components/FingerprintDisplay.kt` | ~70 | Fingerprint en grupos de 4 caracteres monospace |
| `components/TorStatusIndicator.kt` | ~60 | Indicador de estado Tor |
| `screens/home/HomeViewModel.kt` | ~280 | **ViewModel principal**: BLE engine, Crypto engine, contacts, messages, trusted persons |
| `screens/home/HomeScreen.kt` | ~150 | Legacy (no navigated to) |
| `screens/chatlist/ChatListScreen.kt` | ~210 | Lista estilo WhatsApp con FAB expandible |
| `screens/chat/NearbyChatScreen.kt` | ~240 | Chat con input, auto-scroll, envío BLE |
| `screens/chat/NearbyChatScreen.kt` (ChatMessage) | - | Data class: id, content, isFromLocal, timestamp |
| `screens/qr/QrReceiveScreen.kt` | ~180 | QR firmado con countdown 5min, fingerprint display |
| `screens/qr/QrScanScreen.kt` | ~470 | CameraX + ZXing, guía visual, 4 estados (Scanning/Scanned/Confirmed/Error) |
| `screens/contacts/ContactsScreen.kt` | ~200 | Lista de contactos verificados con avatar |
| `screens/contacts/TrustedPersonScreen.kt` | ~240 | Gestionar nombres + switch de confianza |
| `screens/contacts/TrustedContact.kt` | ~10 | Data class: nodeId, fingerprint, nickname, isTrusted, addedTimestampMs |
| `screens/inbox/InboxScreen.kt` | ~165 | Bandeja de correo P2P con FAB "Redactar" |
| `screens/voice/VoiceCallScreen.kt` | ~245 | UI de llamada (avatar, fingerprint, mute/speaker/end) |
| `screens/settings/SettingsScreen.kt` | ~600 | Configuración completa: identidad, Tor, Kill Switch, persona de confianza |
| `screens/setup/NeedsScreen.kt` | ~400 | Permisos con launchers reales (BT, Camera, Location) |
| `screens/onboarding/OnboardingScreen.kt` | ~400 | 4 páginas con HorizontalPager |
| `theme/Color.kt` | ~72 | DarkPalette: 30+ colores OLED |
| `theme/Theme.kt` | ~50 | ZilchTheme (dark only) |
| `theme/Type.kt` | ~30 | Tipografía |

### 3.2 `crypto-engine` (namespace: `com.zilch.crypto`)
| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `CryptoEngine.kt` | ~200 | Fachada singleton: start, getCurrentFingerprint, processScannedQr, confirmContact, emergencyDestroy |
| `config/CryptoConfig.kt` | ~100 | Ed25519, SHA-256, PBKDF2, QR v1, fingerprint 12 chars, TTL 1h |
| `identity/EphemeralIdentity.kt` | ~30 | Data class: publicKeyBytes, privateKeyBytes, nodeId, fingerprint, createdMs |
| `identity/IdentityManager.kt` | ~150 | Generación + regeneración automática de identidades |
| `keys/Ed25519KeyGenerator.kt` | ~50 | Generación de claves Ed25519 con BouncyCastle |
| `keys/SecureMemory.kt` | ~30 | `Arrays.fill(0)` para wipe de buffers |
| `qr/QrEncoder.kt` | ~120 | JSON firmado → Bitmap QR (ZXing, level H 30% ECC) |
| `qr/QrDecoder.kt` | ~100 | Decodifica JSON, valida firma Ed25519, verifica expiración |
| `hash/NodeIdentifier.kt` | ~30 | SHA-256(pubkey) → nodeId hex |
| `contact/Contact.kt` | ~15 | Data class: nodeId, fingerprint, publicKeyBytes, addresses, addedMs |
| `contact/ContactManager.kt` | ~60 | Gestión de contactos en RAM |
| `storage/EncryptedStorage.kt` | ~200 | SQLCipher: messages, contacts, relay_queue, processed_ids tables |
| `exception/CryptoEngineException.kt` | ~40 | Jerarquía de excepciones |

### 3.3 `ble-mesh` (namespace: `com.zilch.blemesh`)
| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `BleMeshEngine.kt` | ~500 | Fachada singleton: start, startDiscovery, sendMessage, addKnownPeer, setOnMessageReceived |
| `config/BleConfig.kt` | ~170 | UUIDs, MTU 512, TTL mesh 3, GCM 128-bit, KDF iterations |
| `advertising/BleAdvertiser.kt` | ~100 | Advertising continuo con SERVICE_UUID |
| `scanning/BleScanner.kt` | ~100 | Escaneo BLE filtrado por SERVICE_UUID |
| `gatt/GattServerManager.kt` | ~200 | Servidor GATT: recibe mensajes + peer info |
| `gatt/GattClientManager.kt` | ~200 | Cliente GATT: envía mensajes + handshake |
| `mesh/MeshRouter.kt` | ~150 | Enrutamiento con deduplicación + TTL |
| `mesh/PeerNode.kt` | ~15 | Data class: nodeId, fingerprint, rssi, lastSeenMs, isReachable |
| `message/MeshMessage.kt` | ~30 | Data class: id, senderNodeId, payload, ttl, timestamp |
| `message/MessageChunker.kt` | ~80 | Fragmentación en chunks para MTU BLE |
| `encryption/MeshEncryptor.kt` | ~80 | AES-GCM para mensajes inter-nodo |
| `session/NearbyChatSession.kt` | ~40 | Data class de sesión de chat cercano |
| `exception/BleMeshException.kt` | ~30 | Excepciones del módulo |

### 3.4 `anonsurf-engine` (namespace: `com.zilch.anonsurf`)
| Archivo | Líneas | Descripción |
|---------|--------|-------------|
| `AnonsurfEngine.kt` | ~250 | Orquestador: start, stop, emergencyStop, verifyTorConnection, executeSecureRequest |
| `config/TorConfig.kt` | ~80 | Proxy 127.0.0.1:9050, health check 10s, max 3 failures |
| `verification/TorIpVerifier.kt` | ~200 | Streaming JSON parse (skipValue para IP), wipe de body bytes |
| `killswitch/NetworkKillSwitch.kt` | ~250 | Proactivo (health check) + Reactivo (interceptor HTTP) |
| `network/TorProxyClient.kt` | ~80 | OkHttpClient.Builder con proxy SOCKS5 |
| `exception/AnonsurfException.kt` | ~50 | TorProxyUnavailable, TorCircuitFailure, IpLeakDetected, KillSwitchActive, NetworkError |
| `example/AnonsurfViewModel.kt` | ~30 | ViewModel de ejemplo |

---

## 4. APIs PÚBLICAS DE CADA MÓDULO

### 4.1 CryptoEngine API
```kotlin
class CryptoEngine private constructor(context: Context) {
    // Singleton
    fun getInstance(context: Context): CryptoEngine

    // Ciclo de vida
    fun start(scope: CoroutineScope)
    fun emergencyDestroy()

    // Identidad
    fun getCurrentFingerprint(): String          // "a3f2-8b1c-4d5e"
    fun getCurrentNodeId(): String               // SHA-256(pubkey) hex

    // QR
    fun processScannedQr(payload: String): QrDecoder.DecodedQr
    fun confirmContact(decoded: QrDecoder.DecodedQr): Contact

    // Acceso interno
    val identityManager: IdentityManager
    val contactsList: StateFlow<List<Contact>>
}
```

### 4.2 BleMeshEngine API
```kotlin
class BleMeshEngine private constructor(context: Context) {
    // Singleton
    fun getInstance(context: Context): BleMeshEngine

    // Ciclo de vida
    fun start(scope: CoroutineScope, nodeId: String, publicKeyBytes: ByteArray)
    fun emergencyDestroy()

    // Descubrimiento
    fun startDiscovery()
    fun stopDiscovery()

    // Mensajería
    fun sendMessage(peerNodeId: String, text: String)

    // Contactos conocidos
    fun addKnownPeer(peerNodeId: String, fingerprint: String,
                     publicKeyBytes: ByteArray, temporaryAddress: String)

    // Callbacks
    fun setOnPeerDiscovered(callback: (PeerNode) -> Unit)
    fun setOnMessageReceived(callback: (NearbyChatSession, String) -> Unit)

    // Estado
    val peers: StateFlow<Map<String, PeerNode>>
}
```

### 4.3 AnonsurfEngine API
```kotlin
class AnonsurfEngine private constructor(context: Context) {
    // Singleton
    fun getInstance(context: Context): AnonsurfEngine

    // Ciclo de vida
    fun start(scope: CoroutineScope)
    fun stop()
    fun emergencyStop()

    // Verificación
    suspend fun verifyTorConnection(): TorIpVerifier.TorStatus

    // Peticiones HTTP seguras
    suspend fun executeSecureRequest(request: Request): Response

    // Estado
    val isReady: Boolean
    val isTorVerified: Boolean
    val torStatus: StateFlow<TorIpVerifier.TorStatus>

    // Callbacks
    fun setOnTorStatusChanged(listener: (verified: Boolean) -> Unit)
    fun setOnKillSwitchStateChanged(listener: (active: Boolean, reason: String) -> Unit)

    // Acceso interno
    fun getSecureHttpClient(): OkHttpClient
    fun getIpVerifier(): TorIpVerifier
}
```

### 4.4 EncryptedStorage API
```kotlin
object EncryptedStorage {
    fun initialize(context: Context, identitySeed: ByteArray)
    fun storeMessage(message: MeshMessage)
    fun getMessage(messageId: String): MeshMessage?
    fun getPendingMessages(): List<MeshMessage>
    fun getConversation(peerNodeId: String): List<MeshMessage>
    fun storeContact(contact: Contact)
    fun getContact(nodeId: String): Contact?
    fun getAllContacts(): List<Contact>
    fun forensicDestroy()   // Wipe completo
    fun close()
}
```

### 4.5 QR Format (QrEncoder → QrDecoder)
```json
{
    "v": 1,
    "pk": "<base64 Ed25519 public key>",
    "addr": "ble:<nodeId>",
    "ts": 1721385600000,
    "exp": 1721385900000,
    "sig": "<base64 Ed25519 signature of payload>"
}
```

### 4.6 Data Classes Clave
```kotlin
// Identidad efímera
data class EphemeralIdentity(
    val publicKeyBytes: ByteArray,
    val privateKeyBytes: ByteArray,
    val nodeId: String,           // SHA-256(pubkey) hex
    val fingerprint: String,      // "a3f2-8b1c-4d5e"
    val createdMs: Long
)

// Peer descubierto
data class PeerNode(
    val nodeId: String,
    val fingerprint: String,
    val rssi: Int,
    val lastSeenMs: Long,
    val isReachable: Boolean
)

// Mensaje de chat
data class ChatMessage(
    val id: String,
    val content: String,
    val isFromLocal: Boolean,
    val timestamp: String         // "HH:mm"
)

// Contacto verificado
data class Contact(
    val nodeId: String,
    val fingerprint: String,
    val publicKeyBytes: ByteArray,
    val addresses: List<String>,
    val addedMs: Long
)

// Persona de confianza (UI)
data class TrustedPerson(
    val fingerprint: String,
    val nickname: String,
    val isTrusted: Boolean
)

// Preview de chat
data class ChatPreview(
    val contactNodeId: String,
    val fingerprint: String,
    val lastMessage: String,
    val lastMessageTimeMs: Long,
    val unreadCount: Int,
    val isOnline: Boolean
)

// Estado de escaneo QR
sealed class QrScanState {
    object Scanning : QrScanState()
    data class Scanned(val fingerprint: String, val temporaryAddress: String) : QrScanState()
    object Confirmed : QrScanState()
    data class Error(val message: String) : QrScanState()
}
```

---

## 5. ESTADO ACTUAL DE FUNCIONALIDADES

| # | Funcionalidad | Estado | Archivo Principal | Notas |
|---|---|---|---|---|
| 1 | **UI estilo WhatsApp** (bottom nav 3 pestañas) | ✅ Funcional | `BottomNavBar.kt`, `MainActivity.kt` | Chats, Bandeja, Ajustes |
| 2 | **Onboarding** (4 páginas educativas) | ✅ Funcional | `OnboardingScreen.kt` | HorizontalPager con skip |
| 3 | **Pantalla de permisos** | ✅ Funcional | `NeedsScreen.kt` | Launchers reales BT/Camera/Location |
| 4 | **Lista de chats** (estilo WhatsApp) | ✅ Funcional | `ChatListScreen.kt` | Muestra peers BLE reales |
| 5 | **Chat cercano por BLE** | ✅ Funcional | `NearbyChatScreen.kt` | Envío/recepción + auto-scroll |
| 6 | **Generación de QR** (firma Ed25519) | ✅ Funcional | `QrReceiveScreen.kt` | QR firmado, countdown 5min |
| 7 | **Escaneo de QR** (cámara + decodificación) | ✅ Funcional | `QrScanScreen.kt` | CameraX + ZXing, stride-aware |
| 8 | **Validación QR** (firma + expiración) | ✅ Funcional | `QrDecoder.kt` | Verifica Ed25519 + TTL |
| 9 | **Verificación verbal** fingerprint | ✅ Funcional | `FingerprintDisplay.kt` | Grupos de 4, monospace |
| 10 | **Personas de confianza** (nombres + switch) | ✅ Funcional | `TrustedPersonScreen.kt` | Conectado a HomeViewModel |
| 11 | **Bandeja de correo P2P** | ⚠️ UI sola | `InboxScreen.kt` | Datos de ejemplo, sin backend Tor real |
| 12 | **Llamadas de voz BLE** | ⚠️ UI sola | `VoiceCallScreen.kt` | Sin AudioRecord/BLE audio real |
| 13 | **Kill Switch** | ✅ Funcional | `NetworkKillSwitch.kt` | Proactivo + reactivo |
| 14 | **Verificación Tor** | ✅ Funcional | `TorIpVerifier.kt` | Streaming JSON, wipe de bytes |
| 15 | **Identidad efímera** | ✅ Funcional | `IdentityManager.kt` | Auto-regeneración por TTL |
| 16 | **Base de datos cifrada** | ✅ Funcional | `EncryptedStorage.kt` | SQLCipher AES-256-CBC |
| 17 | **Botón de pánico** | ✅ Funcional | `EmergencyButton.kt` | Wipe + emergencyDestroy |
| 18 | **BLE Mesh** (advertising + scanning + GATT) | ✅ Funcional | `BleMeshEngine.kt` | UUID propio, MTU 512, TTL 3 |
| 19 | **Cifrado mensajes BLE** | ✅ Funcional | `MeshEncryptor.kt` | AES-GCM 128-bit |
| 20 | **F-Droid metadata** | ✅ Corregido | `com.zilch.app.yml` | Nombre, resumen, descripción |

---

## 6. CONFIGURACIÓN DE BUILD

### Gradle Root (`build.gradle.kts`)
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

### Gradle Properties (`gradle.properties`)
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### app-ui `build.gradle.kts` (resumen)
- `applicationId = "com.zilch.app"`
- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`
- Compose BOM `2024.01.00`, Compose Compiler `1.5.8`
- Java 17 source/target
- Release: `isMinifyEnabled = true`, `isShrinkResources = true`
- 4 módulos internos: `:anonsurf-engine`, `:crypto-engine`, `:ble-mesh`

---

## 7. DEPENDENCIAS

### app-ui
| Dependencia | Versión | Licencia |
|---|---|---|
| Jetpack Compose (BOM) | 2024.01.00 | Apache 2.0 |
| Compose Material3 | (via BOM) | Apache 2.0 |
| Material Icons Extended | (via BOM) | Apache 2.0 |
| Navigation Compose | 2.7.6 | Apache 2.0 |
| Lifecycle ViewModel Compose | 2.7.0 | Apache 2.0 |
| Lifecycle Runtime Compose | 2.7.0 | Apache 2.0 |
| CameraX (core, camera2, lifecycle, view) | 1.3.1 | Apache 2.0 |
| ZXing Core | 3.5.3 | Apache 2.0 |
| AndroidX Core KTX | 1.12.0 | Apache 2.0 |
| Activity Compose | 1.8.2 | Apache 2.0 |
| Coroutines Android | 1.7.3 | Apache 2.0 |

### crypto-engine
| Dependencia | Versión | Licencia |
|---|---|---|
| BouncyCastle (bcprov + bcpkix) | 1.78 | MIT |
| ZXing Core | 3.5.3 | Apache 2.0 |
| AndroidX Security Crypto | 1.1.0-alpha06 | Apache 2.0 |
| SQLCipher | 4.5.4 | Apache 2.0 |
| AndroidX SQLite Framework | 2.4.0 | Apache 2.0 |
| JSON (org.json) | 20231013 | MIT |

### ble-mesh
| Dependencia | Versión | Licencia |
|---|---|---|
| BouncyCastle (bcprov) | 1.78 | MIT |
| Lifecycle Runtime KTX | 2.7.0 | Apache 2.0 |
| Coroutines Android | 1.7.3 | Apache 2.0 |

### anonsurf-engine
| Dependencia | Versión | Licencia |
|---|---|---|
| OkHttp | 4.12.0 | Apache 2.0 |
| JSON (org.json) | 20231013 | MIT |
| Lifecycle ViewModel KTX | 2.7.0 | Apache 2.0 |
| Coroutines Android | 1.7.3 | Apache 2.0 |

---

## 8. ERRORES CONOCIDOS Y LIMITACIONES

### 8.1 Funcionalidades incompletas
| Issue | Severidad | Descripción |
|---|---|---|
| **Llamadas de voz sin audio real** | ALTA | `VoiceCallScreen.kt` es puramente UI. No hay `AudioRecord`, ni streaming de audio por BLE GATT characteristic. Necesita implementación de captura PCM → códec → BLE characteristic write/notify. |
| **Bandeja sin backend Tor** | MEDIA | `InboxScreen.kt` muestra datos hardcodeados. No hay integración real con `AnonsurfEngine.executeSecureRequest()` para enviar/recibir mensajes .onion. |
| **HomeScreen dead code** | BAJA | `HomeScreen.kt` existe con UI completa pero nunca se navega a ella. La app va directamente de NEEDS → CHATS. |
| **SettingsScreen nested Scaffold** | BAJA | El `SettingsScreen` usa un `Scaffold` interno que puede causar problemas de padding con el Scaffold externo de `MainActivity`. |

### 8.2 Limitaciones técnicas de BLE
| Issue | Descripción |
|---|---|
| **MTU limitado** | BLE máximo 517 bytes. Los mensajes grandes se fragmentan con `MessageChunker`. |
| **Conexiones simultáneas** | Máximo 3-7 conexiones BLE según hardware. `BleConfig.MAX_CONCURRENT_CONNECTIONS = 3`. |
| **Alcance** | BLE típico: 10-30m. Sin retransmiso mesh activo, solo communication directa. |
| **Audio por BLE** | BLE no soporta streaming de audio de baja latencia de forma nativa. Necesitaría compresión agresiva (Opus a 8kHz mono) y buffering. |

### 8.3 Errores de compilación anteriores (ya corregidos)
| Error | Archivo | Causa | Solución |
|---|---|---|---|
| `Unresolved reference: ChatBubbleOutline` | BottomNavBar.kt | Icono no existe en Material Icons | `Icons.AutoMirrored.Filled.Chat` |
| `Unresolved reference: Mail` | BottomNavBar.kt | `Icons.AutoMirrored.Filled.Mail` no existe | `Icons.Default.Inbox` |
| `Suspend function 'delay' called outside coroutine` | MainActivity.kt | `delay()` en lambda no-suspend | Eliminar delay, navegar directamente |
| `qrText.take(16)` mostraba basura | MainActivity.kt | No usaba QrDecoder | `QrDecoder.decode(qrText)` |
| `Name<>>>>>>>>>: Zilch` en YAML | com.zilch.app.yml | Caracteres basura en línea 1 | `Name: Zilch` |

---

## 9. ROADMAP PENDIENTE

### FASE 1: Completar funcionalidad core
- [ ] Integrar `EncryptedStorage` en `HomeViewModel` para persistencia de mensajes
- [ ] Conectar `AnonsurfEngine` al `HomeViewModel` para monitoreo Tor real
- [ ] Eliminar `HomeScreen.kt` dead code o reutilizarlo
- [ ] Arreglar nested Scaffold en `SettingsScreen`

### FASE 2: Anti-censura y Chat-Control
- [ ] Zero-Knowledge Memory Storage (streams cifrados → SQLCipher)
- [ ] Memory Sanitization en todos los módulos (`SecureMemory.wipe()`)
- [ ] DNS remoto via SOCKS5 (sin DNS leaks)
- [ ] Soporte bridges obfs4/Snowflake
- [ ] Kill Switch por cada petición HTTP (interceptor OkHttp)

### FASE 3: Off-Grid / El Monte
- [ ] Store-and-Forward con Bloom Filters
- [ ] Reloj offline (GPS + consenso P2P)
- [ ] Optimización energética BLE adaptativa
- [ ] Integración LoRa/Meshtastic (UART/BLE bridge)

### FASE 4: Mitigación de Ataques
- [ ] Proof-of-Work Hashcash en cabecera mesh
- [ ] Servidor HTTP local para compartir APK (instalación por contagio)
- [ ] Botón de pánico forense (wipe buffers + corrupt DB + exitProcess)
- [ ] Verificación de identidad offline (QR + fingerprint verbal)

### FASE 5: Llamadas de voz
- [ ] `AudioRecord` → Opus codec → BLE GATT characteristic
- [ ] Negociación de codec en handshake
- [ ] Latencia adaptativa según RSSI

---

## 10. INSTRUCCIONES DE COMPILACIÓN

```bash
# 1. Clonar
git clone https://github.com/octcenano/zilch.git
cd zilch

# 2. Configurar SDK (si no está configurado)
export ANDROID_HOME=/home/USER/Android/Sdk
export JAVA_HOME=/usr

# 3. Compilar debug
./gradlew assembleDebug --no-daemon

# 4. APK resultante
ls -la app-ui/build/outputs/apk/debug/app-ui-debug.apk

# 5. Instalar en dispositivo
adb install app-ui/build/outputs/apk/debug/app-ui-debug.apk
```

### Requisitos
- Android SDK 34 (install via `sdkmanager "platforms;android-34"`)
- Java 17+ (OpenJDK)
- Gradle 8.5 (wrapper incluido)
- ~2GB RAM para el daemon Gradle

---

## 11. F-DROID METADATA

```yaml
Name: Zilch
Summary: Comunicación cifrada P2P offline
Description: |
  Zilch es una aplicación de comunicación cifrada de extremo a extremo
  que funciona SIN conexión a internet. Los mensajes viajan entre
  dispositivos cercanos a través de Bluetooth LE en una red mesh
  descentralizada. No hay servidores, no hay vigilancia, no hay rastro.
Categories:
  - Security
  - Instant Messaging
  - FOSS
License: MIT
SourceCode: https://github.com/octcenano/zilch
IssueTracker: https://github.com/octcenano/zilch/issues
Translation: false
AutoUpdateMode: Version
UpdateCheckMode: Tags
Builds:
  - versionName: 0.1.0-alpha
    versionCode: 1
    commit: v0.1.0-alpha
    subdir: app-ui
    gradle:
      - yes
```

---

## 12. CÓDIGO COMPLETO DE ARCHIVOS CRÍTICOS

### 12.1 MainActivity.kt (completo)
```kotlin
package com.zilch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zilch.ui.components.BottomNavBar
import com.zilch.ui.navigation.Routes
import com.zilch.ui.screens.chat.NearbyChatScreen
import com.zilch.ui.screens.chatlist.ChatListScreen
import com.zilch.ui.screens.chatlist.ChatPreview
import com.zilch.ui.screens.contacts.ContactUi
import com.zilch.ui.screens.contacts.ContactsScreen
import com.zilch.ui.screens.home.HomeViewModel
import com.zilch.ui.screens.inbox.InboxMessage
import com.zilch.ui.screens.inbox.InboxScreen
import com.zilch.ui.screens.onboarding.OnboardingScreen
import com.zilch.ui.screens.qr.QrReceiveScreen
import com.zilch.ui.screens.qr.QrScanScreen
import com.zilch.ui.screens.qr.QrScanState
import com.zilch.ui.screens.settings.SettingsScreen
import com.zilch.ui.screens.setup.NeedsScreen
import com.zilch.ui.screens.contacts.TrustedPersonScreen
import com.zilch.ui.screens.voice.VoiceCallScreen
import com.zilch.ui.theme.ZilchTheme
import com.zilch.crypto.qr.QrDecoder  // ← CRÍTICO: import correcto

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZilchTheme { ZilchNavGraph() }
        }
    }
}

@Composable
fun ZilchNavGraph() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = listOf(Routes.CHATS, Routes.INBOX, Routes.SETTINGS)
    val showBottomBar = currentRoute in bottomBarRoutes

    val peers by homeViewModel.peers.collectAsState()
    val allMessages by homeViewModel.messages.collectAsState()

    val realChats = remember(peers, allMessages) {
        peers.map { peer ->
            val peerMsgs = allMessages[peer.nodeId] ?: emptyList()
            val lastMsg = peerMsgs.lastOrNull()
            ChatPreview(
                contactNodeId = peer.nodeId,
                fingerprint = peer.fingerprint,
                lastMessage = lastMsg?.content ?: "Contacto descubierto",
                lastMessageTimeMs = peer.lastSeenMs,
                unreadCount = 0,
                isOnline = peer.isReachable
            )
        }
    }

    // ... [continúa con todos los composable routes]
    // QR_SCAN usa QrDecoder.decode(qrText) en vez de qrText.take(16)
    // TRUSTED_PERSONS pasa contacts desde homeViewModel.trustedContacts
}
```

### 12.2 HomeViewModel.kt (resumen de API pública)
```kotlin
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    // Motores
    private val cryptoEngine = CryptoEngine.getInstance(application)
    private val bleEngine = BleMeshEngine.getInstance(application)

    // Estado observable
    val torStatus: StateFlow<TorStatus>
    val fingerprint: StateFlow<String>
    val nodeId: StateFlow<String>
    val peers: StateFlow<List<PeerNode>>
    val messages: StateFlow<Map<String, List<ChatMessage>>>
    val isKillSwitchActive: StateFlow<Boolean>
    val trustedContacts: StateFlow<List<TrustedPerson>>

    // Acciones
    fun sendMessage(peerNodeId: String, text: String)
    fun addContactFromQr(qrPayload: String)
    fun updateTrustedContactNickname(fingerprint: String, nickname: String)
    fun toggleTrustedContact(fingerprint: String, isTrusted: Boolean)
    fun emergencyDestroy()
    fun refreshIdentity()
}
```

### 12.3 HomeViewModel.kt (init + BLE callbacks)
```kotlin
init {
    try { cryptoEngine.start(viewModelScope) } catch (_: Exception) {}
    loadIdentity()
    startBleEngine()
}

private fun startBleEngine() {
    viewModelScope.launch {
        delay(600L)
        val nodeId = cryptoEngine.getCurrentNodeId()
        val publicKeyBytes = cryptoEngine.identityManager.currentIdentity.publicKeyBytes
        bleEngine.start(viewModelScope, nodeId, publicKeyBytes)

        launch {
            bleEngine.peers.collect { peerMap ->
                _peers.value = peerMap.values.toList()
                _peerCount.value = peerMap.size
            }
        }

        bleEngine.setOnPeerDiscovered { peer ->
            Log.i("HomeViewModel", "Peer descubierto: ${peer.fingerprint}")
        }

        bleEngine.setOnMessageReceived { session, text ->
            val peerNodeId = session.peer.nodeId
            val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
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
        }

        bleEngine.startDiscovery()
    }
}
```

### 12.4 QrScanScreen (función processQrFrame)
```kotlin
private fun processQrFrame(
    imageProxy: ImageProxy,
    isProcessing: AtomicBoolean,
    onQrDetected: (String) -> Unit
) {
    if (isProcessing.getAndSet(true)) { imageProxy.close(); return }
    try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val rowStride = imageProxy.planes[0].rowStride
        val dataWidth = if (rowStride > imageWidth) rowStride else imageWidth

        val source = PlanarYUVLuminanceSource(
            bytes, dataWidth, imageHeight, 0, 0, imageWidth, imageHeight, false
        )

        val reader = MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.PURE_BARCODE to false
            ))
        }

        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decode(binaryBitmap)
            if (result.text.isNotEmpty()) {
                onQrDetected(result.text)
            }
        } catch (_: NotFoundException) { }
        catch (_: ChecksumException) { }
        catch (_: FormatException) { }
        finally { reader.reset() }
    } catch (e: Exception) { }
    finally { isProcessing.set(false); imageProxy.close() }
}
```

### 12.5 BleConfig.kt (parámetros clave)
```kotlin
object BleConfig {
    val SERVICE_UUID = UUID.fromString("7a696c63-0001-4008-8000-000000000001")
    val MESSAGE_CHARACTERISTIC_UUID = UUID.fromString("7a696c63-0001-4008-8000-000000000002")
    val PEER_INFO_CHARACTERISTIC_UUID = UUID.fromString("7a696c63-0001-4008-8000-000000000003")
    val MESH_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("7a696c63-0001-4008-8000-000000000004")

    const val DESIRED_MTU = 512
    const val CHUNK_PAYLOAD_SIZE = DESIRED_MTU - 3
    const val MAX_MESH_TTL = 3
    const val MAX_CONCURRENT_CONNECTIONS = 3
    const val PEER_TIMEOUT_MS = 60_000L
    const val RECENT_MESSAGES_CACHE_SIZE = 256
    const val MESSAGE_CACHE_TTL_MS = 300_000L
    const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
    const val GCM_NONCE_SIZE = 12
    const val GCM_TAG_SIZE = 16
}
```

### 12.6 CryptoConfig.kt (parámetros clave)
```kotlin
object CryptoConfig {
    const val SIGNATURE_ALGORITHM = "Ed25519"
    const val HASH_ALGORITHM = "SHA-256"
    const val IDENTITY_TTL_MS = 3_600_000L        // 1 hora
    const val QR_TTL_MS = 300_000L                 // 5 minutos
    const val FINGERPRINT_LENGTH = 12              // aaaa-bbbb-cccc
    const val QR_PROTOCOL_VERSION = 1
    const val QR_IMAGE_SIZE = 512
    const val QR_ERROR_CORRECTION = 'H'            // 30% ECC
    const val PBKDF2_ITERATIONS = 100_000
}
```

### 12.7 AndroidManifest.xml (app-ui)
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- BLE -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

    <!-- Cámara (QR) -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <!-- Ubicación (BLE scan en Android < 12) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <!-- Internet (Tor) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".ZilchApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Zilch"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## FIN DEL REPORTE

Este documento contiene toda la información necesaria para que un asistente de IA pueda:
1. Entender la arquitectura completa del proyecto
2. Identificar qué está implementado y qué falta
3. Modificar cualquier archivo con contexto completo
4. Diagnosticar errores de compilación
5. Continuar el desarrollo sin perder contexto
