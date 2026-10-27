# Datos nativos y CarPlay integrado · investigación 0.3.1

El ZIP de la pantalla ya fue recibido y analizado. Confirma **SDK 29 / etiqueta RELEASE 13** y los formatos que usa el OEM para voltaje, temperatura y aperturas. Las fotos de 0.3.0 confirman acceso al lector TWUtil desde Mustang OS y **14.1 V**, coincidente con la barra original. La 0.3.1 amplía el diagnóstico para localizar las puertas y temperatura que aún faltan en el launcher. Los detalles comprobados están en [docs/DEVICE_FINDINGS.md](docs/DEVICE_FINDINGS.md).

## Evidencia de la pantalla real

El usuario instaló versiones de prueba por Wi-Fi y proporcionó fotos y un ZIP de compatibilidad. Quedan confirmados los paquetes instalados `com.zjinnova.zlink`, `com.tw.radio`, `com.tw.bt`, `com.tw.music`, `com.tw.car`, `com.tw.ac`, `com.dofun.carassistant.car` y `com.dofun.carsetting`. La actividad de entrada de ZLink y su permiso normal de servicio se verificaron en el informe y manifiesto.

El usuario confirma que la interfaz OEM muestra apertura de puertas, temperatura ambiente y voltaje. Las primeras fotos muestran 12.1 V en la barra OEM; las nuevas muestran 14.1 V en esa barra y en el lector de Mustang OS, con 40 eventos. Solo el voltaje está confirmado dentro del launcher. Ninguna de esas cifras se fija como valor actual del widget.

## Corrección de accesos

- La versión anterior presentaba todas las apps al elegir mapas. La 0.2.0 limita esa selección a actividades que resuelven un intent geográfico. Si no hay ninguna, informa de que Apple Maps del iPhone se abre en ZLink. Una asignación antigua a una app que no sea mapas se descarta.
- Los accesos CarPlay, radio, teléfono y música resuelven los paquetes confirmados mediante PackageManager y guardan la actividad encontrada. No se fija a mano un nombre de actividad ni se asume que el paquete exista en otros equipos.
- Si el fabricante exige un permiso de acceso a una actividad, se informa del permiso; el botón no vuelve silenciosamente al mismo selector.
- Estas correcciones están probadas con paquetes de laboratorio; su funcionamiento en la actualización instalada en el T100 sigue pendiente del usuario.

## ¿Qué se puede leer sin OBD?

| Dato | Fuente en Mustang OS | Estado |
|---|---|---|
| Velocidad | LocationManager GPS | Implementado, requiere permiso preciso y señal |
| Precisión, altitud, rumbo y satélites usados | Location/GnssStatus | Implementado, solo muestra lecturas publicadas y recientes |
| Almacenamiento libre, volumen y brillo Android | APIs públicas del equipo | Implementado; no se confunden con información del motor o amplificador OEM |
| Voltaje BatteryManager | Broadcast estándar Android | Se identifica como voltaje publicado por Android; no se atribuye automáticamente a la batería del coche |
| Sensores disponibles | SensorManager | Se enumera lo que realmente declare el dispositivo; no se inventan lecturas ambientales |
| Alimentación OEM | Evento TWUtil 1292 | Confirmado en la pantalla: 14.1 V coincide con la barra OEM |
| Temperatura ambiente OEM | Evento TWUtil 1288 usado por la barra original | Implementado; todavía sin lectura confirmada en el launcher |
| Aperturas OEM | TWUtil 1285 y rama 1281 dependiente del perfil recibido | 0.3.1 añade el canal alternativo y contadores; recepción/correlación pendientes |
| Velocidad CAN, RPM, refrigerante y kilometraje | Sin interfaz verificada | No disponibles; no se deducen a partir de otros sensores |

No se requiere comprar ni conectar OBD. Tampoco se modifica Bluetooth, Wi-Fi, CANBUS, MCU, firmware o la activación de ZLink. Que un dato sea visible en una app privilegiada no lo convierte en un dato público.

## CarPlay real dentro del panel

El requisito es mostrar una superficie real e interactiva de CarPlay. **La 0.3.1 todavía no cumple ese requisito.** El manifiesto real de ZLink deshabilita redimensionamiento/PiP y no exporta el servicio de pantalla secundaria. No se sustituye ese objetivo por una imagen del mapa ni por una captura automatizada.

La incrustación estándar entre apps de Android está documentada desde API 33 y exige autorización en el manifest de la app incrustada, además del soporte de ventanas del dispositivo. No puede concedérsela Mustang OS unilateralmente. El SDK 29 confirmado en esta unidad no dispone de esa vía estándar. [Activity embedding, Android](https://developer.android.com/develop/ui/views/layout/activity-embedding).

Crear una pantalla virtual no elimina las restricciones: Android limita qué actividades ajenas pueden iniciarse en ella. No se implementa captura de pantalla, inyección de toques, accesibilidad ni llamadas ocultas para eludir estas comprobaciones. [Política de lanzamiento en varias pantallas, AOSP](https://source.android.com/docs/core/display/multi_display/activity-launch).

La próxima evidencia será el descriptor del servicio exportado que ZLink protege con un permiso normal. La opción «Inspeccionar servicio ZLink» lee solo ese descriptor, sin iniciar el servicio ni llamar operaciones de aplicación desconocidas. Aunque el servicio sea vinculable, hará falta un contrato autorizado de superficie para integrar contenido interactivo; el descriptor no lo garantiza.

## Investigación MCU

Una investigación pública del proyecto Helm para otra unidad A133/T13 describe clases `android.tw.john.TWUtil` y acceso MCU privilegiado. Es evidencia comparativa, no confirmación del T100 del usuario. Hay diferencias de versión Android, RAM y firmware. No se copia su adaptador ni se envían sus comandos a este equipo. [Investigación original del protocolo](https://github.com/Joshumpa/helm/blob/main/docs/mcu-protocol.md).

El diagnóstico enumera firmas y componentes sin invocarlos. Separadamente, la 0.3.0 permite activar una suscripción TWUtil mediante la factoría pública observada en el SystemUI instalado. No llama `write`, no consulta providers desconocidos, no abre UART ni solicita UID de sistema. Rechaza errores sin elevar permisos y libera su instancia al ocultarse. Un marcador evita un bucle de arranque después de una interrupción inesperada durante la escucha.

## Conseguir evidencia sin ADB ni USB

1. Instalar la APK 0.3.1 sobre la anterior; con la escucha OEM activa y Mustang OS visible, abrir/cerrar una puerta y consultar **Motor**.
2. Abrir **Ajustes → Diagnóstico y exportación → Compatibilidad OEM → Guardar ZIP** y guardar en Descargas.
3. Volver a la página local de descarga, seleccionar ese ZIP y pulsar **Enviar diagnóstico al PC**.

El ZIP contiene informe técnico, manifiestos y archivos DEX estáticos de una lista limitada de apps OEM visibles. No incluye preferencias, cuentas, datos de activación almacenados, contenidos de notificaciones, coordenadas ni identificadores del equipo. Se limita el tamaño de cada entrada y el total. Si el sistema no permite leer un archivo, queda anotado y se continúa sin elevar permisos.

El servidor guarda el ZIP recibido sin descomprimir ni ejecutar su contenido y no lo publica por HTTP. La recepción exige un token de la página y rechaza peticiones desde otro origen. La APK sigue sin permiso de Internet: el envío lo realiza Chrome después de que el usuario elija el archivo.
