# Registro de verificación · Mustang OS 0.3.2

## Resultado actual 0.3.2

**61 pruebas de app/núcleo y seis del servidor pasadas; cero fallos.** `scripts/verify.ps1` completó pruebas JVM/Robolectric, lint y APK debug en 31 segundos en este PC. Lint: cero errores, 24 advertencias. La firma APK v2 conserva el mismo certificado y los permisos son idénticos a 0.3.1.

- APK: `output/MustangOS-0.3.2-debug.apk`, alias `output/MustangOS-debug.apk`.
- Versión 0.3.2 / código 5; paquete `com.mustangos.launcher`.
- Tamaño: 2 560 301 bytes.
- SHA-256: `C3FA4204A599F90540B3A697EAAA912692EDD6966FB59B33984E0A43BFF6F692`.

Cinco pruebas nuevas: consulta de temperatura solo manual/una vez por escucha, sin valor inventado y con timeout; adaptador limitado a la solicitud exacta `write(1288, 255)` del SystemUI exportado; respuesta inmediata conservada; rechazo de consulta sin interrumpir la recepción de voltaje; cierre principal de Motor que conserva la preferencia OEM y no dispara la consulta. La carrera de respuesta inmediata se reprodujo antes de corregirla. El test de diálogo procesa los callbacks del looper de Android antes de comprobar el resultado. También se comprueban los conteos de máscaras de puertas cero/no cero.

Distribución local comprobada: página HTTP 200 con versión 0.3.2 y APK descargada desde el puerto 8766 idéntica por SHA-256 al archivo firmado. Código OEM recibido y ZIP del equipo permanecen fuera de Git y del paquete fuente.

**Evidencia física nueva, obtenida con 0.3.1:** 43 eventos OEM, 12.52 V y cinco eventos de puertas 1285, con última máscara 0. El servicio ZLink respondió con `com.zjinnova.zlink.android.core.codec.IPhoneContentPresenter`. Su MediaSession estaba visible con acciones 0 y sin campos de canción. Temperatura, posiciones/transiciones de puertas y superficie CarPlay siguen pendientes. La consulta manual 0.3.2 aún requiere prueba en la pantalla; no se presenta la simulación como acceso MCU físico confirmado.

## Historial 0.3.1

**56 pruebas de app/núcleo y seis del servidor pasadas; cero fallos.** Verificación completa con `scripts/verify.ps1`: pruebas JVM/Robolectric, lint y APK debug; `BUILD SUCCESSFUL` en 35 segundos en este PC. Lint: cero errores, 24 advertencias. La firma APK v2 está verificada y conserva el certificado de las versiones anteriores.

- APK: `output/MustangOS-0.3.1-debug.apk`, alias `output/MustangOS-debug.apk`.
- Versión 0.3.1 / código 4; paquete `com.mustangos.launcher`.
- Tamaño: 2 480 858 bytes.
- SHA-256: `C23090EC7326CA735FD7D0DE8DE07C3DDA3C2AF4E0E0966FBF28FBEE5EF0FE43`.
- Sin cambios de permisos respecto de 0.3.0; la app sigue sin Internet, Bluetooth, UID de sistema ni acceso UART.

Las ocho pruebas añadidas comprueban selección de protocolo solo desde el evento MCU correspondiente, rechazo de versiones mal formadas/perfiles ajenos, contadores separados y límite de memoria de 24 cabeceras sin contenidos de tramas, recepción alternativa de puertas condicionada al perfil observado, diagnóstico de temperatura inválida, conservación de eventos entregados durante la apertura y rechazo de callbacks de clientes cerrados. Esta última regresión se reprodujo como un fallo antes de corregirla.

Distribución verificada: página local HTTP 200 con versión 0.3.1 y descarga desde el puerto 8766 idéntica a la APK firmada por SHA-256. No se comprobó aún su instalación en la pantalla.

**Evidencia física:** fotos de 0.3.0 confirman suscripción TWUtil y 14.1 V en Mustang OS, iguales a la barra original, con 40 eventos de la fuente. El usuario confirma aviso de puertas y temperatura ambiente en la interfaz OEM. **Pendientes en el launcher:** puertas, temperatura, descriptor ZLink y superficie CarPlay. Los resultados automatizados de 0.3.1 no se presentan como una prueba de esas funciones en el automóvil.

## Historial 0.3.0

**48 pruebas de la app/núcleo y seis del servidor pasadas; cero fallos.** Verificación completa: compilación debug, pruebas JVM/Robolectric y lint. Lint: cero errores, 24 advertencias (incluye la escritura síncrona intencionada del marcador de recuperación y comprobaciones del permiso OEM normal). Última ejecución incremental: 28 segundos en este PC.

