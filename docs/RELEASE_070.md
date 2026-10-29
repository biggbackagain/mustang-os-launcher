# Mustang OS 0.7.0 · Vehículo dinámico y portada CarPlay

El usuario confirmó físicamente el título/datos musicales y los controles de CarPlay de la 0.6.2. Esta entrega conserva esa ruta y añade un modelo tridimensional propio, lectura de imágenes PNG y una opción para solicitar velocidad/RPM al equipo. Las nuevas funciones requieren comprobación en la unidad; no se presenta el mapa de CarPlay dentro de una tarjeta como resuelto.

## Activar y comprobar

1. Actualiza la APK. En **Mi Mustang**, activa **Vista 3D**, selecciona perfil/color y confirma. El modelo es una representación estilizada aproximada, no una digitalización exacta de cada año y edición. Las fotos propias y de catálogo siguen disponibles como modo estático.
2. Activa **Datos del vehículo y luces**. Los faros representan la señal TW 516 recibida; si falta, pueden representar la última señal disponible en la caché CarInfoService, identificada como guardada y sin fecha de medición física.
3. En **Puertas del modelo 3D**, con el coche estacionado, cierra todas las puertas hasta recibir el cierre. Abre solo la izquierda y asígnala; cierra todas y repite con la derecha. Se guardan bits locales para el dibujo. Se rechazan aperturas ambiguas o la misma señal para ambos lados. No se cambia CAN/MCU ni se envía ningún comando de puerta.
4. Si quieres probar las RPM, activa también **Velocidad y RPM OEM**. Solicita el flujo temporal de telemetría observado en CarInfoService. La solicitud sola no produce un número: espera eventos TW 1291 y muestra `—` si no llegan o caducan después de cinco segundos.
5. Mantén **Datos musicales de CarPlay** activo, reproduce música en ZLink, cambia de canción y espera. La tarjeta admite ahora PNG recibidos por el mismo canal. Si falta la imagen, exporta el diagnóstico **sin detener la lectura**; distingue imágenes recibidas, descartadas por falta de anuncio reciente y no decodificables.
6. Para investigar navegación, activa una ruta en CarPlay antes de exportar. El informe conserva tipos y tamaños de campos 8/9, sin calles, destinos, coordenadas o contenido de rutas.

## Modelo dinámico

La malla se proyecta en perspectiva sobre Canvas y usa profundidad para ordenar superficies. El color cambia el material; las dos puertas tienen bisagras y animación independientes. Los faros responden a la señal de iluminación. Las ruedas y el suelo solo avanzan con una velocidad válida OEM o GPS, sin inventar velocidad o movimiento. No utiliza RPM calculadas a partir de la velocidad.

La geometría estática y su proyección se conservan para no reconstruir el vehículo en cada fotograma. La animación se limita aproximadamente a 30 fps cuando hay movimiento o transición; se detiene al ocultar el launcher, entrar en los menús o cerrar la ventana. Sin velocidad válida, el modelo permanece detenido.

## Portada CarPlay

Se interpreta tipo 14 como imagen únicamente si empieza por PNG y declara IHDR. El máximo de transporte para ese tipo es 2 MiB, diferente de los 256 KiB de datos ordinarios; así una portada mayor de 256 KiB no interrumpe por ese solo motivo la canción. La decodificación valida dimensiones (hasta 1 048 576 píxeles) y reduce el bitmap a un máximo de 384 píxeles por lado. No hay búsquedas de imágenes, URLs o servicios externos.

El callback nativo no lleva identificador de canción junto al PNG. Se aplica una asociación temporal conservadora: debe existir una canción identificada y un anuncio de transferencia de portada en los últimos dos segundos de la misma conexión; se consume una sola imagen y se elimina al cambiar canción o identificador de portada. Los resultados tardíos tras cancelar no se publican. **Esto es una inferencia de orden del canal, no un identificador verificable dentro del PNG**; hay que confirmar en el equipo que la imagen corresponde a la canción y no se retrasa al saltar pistas. Una imagen sin condiciones suficientes se descarta.

## Lectura activa de RPM

Se revalidó estáticamente CarInfoService 251014: su método `u0(true)` utiliza exactamente `TWUtil.write(1291,3,0)` para solicitar telemetría, y `u0(false)` utiliza `TWUtil.write(1291,0,0)` para detenerla. No son getters puros de caché; por eso la función nueva es una opción separada, desactivada por defecto. Las cuatro consultas anteriores de CarInfoService siguen siendo getters de caché.

Mustang solicita el inicio una vez por sesión visible y solicita el cierre al salir, deshabilitar la opción o cerrar su suscripción. No hace polling de ese comando, no cambia el tipo CAN, no actúa sobre el motor y no modifica ZLink o Bluetooth. El flujo de la MCU es compartido: la parada utiliza el mismo cierre global que la app OEM, sin una API de propiedad por consumidor. No se afirma que otras apps de instrumentos mantengan su propio flujo al mismo tiempo. Las lecturas solo se muestran a partir de eventos vigentes; habilitar el flujo no garantiza que el coche/decodificador disponga de RPM.

## Lo que sigue pendiente

- Confirmar en el coche portada y asociación con cambios de canción, puertas, iluminación, velocidad y RPM de esta versión.
- Recuperar datos de ruta/maniobra si el canal los publica. Los tipos 8/9 no equivalen a vídeo del mapa ni prueban coordenadas del iPhone.
- La superficie interactiva del mapa de CarPlay sigue en ZLink con la barra Mustang. No se ha verificado una interfaz de superficie/viewport externa compatible con esta unidad. La nueva versión no captura pantallas, no inyecta toques ni modifica la firma o configuración de ZLink.
- El mapa Android de México permanece disponible de forma independiente. Sin una posición válida de la pantalla, no puede centrar el coche ni dar velocidad GPS.

La APK mantiene el certificado anterior y los mismos permisos. Los resultados de pruebas y descarga se registran en [TEST_RESULTS.md](../TEST_RESULTS.md).
