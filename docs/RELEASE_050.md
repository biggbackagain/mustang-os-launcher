# Mustang OS 0.5.0 · México offline, radio y referencias automáticas

Entrega para instalar sobre 0.4.1 conservando preferencias y firma. Paquete `com.mustangos.launcher`, código de versión 11, min SDK 26 / target SDK 35.

## Fondo común

El caballo tribar con azul medianoche que el usuario aprobó en Ajustes se utiliza ahora en Home, Apps, Ajustes, Motor, Radio, la vista ampliada del mapa y los menús propios. El emblema conserva proporción y el sombreado mantiene contraste. El contenido del mapa y las fotos se conservan legibles. La app no sustituye el tema de Android ni de las aplicaciones OEM externas.

## Mi Mustang

Un catálogo de 30 fotografías descargadas de Wikimedia Commons permite elegir automáticamente una referencia por año, versión, carrocería y color disponible. Incluye generaciones clásicas, Mustang II/Fox/SN95/S197/S550/S650 y referencias GT, EcoBoost, Bullitt, Boss y Shelby. El perfil 2010 V6 convertible rojo conserva la imagen original con faros calibrados.

El catálogo no contiene todas las combinaciones de año/color/versión. Cuando no hay coincidencia exacta se muestra **Referencia aproximada**, con los datos reales de la fotografía. Cambiar el perfil no recolorea ni transforma una fotografía. La foto propia tiene prioridad si se eligió una; **Guardar y usar fotos automáticas** restaura el catálogo.

Las fotos están dentro de la APK, por lo que no requieren Internet al cambiar de modelo. Se decodifican fuera del hilo principal y con caché limitada. Los créditos se consultan en Ajustes o manteniendo pulsada la foto. Sus autores, licencias y fuentes se conservan en `assets/cars/catalog.json` y en los avisos de fotografías.

## México completo sin conexión

El mapa vectorial del panel usa Mapsforge 0.30.0. El paquete oficial de México preparado ocupa **719 418 229 bytes (686 MiB)** y contiene también Jalisco, Colima y Michoacán. La copia preparada del proveedor tiene fecha 28 de junio de 2026; la app muestra la fecha de datos leída del archivo.

1. Instala la APK nueva mediante **Actualizar**.
2. Desde la página local del PC, descarga **Mapa de México** en la pantalla.
3. En Mustang OS abre **Ajustes → Mapas sin conexión → Importar mexico.map desde Descargas** y selecciona la descarga `Mexico-MustangOS.map`.
4. Espera el aviso de mapa listo. Queda activado como mapa local; puedes desconectar Internet y explorar, ampliar o centrar GPS.

También se puede descargar directamente desde el proveedor dentro de la app, mediante Wi-Fi sin medición. Si el firmware restringe DownloadManager o esa red no tiene Internet, utiliza la descarga desde el PC y el selector de archivos. Se recomienda al menos 800 MiB libres para descargar; importar desde Descargas puede conservar además la copia original del navegador.

Las vistas rápidas **Ver México**, **Ver Jalisco**, **Ver Colima** y **Ver Michoacán** enfocan la cartografía. Centrar utiliza únicamente una posición válida del GPS de la pantalla; sin señal el mapa se puede explorar sin dibujar una ubicación ficticia.

El paquete se guarda aparte de la APK para evitar descargarlo otra vez en cada actualización. Un archivo parcial o incorrecto no reemplaza el mapa activo. El servidor admite reanudación por rangos. El renderizador y sus trabajadores se liberan cuando el mapa queda oculto; la red de cartografía online mantiene su opción separada.

Este módulo ofrece **cartografía offline**, sin cálculo de rutas ni instrucciones de voz. No extrae el mapa ni la ruta del iPhone. El mapa online anterior permanece disponible como opción. Atribución y licencias de Mapsforge/AndroidSVG/OpenStreetMap se consultan desde el mismo menú.

## Radio dentro de Mustang OS

El botón Radio abre un panel propio que escucha frecuencia, banda, RDS y presintonías que publique el equipo. Anterior/siguiente, búsqueda y cambio de banda utilizan el contrato XT inspeccionado y solo se habilitan cuando el equipo confirma radio activa y el servicio permite control.

No cambia de fuente al abrir el panel durante CarPlay. **Escuchar radio** abre la aplicación OEM cuando se solicita, para seleccionar esa fuente; al volver a Mustang OS el panel observa los datos disponibles. Una respuesta del servicio indica solicitud enviada, no garantiza que el sintonizador haya completado el cambio. Los datos ausentes se mantienen ausentes.

## Motor y lecturas OEM

- Se conserva la escucha TW comprobada de voltaje, temperatura ambiente y puertas.
- Se añade lectura de cuatro getters de caché de CarInfoService: voltaje, odómetro, temperatura de sensor no identificado y señal de iluminación. El origen y la falta de fecha de medición física quedan explícitos. No se presentan como refrigerante o faros reales sin prueba.
- Se escucha pasivamente el canal 1291. Velocidad y RPM solo se usan después de un evento válido y caducan a los cinco segundos. El velocímetro identifica OEM o GPS según la fuente que esté usando.
- No se llama a los getters de velocidad/RPM de CarInfoService ni a su registro/desregistro de callbacks: esos métodos incluyen comandos MCU. No se fuerza la activación del canal 1291. Si el equipo no lo emite, esas lecturas permanecen ausentes y la velocidad conserva el respaldo GPS.

La lectura de CarInfoService respeta la preferencia **Activar/Detener lectura OEM**, se vincula solo a un servicio público ya activo y se detiene al ocultarse Mustang OS. Las comprobaciones físicas en la unidad siguen siendo necesarias.

## CarPlay

Los controles musicales confirmados se conservan. Título, artista, portada, tiempo y superficie integrada de CarPlay continúan sin contrato Android compatible verificado. El ZIP/APK recibido no habilita por sí solo esas interfaces. [Investigación previa](OEM_APPS_20261028.md), [interfaces adicionales](OEM_APPS_20261028_ADDITIONAL.md).

## Fuentes y verificación

- Mapa preparado: https://download.mapsforge.org/maps/v5/north-america/mexico.map
- SHA-256 del mapa: `2e3d24665b0b050637dcaa08536119aabed6478804c615fd39e34e81bc66b77f`.
- Biblioteca y fuente de reconstrucción: https://github.com/mapsforge/mapsforge/tree/0.30.0
- Atribución de datos: https://www.openstreetmap.org/copyright
- Registro de pruebas, instalador y distribución: [TEST_RESULTS.md](../TEST_RESULTS.md).
