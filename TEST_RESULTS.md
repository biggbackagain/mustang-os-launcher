# Registro de verificación · Mustang OS 0.8.1

## Resultado actual 0.8.1

- `scripts/verify.ps1` completo: 60 pruebas de núcleo y 232 de app, cero fallos/errores; lint debug y release con 0 errores; APK debug y release compiladas.
- Release: `output/MustangOS-0.8.1.apk`, alias `output/MustangOS.apk`, 8 397 881 bytes; versión 0.8.1 / código 17, sin depuración. SHA-256 `958788289C7FCF931C73CB7656E355FBD7EF6CD3DDD4D551F9B5652AF4C1E389`. Misma firma que 0.7.0/0.8.0.
- Evidencia física (ZIP `20261029-044447-d550b8cd.zip`): música 1109/0, portadas 14 decodificadas de 15, guía de ruta 149 válidas y 0 rechazadas con tamaños de campo iAP2, GPS sin posición en vivo. [Análisis](docs/RELEASE_081.md).

## Historial 0.8.0


- `scripts/verify.ps1` completo: 59 pruebas de núcleo y 231 de app, cero fallos/errores/omitidas; lint debug y release con 0 errores (236 advertencias); APK debug y release compiladas.
- Release: `output/MustangOS-0.8.0.apk`, alias `output/MustangOS.apk`, 8 397 249 bytes; versión 0.8.0 / código 16, paquete `com.mustangos.launcher`, target 35, sin depuración. SHA-256 `7AF60E73BB7061753A967003AF5B050DBB5822254923D42A5932DF0C7D1FA72E`.
- Debug: `output/MustangOS-0.8.0-debug.apk`, 9 222 998 bytes.
- Certificado de firma idéntico al de 0.7.0 (SHA-256 `ea344a9b…a24117`): instala encima conservando datos. Permisos sin cambios.
- Nuevas pruebas: decodificador de guía iAP2 (tipos 8/9), ventana de portada, estado iAP2, JPEG con prefijo, préstamo de portada solo para la misma canción, geometría de todas las familias/carrocerías dentro de su envolvente con índices de 16 bits, y puertas/ruedas/luces independientes en cada fotograma.
- El render OpenGL se revisó en una vista previa WebGL con los mismos shaders y la misma geometría (`docs/render-3d-*.png`). Robolectric no ejecuta OpenGL: en las pruebas se dibuja el respaldo Canvas.

**Pendiente en el coche:** portada con cambios de canción y con ZLink al frente, guía de ruta (depende de que ZLink publique los tipos 8/9), rendimiento del render en la GPU de la unidad y la barra que se reduce sola. [Entrega 0.8.0](docs/RELEASE_080.md).

## Historial 0.7.0

## Resultado actual 0.7.0

**282 pruebas app/núcleo y once del servidor pasadas, cero fallos/errores/omitidas.** Verificación integral final con `scripts/verify.ps1`: 1 minuto 11 segundos, pruebas, lint debug/release y ambos instaladores. Lint: cero errores y 217 advertencias por variante; sin nuevos baseline o supresiones. Las capturas de prueba se revisaron visualmente, incluidas puertas, faros, color y dashboard.

- Release: `output/MustangOS-0.7.0.apk`, alias `output/MustangOS.apk`, 8 330 661 bytes; versión 0.7.0 / código 15, paquete `com.mustangos.launcher`, min SDK 26 / target 35, sin depuración.
- SHA-256 release: `42396197A98C74350921DD14F37A61C3031D7EBD55CDBDCB5535D4781269EAA5`.
- Debug: `output/MustangOS-0.7.0-debug.apk`, 9 310 344 bytes; SHA-256 `3B8D419431ECDCCE6EEBC83B4D0DFC10E7022270E2B255D96B1279226B373CAD`.
- Firma v2/certificado anterior y ausencia de permisos nuevos verificados; APK histórica 0.6.2 sin modificar. No se generaron ni exportaron llaves.

