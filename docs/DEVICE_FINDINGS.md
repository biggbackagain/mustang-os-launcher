# Hallazgos del equipo real · 26 de octubre de 2026

## Evidencia recibida

Paquete de compatibilidad enviado voluntariamente por el usuario: 10 599 372 bytes comprimidos, 25 entradas, 27 362 186 bytes sin comprimir. SHA-256 `D563851D4DD7D0EC7D6396EBCDD96BE313D79A9CBD23787E898EE57349565DED`. Se validaron nombres, tamaños y rutas antes de extraer los archivos permitidos al área local de análisis.

El ZIP y el código OEM analizado permanecen fuera de Git y del paquete fuente distribuible. No se ejecutaron aplicaciones del ZIP ni se descomprimieron rutas arbitrarias. JADX 1.5.6 se usó para análisis estático; algunas funciones complejas no se reconstruyeron como Java y se consultó su representación de instrucciones. No se presenta la decompilación como código fuente original completo.

## Sistema y multimedia confirmados

- `Build.VERSION.SDK_INT = 29`, `RELEASE = 13`. El SDK de ejecución corresponde a las APIs Android 10; la etiqueta comercial es inconsistente. SystemUI también declara versión 10. Esto corrige las suposiciones iniciales basadas en API 32 de CPU-Z.
- Resolución 1280×720, densidad 160 dpi, RAM total 1974 MiB; 781 MiB disponibles al exportar.
- Ubicación precisa y acceso a sesiones multimedia: concedidos. En esa captura no había lectura GPS válida.
- Sesiones visibles: `com.tw.bt` y `com.zjinnova.zlink`. ZLink estaba seleccionado; sus acciones publicadas eran `0`. Esto explica que los controles del widget permanecieran deshabilitados. No demuestra que una pulsación deba enviarse ignorando capacidades.
- El informe excluye los contenidos de título, artista y portada. La 0.3.0 añade únicamente indicadores booleanos de presencia para comprobar qué campos publica la fuente sin exportar canciones.

## ZLink instalado

Versión 6.1.06, paquete `com.zjinnova.zlink`, actividad de entrada `com.zjinnova.android.zlink.features.main.MainActivity`.

Su manifiesto declara `resizeableActivity=false` y `supportsPictureInPicture=false` para la presentación principal. `SecondaryScreenService` no está exportado. No hay autorización de incrustación identificada. Junto con SDK 29, esto bloquea la vía estándar de actividad ajena dentro de un panel. No se fuerza redimensionamiento ni se usa captura o inyección de entrada.

El servicio exportado `DaemonService` exige `zjinnova.android.permission.ZLINK_SERVICE`, declarado sin protectionLevel adicional y reportado como **normal (0)** por PackageManager. La 0.3.0 solicita ese permiso normal y ofrece un diagnóstico que:

1. Verifica propietario, nivel normal y declaración exportada del servicio.
2. Usa `bindService` con flags `0`, sin AUTO_CREATE ni `startService`.
3. Lee exclusivamente `IBinder.getInterfaceDescriptor()` y libera la conexión.

No llama transacciones de aplicación desconocidas. Este diagnóstico no constituye una integración de superficie, metadatos o navegación. Su resultado real necesita la ejecución del usuario. El DEX exportado de ZLink contiene un envoltorio de carga; no se elude su protección ni se modifica su licencia o activación para obtener el código restante.

## Protocolo OEM identificado

La clase pública OEM `android.tw.john.TWUtil` existe y sus métodos son visibles desde Mustang OS. El código de SystemUI usa una factoría pública `open(int, short[], int)` y registra un Handler. **La suscripción desde Mustang OS 0.3.0 ya está confirmada en el equipo:** la foto del usuario muestra `OEM · recepción real`, 40 eventos y **14.1 V**, coincidente con la barra OEM. El contador incluye actividad de la fuente; no significa 40 mediciones distintas de voltaje.

En esa misma prueba, Mustang OS muestra `Puertas: sin evento` y no presenta temperatura exterior. El usuario confirma que el aviso grande de puertas y la temperatura ambiente sí aparecen en la interfaz original de Android. Esto confirma disponibilidad en el equipo, sin confirmar todavía su recepción en el launcher. El campo de batería Android de 1.1 °C no es evidencia de temperatura ambiente ni del refrigerante. El GPS solo reportó un satélite usado y ninguna lectura válida de velocidad/precisión/altitud/rumbo.

