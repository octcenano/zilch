# PLAN DE MEJORAS — ZILCH vs EL ESTADO DEL ARTE

Análisis comparativo con Signal, Briar, Session y Element.
Cada mejora tiene un impacto medible en seguridad, usabilidad o rendimiento.

---

## 1. CIFRADO DE TRANSPORTE (Crítico)

**Problema actual:** Los mensajes BLE se envían en texto plano desde `BleMeshEngine.sendMessage()`. SQLCipher protege el disco pero no el aire.

**Solución:** ChaCha20-Poly1305 en cada paquete mesh.

**Implementación:**
- Añadir `MeshEncryptor.encrypt(payload, sharedSecret)` en `ble-mesh`
- Derivar shared secret con X25519 (ya tenemos Ed25519 en BouncyCastle)
- Cifrar antes de GATT write, descifrar tras GATT onCharacteristicChanged
- Nonce aleatorio de 12 bytes por mensaje (nunca reutilizar)

---

## 2. INTERCAMBIO DE CLAVES SEGURO (Crítico)

**Problema actual:** El QR contiene la clave pública pero no hay handshake de sesión. Si alguien intercepta el QR, puede suplantar al peer.

**Solución:** X3DH simplificado + fingerprints verificados verbalmente.

**Implementación:**
- QR contiene: identity key + signed prekey + one-time prekey
- Al escanear QR: generar ephemeral key, computing shared secret
- Verificación verbal del fingerprint (ya existe en UI)
- Almacenar sesión derivada en SQLCipher

---

## 3. LISTA DE CHATS REAL (Alto impacto UX)

**Problema actual:** La lista de chats solo muestra peers BLE descubiertos. Si no hay nadie cerca, está vacía.

**Solución:** Mostrar conversaciones existentes + peers cercanos.

**Implementación:**
- Cargar conversaciones de EncryptedStorage en ChatListScreen
- Fusionar con peers BLE en tiempo real
- Mostrar preview del último mensaje + timestamp + badge de no leídos

---

## 4. CHAT FUNCIONAL END-TO-END (Alto impacto)

**Problema actual:** El envío de mensajes pasa por BLE pero no hay confirmación de entrega ni reintentos.

**Solución:** ACK de entrega + reintentos + timestamps cifrados.

**Implementación:**
- Protocolo de acknowledgment: emisor guarda estado (PENDING → SENT → DELIVERED)
- Reintentos automáticos con backoff exponencial
- Indicator de estado en la UI (✓ enviado, ✓✓ entregado)

---

## 5. VOZ BLE REAL (Medio impacto, alta complejidad)

**Problema actual:** VoiceCallScreen es solo UI sin backend de audio.

**Solución:** Streaming de audio PCM por GATT con cifrado ChaCha20.

**Implementación:**
- AudioRecord → PCM → ChaCha20-Poly1305 → GATT write chunks
- GATT notification → ChaCha20 decrypt → PCM → AudioTrack
- Sample rate: 8kHz (suficiente para voz, bajo consumo BLE)
- MTU negotiation para chunks grandes (512 bytes)

---

## 6. GRUPOS PEER-TO-PEER (Medio impacto)

**Problema actual:** Solo chat 1:1.

**Solución:** Grupos de hasta 8 personas con retransmisión mesh.

**Implementación:**
- Grupo = lista de nodeIds firmada por el creador
- Mensaje grupal = ciphertext por cada miembro (multi-recipient)
- Retransmisión automática en el mesh
- Límite de 8 para evitar explosión de tráfico

---

## 7. MENSAJES TEMPORALES (Medio impacto)

**Problema actual:** Los mensajes persisten indefinidamente en SQLCipher.

**Solución:** TTL configurable por conversación.

**Implementación:**
- Timestamp de expiración cifrado en cada mensaje
- Limpieza periódica de mensajes expirados
- Opciones: 5min, 1h, 24h, 7d, nunca

---

## 8. BÚSQUEDA MEJORADA (Bajo impacto)

**Problema actual:** Búsqueda básica por fingerprint o contenido.

**Solúsqueda mejorada:** Búsqueda por contacto, fecha, contenido con highlighting.

**Implementación:**
- Fts5 en SQLCipher para búsqueda full-text
- Índice por peerNodeId + timestamp
- UI con resultados resaltados

---

## PRIORIDAD DE IMPLEMENTACIÓN

1. ✅ CIFRADO DE TRANSPORTE (ya compilado, necesita integración)
2. ✅ LISTA DE CHATS REAL (cambiar ChatListScreen para usar datos reales)
3. ✅ CHAT FUNCIONAL (ACK + reintentos)
4. ✅ INTERCAMBIO DE CLAVES SEGURO (mejorar QR flow)
5. 🔲 VOZ BLE REAL (arquitectura base)
6. 🔲 GRUPOS (futuro)
7. 🔲 MENSAJES TEMPORALES (futuro)
8. 🔲 BÚSQUEDA MEJORADA (futuro)