Regresiones nuevas: aprendizaje de bits independientes de puerta y rechazo de aperturas ambiguas; geometría 3D de puerta izquierda/derecha, faros, color y ruedas; inicio/pausa de movimiento con velocidad válida; iluminación de caché y prioridad de TW directo; RPM de eventos reales con caducidad; inicio optativo/único y cierre del flujo 1291; tuplas exactas de solicitud/cierre reflejadas en un vendor de prueba; PNG con dimensiones/tamaño acotados; ventana de anuncio y rechazo de imágenes sin propietario, duplicadas o tardías; transporte de una portada de más de 256 KiB sin perder el mensaje musical siguiente. La regresión de selector se actualizó del perfil 2D al 3D manteniendo las comprobaciones de borrador/confirmación/cancelación.

Durante las pruebas de animación se identificó una limitación del Activity vacío de Robolectric: AttachInfo conservaba ventana GONE aunque la vista estaba adjunta. Se corrigió la fixture para representar una ventana realmente visible; no se retiró la protección de visibilidad del código de producción.

**Evidencia física:** el usuario confirmó los datos de canción y controles de la 0.6.2. No hay todavía un ZIP posterior al diagnóstico del 28 de octubre, 18:08. Portada/asociación, señales del modelo y RPM nuevas deben validarse en el coche; el mapa CarPlay dentro del panel sigue pendiente. [Procedimiento y límites de la entrega](docs/RELEASE_070.md).

## Historial 0.6.2

Verificación integral con `scripts/verify.ps1`: **268 pruebas app/núcleo, cero fallos/errores/omitidas**, lint debug/release y ambos instaladores; `BUILD SUCCESSFUL` en 1 minuto 40 segundos. Se añadió después una regresión de tarjeta HUD frente al polling Android y pasó toda `DashboardInteractionTest`: **269 casos únicos verificados**. Las once pruebas HTTP del servidor también pasaron. Lint: cero errores y 198 advertencias por variante, sin baseline ni nuevas supresiones.

- Release: `output/MustangOS-0.6.2.apk`, alias `output/MustangOS.apk`, 8 308 925 bytes. Paquete `com.mustangos.launcher`, versión 0.6.2 / código 14, min SDK 26 / target 35, sin depuración.
- SHA-256 release: `B8AA3012A9B4C44A5C3E5BD7A224B8EDDA370D621622CF145B535B1BACF7D6B2`.
- Debug: `output/MustangOS-0.6.2-debug.apk`, 9 096 200 bytes; SHA-256 `427E495FF7B2E7B9343972C56C9BAF1434512221D66524E8ABE69F8D23CCFD42`.
- Firma v2 y certificado anterior verificados; permisos idénticos a 0.6.1, cuya APK congelada conserva su SHA-256. No se generaron ni exportaron llaves.
- Descarga HTTP verificada en `http://192.168.100.2:8766/?v=0.6.2`: página 200, APK 200 con SHA-256 idéntico al anterior y mapa de México disponible por HEAD con 719 418 229 bytes. Servidor limitado a dos horas; registro privado en `output/http-verification-062.json`.
- Evidencia física de entrada: 17 tramas tipo 10 / 204 bytes en la muestra 0.6.1. Sus cuerpos fueron descartados; la prueba no acredita todavía valores de canción, tiempo, portada o ruta.

Los casos nuevos cubren TLV big-endian, atributos completos, actualización de 12 bytes con solo tiempo, datos parciales, cambio de canción, retroceso de reloj, límites, duplicados, UTF-8 inválido y alineación entre mensajes retenidos/descartados. Los casos Android cubren memoria sin contenido en el informe, una conexión compartida Home/barra, opt-in, cierre del último propietario, incompatibilidad de otra proyección, aislamiento de Bluetooth, reloj sin interpolación, duración sin tiempo y tarjeta resistente al polling. Se usan fixtures sintéticos y sockets de prueba; no ejecutan ZLink ni contactan al coche.

La nueva función debe validarse en la pantalla con [el procedimiento 0.6.2](docs/RELEASE_062.md). Portada asociada y mapa/ruta de CarPlay permanecen pendientes; la APK no los presenta como conseguidos.

## Historial 0.6.1

**254 pruebas app/núcleo y once del servidor pasadas; cero fallos/errores/omitidas.** Verificación integral con `scripts/verify.ps1`: pruebas, lint debug/release y ambos instaladores; `BUILD SUCCESSFUL` en 1 minuto 19 segundos. Lint: cero errores y 183 advertencias en ambas variantes, sin baseline ni supresiones nuevas.

