# ═══════════════════════════════════════════════════════════════════
# ProGuard/R8 rules para BLE Mesh Engine
# ═══════════════════════════════════════════════════════════════════

# ═══ Android BLE ═══
-keep class android.bluetooth.** { *; }
-dontwarn android.bluetooth.**

# ═══ Bouncy Castle ═══
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ═══ API pública del módulo ═══
-keep public class com.zilch.blemesh.BleMeshEngine {
    public *;
}
-keep public class com.zilch.blemesh.mesh.PeerNode { *; }
-keep public class com.zilch.blemesh.mesh.MeshRouter { public *; }
-keep public class com.zilch.blemesh.session.NearbyChatSession { *; }
-keep public class com.zilch.blemesh.session.SessionMessage { *; }
-keep public class com.zilch.blemesh.message.MeshMessage { *; }
-keep public class com.zilch.blemesh.message.MessageType { *; }
-keep public class com.zilch.blemesh.exception.BleMeshException { *; }
-keep public class com.zilch.blemesh.exception.BleMeshException$* { *; }
-keep public class com.zilch.blemesh.encryption.MeshEncryptor { public *; }
-keep public class com.zilch.blemesh.encryption.MeshEncryptor$EncryptedPayload { *; }

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

# ═══ Bluetooth GATT callbacks (reflexión del framework) ═══
-keep class com.zilch.blemesh.gatt.GattServerManager$* { *; }
-keep class com.zilch.blemesh.gatt.GattClientManager$* { *; }
