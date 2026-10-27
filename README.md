# MUSTANG OS · 0.1.0

Launcher Android nativo para Ford Mustang 2010 V6 4.0L convertible rojo con capota negra. Kotlin, Views/XML, MVVM y StateFlow. La referencia visual se traduce en tres paneles, superficies grafito, rojo moderado e iconos vectoriales de trazo uniforme.

![Interfaz Android renderizada en pruebas, sin datos CarPlay simulados](docs/dashboard-1280x720.png)

APK de depuración generada; 29 pruebas automatizadas pasadas. La instalación y las integraciones del T100 siguen pendientes de validación en hardware.

## Instalar y empezar

Para instalar **por enlace, sin memoria USB ni ADB**, sirve la APK desde un PC conectado a la misma red Wi-Fi. Se incluye `scripts/serve-apk.py`; consulta [la guía de descarga local](docs/LOCAL_DOWNLOAD.md). Abre su dirección en el navegador de la pantalla, descarga la APK y permite a ese navegador instalar aplicaciones cuando Android lo solicite. La autorización de instalación es independiente de las opciones de depuración protegidas por contraseña.

1. Con el coche estacionado, descarga y abre `MustangOS-debug.apk` desde el enlace local. Como alternativa, el archivo de `output/` también puede copiarse desde una USB y abrirse con el gestor de archivos. Si ADB está disponible:

   ```powershell
   adb install -r .\output\MustangOS-debug.apk
   adb shell am start -n com.mustangos.launcher/.ui.MainActivity
   ```

2. Abre Mustang OS como una aplicación normal. No cambia automáticamente el Home predeterminado.
3. En **Ajustes → Elegir launcher original**, selecciona la interfaz del fabricante. Prueba **Abrir launcher original** antes de elegir Mustang OS como Home.
4. Pulsa **CarPlay** y selecciona la app ZLink que ya existe en la pantalla. Se muestran etiqueta y paquete reales. La selección queda guardada. No se instala, actualiza ni reinicia ZLink, ni se borran datos o activación.
5. En **Ajustes**, asigna radio, teléfono, música y mapas Android a sus aplicaciones instaladas. Si una app falta, el botón muestra el selector; no ejecuta una operación ficticia.
6. Pulsa el estado GPS para conceder ubicación **precisa**. El velocímetro pasa de `—` a una lectura real cuando el receptor entrega una velocidad válida. Sin señal o después de cinco segundos sin lectura válida vuelve a `—`.
7. En **Elegir fuente**, puedes autorizar acceso a notificaciones para descubrir MediaSessions. Reproduce Apple Music en CarPlay, vuelve al launcher y comprueba las sesiones. Si ZLink no publica una, la tarjeta muestra que no hay datos y los controles permanecen deshabilitados. La autorización no garantiza acceso al iPhone.

La APK usa una firma de depuración y el paquete propio `com.mustangos.launcher`. Las futuras actualizaciones deben conservar la misma firma para instalarse con `-r`; este paquete es independiente de ZLink.

## Volver al launcher original

- En Mustang OS: **Ajustes → Abrir launcher original**. Esto abre la aplicación elegida sin cambiar el Home.
- Para restaurarlo como predeterminado: **Ajustes → Elegir launcher predeterminado**, selecciona el del fabricante en Android. Algunos firmwares no ofrecen esta pantalla; también puedes acceder desde Ajustes Android → Apps → Apps predeterminadas → Inicio, si existe.
- Antes de cambiar Home, conserva acceso al panel de ajustes y prueba la restauración. Un firmware puede imponer su propia interfaz durante el arranque aunque Mustang OS sea Home.
- Si es necesario y ADB está autorizado: `adb shell am start -a android.settings.HOME_SETTINGS`. Para abrir una actividad original usa el componente que viste en el selector: `adb shell am start -n PAQUETE/ACTIVIDAD`. No sustituyas ese dato por un paquete supuesto.
- Desinstala **solo Mustang OS** desde Ajustes Android después de recuperar el launcher original. No ejecutes comandos de borrado sobre ZLink ni sobre componentes del fabricante.

## Qué incluye

| Función | Implementación | Validación pendiente |
|---|---|---|
| Dashboard 1280×720, hora y red reales | Views/XML, iconos vectoriales propios | Aspecto y densidad reales del T100 |
| Apps, radio, teléfono y mapas | Selector PackageManager y actividad explícita guardada | Apps OEM concretas y permisos de cada firmware |
| CarPlay | Abre el ZLink elegido; conserva la tarea existente | Paquete, actividad y comportamiento de ZLink 6.1.06 |
| Multimedia | Metadatos, portada, álbum, progreso y acciones por MediaController | Exposición de Apple Music por ZLink |
| GPS | Ubicación precisa, filtro, caducidad y ciclo de vida visible | Calidad y frecuencia del GPS del coche |
| Diagnóstico | SDK, RAM, permisos, sesiones y componentes exportados | Capturar informe en pantalla real |
| OBD2 | Contrato y estado no disponible | Transporte ELM327 y adaptador aún no implementados |
| Ruta CarPlay / mapa incrustado | Acceso a ZLink y mapas Android | Sin contrato público verificado para datos o incrustación |

No hay temperatura exterior, kilometraje, rutas ni canciones de demostración. No se solicitan root, control de Bluetooth/Wi-Fi, accesibilidad, micrófono, contactos, audio focus ni permisos privilegiados. No hay permiso de Internet, analítica de la APK ni servicio de GPS en segundo plano. El servicio de acceso multimedia no lee el contenido de las notificaciones.

## Compilar

Abre la carpeta en Android Studio compatible con AGP 8.13.2, selecciona JDK 17 e instala SDK 35 / Build Tools 35.0.0. El wrapper usa Gradle 8.13 y Kotlin 2.2.21. `minSdk=26`, `targetSdk=35`. Puedes usar tu SDK existente con `local.properties`.

En este PC también se incluye un bootstrap reproducible para Windows que descarga herramientas oficiales dentro de `.tools/`, verifica sus sumas y acepta las licencias de SDK para el entorno local:

```powershell
.\scripts\bootstrap.ps1
.\scripts\verify.ps1
```

Sin bootstrap, con JDK 17 y SDK configurados:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El script de verificación copia la APK a `output/MustangOS-debug.apk`. Los reportes se encuentran en `core/build/reports/tests/test`, `app/build/reports/tests/testDebugUnitTest` y `app/build/reports/lint-results-debug.html`. `output/` y las herramientas descargadas no forman parte del control de versiones.

## Organización

`core/` contiene las reglas Kotlin puras para selección multimedia, capacidades, progreso y filtro GPS; `app/` contiene `ui`, `settings`, `appintegration`, `carplay`, `media`, `navigation`, `gps`, `vehicle` y `diagnostics`. `LauncherViewModel` conecta las integraciones y su estado con la actividad. Los listeners y GPS se liberan al ocultarse la pantalla. El progreso visible se actualiza una vez por segundo sin enviar acciones al reproductor.

Lee [COMPATIBILITY.md](COMPATIBILITY.md), [ZLINK_INTEGRATION.md](ZLINK_INTEGRATION.md), [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md) y [TEST_RESULTS.md](TEST_RESULTS.md) para distinguir evidencia automatizada de pruebas pendientes en el coche. El asset y su prompt están documentados en [docs/ASSETS.md](docs/ASSETS.md).