- Release: `output/MustangOS-0.6.1.apk`, alias `output/MustangOS.apk`, 8 298 549 bytes; paquete `com.mustangos.launcher`, versión 0.6.1 / código 13, min SDK 26 / target 35, sin depuración.
- SHA-256 release: `44C07CA050A3D2F711402B58A059531FC8BD79010FA425A363A54AF57BAF7C46`.
- Debug: `output/MustangOS-0.6.1-debug.apk`, 9 076 496 bytes; SHA-256 `2EDD8A67C822D5C884C0FC51780683A2B19DCF85DA2AB235DA128E075343565E`.
- Firma v2/certificado anterior verificados; permisos idénticos a 0.6.0 y APK histórica 0.6.0 sin cambios. No se generaron ni copiaron llaves.
- Página HTTP 200 con versión 0.6.1; APK descargada por `http://192.168.100.2:8766/?v=0.6.1` idéntica por SHA-256. El mapa de México permanece disponible con su tamaño anterior. Ayudante limitado a dos horas.

Las nuevas pruebas verifican fragmentación TCP, límites antes de leer contenido, fin de archivo/truncamiento/deadline, endpoint loopback exacto, ausencia de escritura al socket, cancelación de lectura bloqueada, rechazo de resultados tardíos, falta de reintentos y ausencia de contenido en el informe. Usan sockets de prueba; no contactan al equipo ni a ZLink.

Investigación estática: se revalidaron las constantes del cliente y servidor, ambos 1555, sin ejecutar las bibliotecas. Se identificó cabecera de ocho bytes y servidor de un consumidor; el despachador contiene tipos CP de música, portada PNG y maniobras. Son capacidades compiladas, con recepción/identidad/formato útil todavía pendientes en el dispositivo. La prueba manual nueva solo cuenta cabeceras y descarta contenido; no integra todavía una canción o ruta en las tarjetas.

**Evidencia física nueva:** el usuario confirmó que la barra Mustang funciona encima de ZLink. Esto acredita la superposición, no una app del iPhone dentro del menú CarPlay. Se descartó la app complementaria/IPA por indicación del usuario. [Investigación, límites y prueba de ocho segundos](docs/INVESTIGACION_ZLINK_20261028.md).

## Historial 0.6.0

Verificación integral con `scripts/verify.ps1`: **247 pruebas app/núcleo, cero fallos/errores/omitidas**, lint debug/release y ambos instaladores; `BUILD SUCCESSFUL` en 1 minuto 24 segundos. Una regresión adicional de selección exclusiva de ZLink se añadió después y pasó con toda la clase `MediaRoutingTest`; son 248 casos únicos verificados. El servidor pasó sus once pruebas. Lint final de producción: cero errores y 172 advertencias en ambas variantes, sin baseline ni supresiones nuevas.

- Release: `output/MustangOS-0.6.0.apk`, alias `output/MustangOS.apk`, 8 289 521 bytes. Paquete `com.mustangos.launcher`, versión 0.6.0 / código 12, min SDK 26 / target 35, no debuggable.
- SHA-256 release: `C6CAAA8FF035D89EADAFE35E6A66A9E4862262408BE43366B2BF1E03905CD840`.
- Debug: `output/MustangOS-0.6.0-debug.apk`, 9 063 236 bytes; SHA-256 `0DF88EEAC4DECDA12D7C24AC83A2159950FEEDDD3088D369033A4B553F21A56C`.
- Firma v2 verificada, mismo certificado SHA-256 `ea344a9b8f345999a123c89eb450bb97a95aba40857febd43177282ab9a24117`. La APK histórica 0.5.0 conserva su hash; no se generaron ni copiaron llaves.
- Permisos añadidos: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`. La barra es opcional, exige autorización de superposición en Android y su servicio no está exportado. No se añaden captura, accesibilidad, ubicación de fondo, Bluetooth ni control del sistema.
- Página HTTP 200 con 0.6.0 en `http://192.168.100.2:8766/?v=0.6.0`; APK descargada idéntica por SHA-256. El mapa de México sigue disponible, con HEAD confirmando 719 418 229 bytes y soporte de rangos. El ayudante caduca dos horas después de iniciarse.