Nuevas pruebas: cuatro del decodificador OEM (voltaje, invalidación, temperatura con signo/conversión y máscara de aperturas); seis del lector pasivo (no abrir si está desactivado, rechazo sin datos inventados, solo valores recibidos, liberación, caducidad y recuperación de interrupción); dos del descriptor ZLink (rechazo de permiso privilegiado y lectura/liberación del servicio normal, con flags 0 y sin startService).

- APK: `output/MustangOS-0.3.0-debug.apk` y alias `MustangOS-debug.apk`.
- Versión: 0.3.0 / código 3; paquete propio sin cambios.
- Tamaño: 2 535 914 bytes.
- SHA-256: `438772781968D39FD380E4D6FD9BD8E4C1FD67330B9D904EFD0F14DB3FF2DFBA`.
- Firma v2 verificada y mismo certificado que 0.1.0/0.2.0; actualización sin desinstalar.
- Nuevo permiso: `zjinnova.android.permission.ZLINK_SERVICE`, confirmado como normal en el manifiesto y PackageManager de la unidad. El código vuelve a verificar propietario/nivel antes de vincular.

Hardware confirmado por el ZIP: SDK 29 / RELEASE 13, ZLink 6.1.06, MediaSession visible con acciones 0 en esa captura, clase TWUtil visible, formatos de alimentación/temperatura/aperturas identificados estáticamente. [Evidencia y límites del análisis](docs/DEVICE_FINDINGS.md).

**Pendiente:** aceptación del lector OEM desde Mustang OS, recepción de datos reales, correlación de puertas y descriptor del servicio ZLink en la pantalla. La superficie CarPlay dentro del widget sigue sin integración verificada. Las pruebas simuladas del transporte no prueban acceso al firmware.

## Historial 0.2.0

### Resultado 0.2.0

**36 pruebas de la app y seis del servidor pasadas; cero fallos.** Gradle completó `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. Lint: cero errores y 19 advertencias. La última verificación completa duró 26 segundos en el PC; no es una medida del T100.

| Suite actual | Pruebas |
|---|---:|
| IntegrationPolicyTest | 9 |
| SpeedFilterTest | 4 |
| LauncherTest | 14 |
| IntegrationLifecycleTest | 3 |
| DashboardRenderTest | 2 |
| NativeInspectionTest | 4 |
| DownloadServerTest, Python | 6 |

Nuevas regresiones verificadas: acceso automático a los paquetes OEM instalados de prueba, exclusión de radio del selector de mapas, rechazo de asignaciones inválidas y explicación de permisos OEM. Se comprueba que el voltaje Android no se promocione a voltaje MCU y que el ZIP exporte solo manifiestos/DEX de paquetes permitidos, excluyendo assets de activación y entradas ajenas. La inspección no inicia servicios OEM.

El servidor se prueba contra HTTP real con archivos aislados: descarga idéntica, rutas ajenas 404, subida sin token 403, origen externo 403, contenido no ZIP 415 y ZIP guardado sin extraer entradas ni ejecutarlas. La descarga de la APK 0.2.0 desde el enlace LAN también se verificó por SHA-256.

- APK: `output/MustangOS-0.2.0-debug.apk` y alias `output/MustangOS-debug.apk`.
- Paquete: `com.mustangos.launcher`; versionCode 2; versionName 0.2.0.
- Tamaño: 2 455 398 bytes.
- SHA-256: `88CCA1D8138E83799CCECDD309F81C3377CF353CEB46A22EFBB5BD0872378B00`.
- Firma v2 verificada. Certificado igual al de la APK 0.1.0 descargada previamente: se puede actualizar sin desinstalar.
- Sin permisos nuevos de Internet, Bluetooth, UART o control MCU.

Evidencia física recibida: fotos del selector con paquetes OEM reales y confirmación del usuario de puertas, temperatura y voltaje en la interfaz del fabricante. El usuario abrió ZLink desde la 0.1.0 y comprobó que el mapa permanece en ZLink. La incrustación CarPlay, adquisición de datos MCU y comportamiento de la 0.2.0 en la pantalla **siguen pendientes**. La exportación del equipo y su análisis serán la siguiente evidencia necesaria.

## Historial de la entrega inicial 0.1.0

Fecha de la entrega: 26 de octubre de 2026, zona del usuario America/Mexico_City.

## Resultado automatizado final

**29 pruebas pasadas, 0 fallos, 0 errores. APK de depuración compilada y firma verificada.**

| Suite | Pruebas | Resultado |
|---|---:|---|
| IntegrationPolicyTest · Kotlin/JVM | 9 | PASÓ |
| SpeedFilterTest · Kotlin/JVM | 4 | PASÓ |
| LauncherTest · Robolectric | 11 | PASÓ |
| IntegrationLifecycleTest · Robolectric | 3 | PASÓ |
| DashboardRenderTest · render nativo Robolectric | 2 | PASÓ |

La suite Android comprueba arranque sin permisos en API 26, 32 y 35; ausencia y detección de ZLink por una actividad instalada de prueba; apertura explícita del componente elegido; paquete desaparecido; retorno stop/start; ausencia de metadatos; revocación de acceso; callbacks; despacho de siguiente al controlador elegido; rechazo de pausa no soportada; caducidad GPS y liberación del listener al detenerse.

Las pruebas de layout verifican controles grandes a 1280×720 y controles contenidos, iconos y etiquetas completos a 853×480 dp, equivalentes aproximadamente a una pantalla 1280×720 a 240 dpi. Las fuentes y ubicaciones de las pruebas son fixtures de laboratorio, no datos del coche ni prueba de compatibilidad ZLink.

```powershell
.\scripts\verify.ps1
# Ejecuta: :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Resultado final de Gradle: `BUILD SUCCESSFUL`, 24 segundos en este PC para la última ejecución incremental. Este tiempo **no** mide el arranque de la APK ni el rendimiento del T100.

