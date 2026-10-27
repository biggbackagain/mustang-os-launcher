# Registro de verificación · Mustang OS 0.1.0

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