Se probaron permisos ausentes/revocados, notificación, cierre y limpieza de la barra, más su tipo de servicio en API 29 y 35. La nueva regresión comprueba que nunca controla Bluetooth como fallback cuando falta ZLink y conserva la preferencia multimedia del usuario. Los controles anteriores siguen cubiertos por la suite completa.

Para el vehículo se probaron cambio efectivo de rojo a azul conservando capota/ruedas/regiones de faros, original intacto, todas las familias y diez pinturas, borrador hasta confirmar, cancelar sin guardar, validación de año y señal TW 516 separada de ACC. El mapa acepta una posición de red aproximada sin crear velocidad; se verifican caducidad y retirada de listeners, incluido el pasivo.

Renders revisados: selector 1280×720 y compacto 853×480, vista de pintura azul y dashboard 1280×720. En compacto los controles permanecen dentro de un área desplazable y la confirmación queda visible. El dibujo de familias es una ilustración aproximada, no una reproducción exacta de cada Mustang.

**Pendiente físico:** autorización y aspecto de barra sobre ZLink, emisión de iluminación TW 516 y señal/antena GPS de esta pantalla. La captura del usuario prueba que México ya dibuja cartografía, pero no una posición. La nueva entrega no acredita reparar hardware ni extraer la portada/ruta de CarPlay hacia las tarjetas. [Uso y límites](docs/RELEASE_060.md).

## Historial 0.5.0

**237 pruebas app/núcleo y once del servidor pasadas; cero fallos/errores/omitidas.** Verificación integral con `scripts/verify.ps1`: pruebas, lint debug/release y ambos instaladores; `BUILD SUCCESSFUL` en 1 minuto 27 segundos. Lint: cero errores y 146 advertencias en ambas variantes, sin baseline ni supresiones nuevas. Permanecen avisos de textos, layout/accesibilidad y APIs deprecadas; la comprobación física en la pantalla sigue pendiente.

- Release: `output/MustangOS-0.5.0.apk`, alias `output/MustangOS.apk`, 8 264 317 bytes.
- Paquete `com.mustangos.launcher`, versión 0.5.0 / código 11, min SDK 26 / target 35.
- SHA-256 release: `C72DA5915DFF015B1D8D9DF422CA1F6793B028DD418F1B0BFD476F94EF15D0F4`.
- Firma v2 verificada, mismo certificado SHA-256 `ea344a9b8f345999a123c89eb450bb97a95aba40857febd43177282ab9a24117`. Release sin depuración; único permiso añadido: `com.tw.carinfoservice.permission.USE_AIDL`, declarado normal por el APK OEM inspeccionado. Sin generar/copiar llaves. Las APK históricas 0.4.0 y 0.4.1 conservan sus hashes.
- Debug: `output/MustangOS-0.5.0-debug.apk`, 9 026 638 bytes; SHA-256 `69991148D8FA0D4D16DC697C6A551DCB25F0F7ED0FF6DE2D883399A0AB6BC430`.
- Página HTTP 200 en `http://192.168.100.2:8766/?v=0.5.0`, versión y enlace al mapa comprobados. La APK descargada coincide por SHA-256 con la release firmada. El ayudante conserva recepción APK/ZIP y caduca dos horas después de iniciarse.
- Mapa México: `output/offline-maps/mexico.map`, 719 418 229 bytes, SHA-256 `2e3d24665b0b050637dcaa08536119aabed6478804c615fd39e34e81bc66b77f`. HEAD HTTP confirma tamaño y reanudación; dos GET parciales devuelven 206 y bytes idénticos al archivo local, incluido el encabezado Mapsforge. No se duplicó una transferencia completa de 686 MiB para esta comprobación.

Las nuevas regresiones cubren referencias fotográficas y créditos, importación de mapa válida/rechazada conservando el anterior, fondo común, vínculo de caché CarInfoService, lectura y controles de radio condicionados a fuente activa, decodificación/caducidad de velocidad y RPM OEM y atribución de fuente del velocímetro. No se ejecutaron los APK OEM recibidos ni se enviaron comandos para activar velocidad/RPM.

El archivo real de México se abrió con Mapsforge y devolvió calles/objetos en Guadalajara, Colima, Morelia y Ciudad de México sin solicitar cartografía de red. El paquete es independiente de la APK y contiene los tres estados solicitados dentro de México completo. Su copia preparada por el proveedor es del 28 de junio de 2026. Ofrece cartografía offline; no cálculo de rutas ni voz.

