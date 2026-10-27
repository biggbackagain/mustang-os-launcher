# MUSTANG OS · 0.3.2

Launcher Android nativo para Ford Mustang 2010 V6 4.0L convertible rojo con capota negra. Kotlin, Views/XML, MVVM y StateFlow. La referencia visual se traduce en tres paneles, superficies grafito, rojo moderado e iconos vectoriales de trazo uniforme.

![Interfaz Android renderizada en pruebas, sin datos CarPlay simulados](docs/dashboard-1280x720.png)

El usuario instaló versiones de prueba y envió tres ZIP de la unidad. Se confirmó SDK 29 pese a la etiqueta Android 13. Las fotos de 0.3.0 confirman voltaje OEM de 14.1 V coincidente con la barra original. El informe activo de 0.3.1 confirma 12.52 V y cinco eventos del canal estándar de puertas, con última máscara 0. La temperatura y las posiciones/transiciones de puertas aún necesitan comprobarse.

La 0.2.0 corrigió el selector de mapas y detectó las apps OEM confirmadas. La 0.3.0 permite activar **Motor → Activar lectura OEM → Activar escucha** para intentar recibir voltaje, temperatura y aperturas con los formatos encontrados en la unidad, sin órdenes ni consultas a la MCU. No muestra valores antes de recibirlos. **La superficie CarPlay dentro del widget sigue sin integración verificada.** El estado exacto está en [NATIVE_INTEGRATION.md](NATIVE_INTEGRATION.md) y [los hallazgos del equipo](docs/DEVICE_FINDINGS.md).

La 0.3.1 escucha también el canal alternativo de aperturas y el perfil MCU. Solo interpreta la rama de puertas verificada si recibe el perfil correspondiente del equipo; no lo deduce del modelo de coche. **Motor** y el informe exportado muestran eventos recibidos/interpretados por canal y hasta 24 cabeceras CAN, sin guardar el contenido de las tramas.

La 0.3.2 añade **Motor → Solicitar temperatura OEM**, una consulta manual fija usada por la barra original, limitada a una llamada por escucha activa. El inicio sigue siendo pasivo; solo un evento válido se convierte en temperatura. También cuenta máscaras de puertas cero/no cero y usa **Cerrar** como botón principal de Motor para conservar la escucha al salir del diálogo. El servicio ZLink es vinculable y su descriptor está confirmado; su contrato de superficie sigue sin estar disponible. [Datos que solicitar al proveedor](docs/ZLINK_VENDOR_QUESTIONS.md).

## Instalar y empezar

Para instalar **por enlace, sin memoria USB ni ADB**, sirve la APK desde un PC conectado a la misma red Wi-Fi. Se incluye `scripts/serve-apk.py`; consulta [la guía de descarga local](docs/LOCAL_DOWNLOAD.md). Abre su dirección en el navegador de la pantalla, descarga la APK y permite a ese navegador instalar aplicaciones cuando Android lo solicite. La autorización de instalación es independiente de las opciones de depuración protegidas por contraseña.

1. Con el coche estacionado, descarga y abre `MustangOS-debug.apk` desde el enlace local. Como alternativa, el archivo de `output/` también puede copiarse desde una USB y abrirse con el gestor de archivos. Si ADB está disponible:

   ```powershell
   adb install -r .\output\MustangOS-debug.apk
   adb shell am start -n com.mustangos.launcher/.ui.MainActivity
   ```

2. Abre Mustang OS como una aplicación normal. No cambia automáticamente el Home predeterminado.
3. En **Ajustes → Elegir launcher original**, selecciona la interfaz del fabricante. Prueba **Abrir launcher original** antes de elegir Mustang OS como Home.
4. Pulsa **CarPlay**. Se detecta el ZLink confirmado si está instalado; cuando hay varios candidatos se permite elegir. Se guarda el componente resuelto. No se instala, actualiza ni reinicia ZLink, ni se borran datos o activación.
5. Los accesos a radio, teléfono y música detectan las apps del firmware confirmadas. Puedes cambiarlas en **Ajustes**. El selector de mapas solo muestra apps de mapas Android; si no hay ninguna, explica la alternativa CarPlay. Un permiso OEM denegado se muestra como tal.
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

No se inventan temperaturas, kilometraje, rutas ni canciones. No se solicitan root, control de Bluetooth/Wi-Fi, accesibilidad, micrófono, contactos, audio focus ni permisos privilegiados. No hay permiso de Internet, analítica de la APK ni servicio de GPS en segundo plano. El servicio de acceso multimedia no lee el contenido de las notificaciones.

La única autorización adicional de 0.3.0 es `zjinnova.android.permission.ZLINK_SERVICE`, confirmado como permiso **normal** en el equipo. La inspección comprueba propietario/nivel y lee únicamente el descriptor Binder de un servicio ya activo. No inicia ZLink ni invoca transacciones de aplicación desconocidas. Abre primero CarPlay, regresa y usa **Ajustes → Inspeccionar servicio ZLink**; luego exporta el informe si es necesario.

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
