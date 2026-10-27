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

La clase pública OEM `android.tw.john.TWUtil` existe y sus métodos son visibles desde Mustang OS. El código de SystemUI usa una factoría pública `open(int, short[], int)` y registra un Handler. La posibilidad de hacerlo desde una APK ordinaria todavía no está confirmada: la clase visible no prueba que su llamada sea aceptada.

| Mensaje | Evidencia en los archivos de la unidad | Interpretación usada en 0.3.0 |
|---|---|---|
| 1292 / 0x050C | `StatusBar` actualiza el indicador de voltaje | arg1=0, bit de invalidez apagado; entero de 15 bits en los primeros dos bytes, centésimas de voltio |
| 1288 / 0x0508 | `StatusBarViewInit.setPValue` muestra temperatura | segundo byte con signo; respetar `TEMP_CONVERT`; rechazar el valor inválido y los límites que usa el OEM |
| 1285 / 0x0505 | Handler TWService entrega el primer byte a la vista de aperturas | máscara `byte0 & 0xF8`; no se atribuyen posiciones sin comprobar correlación física |
| 514 / 0x0202 | Suscrito por SystemUI/TWService | Actividad de la fuente, sin convertirla en lectura del motor |

La implementación propia solo suscribe estos eventos mediante la factoría, registra su Handler y libera su propia instancia al ocultarse la app. **No resuelve ni llama `write`, no envía consultas iniciales y no abre UART ni cambia CANBUS.** Por ello una lectura puede permanecer vacía hasta que el fabricante publique un evento de forma natural. El retorno de suscripción rechazada se muestra sin intentar elevar permisos ni reintentar en bucle.

Si la escucha queda interrumpida de forma inesperada, un marcador persistente impide reactivarla automáticamente en el siguiente arranque. Se puede probar de nuevo manualmente. Los valores se eliminan al cerrar la fuente o cuando su actividad caduca durante 15 segundos. La etiqueta se refiere a lecturas OEM recibidas, no a un diagnóstico de seguridad del automóvil.

El broadcast `com.tw.launcher.dvrinfo` también contiene un campo de voltaje, pero proviene de una lectura de DVR y **no se utiliza como batería del coche**.

## Pendientes concretos

- Activar el lector desde Motor; confirmar suscripción aceptada y contador de eventos.
- Contrastar voltaje y temperatura recibidos con la barra OEM, sin rellenar valores si no llegan.
- Abrir/cerrar una puerta, comprobar máscara y después calibrar posiciones. No mostrar todas cerradas antes de recibir estado.
- Inspeccionar el servicio de ZLink con CarPlay ya abierto y compartir un informe nuevo.
- Las RPM, refrigerante, kilometraje y velocidad CAN no tienen un contrato verificado; permanecen ausentes. La velocidad GPS sigue siendo una fuente independiente.
- La superficie interactiva CarPlay continúa sin integración verificada dentro del widget. Haría falta un contrato de superficie autorizado del proveedor; el nombre del Binder por sí solo no bastará.