Renders nativos revisados: dashboard 1280×720 y 853×480, Apps y Ajustes 1280×720; fondo azul/caballo común y fotografías legibles, sin recortes materiales observados. El catálogo incluye 30 fotografías con atribución/licencia y avisa cuando la referencia es aproximada. Los valores no disponibles no se inventan.

Los controles musicales CarPlay confirmados se conservan; título, artista, portada, tiempo y superficie/ruta del iPhone siguen sin contrato externo compatible verificado. [Cambios e instalación](docs/RELEASE_050.md), [interfaces adicionales inspeccionadas](docs/OEM_APPS_20261028_ADDITIONAL.md).

## Historial 0.4.1

**188 pruebas app/núcleo y ocho del servidor pasadas; cero fallos/errores/omitidas.** Verificación integral con `scripts/verify.ps1`: pruebas, lint debug/release y ambos instaladores; `BUILD SUCCESSFUL` en 1 minuto 7 segundos. Lint: cero errores y 118 advertencias en ambas variantes, sin baseline ni supresiones nuevas. Las dos advertencias añadidas frente a la revisión local anterior son las etiquetas de referencia de imagen de 10sp. Permanecen pendientes avisos de textos, layout/accesibilidad y APIs deprecadas.

- Release: `output/MustangOS-0.4.1.apk`, alias `output/MustangOS.apk`, 2 040 214 bytes.
- Paquete `com.mustangos.launcher`, versión 0.4.1 / código 10, min SDK 26 / target 35.
- SHA-256 release: `62258D455C00E87168DD95877F9C6DC569BAD729F34847A58F41E64C1056DF8E`.
- Firma v2 verificada, mismo certificado SHA-256 `ea344a9b8f345999a123c89eb450bb97a95aba40857febd43177282ab9a24117` que la 0.4.0. Sin depuración, sin permisos nuevos y sin generar/copiar llaves. La APK histórica 0.4.0 conserva su hash.
- Debug: `output/MustangOS-0.4.1-debug.apk`, 2 780 316 bytes; SHA-256 `10B9B8524205C7A2F0F53D9C23354D4634E4901BC7966DD3A7FDC81F6935A042`.
- Página HTTP 200 con versión 0.4.1; descarga por `http://192.168.100.2:8766/` idéntica por SHA-256 a la release firmada. La recepción APK/ZIP sigue habilitada; el ayudante caduca dos horas después de iniciarse. La instalación física corresponde al usuario.

Regresiones verificadas: ranking/cambio de wrapper multimedia; perfil persistente e importación privada de foto; cancelación de importación al restaurar la imagen; exportación de HOME; consulta inicial MCU una vez por apertura, tolerante a rechazo. La etiqueta/descripción de imagen stock se reprodujo incorrecta al elegir un perfil distinto y se corrigió, manteniendo los datos del perfil separados de la referencia visual. Renders de dashboard 1280/853 y Ajustes 1280 revisados; no se detectó recorte material por la nueva etiqueta.

Los controles CarPlay físicos confirmados anteriormente se conservan; metadata actual y superficie CarPlay siguen sin interfaz verificada. Esta entrega autoriza probar los avances y no acredita esas integraciones. No se ejecutaron los APK OEM recibidos. [Cambios y prueba en la pantalla](docs/RELEASE_041.md), [análisis de los APK completos](docs/OEM_APPS_20261028.md).

## Historial 0.4.0

**161 pruebas app/núcleo y siete del servidor pasadas; cero fallos/errores.** Verificación integral con `scripts/verify.ps1`: pruebas, lint debug/release y ambos instaladores; `BUILD SUCCESSFUL` en 1 minuto 16 segundos. Lint: cero errores y 115 advertencias en ambas variantes, sin baseline ni supresiones nuevas. Los avisos pendientes incluyen textos españoles, sugerencias de layout/accesibilidad y APIs deprecadas; no sustituyen la validación física.

