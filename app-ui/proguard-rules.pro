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
