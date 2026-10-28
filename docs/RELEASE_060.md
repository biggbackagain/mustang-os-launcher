# Mustang OS 0.6.0 · CarPlay con barra y selector visual

Actualiza sobre 0.5.0, con la misma firma, paquete y datos. Código de versión 12. El archivo de México importado permanece disponible: no hay que descargarlo otra vez.

## CarPlay real con controles Mustang

En **Ajustes → CarPlay con barra Mustang**, permite a Mustang OS mostrarse sobre otras aplicaciones cuando Android lo solicite. ZLink se abre con su interfaz real, música y mapas del iPhone; una barra pequeña añade Inicio, anterior, pausa/reanudar, siguiente y cerrar. Se arrastra desde el caballo. Los controles se dirigen únicamente a la sesión de la app de CarPlay elegida, sin mezclar otra fuente.

La barra se inicia con una acción explícita, muestra una notificación de servicio y no se reinicia automáticamente. Se cierra al volver a Mustang OS, pulsar Cerrar, revocar el permiso o retirar la tarea. Permanece sobre otras pantallas hasta que se cierra o se vuelve al launcher; no intenta detectar qué aplicación está en primer plano. Consume toques únicamente dentro de su rectángulo. Puede desplazarse si tapa un control de ZLink.

No se modifica la APK, firma, activación, configuración privada o firmware de ZLink. No hay captura de pantalla, OCR, inyección táctil, accesibilidad ni cambios de Bluetooth/Wi-Fi. El GPS del iPhone dentro de CarPlay es independiente del GPS de la pantalla.

**Límite:** esta opción presenta ZLink junto a controles propios; no convierte su vídeo en un widget. Portada, canción, progreso y ruta no se extraen a las tarjetas de Mustang OS. El manifiesto de la versión 6.1.06 recibida declara `resizeableActivity=false` para su aplicación y MainActivity, y `supportsPictureInPicture=false`; no se presenta la pantalla dividida como solución disponible en esta unidad. La integración externa continúa necesitando un contrato compatible del proveedor.

Android documenta [las ventanas superpuestas](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY) y el [servicio de uso especial](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use). La [pantalla dividida](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode) depende de la versión y de la app; [activity embedding](https://developer.android.com/develop/ui/views/layout/activity-embedding) no proporciona un host universal en API 29. [ZJINNOVA](https://zjinnova.com/android-zlink.html) ofrece documentos y soporte de integración mediante contacto técnico. No se envió ningún mensaje a terceros.

## Selector del vehículo

**Ajustes → Mi Mustang** abre una galería horizontal de siete familias: Clásico, Mustang II, Fox Body, SN95/New Edge, S197, S550 y S650. La selección actualiza una vista grande; año, versión, carrocería y diez colores se editan antes de **Confirmar vehículo**. Cancelar conserva el perfil anterior.

La vista digital es configurable y aproximada: no es un catálogo de VIN, motorizaciones o configuraciones de fábrica válidas para cada año. Las familias se basan en la evolución conocida del modelo; [Ford](https://www.ford.com/mustang/history/) documenta el origen y la historia del Mustang. Los presets de familia eligen una versión de presentación coherente, pero las opciones avanzadas siguen siendo un perfil visual libre.

Para el convertible S197 de 2010–2014 se utiliza la imagen de referencia de 2010 con una vista de pintura calculada localmente. Cambiar solo de rojo a azul sí modifica los píxeles de la carrocería, conservando las regiones calibradas de capota, ruedas y luces. El archivo original no se altera. El resultado se etiqueta referencia 2010, no fotografía exacta de cada año/versión o tono de fábrica.

Las demás familias usan una ilustración original estilizada, sin pretender reproducir cada edición o pieza exacta. Todas admiten cambio visual de pintura y luces. Las 30 fotos reales del catálogo y la foto propia continúan como modo separado; una fotografía sin calibración no se recolorea ni recibe luces en posiciones arbitrarias. La galería usa referencias locales, sin descargas de imágenes.

## Iluminación y GPS

La vista del coche ahora sigue **TW 516**, la señal que CarInfoService identifica como iluminación. Se corrigió el enlace anterior a ACC: encender la pantalla no equivale a encender los faros. Se añade solo la lectura inicial fija `write(516,255)` demostrada en el SystemUI recibido; no se escriben valores de brillo ni ajustes MCU. Valores distintos de 0/1 se descartan y al reconectar se espera un evento real.

Activa **Ajustes → Datos del vehículo y luces**. Si el firmware entrega el canal, la vista cambia con la iluminación; si no lo entrega, permanece sin confirmación. La prueba manual de luces dentro del selector es una previsualización y no escribe al coche.

El mapa offline ya dibujaba cartografía en la captura del usuario; faltaba una posición. Se eliminó el rechazo de todas las posiciones con precisión superior a 100 m: una posición de red aproximada puede centrar ahora el mapa con su precisión indicada, sin conducir el velocímetro. El filtro estricto de velocidad GPS no cambia. Se añade escucha pasiva de ubicaciones legítimas y contadores sin coordenadas para distinguir ausencia de señal de lecturas rechazadas.

Al tocar el estado GPS se ven satélites, precisión y lecturas, con **Reintentar** y acceso a ajustes de ubicación. No se genera una posición ni velocidad desde el centro del mapa o movimientos de red. Si no llegan satélites/posiciones a cielo abierto, queda por comprobar la antena o el receptor del equipo; esta actualización no acredita reparar hardware.

## Verificación

La suite cubre cambio de color efectivo, conservación del original y regiones sin pintura, vista de todas las familias/colores, galería sin guardar hasta confirmar, cancelación, formulario compacto, señal de iluminación independiente de ACC, posición aproximada sin velocidad, liberación de listeners, permiso/cierre de barra y tipo de servicio en API 29/35. Las pruebas Android simuladas no sustituyen la validación física de ZLink, GPS e iluminación en esta unidad. [Registro de verificación](../TEST_RESULTS.md).
