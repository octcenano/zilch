# ═══════════════════════════════════════════════════════════════════
# ProGuard/R8 rules para Crypto Engine
# ═══════════════════════════════════════════════════════════════════

# ═══ Bouncy Castle ═══
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ═══ ZXing ═══
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ═══ Preservar API pública del motor ═══
-keep public class com.zilch.crypto.CryptoEngine {
    public *;
}
-keep public class com.zilch.crypto.identity.EphemeralIdentity { *; }
-keep public class com.zilch.crypto.identity.IdentityManager { public *; }
-keep public class com.zilch.crypto.contact.Contact { *; }
-keep public class com.zilch.crypto.contact.ContactManager { public *; }
-keep public class com.zilch.crypto.qr.QrEncoder { public *; }
-keep public class com.zilch.crypto.qr.QrDecoder { public *; }
-keep public class com.zilch.crypto.qr.QrDecoder$DecodedQr { *; }
-keep public class com.zilch.crypto.hash.NodeIdentifier { public *; }
-keep public class com.zilch.crypto.exception.CryptoEngineException { *; }
-keep public class com.zilch.crypto.exception.CryptoEngineException$* { *; }

# ═══ Logs en release ═══
# Eliminar logs verbose/debug — pueden filtrar información sensible
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
-keepclassmembers class android.util.Log {
    public static int w(...);
    public static int e(...);
    public static int i(...);
}

# ═══ EdDSA provider ═══
-keep class java.security.spec.EdDSAParameterSpec { *; }
-keep class java.security.spec.EdDSAKey { *; }
-keep class java.security.spec.EdDSAPrivateKeySpec { *; }
-keep class java.security.spec.EdDSAPublicKeySpec { *; }

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
