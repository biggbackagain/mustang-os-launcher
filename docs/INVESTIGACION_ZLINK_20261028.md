# Integración de Mustang OS con ZLink · revisión del 28 de octubre de 2026

## Conclusión

La barra Mustang sobre ZLink está **confirmada por el usuario en la pantalla real**. Es la base disponible para una integración visual: mantener CarPlay como superficie original y añadir controles/paneles propios que puedan moverse o cerrarse. No equivale a una app del iPhone dentro del menú CarPlay, ni a extraer su vídeo.

Además se identificó un **canal interno de metadatos concreto**, que merece una prueba limitada. No está validado en vivo y atiende un cliente a la vez; no debe conectarse automáticamente ni presentarse como API pública. La revisión no acredita todavía título, portada, instrucciones o mapa de CarPlay en tarjetas independientes.

Se descarta para este alcance la app complementaria del iPhone, la IPA y la compilación en la nube, por indicación del usuario. La base 0.6.0 permanece versionada y no se borra.

## Evidencia física que sí tenemos

- iPhone 16 Pro Max con iOS 27, CarPlay inalámbrico y Apple Music.
- El usuario confirmó anteriormente los controles musicales de Mustang sobre ZLink.
- En esta revisión confirmó específicamente **la barra Mustang encima de ZLink**.
- La canción y portada se ven dentro de CarPlay; no se han recuperado en las tarjetas de Mustang.
- El usuario probó Android Auto y tampoco obtuvo la integración de tarjetas esperada. Bluetooth normal sí entrega información musical, pero sin portada en su prueba.
- El mapa de México dibuja cartografía en su captura. La ausencia de posición GPS del equipo es un problema distinto de la falta de mapa/ruta del iPhone.
- El launcher original no muestra CarPlay en una tarjeta; su función de ventana no ofrece ZLink o lo declara incompatible.

## Qué podemos y qué no podemos afirmar

| Función | Estado | Condición o límite |
|---|---|---|
| Barra Mustang sobre CarPlay | Confirmada físicamente | Permiso de superposición concedido; ventana propia, pequeña y cerrable |
| Controles de música dirigidos a ZLink | Confirmados anteriormente | Sesión disponible; el envío no acredita que la app publique título/portada |
| Más controles o paneles Mustang sobre ZLink | Viable con la vía confirmada | Ocupan píxeles del CarPlay original; no redimensionan su vídeo ni deben tapar sus controles |
| Datos OEM en paneles propios | Viable según las fuentes disponibles | Voltaje/puertas/temperatura/iluminación requieren recepción real; no se convierten en datos del iPhone |
| Título/artista/progreso de CarPlay en nuestras tarjetas | Candidato interno identificado | Falta verificar recepción del canal, formatos y correspondencia con la canción actual |
| Portada real de CarPlay | Candidato interno identificado | Hay un evento PNG en el cliente nativo; no se ha recibido ni validado una portada real |
| Próxima maniobra, distancia o ETA de CarPlay | Candidato interno identificado | Guía de ruta y maniobras son datos separados del vídeo del mapa |
| Mapa completo e interactivo de CarPlay en una tarjeta | Sin interfaz disponible verificada | Requiere una superficie/viewport y entrada táctil compatibles del proveedor |
| GPS del iPhone para nuestro mapa | Sin fuente disponible verificada | Los nombres de callbacks de maniobra no demuestran que publiquen coordenadas GPS |
| Portada por Bluetooth | No demostrada en esta unidad | Depende de la pila/reproductor OEM y de lo que publiquen; una sesión con título no implica imagen |
| Lanzar nuestro APK Android como app nativa del iPhone en CarPlay | No mediante esa instalación | Las apps CarPlay del iPhone requieren desarrollo, firma y capacidades de Apple; la barra es otra vía |

## Nuevo hallazgo: transporte HUD/metadata

Se analizaron estáticamente las bibliotecas ARM de **com.zjinnova.zlink 6.1.06** suministradas por el usuario. No se cargaron ni ejecutaron. El DEX protegido no se desempaquetó, no se eludió la activación y no se consultaron archivos privados de la unidad.

En `libzlink_metadata.so`, el hilo lector crea un socket TCP y conecta a **127.0.0.1:1555**. La dirección, el argumento de puerto y las llamadas de lectura se comprobaron en las instrucciones ARM, no solo por una coincidencia de cadenas.

El encuadre observado tiene ocho bytes de cabecera: dos enteros de 32 bits little-endian, primero tipo y después longitud del contenido. El cliente recibe la cabecera, lee el contenido y lo entrega a un despachador. Para la ruta observada no se identificó un mensaje de suscripción enviado por el cliente.

El despachador contiene, entre otros, estos identificadores:

| Tipo | Nombre/función interna observada |
|---|---|
| 8 | Guía de ruta CP |
| 9 | Maniobra de guía CP |
| 10 | Información multimedia CP |
| 14 | Información de portada CP en PNG |

Los formatos internos contienen nombres de título, artista, álbum, duración, posición de reproducción y detalles de maniobra. Su presencia demuestra capacidad interna compilada, no entrega activa en este equipo. La decodificación completa, asociaciones de portada/canción y unidades deben verificarse antes de mostrar valores reales.

En `libzjL10001.so` se identificaron el servidor correspondiente, el inicio del hilo y el envío de cabecera/contenido. La ruta inspeccionada mantiene **un descriptor de cliente**, espera su cierre y acepta el siguiente; no demuestra difusión simultánea a varios consumidores. Un cliente ya conectado puede dejar una conexión nueva esperando sin entregar datos. Por eso conexión TCP sin tramas no demuestra ausencia definitiva de la interfaz.

