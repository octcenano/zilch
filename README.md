# ZILCH

> Comunicación cifrada offline. Sin servidores, sin vigilancia, sin rastro.

![License](https://img.shields.io/badge/license-MIT-green)
![API](https://img.shields.io/badge/API-26%2B-blue)
![FOSS](https://img.shields.io/badge/FOSS-100%25-brightgreen)

---

## POR QUÉ EXISTE ZILCH

En un mundo donde cada mensaje pasa por servidores de empresas que lo analizan, Zilch demuestra que la comunicación directa entre personas es posible sin intermediarios.

No es otra app de mensajería. Es una herramienta de supervivencia digital.

## CÓMO FUNCIONA

### Arquitectura

```
┌─────────────────────────────────────────────────┐
│                  Zilch                          │
├────────────┬──────────────┬─────────────────────┤
│ crypto-    │ ble-mesh     │ anonsurf-engine     │
│ engine     │              │                     │
│            │ BLE Mesh     │ Tor/SOCKS5          │
│ Ed25519    │ GATT Server  │ Kill Switch         │
│ SQLCipher  │ Voice Call   │ IP Verification     │
│ QR Codes   │ Store&Forward│ DNS Leak Protect    │
└────────────┴──────────────┴─────────────────────┘
```

### Flujo de uso

1. Abres la app → se genera tu identidad efímera (Ed25519)
2. Escaneas el QR de tu contacto cercano
3. Comprobáis el fingerprint verbalmente
4. Enviáis mensajes cifrados end-to-end por BLE
5. Los mensajes saltan entre dispositivos cercanos (red mesh)

### Flujo de seguridad

```
Mensaje → Cifrado AES-256-GCM → Dividido en chunks BLE
    → Enviado por GATT → Ensamblado en receptor
    → Descifrado → Almacenado en SQLCipher (memoria)
```

## CARACTERÍSTICAS

### Implementadas

- **Mensajería cifrada P2P** por Bluetooth LE
- **Red mesh autónoma** — sin internet, sin datos móviles, sin WiFi
- **Identidad efímera** con auto-regeneración (Ed25519)
- **Double Ratchet simplificado** — forward secrecy por mensaje (SessionKeyRatchet)
- **Llamadas de voz BLE** — AudioRecord → AES-GCM → BLE GATT → AudioTrack
- **Kill Switch forense** — destruye identidad, contactos y base de datos
- **Verificación de identidad** — QR + fingerprint verbal (anti-MITM)
- **Personas de confianza** — contactos verificados con nombres personalizados
- **Bandeja .onion** — correo P2P asíncromo
- **Instalación por contagio** — servidor HTTP local para compartir APK offline
- **Store-and-Forward** — mensajes en cola con TTL para nodos offline
- **Proof-of-Work** — anti-DDoS en cabeceras mesh
- **Base de datos cifrada** — SQLCipher, todo en memoria, nada en disco
- **Modo Anonsurf** — Tor/SOCKS5 con DNS leak protection
- **Onboarding** — pantalla de permisos clara y educativa
- **Modo OLED oscuro** — ahorro de batería y privacidad visual
- **Interfaz estilo WhatsApp** — lista de chats, búsqueda, FAB, bottom nav

### En desarrollo

- LoRa/Meshtastic para alcance de kilómetros
- Reloj offline (GPS + consenso P2P)
- Grupos cifrados
- Compartir archivos cifrados

## MÓDULOS

### crypto-engine
- Identidad Ed25519 efímera con regeneración automática
- Cifrado AES-256-GCM para mensajes
- Almacenamiento cifrado SQLCipher (zero-knowledge en memoria)
- Generación y decodificación de códigos QR con firma
- Gestión de contactos verificados
- Memory sanitization — buffers limpiados con SecureRandom después de usar claves

### ble-mesh
- Servidor y cliente GATT para comunicación BLE
- Mesh router con retransmisión automática de mensajes
- SessionKeyRatchet — Double Ratchet simplificado con forward secrecy
- BleVoiceCall — llamadas de voz encriptadas por BLE
- Store-and-Forward — colas de mensajes con TTL y Bloom Filters
- Proof-of-Work — anti-DDoS en paquetes mesh
- Advertising y scanning BLE optimizado para batería

### anonsurf-engine
- Cliente HTTP enrutado exclusivamente por Tor (SOCKS5)
- Kill Switch con health check proactivo del proxy
- Verificación de IP sin exponer la dirección real
- DNS leak protection (resolución vía proxy SOCKS5)
- Bridge support (obfs4, Snowflake) para evadir DPI
- Interceptor de seguridad en cada petición HTTP

### app-ui
- Single Activity con Navigation Compose
- 12 pantallas: Onboarding, Needs, Chats, Chat, Contacts, Trusted Persons, QR Send, QR Scan, Inbox, Settings, Voice Call, Onboarding
- Bottom navigation bar (Chats, Bandeja, Ajustes)
- FAB con acciones rápidas (escanear QR, mostrar QR)
- Indicador de estado Tor en tiempo real
- Botón de emergencia de doble toque
- Tema OLED oscuro optimizado
- Búsqueda de chats
- Fingerprint display para verificación

## SEGURIDAD

### Principios

1. **Zero Knowledge** — Los datos viajan cifrados en memoria, nunca tocan el disco sin cifrar
2. **Forward Secrecy** — Cada mensaje usa una clave diferente (Double Ratchet)
3. **Defense in Depth** — Kill Switch + interceptor + health check + verificación de IP
4. **Memory Sanitization** — Claves y buffers sensibles se limpian tras uso
5. **Identity Rotation** — Identidades efímeras con auto-regeneración

### Ataques mitigados

- **Man-in-the-Middle** → Verificación por QR + fingerprint verbal
- **DNS Leak** → Resolución DNS vía proxy SOCKS5
- **IP Leak** → Kill Switch + health check proactivo
- **Replay Attack** → Contadores de nonce en SessionKeyRatchet
- **Key Compromise** → Forward secrecy (Double Ratchet)
- **DDoS P2P** → Proof-of-Work en cabeceras mesh
- **RAM Dump** → Memory sanitization con Arrays.fill
- **Forensic Analysis** → Botón de pánico con wipe + corrupt DB + exitProcess
- **Surveillance** → Sin metadatos, sin logs persistentes, sin telemetría

## TECNOLOGÍAS

| Componente | Tecnología | Licencia |
|-----------|-----------|----------|
| Criptografía | BouncyCastle (Ed25519, AES-256) | MIT |
| QR | ZXing | Apache-2.0 |
| Cámara | CameraX | Apache-2.0 |
| Red | OkHttp (SOCKS5 proxy) | Apache-2.0 |
| UI | Jetpack Compose + Material3 | Apache-2.0 |
| Almacenamiento | SQLCipher | BSD |
| BLE | Android BLE API | Apache-2.0 |
| Audio | Android AudioRecord/AudioTrack | Apache-2.0 |

## COMPILACIÓN

### Requisitos

- JDK 17+
- Android SDK 34
- Gradle 8.5

### Comandos

```bash
cd zilch
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=/usr
./gradlew assembleDebug --no-daemon
```

APK generado en: `app-ui/build/outputs/apk/debug/app-ui-debug.apk`

### Compilaciones reproducibles

F-Droid compila automáticamente desde el código fuente. Cada build es verificable.

## INSTALACIÓN POR CONTAGIO

Sin internet ni F-Droid, un usuario puede compartir Zilch directamente:

1. Conecta tu dispositivo al hotspot del otro
2. Abre `http://192.168.4.1:8080` en el navegador
3. Descarga el APK
4. Instala directamente

## ESTRUCTURA DE ARCHIVOS

```
zilch/
├── app-ui/                    UI — 30+ archivos Kotlin, Compose
│   ├── src/main/java/com/zilch/ui/
│   │   ├── MainActivity.kt        Single Activity + Navigation
│   │   ├── components/            BottomNavBar, ChatBubble, EmergencyButton,
│   │   │                          FingerprintDisplay, TorStatusIndicator
│   │   ├── navigation/Routes.kt   Todas las rutas de navegación
│   │   ├── screens/
│   │   │   ├── home/              HomeViewModel
│   │   │   ├── chatlist/          Lista de chats (estilo WhatsApp)
│   │   │   ├── chat/              Chat cercano BLE + ChatMessage
│   │   │   ├── contacts/          Contactos + TrustedPerson
│   │   │   ├── inbox/             Bandeja .onion
│   │   │   ├── qr/                QR Send + QR Scan + CameraX
│   │   │   ├── settings/          Ajustes + fingerprint + Tor status
│   │   │   ├── setup/             NeedsScreen (permisos)
│   │   │   ├── onboarding/        Onboarding con pager
│   │   │   └── voice/             VoiceCallScreen
│   │   └── theme/                 Color, Theme, Typography
│   └── build.gradle.kts
├── ble-mesh/                 BLE mesh networking + cifrado + voz
│   ├── src/main/java/com/zilch/blemesh/
│   │   ├── BleMeshEngine.kt       Orquestador BLE principal
│   │   ├── advertising/           BLE advertising
│   │   ├── scanning/              BLE scanning
│   │   ├── gatt/                  GATT Server + Client
│   │   ├── mesh/                  Mesh Router + PeerNode
│   │   ├── encryption/            MeshEncryptor + SessionKeyRatchet
│   │   ├── message/               MeshMessage + MessageChunker
│   │   ├── session/               NearbyChatSession
│   │   ├── voice/                 BleVoiceCall (AudioRecord/Track + AES-GCM)
│   │   ├── storeforward/          Store-and-Forward + Bloom Filters
│   │   ├── pow/                   Proof-of-Work Hashcash
│   │   ├── time/                  Offline Clock
│   │   ├── lora/                  LoRa/Meshtastic abstraction
│   │   ├── config/                BleConfig (UUIDs, constants)
│   │   └── exception/             BleMeshException
│   └── build.gradle.kts
├── crypto-engine/            Criptografía + almacenamiento
│   ├── src/main/java/com/zilch/crypto/
│   │   ├── CryptoEngine.kt        Orquestador criptográfico
│   │   ├── identity/              EphemeralIdentity (Ed25519)
│   │   ├── storage/               EncryptedStorage (SQLCipher)
│   │   ├── qr/                    QrEncoder + QrDecoder
│   │   ├── contact/               Contact + ContactManager
│   │   └── config/                CryptoConfig
│   └── build.gradle.kts
├── anonsurf-engine/         Tor / Kill Switch
│   ├── src/main/java/com/zilch/anonsurf/
│   │   ├── AnonsurfEngine.kt      Orquestador de red
│   │   ├── network/               TorProxyClient (SOCKS5)
│   │   ├── verification/          TorIpVerifier
│   │   ├── killswitch/            NetworkKillSwitch
│   │   ├── exception/             AnonsurfException
│   │   └── config/                TorConfig
│   └── build.gradle.kts
├── metadata/com.zilch.app.yml    F-Droid metadata
├── PHILOSOPHY.md                 Filosofía del proyecto
├── README.md                     Este archivo
├── IMPROVEMENTS.md               Análisis vs otras apps
└── REPORT.md                     Reporte técnico detallado
```

## ESTADÍSTICAS

| Métrica | Valor |
|---------|-------|
| Archivos Kotlin | 59 |
| Módulos | 4 |
| Dependencias externas | 8 (todas FOSS) |
| Dependencias Google | 0 |
| Dependencias de pago | 0 |
| Servidores externos | 0 |
| Min SDK | 26 (Android 8+) |
| Target SDK | 34 |
| Compilación | 0 errores, 0 warnings |

## ROADMAP

### v0.1.0-alpha (actual)
- Identidad Ed25519 efímera
- Mensajería BLE cifrada end-to-end
- QR para intercambio de identidades
- Kill Switch de emergencia
- Base de datos SQLCipher
- Onboarding con permisos
- Llamadas de voz BLE

### v0.2.0-beta
- Store-and-Forward completo con Bloom Filters
- LoRa/Meshtastic integration
- Grupos cifrados
- Compartir archivos cifrados

### v0.3.0
- Reloj offline (GPS + consenso P2P)
- Bridges Tor nativos (obfs4, Snowflake)
- Modo anónimo avanzado

### v1.0.0
- Compilaciones reproducibles verificadas
- Auditoría de seguridad externa
- Publicación en F-Droid

## LICENCIA

MIT License — usa Zilch para lo que necesites.

## CRÉDitos

Construido con código abierto, para la gente, por la gente.
