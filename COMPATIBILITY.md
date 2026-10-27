# Compatibilidad y límites

## Equipo objetivo

Estado 0.3.1: [NATIVE_INTEGRATION.md](NATIVE_INTEGRATION.md) y [los hallazgos reales](docs/DEVICE_FINDINGS.md) registran SDK 29 / RELEASE 13, acceso TWUtil y voltaje OEM de 14.1 V comprobados en Mustang OS. Las puertas, temperatura ambiente y superficie CarPlay siguen sin recepción/integración confirmadas en el launcher. El diagnóstico ampliado distingue eventos por canal y solo aplica la rama alternativa de puertas después de recibir el perfil MCU correspondiente. El voltaje BatteryManager conserva su origen Android.

T10S / T100, Allwinner A133 / sun50iw10p1, Cortex-A53, 2 GB RAM, 1280×720, sin root. Los datos de firmware, MCU, CAN y PT2313/QN8035 provienen del usuario, no de una inspección de hardware realizada aquí.

La app consulta `Build.VERSION.SDK_INT` y muestra también `Build.VERSION.RELEASE`. API 32 corresponde a Android 12L y API 33 a Android 13: se decide por SDK, no por el nombre comercial. Un firmware puede alterar propiedades del sistema; el informe no equivale a una auditoría de conformidad Android. [Plataformas oficiales Android](https://developer.android.com/tools/releases/platforms).

## Requisitos

- Android API 26 o superior. Compilación SDK 35; sin bibliotecas nativas propias: independiente de ARM32/ARM64.
- Actividad principal horizontal y Home opcional. El firmware conserva control del arranque, multitarea, cámara, radio y controles OEM.
- GPS precisa necesita permiso `ACCESS_FINE_LOCATION` y proveedor GPS real. Se solicitan FINE y COARSE juntas; una concesión aproximada no habilita el velocímetro. No se pide ubicación en segundo plano.
- Descubrimiento multimedia: acceso opcional del usuario a `NotificationListenerService`, MediaSessionManager y callbacks de MediaController. En Android 8.0 se consulta la lista de listeners autorizados; desde API 27 se usa NotificationManager.
- Visibilidad de aplicaciones limitada a los intents MAIN/LAUNCHER, MAIN/HOME y VIEW/geo declarados mediante `<queries>`. No se usa `QUERY_ALL_PACKAGES`. Una aplicación OEM sin actividad visible o exportada puede no ser abrible desde una APK normal. [Visibilidad de paquetes](https://developer.android.com/training/package-visibility/declaring).

## Diseño y rendimiento

- Tres columnas flexibles; controles principales de al menos 48 dp; tipografía clara y contraste alto. Objetivo 1280×720, con ajustes según densidad real pendientes del equipo.
- Iconos vectoriales propios y una imagen JPEG de 900×600 (~85 KB) en `drawable-nodpi`, sin motor 3D.
- Animación de velocidad de 280 ms, cancelable y respetando las animaciones del sistema. No hay animaciones continuas de fondo.
- GPS a intervalos mínimos de un segundo solo con el launcher visible. Lecturas sin velocidad, fuera de orden, con precisión horizontal >50 m, precisión de velocidad >5 m/s o antigüedad >5 s se rechazan. Filtro exponencial y parada a cero cuando existe una medición real <1 km/h. No deriva velocidad por distancia entre coordenadas.
- Portadas limitadas a 384 px; no se siguen URLs de portada ni se descarga información de Internet.
- No se solicitan audio focus ni capturas de pantalla; no se envían teclas multimedia globales. El único envío de teclas es dirigido al MediaController elegido si este solo admite play/pause combinado.

## Matriz de estado

| Categoría | Funciones |
|---|---|
| Implementado, con pruebas automatizadas | Política de fuente; controles por capacidades; progreso; filtro GPS; apertura de actividad elegida; estado sin ZLink; arranque sin permisos; retorno de actividad; layout base; diagnóstico |
| Implementado, pendiente del T100 | GPS real; acceso multimedia autorizado por firmware; apertura de radio/teléfono/ZLink OEM; regreso Home; señal de cámara de reversa y mandos sin regresiones |
| Viable, aún no implementado | Transporte OBD2 ELM327 y PIDs confirmados; mapas Android nativos dentro de un módulo con SDK y licencia propios |
| Sin integración pública verificada | Estado conectado de CarPlay; maniobras Apple Maps/Waze del iPhone; incrustación de la superficie interactiva ZLink; metadatos Apple Music si ZLink no los publica |

La cámara y los mandos se dejan bajo responsabilidad del fabricante. No se declara probado que sigan funcionando: hay que ejecutar el plan manual antes de usar Mustang OS como Home habitual. El velocímetro es orientativo y no reemplaza los instrumentos originales.

## Toolchain

AGP 8.13.2, Gradle 8.13, JDK 17, Build Tools 35.0.0, Kotlin 2.2.21, AndroidX Activity 1.10.1, Lifecycle 2.8.7 y coroutines 1.10.2. Se fijan versiones, sin dependencias dinámicas. La combinación AGP/Gradle/JDK sigue la [tabla de compatibilidad de Android](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