- Instalador principal release: `output/MustangOS-0.4.0.apk`, alias `output/MustangOS.apk`.
- Tamaño: 2 015 682 bytes; paquete `com.mustangos.launcher`, versión 0.4.0 / código 9, min SDK 26 / target 35.
- SHA-256 release: `205FFCC228C6B923DF2BDA832608EFD66955917A5E4470637F1B6B02058D7399`.
- Firma v2 verificada, certificado idéntico a 0.3.7; no debuggable según manifest/badging. Se conserva el certificado local existente para permitir actualización, sin generar/copiar llaves. R8 desactivado.
- Variante debug: 2 644 117 bytes, SHA-256 `568812CD9EF10B47714E6F70E37D971F5D25484480056F603267602820B3CE70`.
- Único permiso añadido: INTERNET para el mapa. No se añade GPS de fondo, permisos de Bluetooth/Wi-Fi, captura, accesibilidad ni privilegios OEM.

Distribución local comprobada: página HTTP 200 con 0.4.0 y APK descargada por `http://192.168.100.2:8766/` idéntica por SHA-256 a la release firmada. Página/APK usan no-store para evitar versiones o tokens anteriores en caché; ayudante limitado a dos horas. La instalación desde la pantalla queda por comprobar.

La suite cubre lectura/cancelación de portadas content URI (18 casos), identidad estable frente a copias Binder, cambio de fuente/canción, límites de bytes/dimensiones/tiempo, API multimedia existente, geodesia, caché HTTP, backoff, viewport, cancelación fuera de Main y rechazo de callbacks tardíos. Las regresiones de fallo parcial del mapa y recuperación por caché fallaban antes de su corrección. La cola cancelada se purga y las imágenes vencidas se retiran de memoria al reactivar.

También se comprueban la ampliación/restauración de la misma vista de mapa, preferencias de pantalla y búsqueda sin acentos. Los fixtures modelan visibilidad WindowManager de Robolectric y esperan el callback asincrónico onDismiss; no se relaja el ciclo de vida de producción. Se retiró un atributo de estilo API 27 de la base min 26 en lugar de suprimir lint.

Ninguna prueba automatizada solicita teselas de OpenStreetMap: usa transporte/fuente falsos con PNG locales. Una comprobación manual separada solicitó **solo una tesela global**, con UA MustangOS: HTTP 200, PNG de 6 799 bytes y cabecera Cache-Control válida. Esto verifica el proveedor desde el PC, no la conectividad de la pantalla.

Renders revisados: home 1280×720 y 853×480, Apps, Ajustes y faros. Los valores ausentes permanecen ausentes. Apps usa etiquetas de muestra; el raster nocturno es una imagen sintética de prueba, no una ruta/cartografía o posición real. La atribución está dibujada dentro de la vista nativa, también en pantalla completa; no se obstruye con estado/controles.

**Límites físicos pendientes:** GPS/Internet de la unidad, metadatos que publique ZLink y avisos ACC. El mapa Android integrado no extrae la ruta del iPhone ni ofrece giro a giro dentro del panel. No hay un ZIP nuevo 0.3.7/0.4.0 de la pantalla que permita cerrar la investigación del puente OEM. [Entrega y primer uso](docs/RELEASE_040.md).

## Historial 0.3.7

**112 pruebas app/núcleo y siete del servidor pasadas; cero fallos/errores.** Verificación completa con `scripts/verify.ps1`, `BUILD SUCCESSFUL` en 41 segundos. Lint: cero errores y 75 advertencias, sin cambios de permisos. Se conserva firma v2/certificado de 0.3.6.

- Paquete `com.mustangos.launcher`, versión 0.3.7 / código 8.
- APK `output/MustangOS-0.3.7-debug.apk`, 2 554 269 bytes.
- SHA-256: `4F79CCE8122766AF69DF276CA6DC8F2BF9777549682E81B5611B5571B1BB0DDB`.

Distribución local verificada: página HTTP 200 con versión 0.3.7 y APK descargada por `http://192.168.100.2:8766/` idéntica por SHA-256. El ayudante caduca dos horas después de iniciarse. La instalación desde la pantalla todavía depende de la comprobación del usuario.

