# Mis Hábitos para Android

Aplicación Android para organizar rutinas y construir hábitos a tu ritmo. La interfaz se incluye dentro de la app y funciona sin conexión.

## Funciones

- Perfil inicial con apodo, edad 13+ y actividad física opcional.
- Inicio con frase diaria, citas atribuidas, racha, avance y hábitos del día.
- Agenda semanal con trabajo/estudio, sueño, otros compromisos y **Traslados manuales con Desde/Hasta**; muestra rangos libres explícitos en formato 24 h. No crea campos ni bloques automáticos de trayecto al trabajo.
- Hábitos en varios días, horario y detección de conflictos, con opción de cambiar o continuar.
- Avisos programados para hábitos y hasta tres mensajes motivacionales diarios configurables (mañana, mediodía y tarde), con permiso explícito; se ejecutan como notificaciones locales de Android, sin servidor push.
- Sección de motivación con siete lecturas originales y citas de Epicteto, Marco Aurelio y Séneca, con referencia a sus obras.
- Datos locales en SQLite dentro del almacenamiento privado de Android, con migración automática del estado previo guardado por WebView; no requiere cuenta ni conexión.

## Notificaciones

Android puede solicitar permiso para notificaciones y acceso a alarmas exactas. Si no se concede el acceso a alarmas exactas, la app programa avisos aproximados; el ahorro de batería del fabricante también puede retrasarlos.

## Datos y compatibilidad

Los datos de perfil, agenda y hábitos permanecen en el dispositivo. Se conserva el identificador interno de la aplicación para permitir actualizar instalaciones anteriores y mantener sus datos locales.

## Compilar

Requiere JDK 17, Android SDK 34 y Gradle 8.5.

```bash
gradle testDebugUnitTest --no-daemon
gradle assembleDebug --no-daemon
node --test scripts/test-availability.cjs scripts/test-app-render.cjs
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`. Esta compilación no se ha probado en un teléfono físico y no es una versión de publicación para Google Play.
