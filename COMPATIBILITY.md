# Compatibilidad y límites

## Equipo objetivo

Estado **0.4.0**: mapa Android nativo en el panel con zoom/centrado/ampliación, portadas content URI limitadas, búsqueda y ajustes de producto. Se añade INTERNET para cartografía, desactivada hasta Centrar/Ajustes. La ruta del iPhone sigue sin superficie ZLink integrada. [Entrega y límites](docs/RELEASE_040.md).

Estado 0.3.7: controles confirmados con el iPhone en 0.3.6; metadatos y mapa integrado siguen pendientes. Se corrige la omisión de campos DISPLAY y el ZIP añade clientes HOME/BT/Música que faltaban. [Nueva evidencia necesaria y límites](docs/UPDATE_037.md).

Estado 0.3.6: [correcciones actuales](docs/UPDATE_036.md) sobre los cambios del usuario, conservados en `8c31274`. Los datos OEM anteriores se retienen con su antigüedad, sin convertir BatteryManager en voltaje del coche o temperatura exterior. Las consultas usan formatos fijos observados en la unidad. Multimedia evita envíos duplicados y abre CarPlay para la fuente del teléfono. GPS distingue red/GNSS y ACC se observa sin controles de potencia. La verificación física de esta revisión sigue pendiente. La superficie CarPlay dentro del panel continúa sin contrato disponible.

T10S / T100, Allwinner A133 / sun50iw10p1, Cortex-A53, 2 GB RAM, 1280×720, sin root. Los datos de firmware, MCU, CAN y PT2313/QN8035 provienen del usuario, no de una inspección de hardware realizada aquí.

La app consulta `Build.VERSION.SDK_INT` y muestra también `Build.VERSION.RELEASE`. API 32 corresponde a Android 12L y API 33 a Android 13: se decide por SDK, no por el nombre comercial. Un firmware puede alterar propiedades del sistema; el informe no equivale a una auditoría de conformidad Android. [Plataformas oficiales Android](https://developer.android.com/tools/releases/platforms).

## Requisitos

- Android API 26 o superior. Compilación SDK 35; sin bibliotecas nativas propias: independiente de ARM32/ARM64.
- Actividad principal horizontal y Home opcional. El firmware conserva control del arranque, multitarea, cámara, radio y controles OEM.
- GPS precisa necesita permiso `ACCESS_FINE_LOCATION` y proveedor GPS real. Se solicitan FINE y COARSE juntas; una concesión aproximada no habilita el velocímetro. No se pide ubicación en segundo plano.
- Descubrimiento multimedia: acceso opcional del usuario a `NotificationListenerService`, MediaSessionManager y callbacks de MediaController. En Android 8.0 se consulta la lista de listeners autorizados; desde API 27 se usa NotificationManager.
- Visibilidad de aplicaciones limitada a MAIN/LAUNCHER, MAIN/HOME, MAIN/APP_MAPS y VIEW/geo declarados mediante `<queries>`. No se usa `QUERY_ALL_PACKAGES`. Una aplicación OEM sin actividad visible o exportada puede no ser abrible desde una APK normal. [Visibilidad de paquetes](https://developer.android.com/training/package-visibility/declaring).

## Diseño y rendimiento

- Tres columnas flexibles; controles principales de al menos 48 dp; tipografía clara y contraste alto. Objetivo 1280×720, con ajustes según densidad real pendientes del equipo.
- Iconos vectoriales propios y una imagen JPEG de 900×600 (~85 KB) en `drawable-nodpi`, sin motor 3D.
- Animación de velocidad de 280 ms, cancelable y respetando las animaciones del sistema. No hay animaciones continuas de fondo.
- GPS a intervalos mínimos de un segundo solo con el launcher visible. Lecturas sin velocidad, fuera de orden, con precisión horizontal >50 m, precisión de velocidad >5 m/s o antigüedad >5 s se rechazan. Filtro exponencial y parada a cero cuando existe una medición real <1 km/h. No deriva velocidad por distancia entre coordenadas.
- Portadas limitadas a 384 px; bitmap o content URI de la sesión elegida, máximo 4 MiB y sin URLs remotas. El mapa sí descarga imágenes HTTPS del área vista, con caché y límites; no carga recorridos ni mapas por anticipado.
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
