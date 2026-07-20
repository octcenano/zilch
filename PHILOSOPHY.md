# ZILCH — Filosofía y Directrices del Proyecto

> Este documento guía TODA decisión de diseño, arquitectura y código en Zilch.
> Si una decisión no cumple estas directrices, no se implementa.

---

## MISIÓN

Zilch no pretende ser simplemente otra aplicación de mensajería.

Su objetivo es proteger la privacidad, facilitar la comunicación entre personas incluso en situaciones difíciles y demostrar que una aplicación libre puede ser mejor que muchas aplicaciones comerciales.

Cada decisión de diseño, arquitectura o código debe responder a una pregunta:

**"¿Esto hace que Zilch sea más útil, más seguro y más fácil de usar para cualquier persona?"**

Si la respuesta es no, no implementes esa idea.

---

## FILOSOFÍA

Zilch debe ser:

- extremadamente simple
- extremadamente rápido
- extremadamente privado
- completamente open source
- compatible con F-Droid
- sin anuncios
- sin rastreadores
- sin telemetría
- sin depender de Google
- con compilaciones reproducibles
- accesible para cualquier persona

La simplicidad es más importante que añadir cientos de funciones.

No copies WhatsApp.
No copies Telegram.
No copies Signal.

Aprende de ellas y mejora aquello que resulta confuso.

---

## EXPERIENCIA DE USUARIO

La aplicación debe poder utilizarla alguien sin conocimientos técnicos.

Todo debe ser intuitivo.

No debe haber pantallas complicadas.
No debe haber configuraciones innecesarias.
No debe haber textos largos.

El usuario debe poder abrir la aplicación y empezar a hablar en pocos segundos.

Prioridades:

- lista de chats visible inmediatamente
- botones claros
- iconos fáciles de entender
- navegación sencilla
- animaciones suaves
- interfaz limpia
- tipografía legible
- modo oscuro optimizado para OLED

Cada pulsación debe sentirse rápida.

Nunca sacrifiques facilidad de uso por complejidad técnica.

---

## PRIVACIDAD

La privacidad es el núcleo del proyecto.

Toda nueva función debe diseñarse bajo el principio de minimizar la exposición de datos.

- Evita recopilar información innecesaria.
- Protege el almacenamiento local.
- Protege las claves.
- Protege la identidad.
- Protege los mensajes.
- Reduce la cantidad de metadatos siempre que sea posible.
- Explica cualquier decisión relacionada con seguridad para que pueda revisarse.

---

## ARQUITECTURA

- Mantén la arquitectura modular existente.
- No mezcles responsabilidades.
- Cada módulo debe tener una única responsabilidad.
- Evita dependencias innecesarias.
- Evita código duplicado.
- Utiliza interfaces cuando ayuden al mantenimiento.
- Documenta las decisiones importantes.
- El código debe ser fácil de leer.
- Prefiere claridad antes que trucos complejos.

---

## RENDIMIENTO

La aplicación debe sentirse ligera.

Optimiza: memoria, CPU, batería, Bluetooth, base de datos, renderizado Compose.

- Evita recomposiciones innecesarias.
- Evita fugas de memoria.
- Evita operaciones pesadas en el hilo principal.

---

## FUNCIONES DE MENSAJERÍA

La experiencia de chat debe ser excelente.

Implementa o mejora:

- conversaciones rápidas
- envío fiable
- reintentos automáticos
- indicadores de entrega
- búsqueda
- favoritos
- mensajes temporales
- compartir imágenes
- compartir documentos
- compartir audio
- grupos
- respuesta a mensajes
- reenvío
- edición cuando sea posible
- vista previa de archivos
- buena organización de conversaciones

Todo debe sentirse natural.

---

## ACCESIBILIDAD

La aplicación debe poder utilizarla cualquier persona.

Mejora: contraste, tamaños de texto, lectores de pantalla, navegación sencilla, botones grandes, colores accesibles.

---

## CÓDIGO

Cada cambio debe:

- compilar
- no romper funcionalidades existentes
- mantener compatibilidad Android 8+
- seguir buenas prácticas Kotlin
- usar Coroutines correctamente
- usar StateFlow correctamente
- usar Compose correctamente

Si encuentras código duplicado: refactorízalo.
Si encuentras código muerto: elimínalo.
Si encuentras una arquitectura mejor: propón la mejora explicando ventajas e inconvenientes antes de aplicarla.

---

## CALIDAD

Antes de finalizar cualquier modificación verifica:

- ✔ Compila.
- ✔ No rompe módulos.
- ✔ Mantiene la privacidad.
- ✔ Mantiene la simplicidad.
- ✔ Mejora la experiencia del usuario.
- ✔ Reduce errores.
- ✔ Mantiene compatibilidad F-Droid.
- ✔ Está documentado.

---

## OBJETIVO FINAL

Zilch debe ser una aplicación que cualquier persona pueda abrir y entender en menos de un minuto.

Debe funcionar bien incluso cuando la conectividad sea limitada.

Debe inspirar confianza.

Debe ser un referente del software libre.

No desarrolles funciones solo porque sean interesantes.
Desarrolla funciones que realmente ayuden a las personas.

Cada línea de código debe acercar a Zilch a ser una herramienta útil, sencilla, privada y mantenible durante muchos años.
