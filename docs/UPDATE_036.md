# Correcciones sobre la versión del usuario · 0.3.6

La base 0.3.5 del usuario se conservó en el commit `8c31274` y en una copia local bajo `output/user-changes-20261027-031636`. Su compilación, pruebas y lint pasaron antes de comenzar; eso no demostraba la reproducción física en el coche. Se mantuvieron el tablero Motor, el menú de Apps y las mejoras de presentación.

## Música y CarPlay

El usuario confirmó que el fallo de pausa ocurre con el iPhone en CarPlay/ZLink. El código anterior interpretaba un estado multimedia ausente como pausa y enviaba PLAY. Además enviaba cada pulsación por transporte, tecla de sesión y broadcast, con un posible control global adicional.

Ahora se usa un solo camino dirigido a la sesión elegida. Con acciones específicas publicadas se usan sus controles; el play/pause combinado y los perfiles OEM conocidos sin estado se manejan con una pareja de teclas PLAY_PAUSE. Las acciones publicadas permanecen intactas: `0` no se transforma en capacidades declaradas. Un envío aceptado por Android no prueba que el iPhone haya cambiado de reproducción. Sin estado no se pinta «Pausado» ni un progreso del 50%; se muestra un control combinado.

Portada, título y dock Música abren el CarPlay configurado cuando se usa ZLink o su puente Bluetooth OEM. Una fuente Android seleccionada explícitamente conserva su reproductor. Los errores no cambian silenciosamente a otra fuente. El componente elegido de ZLink tiene prioridad; no se intenta una actividad privada de CarPlay.

El botón **Ver mapa en CarPlay** abre ZLink y ya no exige una app de mapas Android. El dock Navegación mantiene la elección Android/CarPlay. **No hay mapa del iPhone incrustado en el panel:** el contrato de superficie sigue sin estar disponible; el descriptor Binder no lo proporciona. Véanse las [preguntas para el proveedor](ZLINK_VENDOR_QUESTIONS.md).

## Lecturas Motor

Las últimas lecturas se conservan en memoria con timestamps y antigüedad. Detener u ocultar la fuente marca la escucha como inactiva y los datos como anteriores; no los presenta como actuales ni los borra tras 15 segundos de silencio. Los datos no se persisten como lecturas de una sesión futura del proceso.

La escucha habilitada solicita el voltaje mediante el formato fijo OEM `1292,255` al iniciar y cada cinco segundos, con pausa de 30 segundos ante un rechazo. Temperatura usa la petición fija `1288,255`, al iniciar o manualmente, con límite de 30 segundos entre solicitudes. Solo los eventos válidos actualizan valores y timestamps; enviar una consulta no rejuvenece el dato anterior. No se envían consultas de brillo, códigos de control arbitrarios ni peticiones globales de potencia.

El marcador de recuperación protege la inicialización y se limpia al completarla. Mantenerlo durante toda la escucha hacía que un corte normal de energía pareciera un intento fallido y desactivara la próxima escucha. Un marcador previo de inicialización interrumpida sigue evitando el bucle de arranque.

Se corrigieron interpretaciones que no corresponden al código de la unidad: `SYSTEM_DOOR` es configuración, no estado de puertas; el bit alto de voltaje es invalidez en el SystemUI analizado; los códigos 258/516 son brillo. Las posiciones de puertas no están calibradas y se muestran como aperturas publicadas. BatteryManager permanece separado del voltaje OEM y de la temperatura exterior. Tampoco se deduce «motor apagado/en marcha» por un rango de voltaje.

## GPS y sensores

El estado distingue permiso, proveedor apagado/ausente, error de suscripción, satélites y posición disponible. Se permite reutilizar una muestra reciente con su timestamp real. La velocidad solo proviene de una medición GNSS válida; no se calcula a partir de saltos entre posiciones de red ni se rejuvenecen muestras antiguas. Los datos ausentes se muestran con `—`, incluido el velocímetro de Motor.

Esta implementación no obtiene el GPS del iPhone. El receptor/proveedor Android de la pantalla debe entregar una lectura válida. No se ha probado la velocidad en un recorrido físico. Los ejes de acelerómetro/giroscopio se identifican como sensores del equipo, sin atribuirles ejes del coche ni valores si no existen.

## Foto, emblema y faros

Apps usa la foto del Mustang con una capa oscura y tarjetas translúcidas. El emblema del caballo acompaña al nombre. La procedencia del emblema y su uso en el tema personal están documentados en [ASSETS.md](ASSETS.md). En la vista compacta se conserva completa la foto; la unidad km/h tiene tamaño mínimo legible.

Los faros son una capa Canvas ligera sobre la misma foto, sin editar el JPEG. Hay una bienvenida visual al iniciar y respuesta a los broadcasts `com.unisound.intent.action.ACC_ON` / `ACC_OFF`, observados en el TWService de la unidad. El receptor pasivo conserva estado con contexto de Application mientras ZLink está delante y se libera al cerrar el ViewModel. No envía GET_POWER, DO_WAKEUP ni controles de potencia. ACC indica accesorios y no confirma motor en marcha; si no llega el evento, la bienvenida sigue siendo un efecto gráfico.

## Comprobación en la pantalla

1. Instalar 0.3.6 sobre la versión anterior, sin borrar datos.
2. Reproducir desde el iPhone, probar pausa/reanudar una vez por pulsación, siguiente/anterior y apertura desde portada y Música.
3. Con escucha OEM habilitada, observar voltaje/temperatura, cerrar Motor, regresar y comprobar la antigüedad conservada. Exportar el ZIP para registrar la respuesta real.
4. Revisar permiso preciso, proveedor, satélites y señal GPS. La prueba de velocidad física sigue pendiente.
5. Abrir Apps y comprobar foto/caballo; observar bienvenida y, cuando la unidad los publique, avisos ACC. El render con faros activados es una prueba visual, no una captura de encendido real.
