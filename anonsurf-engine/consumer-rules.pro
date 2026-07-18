# Consumer ProGuard rules — aplicables al módulo app que consuma anonsurf-engine
# Preservar la API pública para que R8 no elimine lo que la app necesita
-keep class com.zilch.anonsurf.AnonsurfEngine { public *; }
-keep class com.zilch.anonsurf.exception.** { *; }
