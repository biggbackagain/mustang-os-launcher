# ZLink / CarPlay: investigación y contrato de Mustang OS

Fecha de investigación: 26 de octubre de 2026. Sin conexión física confirmada al T100 ni copia del manifiesto de su ZLink 6.1.06. No se ha determinado todavía su paquete o actividad; no se presupone `com.zjinnova.zlink`.

Actualización 0.2.0: las fotos enviadas por el usuario confirman el paquete `com.zjinnova.zlink`. Su actividad y contrato aún deben inspeccionarse en el ZIP de compatibilidad. La app ahora resuelve el paquete confirmado cuando está instalado. Consulta [NATIVE_INTEGRATION.md](NATIVE_INTEGRATION.md) para la investigación del panel interactivo y datos OEM sin OBD.

Actualización 0.3.0: el ZIP fue recibido. Confirma MainActivity, versión 6.1.06, MediaSession visible con acciones `0` en esa captura, SDK real 29 y PiP/redimensionamiento desactivados en ZLink. El permiso del servicio exportado es normal, no privilegiado. Se añade una inspección limitada al descriptor Binder del servicio ya activo. Consulta [docs/DEVICE_FINDINGS.md](docs/DEVICE_FINDINGS.md); no se ha integrado la superficie CarPlay.

Prueba física posterior en 0.3.1: el servicio sí fue vinculable y devolvió `com.zjinnova.zlink.android.core.codec.IPhoneContentPresenter`. La sesión de ZLink estaba visible con acciones 0 y sin metadatos de canción. No se encontró una definición pública de métodos para ese descriptor; el DEX exportado está protegido por un envoltorio. La 0.3.2 conserva el diagnóstico, sin transacciones por conjetura ni una superficie CarPlay integrada. [Contrato que hace falta solicitar al proveedor](docs/ZLINK_VENDOR_QUESTIONS.md).

## Evidencia pública

ZJINNOVA presenta Android zLink como una solución que entrega APK, HAL, drivers y documentación de integración a fabricantes. Enumera Allwinner y conectividad USB/Wi-Fi. Su página invita a solicitar el documento de integración, pero la documentación pública revisada no especifica intents, extras, AIDL, broadcasts ni proveedores para lectura de metadatos y navegación por una APK externa. Esto no demuestra que tales contratos no existan en una variante OEM: falta inspeccionar el equipo o recibir documentación autorizada. [Android zLink, fabricante](https://www.zjinnova.com/android-zlink.html).

`libzlink SDK` se anuncia para Linux/RTOS; esa oferta no prueba que el ZLink Android del T100 exponga la misma API. [Oferta oficial de ZJINNOVA](https://www.zjinnova.com/).

Android permite enumerar sesiones activas con MediaSessionManager cuando el usuario habilita un NotificationListenerService. Mustang OS emplea esa vía; no solicita el permiso privilegiado MEDIA_CONTENT_CONTROL. La sesión solo contiene lo que el publicador Android decida proporcionar. [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager).

El framework CarPlay de Apple permite que aplicaciones del iPhone presenten funciones en CarPlay. No es una API para leer rutas de Apple Maps desde un launcher Android. [CarPlay, Apple Developer](https://developer.apple.com/carplay/).

## Integración implementada

1. PackageManager consulta actividades MAIN/LAUNCHER realmente instaladas y exportadas. Se priorizan candidatos por etiqueta ZLink o segmentos de paquete, pero la identidad la confirma el usuario en el selector mostrando etiqueta y paquete. También se permite elegir una variante OEM con otro nombre.
2. Se guarda el ComponentName elegido; los accesos lanzan esa actividad con NEW_TASK y RESET_TASK_IF_NEEDED. No se usan CLEAR_TASK, force-stop, reinstalación ni limpieza de datos. Android decide cómo reusar la tarea según el manifest del fabricante.
3. MediaIntegrationManager se suscribe a las sesiones activas autorizadas, sigue el token elegido y libera callbacks al ocultarse el launcher. Fuente explícita tiene prioridad, después la sesión del ZLink seleccionado, después una fuente única. Varias fuentes sin preferencia requieren elección. Si desaparece una fuente elegida no se controla automáticamente otra.
4. Se muestran título, artista, álbum, bitmap de portada, duración, progreso extrapolado de PlaybackState y reproducción cuando se publican. Campos vacíos se dejan sin datos. No se usa un reproductor de demostración.
5. Se habilita anterior, play/pause y siguiente según las capacidades. Se revalidan al pulsar. Play/pause combinado usa un evento dirigido al controlador elegido. Nunca se manda una tecla global ni se pide audio focus.
6. CarPlay se abre en la app ZLink original. El launcher declara `Conexión CarPlay no verificable`; una app instalada, Wi-Fi activo o una sesión multimedia **no prueban** una conexión del iPhone.
7. La tarjeta de navegación abre ZLink o una app de mapas Android elegida. No se incrustan actividades ajenas ni se dibujan mapas o rutas ficticias.

## Comprobaciones concretas en el T100

En Mustang OS, elige ZLink y abre **Ajustes → Diagnóstico**. El informe muestra el paquete, versión y actividad elegidos, componentes que el paquete declara exportados (actividades, servicios, receptores y proveedores) y sus permisos. Que un componente sea exportado no prueba su contrato ni que sea seguro llamarlo: esta versión solo usa la actividad de lanzamiento.

Con el vehículo estacionado, autoriza multimedia, reproduce Apple Music en el iPhone y regresa a Mustang OS mediante Home o la app Mustang OS. Revisa las sesiones visibles. Si aparece la sesión correspondiente, elige su paquete y prueba pausa/anterior/siguiente. Repite con pantalla bloqueada, desconexión y reconexión. Registra el resultado en DEVICE_TEST_PLAN.md.

Si hay acceso ADB, estos comandos son de lectura y no requieren root:

```powershell
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell wm size
adb shell wm density
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
# Sustituye PAQUETE_VERIFICADO por el dato del selector, nunca por un supuesto:
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER PAQUETE_VERIFICADO
adb shell dumpsys package PAQUETE_VERIFICADO
```

Inspecciona localmente los filtros y permisos declarados del último comando. No ejecutes servicios, receptores, comandos CAN/MCU ni providers sin un contrato documentado. No exportes dumps completos de notificaciones, sesiones multimedia, Bluetooth, Wi-Fi o logcat: pueden contener información privada. El diagnóstico reducido de Mustang OS es el informe destinado a compartirse.

## Resultados todavía pendientes

| Pregunta | Estado actual / siguiente evidencia necesaria |
|---|---|
| Paquete, versión y actividad instalados | Pendiente del selector e informe de la pantalla |
| Intents y servicios de integración públicos | No encontrados en documentación pública; revisar manifest y obtener contrato OEM |
| Sesión de Apple Music vía ZLink | Pendiente de inspección durante reproducción real |
| Portada y acciones transportControls | Implementado con APIs Android; pendiente de publicación y respuesta real de ZLink |
| Estado inalámbrico CarPlay | No hay interfaz verificada; UI permanece desconocida |
| Apple Maps, Google Maps o Waze del iPhone | Sin contrato verificado para rutas/maniobras |
| Incrustar CarPlay en una tarjeta | No implementado: no hay API verificada para transferir la superficie interactiva |

Si ZLink no publica una sesión, la alternativa funcional es entrar a CarPlay para controlar Apple Music y navegar allí. Un reproductor Android se distingue por su paquete real y no se etiqueta como la música del iPhone. No se modifican licencias, activación, ajustes inalámbricos ni componentes OEM.