Lint: **0 errores, 19 advertencias**, sin baseline para ocultar errores. Quedan avisos de versiones más recientes, orientación fija futura, preferencias sincronizadas durante un crash, sugerencias KTX, pesos anidados y posible overdraw. Las preferencias normales se escriben de forma asíncrona; solo los códigos de crash se guardan de forma síncrona antes de delegar al handler original. El informe íntegro se conserva en `docs/test-results/lint-results-debug.txt`.

## APK entregada

- Ruta: `output/MustangOS-debug.apk`.
- Tamaño: **2 580 669 bytes**, aproximadamente 2.46 MiB.
- Paquete: `com.mustangos.launcher`, versión `0.1.0` / código `1`.
- Mínimo API 26; target/compile API 35; sin bibliotecas nativas propias.
- `apksigner verify --verbose`: PASÓ, firma APK v2, un firmante de depuración.
- `aapt dump badging`: PASÓ; actividad `com.mustangos.launcher.ui.MainActivity`.
- Permisos comprobados: ubicación aproximada/precisa, estado de red y permiso interno de receptores de AndroidX. No solicita Internet, root, micrófono, contactos ni MEDIA_CONTENT_CONTROL.
- SHA-256: `1C3D4788D82CA5639687ADE28D57877303CEA232BF5C24C069B2496A267F2FAF`.

## Evidencia guardada

- XML JUnit de las cinco suites: `docs/test-results/`.
- [Render nativo 1280×720](docs/dashboard-1280x720.png).
- [Render compacto 853×480](docs/dashboard-853x480.png).
- Los renders proceden de la Activity y recursos Android reales bajo Robolectric; la hora y red pertenecen al entorno de pruebas. No son fotografías de la pantalla ni un mockup que simule datos CarPlay.
- `git diff --check`: sin errores de whitespace.

Durante la verificación se corrigieron dos errores de lint de compatibilidad y el desbordamiento del layout compacto, reproducido por una prueba antes de la corrección. Los fixtures MediaController incorporan un binder de prueba válido. La revisión final usa render nativo para medir tipografía en vez de las métricas simplificadas del modo gráfico legado.

## Hardware: pendiente

La comprobación inicial ADB encontró **0 equipos autorizados conectados**. El usuario preparará una conexión por red. No se ha instalado esta APK en el T100 ni se ha probado ZLink 6.1.06, CarPlay inalámbrico, Apple Music del iPhone, Apple Maps, GPS físico, llamadas, cámara de reversa, mandos del volante o retorno al Home OEM.

Las filas de [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md) permanecen pendientes hasta obtener resultados reales. No se ha medido PSS, tiempo de arranque ni fluidez en la pantalla. El tamaño pequeño de APK y las pruebas JVM no demuestran esas medidas.

## Distribución local por enlace

Se añadió un servidor de descarga limitado a la APK y una página de instalación. Verificaciones manuales desde el PC: página HTTP 200, descarga HTTP completada con SHA-256 idéntico a la APK entregada y acceso a otro archivo del proyecto rechazado con HTTP 404. El servidor no cambia la APK ni sus resultados de compilación. Acceso desde el navegador de la pantalla e instalación: pendientes de confirmar por el usuario. ADB no pudo conectarse en el puerto probado y el usuario indicó que la depuración está protegida por contraseña.
