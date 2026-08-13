# Instalación de Zilch

Zilch es 100% FOSS y no requiere servicios de Google. Dos vías de instalación:

## 1. F-Droid (recomendado)
Cuando la app se apruebe en F-Droid, añádela desde el cliente:
- F-Droid → Ajustes → Repositorios → añade **F-Droid**.
- Busca **Zilch** e instala.
- El APK se compila en los servidores de F-Droid directamente desde el código fuente (ver `metadata/com.zilch.app.yml`).

## 2. APK directo (compilación local)
```bash
cd zilch
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=/usr
./gradlew assembleDebug --no-daemon
# APK en: app-ui/build/outputs/apk/debug/app-ui-debug.apk
```

## 3. Instalación por contagio (offline)
Comparte el APK sin internet:
1. Conecta el dispositivo al hotspot del otro.
2. Abre `http://192.168.4.1:8080`.
3. Descarga e instala el APK.

## Permisos
- **Bluetooth (BLE)**: necesaria para la red mesh.
- **Cámara**: opcional, para escanear/ver QRs.
- **Notificaciones**: para alertas de mensajes.
