# ═══════════════════════════════════════════════════════════════════
# ProGuard/R8 rules para la app principal Zilch
# ═══════════════════════════════════════════════════════════════════

# ═══ Compose ═══
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ═══ Navigation Compose ═══
-keep class androidx.navigation.** { *; }
-keep class * extends androidx.navigation.Navigator

# ═══ CameraX ═══
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ═══ ZXing ═══
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ═══ Activity de navegación ═══
-keep class com.zilch.ui.MainActivity { *; }
-keep class com.zilch.ui.ZilchApp { *; }

# ═══ ViewModels ═══
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ═══ Logs en release ═══
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
-keepclassmembers class android.util.Log {
    public static int w(...);
    public static int e(...);
    public static int i(...);
}

# ═══ Keystore de red ═══
-keep class android.security.** { *; }

# Bouncy Castle (Ed25519 crypto)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# JSON parsing
-keep class org.json.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# App data classes (needed for serialization)
-keep class com.zilch.ui.screens.chat.ChatMessage { *; }
-keep class com.zilch.ui.screens.chatlist.ChatPreview { *; }
-keep class com.zilch.ui.screens.inbox.InboxMessage { *; }
-keep class com.zilch.ui.screens.contacts.ContactUi { *; }
-keep class com.zilch.ui.screens.contacts.TrustedPerson { *; }
-keep class com.zilch.ui.screens.qr.QrScanState { *; }
-keep class com.zilch.ui.components.TorStatus { *; }

# Keep data class members for Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
