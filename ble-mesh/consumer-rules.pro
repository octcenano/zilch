# Consumer ProGuard rules para módulos que consuman ble-mesh
-keep class com.zilch.blemesh.BleMeshEngine { public *; }
-keep class com.zilch.blemesh.exception.** { *; }
-keep class com.zilch.blemesh.mesh.PeerNode { *; }
-keep class com.zilch.blemesh.session.NearbyChatSession { *; }
-keep class com.zilch.blemesh.session.SessionMessage { *; }
