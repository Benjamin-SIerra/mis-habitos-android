# Mis Hábitos para Android

Aplicación Android para organizar rutinas y construir hábitos a tu ritmo. La interfaz se incluye dentro de la app y funciona sin conexión.

## Funciones

- Perfil inicial con apodo, edad 13+ y actividad física opcional.
- Inicio con frase diaria, racha, avance y hábitos del día.
- Agenda semanal con sueño, trabajo/estudio, traslados y otros compromisos; calcula el tiempo disponible. El trayecto al trabajo se registra por viaje y reserva tanto la ida como el regreso (por ejemplo, 40 min cada uno).
- Hábitos en varios días, horario y detección de conflictos, con opción de cambiar o continuar.
- Avisos programados en Android con opciones de 5, 10, 15 o 30 minutos antes.
- Sección de motivación con siete lecturas originales.
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
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`. Esta compilación no se ha probado en un teléfono físico y no es una versión de publicación para Google Play.