La configuración recibida tiene `enable_hud: false` por defecto; `ceres` selecciona el perfil `cnf_dingwei-t100` y no sobrescribe esa bandera. T100 también declara resolución adaptativa. No se infiere que cambiar esas dos opciones habilite una API externa: la configuración efectiva, negociación con el iPhone y clientes activos siguen sin comprobarse. No se cambia la configuración ni se cargan esas bibliotecas en Mustang.

El puerto tampoco autentica al proceso que escucha. Una conexión o una cabecera plausible se registra como **canal candidato**, no como identidad verificada de ZLink. No se leen otros puertos ni se escanea la red.

## Prueba preparada en 0.6.1

**Ajustes → Comprobar metadatos de ZLink** permite iniciar una sola observación manual. **Probar en CarPlay** inicia la muestra y abre la app de proyección elegida; si la barra ya estaba habilitada y autorizada, utiliza ese modo confirmado.

1. Conecta el iPhone a CarPlay y reproduce una canción; prepara una ruta en la app del iPhone.
2. Regresa a Mustang OS y abre la comprobación.
3. Pulsa **Probar en CarPlay**. Durante la muestra cambia de canción una vez desde CarPlay y espera unos diez segundos.
4. Vuelve al launcher y abre otra vez la comprobación. El resultado también aparece en el diagnóstico exportado.

La conexión tiene un timeout de 800 ms y la lectura se limita a ocho segundos, 64 tramas y aproximadamente 1 MiB de contenido, con cada trama limitada a 256 KiB. El contenido se descarta en bloques pequeños. No se obtiene el stream de salida, no se envía handshake, no se activa HUD, no se cambia de configuración y no hay reconexión automática.

La prueba puede ocupar brevemente el canal si estaba libre; no fuerza el cierre de otros clientes. No se ha ejecutado contra el equipo desde el PC. El resultado tendrá estas interpretaciones:

- **Conexión rechazada:** no hay listener accesible en ese momento, o el firmware lo impide. No se intenta forzar su inicio.
- **Conexión sin tramas:** puede estar en espera por otro cliente, sin eventos durante la ventana o sin metadata habilitada. No se concluye que los datos sean imposibles.
- **Tipos 10/14 reales y repetibles:** justificarían implementar y validar el lector musical siguiente; todavía no acreditan contenido correcto.
- **Tipos 8/9 reales y repetibles:** justificarían investigar una tarjeta de maniobras; no proporcionan por sí solos un mapa completo.
- **Cabecera incompatible:** se detiene sin intentar adivinar el formato o asignarlo a una canción/ruta.

El informe guarda solo estado, tipos, cantidades y bytes descartados. No contiene títulos, imágenes, rutas ni coordenadas.

## Límites de Android y vías descartadas

El manifiesto recibido declara `resizeableActivity=false` en ZLink y MainActivity, y `supportsPictureInPicture=false`. En esta unidad API 29, no hay una interfaz normal verificada para incrustar esa actividad en nuestro panel. La documentación de [pantalla múltiple de Android](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode) y [activity embedding](https://developer.android.com/develop/ui/views/layout/activity-embedding) no convierte otra app arbitraria en un View.

Una [superposición Android](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY) dibuja nuestros controles encima de la ventana existente. Cambiar su tamaño no cambia el viewport de ZLink. Por eso no se propone cubrir medio CarPlay y llamarlo un mapa incrustado.

[MediaProjection](https://developer.android.com/media/grow/media-projection) captura píxeles con autorización; en API 29 no ofrece el aislamiento de una sola app incorporado en API 34. Tampoco entrega metadatos semánticos ni el control táctil de otra app. No se implementa captura/OCR ni inyección como sustituto de una API.

Las [sesiones multimedia Android](https://developer.android.com/reference/android/media/session/MediaSessionManager) y [MediaMetadata](https://developer.android.com/reference/android/media/MediaMetadata) permiten leer lo que publica una sesión autorizada; no obligan a ZLink/BT a publicar portada o el estado interno de CarPlay. Buscar una imagen por texto no acreditaría que sea la portada recibida del iPhone y no se usa como tal.

## Integración con el fabricante

[ZJINNOVA describe Android zLink](https://zjinnova.com/android-zlink.html) como una integración con APK, HAL, controladores, modificaciones del sistema y documentación técnica. El `libzlink SDK` anunciado para Linux/RTOS no es una dependencia Android pública que podamos añadir al Gradle del launcher.

Su [soporte para usuarios](https://www.zjinnova.com/personal.html) indica que zLink depende de la integración del fabricante y recomienda conseguir el paquete compatible a través del vendedor. No se sustituye por un APK descargado al azar ni se modifica su firma/activación. Las nuevas funciones de KALOS anunciadas por el proveedor no documentan una API para nuestras tarjetas y no se usan como prueba de compatibilidad del firmware actual.

Para un mapa incrustado auténtico siguen haciendo falta soporte de superficie/viewport, entrada táctil, ciclo de vida y contrato de metadata compatibles con 6.1.06/T100. No se ha enviado ningún mensaje a terceros.

## Recomendación de implementación

Mantener la barra confirmada y ampliarla solo con controles o datos propios realmente disponibles. Probar el canal identificado antes de invertir en un nuevo panel de música/ruta. Si entrega datos, validar primero música/portada y después maniobras, manteniendo separado el requisito de vídeo del mapa. Si no entrega datos utilizables, conservar CarPlay real en ZLink y comunicar ese límite; no presentar accesos directos, portadas buscadas o mapas independientes como integración conseguida.
