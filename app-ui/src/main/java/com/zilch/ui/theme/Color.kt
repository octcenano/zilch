package com.zilch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores de Zilch — DARK ONLY.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  DECISIÓN DE DISEÑO: DARK THEME EXCLUSIVO
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * La app solo opera en modo oscuro por razones de privacidad:
 *
 * 1. **Reducir fingerprint visual:** Un tema claro en una pantalla
 *    OLED hace que el contenido sea más visible desde lejos.
 *    El tema oscuro reduce la visibilidad del contenido.
 *
 * 2. **Ahorrar batería:** Los dispositivos OLED apagan los píxeles
 *    negros, reduciendo significativamente el consumo de batería
 *    durante sesiones prolongadas de uso.
 *
 * 3. **Consistencia UX:** Elimina la posibilidad de que el usuario
 *    accidentalmente muestre contenido en modo claro.
 */
object DarkPalette {
    // ═══ Backgrounds ═══
    val background = Color(0xFF0D1117)        // Negro profundo
    val surface = Color(0xFF161B22)           // Superficie principal
    val surfaceVariant = Color(0xFF21262D)    // Superficies secundarias
    val surfaceElevated = Color(0xFF30363D)   // Elevated cards

    // ═══ Primary (Acento cian — "digital libre") ═══
    val primary = Color(0xFF58A6FF)           // Azul cian
    val primaryVariant = Color(0xFF388BFD)
    val onPrimary = Color(0xFF0D1117)

    // ═══ Secondary (Acento verde — "conexión activa") ═══
    val secondary = Color(0xFF3FB950)         // Verde
    val secondaryVariant = Color(0xFF2EA043)
    val onSecondary = Color(0xFF0D1117)

    // ═══ Tertiary (Acento púrpura — "cifrado") ═══
    val tertiary = Color(0xFFBC8CFF)
    val tertiaryVariant = Color(0xFFA371F7)
    val onTertiary = Color(0xFF0D1117)

    // ═══ Error / Emergency ═══
    val error = Color(0xFFFF7B72)             // Rojo suave
    val errorContainer = Color(0xFF490202)    // Rojo oscuro para fondo
    val emergency = Color(0xFFDA3633)         // Rojo de emergencia puro
    val emergencyDark = Color(0xFF8B1A1A)     // Rojo oscuro para barra
    val onError = Color(0xFFFFFFFF)

    // ═══ Text ═══
    val onBackground = Color(0xFFE6EDF3)     // Texto principal
    val onSurface = Color(0xFFE6EDF3)
    val onSurfaceVariant = Color(0xFF8B949E) // Texto secundario
    val textMuted = Color(0xFF6E7681)         // Texto muy muted

    // ═══ Tor Status ═══
    val torActive = Color(0xFF3FB950)         // Verde: Tor activo
    val torInactive = Color(0xFFFF7B72)       // Rojo: Tor inactivo
    val torChecking = Color(0xFFF0C543)       // Amarillo: verificando

    // ═══ QR ═══
    val qrBackground = Color(0xFFFFFFFF)     // Fondo blanco del QR
    val qrForeground = Color(0xFF0D1117)     // QR negro sobre fondo blanco

    // ═══ Dividers / Borders ═══
    val divider = Color(0xFF21262D)
    val border = Color(0xFF30363D)
}