| Mensaje | Evidencia en los archivos de la unidad | Interpretación usada en 0.3.0 |
|---|---|---|
| 1292 / 0x050C | `StatusBar` actualiza el indicador de voltaje | arg1=0, bit de invalidez apagado; entero de 15 bits en los primeros dos bytes, centésimas de voltio |
| 1288 / 0x0508 | `StatusBarViewInit.setPValue` muestra temperatura | segundo byte con signo; respetar `TEMP_CONVERT`; rechazar el valor inválido y los límites que usa el OEM |
| 1285 / 0x0505 | Handler TWService entrega el primer byte a la vista de aperturas | máscara `byte0 & 0xF8`; no se atribuyen posiciones sin comprobar correlación física |
| 514 / 0x0202 | Suscrito por SystemUI/TWService | Actividad de la fuente, sin convertirla en lectura del motor |

La 0.3.0 solo suscribe estos cuatro eventos mediante la factoría, registra su Handler y libera su propia instancia al ocultarse la app. **No resuelve ni llama `write`, no envía consultas iniciales y no abre UART ni cambia CANBUS.** Por ello una lectura puede permanecer vacía hasta que el fabricante publique un evento de forma natural. El retorno de suscripción rechazada se muestra sin intentar elevar permisos ni reintentar en bucle. La 0.3.1 amplía únicamente las suscripciones como se detalla más abajo.

Si la escucha queda interrumpida de forma inesperada, un marcador persistente impide reactivarla automáticamente en el siguiente arranque. Se puede probar de nuevo manualmente. Los valores se eliminan al cerrar la fuente o cuando su actividad caduca durante 15 segundos. La etiqueta se refiere a lecturas OEM recibidas, no a un diagnóstico de seguridad del automóvil.

El broadcast `com.tw.launcher.dvrinfo` también contiene un campo de voltaje, pero proviene de una lectura de DVR y **no se utiliza como batería del coche**.

## Revisión 0.3.1 tras la prueba física

TWService muestra una segunda ruta de aperturas en 1281. Para tipo 2, modelo 0x41, `arg1=56` entrega el primer byte, enmascarado con 0xF8, al indicador de aperturas. El perfil depende de los dos primeros dígitos hexadecimales y el tercer dígito del tercer grupo de la versión recibida por el evento 266. La versión MCU aportada inicialmente contiene `412`, compatible con esa rama, pero la app **no la presupone**: espera el evento 266 real en cada sesión. Solo se conservan modelo/tipo; se descarta la cadena completa.

La 0.3.1 suscribe 266 y 1281 además de los cuatro códigos iniciales. La interpretación alternativa requiere perfil 0x41/tipo 2 observado y `arg1=56`; otros mensajes 1281 permanecen sin interpretar. La máscara solo indica apertura publicada; no asigna puertas físicas ni afirma estado de maletero. El permiso y transporte siguen siendo los mismos, sin consultas o comandos.

Motor y el diagnóstico exportado separan conteos recibidos/interpretados por código; guardan hasta 24 combinaciones de cabecera CAN (arg1/arg2/longitud y conteo), sin contenidos de tramas. Esto permite distinguir un evento ausente de un evento recibido con formato no interpretado. Los contadores se reinician al abrir la suscripción y se descartan al cerrarla. Se corrigen también la pérdida de un evento entregado durante `open` y el posible callback atrasado de una suscripción ya cerrada. No se presenta esta revisión como puertas/temperatura comprobadas en hardware.

## Pendientes concretos

- Voltaje y acceso TWUtil: confirmados por la prueba física de 0.3.0; falta comprobar continuidad y reconexión.
- Localizar el canal de puertas y la ausencia de temperatura en Mustang OS, contrastando con la interfaz original. TWService también procesa aperturas en `1281 / 0x0501`, con ramas dependientes del modelo y tipo de protocolo. No se aplica una máscara genérica a todos esos mensajes.
- Abrir/cerrar una puerta, comprobar máscara y después calibrar posiciones. No mostrar todas cerradas antes de recibir estado.
- Inspeccionar el servicio de ZLink con CarPlay ya abierto y compartir un informe nuevo.
- Las RPM, refrigerante, kilometraje y velocidad CAN no tienen un contrato verificado; permanecen ausentes. La velocidad GPS sigue siendo una fuente independiente.
- La superficie interactiva CarPlay continúa sin integración verificada dentro del widget. Haría falta un contrato de superficie autorizado del proveedor; el nombre del Binder por sí solo no bastará.

## Segundo informe recibido · 27 de octubre de 2026

ZIP de 0.3.1: 10 599 573 bytes, 25 entradas y 27 362 615 bytes sin comprimir. SHA-256 `0BCC9B21DC61B62E05DF2E7FC2AB3AE15466531DE03649BB9059A6DCB0DDF62F`. Se validaron rutas y tamaños; los manifiestos/DEX OEM son idénticos al primer ZIP por SHA-256. No fue necesario repetir su decompilación.

