# Metadatos y evidencia de integración · 0.3.7

El usuario confirmó que pausa/reanudar y cambio de canción de 0.3.6 funcionan con CarPlay/ZLink y el iPhone. Apple Music muestra título, artista y portada dentro de CarPlay, pero Mustang OS no los presenta. La navegación del launcher sigue siendo un acceso a ZLink; el usuario requiere un mapa real e interactivo dentro de la tarjeta. El launcher original también abre CarPlay completo y no ofrece una tarjeta de mapa conocida.

## Corrección multimedia

El lector anterior solo extraía TITLE/ARTIST/ALBUM y bitmaps. Eso deja fuera DISPLAY_TITLE y DISPLAY_SUBTITLE. La línea anterior del diagnóstico, basada en los campos ya extraídos, **no demostraba ausencia de todos los metadatos en ZLink**. Ahora hay fallback de presentación para esos dos campos estándar. Un subtítulo de presentación no se registra como artista, ni se interpreta una descripción genérica como artista/álbum. Los controles, selección de sesión y apertura confirmados no se modifican.

El reporte inspecciona cada sesión visible (hasta doce), incluida Bluetooth, y separa: existencia de metadata, claves/valores principales y DISPLAY, MediaDescription, bitmap, URI, esquema content, reproducción y acciones. Exporta indicadores y procedencia; no canciones, valores URI ni claves personalizadas. No mezcla metadatos de Bluetooth con controles de ZLink sin una asociación verificada. Las URI se detectan pero todavía no se abren. Si el nuevo informe muestra una portada por content URI, se podrá implementar su carga acotada con esa evidencia.

La vía es la MediaSession Android publicada por CarPlay/ZLink, independiente de la app de música del iPhone. **No garantiza datos para cualquier app** si el puente no los publica. Un callback vacío o una URI presente no confirma que haya una portada accesible.

Referencia: [MediaMetadata, campos de presentación de Android](https://developer.android.com/reference/android/media/MediaMetadata).

## Evidencia OEM adicional

La exportación anterior solo incluía ocho paquetes y omitía Bluetooth, Música y el HOME OEM original. El nuevo ZIP incluye manifiestos y DEX base de `com.tw.bt`, `com.tw.music` y hasta cuatro paquetes HOME de sistema/actualización de sistema descubiertos por MAIN/HOME. Lista las actividades HOME visibles (máximo 64), excluye la propia APK y los launchers de usuario de la copia, y comparte una instantánea entre selección y reporte. Los nombres de bibliotecas declaradas se reportan sin copiar/cargar esas bibliotecas.

Se mantienen límites de tamaño/número y el filtro de manifiesto/DEX. No se exportan recursos, bibliotecas nativas, activación ni datos privados; tampoco se ejecuta código de otras APK. El diagnóstico añade fabricante/marca/modelo/placa/hardware declarados, sin seriales ni identificadores personales.

## Mapa que falta

La ficha identifica ZLink 6.1.06, DINGWEI Ultimate, DINGWEI-A133, T10S/T100. La unidad tiene SDK real 29, aunque las etiquetas comerciales/CPU-Z indiquen otras versiones. El servicio vinculado expone el descriptor IPhoneContentPresenter, pero no conocemos sus métodos. En los archivos recibidos no aparece un contrato para entregar Surface/tamaño/ciclo de vida y entrada táctil desde una APK externa. El servicio de pantalla secundaria no está exportado. Esto no prueba que el proveedor carezca de una integración OEM.

Esta revisión **no incrusta el mapa ni convierte abrir ZLink en navegación integrada**. El siguiente ZIP puede aportar un cliente SDK del launcher/Bluetooth que no estaba disponible. Si no hay contrato legible allí, se requiere el SDK/AIDL/documentación correspondiente del proveedor, incluida confirmación de soporte para APK sin firma de sistema. Debe aclararse si permite solo proyección completa o una superficie independiente de navegación. [Solicitud técnica preparada](ZLINK_VENDOR_QUESTIONS.md); no se ha enviado a terceros. ZJINNOVA ofrece documentación e integración a fabricantes en su [página oficial](https://zjinnova.com/android-zlink.html).

## Captura necesaria en la pantalla

1. Instalar 0.3.7 sobre 0.3.6, conservando datos.
2. Con el coche estacionado, conectar CarPlay, dejar una canción reproduciéndose e iniciar una ruta.
3. Regresar por Home a Mustang OS. No detener la reproducción ni desconectar el iPhone.
4. Ajustes → Diagnóstico y exportación → Compatibilidad OEM → Guardar ZIP.
5. Enviar ese ZIP nuevo mediante la página local del PC. El ZIP anterior no contiene los campos/clientes añadidos.

Una segunda captura con otra canción ayuda a distinguir valores cambiantes de una sesión estancada. La escucha OEM de Motor no es requisito para esta investigación multimedia.
