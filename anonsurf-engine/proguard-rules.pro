# ═══════════════════════════════════════════════════════════════════
# ProGuard/R8 rules para Anonsurf Engine
# ═══════════════════════════════════════════════════════════════════

# OkHttp — necesario para que el proxy SOCKS5 funcione correctamente
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Preservar la API pública del motor
-keep public class com.zilch.anonsurf.AnonsurfEngine {
    public *;
}
-keep public class com.zilch.anonsurf.killswitch.NetworkKillSwitch {
    public *;
}
-keep public class com.zilch.anonsurf.verification.TorIpVerifier {
    public *;
}
-keep public class com.zilch.anonsurf.verification.TorIpVerifier$TorStatus { *; }
-keep public class com.zilch.anonsurf.exception.AnonsurfException { *; }
-keep public class com.zilch.anonsurf.exception.AnonsurfException$* { *; }
-keep public class com.zilch.anonsurf.network.TorProxyClient {
    public *;
}
-keep public class com.zilch.anonsurf.network.TorProxyClient$ConnectionListener { *; }
-keep public class com.zilch.anonsurf.config.TorConfig { *; }

# NO preservar logs en release — los logs pueden filtrar información sensible
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
# Solo preservar W y E para diagnóstico en producción
-keepclassmembers class android.util.Log {
    public static int w(...);
    public static int e(...);
    public static int i(...);
}

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
