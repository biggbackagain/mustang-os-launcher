# Mustang OS 0.4.0 · Cockpit

Esta entrega reúne las funciones disponibles de la pantalla en una interfaz de uso diario. Conserva los controles CarPlay que el usuario confirmó en 0.3.6 y la configuración existente. El diseño se inspira en el cockpit S650/SYNC 4: grafito, blanco y azul hielo, controles grandes y una superficie visual continua. Es un launcher independiente para el Mustang 2010, no software oficial de Ford ni una instalación de SYNC 4.

El instalador principal es una APK release con depuración desactivada y el certificado local existente, para actualizar sin desinstalar. La firma conserva continuidad, sin crear llaves ni guardar credenciales en el código. Se omite R8 en esta entrega pequeña para no añadir cambios de ofuscación a las interfaces OEM.

Referencia visual primaria: [cockpit del Mustang reciente, Ford](https://mustang.fordmedia.eu/). La fotografía y el caballo conservan la procedencia documentada en [ASSETS.md](ASSETS.md).

## Funciones de la entrega

- Mapa Android real dentro del panel: arrastrar, pellizcar/zoom, centrar ubicación y ampliar a pantalla completa. Se conserva la misma instancia al ampliar/regresar. El mapa usa la ubicación entregada por Android; una posición de red no se etiqueta como GPS ni se usa para inventar velocidad.
- Cartografía OpenStreetMap por HTTPS, con aspecto nocturno, atribución visible, solo el área vista y caché local limitada a 24 MiB. Dos trabajadores de carga y bitmaps limitados a 4 MiB; el mapa pausa al ocultarse. No hay descarga de ciudades/regiones ni un historial de recorrido. La disponibilidad del servicio público no está garantizada.
- Música con controles dirigidos a la fuente elegida, título/texto de presentación alternativo y portadas bitmap o `content://` publicadas por esa sesión. Las portadas se leen fuera de la interfaz, hasta 4 MiB y 384 px, con cancelación y protección frente a cambios de canción/fuente. No se abre una URL arbitraria ni se mezcla información entre sesiones.
- Motor mantiene voltaje, temperatura y aperturas que publique el OEM, con antigüedad de los datos. El velocímetro usa una velocidad GNSS válida. No se presentan RPM/refrigerante/kilometraje ausentes como cero ni se deduce encendido del motor a partir del voltaje.
- Apps con fotografía del Mustang, búsqueda que ignora mayúsculas y acentos e iconos uniformes para funciones OEM conocidas.
- Ajustes propios para conexiones, mapa, pantalla encendida, ubicación, asignaciones y retorno a la interfaz original. Los informes técnicos quedan en Diagnóstico. Denegar el GPS deja de provocar una solicitud automática en cada apertura.
- Foto del Mustang convertible, caballo, bienvenida de faros y respuesta a avisos ACC compatibles. La bienvenida es un efecto visual; ACC indica accesorios y no confirma motor en marcha.

## Primer uso

1. Instalar la APK sobre la anterior, sin borrar datos.
2. Conceder ubicación precisa si se desea centrar el mapa y medir velocidad. La antena/proveedor Android de la pantalla debe entregar una medición; el launcher no recibe el GPS del iPhone.
3. Pulsar **Centrar** para habilitar el mapa por Internet y seguir la ubicación. Sin medición se puede explorar el mapa global, sin marcador de posición inventado. En Ajustes se puede desactivar la red del mapa.
4. Autorizar las sesiones multimedia si aún no lo están. Reproducir desde CarPlay y elegir la fuente solo cuando haya varias y sea necesario.
5. Mantener ZLink y sus emparejamientos originales. Para arrancar con Mustang OS, elegirlo en los ajustes Home de Android después de configurar una vía de retorno al launcher original. El firmware puede imponer su propia secuencia de arranque.

La pantalla necesita conexión propia a Internet para cartografía nueva; estar conectada al iPhone para CarPlay no demuestra acceso a Internet. Las vistas ya consultadas pueden reutilizarse según las reglas de caché del servidor; no se ofrece navegación offline.

## Límite de integración CarPlay

El mapa Android del panel **no es la ruta del iPhone** y no ofrece por sí mismo instrucciones giro a giro. El dock Navegación permite abrir una app Android o CarPlay para esas funciones. Extraer la superficie/ruta de CarPlay sigue requiriendo una interfaz compatible de ZLink 6.1.06. No se usa captura de pantalla, inyección de toques, root ni cambios de MCU/firmware para aparentar esa integración.

Los metadatos también dependen de lo que publique ZLink. Que Apple Music, Spotify u otra app muestre la portada dentro de CarPlay no garantiza su publicación externa. Se admite el contrato multimedia estándar de Android con independencia de la app del iPhone; una ausencia real permanece sin datos. La nueva exportación de compatibilidad sigue disponible para investigar el puente OEM cuando el usuario la envíe.

## Red y datos

La 0.4.0 añade el permiso normal INTERNET para cartografía. La red del mapa empieza desactivada y se activa mediante Centrar o Ajustes. Las peticiones HTTPS identifican Mustang OS y solicitan imágenes del área visible a OpenStreetMap; esas imágenes implican el área consultada. Las coordenadas precisas permanecen en memoria y no se incluyen en el diagnóstico. El ZIP solo se envía al PC cuando el usuario lo selecciona en la página local.

La caché respeta cabeceras HTTP, validadores y reintentos; usa siete días cuando no hay una vigencia legible. No se descargan áreas anticipadamente. [Política del servicio de teselas](https://operations.osmfoundation.org/policies/tiles/), [atribución OpenStreetMap](https://www.openstreetmap.org/copyright).

Las pruebas locales verifican código, escenarios y renders, no la antena GPS, Internet, permisos del firmware o campos que publique ZLink en el coche. El detalle de ejecución está en [TEST_RESULTS.md](../TEST_RESULTS.md).