Las trece nuevas regresiones comprueban fallback DISPLAY sin inventar artista, prioridad de campos principales/bitmap, cambios/limpieza por callback, flags por sesión sin mezclar fuentes ni exportar valores de canciones/URI, wiring del tablero/informe y selección/copia de clientes HOME/BT/Música con límites y sin recursos/licencias. La omisión de campos alternativos se reprodujo antes de corregirla. El fixture de privacidad ahora usa una FINGERPRINT única: el valor predeterminado de Robolectric coincidía con MODEL/BRAND, que sí son datos permitidos del equipo.

La séptima prueba del servidor comprueba caducidad de dos horas con reloj simulado. La implementación vuelve a aplicar el límite ya descrito por la página/guía; no se mantiene el bucle indefinido de la base modificada.

**Evidencia física nueva de 0.3.6:** el usuario confirma pausa/reanudar y cambio de canción con CarPlay/ZLink+iPhone. Las fotos muestran sesión `com.zjinnova.zlink`, título/artista/portada ausentes y un panel de navegación que solo abre CarPlay. El usuario confirma datos completos dentro de CarPlay usando Apple Music y que el launcher original también abre CarPlay completo. Eso no demuestra que la sesión Android publique esos campos.

**Pendiente:** nuevo ZIP con 0.3.7 durante reproducción/ruta, portada accesible y mapa interactivo en el panel. Esta revisión no implementa superficie CarPlay, carga de URI ni integración específica con una app del iPhone. [Estado y captura necesaria](docs/UPDATE_037.md).

## Historial 0.3.6

**99 pruebas de app/núcleo y seis del servidor pasadas; cero fallos.** Verificación completa con `scripts/verify.ps1`: tests JVM/Robolectric, lint y APK debug; `BUILD SUCCESSFUL` en 38 segundos en este PC. Lint: **cero errores y 75 advertencias**, frente a 88 en la base 0.3.5 del usuario; no se añadió baseline para ocultar errores. Los avisos pendientes incluyen textos del tablero, versiones de dependencias, APIs OEM por reflexión y sugerencias de layout/KTX.

- APK: `output/MustangOS-0.3.6-debug.apk`, alias `output/MustangOS-debug.apk`.
- Paquete `com.mustangos.launcher`, versión 0.3.6 / código 7.
- Tamaño: 2 540 849 bytes.
- SHA-256: `57C5A8D95130B103CFB775C776785C3E0492C3C921C541624607657B46BA257E`.
- Firma APK v2 verificada; certificado idéntico a las entregas anteriores. Permisos sin cambios; se añade solo visibilidad MAIN/APP_MAPS.

Distribución local comprobada: `http://192.168.100.2:8766/` devuelve HTTP 200 y anuncia 0.3.6. La APK descargada por ese enlace coincide por SHA-256 con el archivo firmado. Esta comprobación desde el PC no demuestra conectividad ni instalación desde la pantalla del coche.

La base del usuario se conservó en `8c31274` después de pasar su verificación. La revisión añade pruebas de controles dirigidos sin duplicación, play/pause con estado desconocido, selección/apertura de la fuente, enrutamiento del botón de mapa, datos Android separados del coche, retención y edad OEM, consultas fijas, corte normal de energía, GPS sin velocidad por jitter de red, ACC en API 29/35 y gráficos nativos de Apps/faros. Las cuatro regresiones de interfaz se reprodujeron antes del wiring.

Se corrigió el aislamiento de Application en los fixtures: AndroidViewModelFactory de AndroidX conservaba su singleton entre métodos de Robolectric y registraba intents en una Application anterior. Se restablece solo en pruebas, con una comprobación de identidad del contexto. La producción usa una única Application y no necesita ese cambio. El receptor ACC usa ContextCompat con el flag de exportación explícito, sin suprimir el error de lint.

Renders nativos: dashboard 1280×720, compacto 853×480, Apps con etiquetas de muestra y Mustang con faros activados explícitamente. Los últimos dos son fixtures visuales, no pruebas de apps instaladas ni de encendido físico. Capota/ruedas permanecen completas en compacto y la unidad km/h es legible.

**Pendiente en hardware:** pausa/reanudar con iPhone, comportamiento de consultas OEM 0.3.6, señal GPS y velocidad en recorrido, recepción de avisos ACC. El mapa CarPlay dentro del panel sigue sin contrato verificado. [Cambios y protocolo de comprobación](docs/UPDATE_036.md).

## Historial 0.3.2

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