El informe se generó con la lectura OEM desactivada, cero eventos y sin valores/perfil. El usuario confirma que vio `OEM · recepción real` y **después detuvo la lectura** antes de exportar. `setEnabled(false)` cierra la fuente y borra los valores/contadores: esta captura vacía no prueba un rechazo de suscripción ni la ausencia de puertas/temperatura en los canales nuevos. No había errores registrados. La próxima captura debe generarse mientras la escucha está activa; cerrar el diálogo con **Cerrar** conserva la fuente, mientras que **Detener lectura OEM** la desactiva.

ZLink seguía sin inspeccionar en ese informe; solo estaba visible `com.tw.bt`, con acciones 0 y sin campos multimedia publicados. Esto describe ese instante y no demuestra que ZLink nunca publique una sesión. El descriptor y la superficie CarPlay siguen pendientes. Para capturar ambos diagnósticos: inspeccionar ZLink primero, luego iniciar escucha OEM, abrir/cerrar la puerta con Mustang OS visible, cerrar el diálogo con **Cerrar** y generar el informe antes de detener la escucha. La app conserva el texto generado antes de abrir el selector de archivos.

## Tercer informe recibido · 27 de octubre de 2026

ZIP 0.3.1: 10 599 726 bytes, 25 entradas y 27 363 040 bytes sin comprimir. SHA-256 `E9BD407E4D6CE179E86972285AE05374696C86580ADE9168D000696DA3B1F36F`. Rutas y tamaños validados; todos los manifiestos/DEX OEM coinciden con el primer envío por SHA-256. Sin errores registrados.

Con la escucha activa llegaron **43 eventos**: 28 de actividad (514), **cinco de aperturas (1285)** y diez de alimentación (1292). Último voltaje **12.52 V**; última máscara de aperturas **0**. Esto confirma recepción del canal estándar de puertas en Mustang OS; el informe 0.3.1 solo conserva la última máscara y no demuestra las posiciones ni una transición de abierto a cerrado. No llegaron 1288 (temperatura), 266 (perfil) ni 1281 (canal alternativo). No había señal GPS válida.

El diagnóstico del servicio ZLink sí funcionó: descriptor **`com.zjinnova.zlink.android.core.codec.IPhoneContentPresenter`**, obtenido mediante el permiso normal, sin invocar métodos de aplicación del Binder. Había sesiones de `com.tw.bt` y `com.zjinnova.zlink`, con ZLink seleccionado; sus acciones eran 0 y título/artista/álbum/duración/portada estaban ausentes. Esto describe la publicación de esa sesión en ese instante, no una incapacidad universal de todas las versiones.

Se buscó el nombre exacto del descriptor y contratos de superficie en fuentes públicas; no se localizó AIDL ni una definición de operaciones. El DEX estático exportado sigue siendo un envoltorio sin ese contrato. La [página oficial de Android zLink](https://zjinnova.com/android-zlink.html) ofrece guías de integración a través del equipo técnico, pero no publica en esa página el contrato de `IPhoneContentPresenter`. Los análisis públicos de otras unidades no aportan ese contrato ni prueban compatibilidad con esta versión. El servicio vinculable y su nombre no demuestran que admita una superficie de un launcher externo. No se envían transacciones numeradas por conjetura.

## Revisión 0.3.2: consulta manual de temperatura

En `StatusBarViewInit` de **esta unidad**, la barra registra su Handler y solicita la temperatura mediante `TWUtil.write(1288, 255)` al iniciar. `TWSystemUI` delega exactamente esos dos argumentos al método público, cuya firma `write(int, int): int` también aparece en el informe real. La ausencia de eventos 1288 durante la escucha pasiva justifica probar esa solicitud de lectura; no prueba aún que nuestra APK vaya a recibir la respuesta.

La 0.3.2 conserva el inicio pasivo y añade **Motor → Solicitar temperatura OEM**, como acción manual limitada a una llamada por suscripción activa. El adaptador solo expone esa solicitud fija, sin un método genérico de comandos. El retorno OEM se registra sin interpretarlo como una temperatura ni como confirmación de aceptación. Únicamente un evento 1288 válido actualiza el valor. Si no llega respuesta, se indica tras cinco segundos; el rechazo conserva la escucha pasiva. No consulta la versión MCU ni modifica ajustes, CANBUS, firmware o ZLink.

La misma revisión cuenta por separado máscaras de aperturas cero/no cero para conservar evidencia de transiciones en el próximo informe. **Cerrar** pasa a ser el botón principal de Motor y mantiene la escucha; detenerla continúa siendo una acción explícita. La solicitud de temperatura y la correlación de puertas siguen pendientes de prueba física en 0.3.2.
