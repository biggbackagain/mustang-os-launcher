# Información necesaria para integrar ZLink en Mustang OS

Contexto confirmado en la unidad: Android SDK 29, paquete `com.zjinnova.zlink` versión 6.1.06, plataforma DINGWEI-A133. ZLink ya funciona con el iPhone y debe conservar su instalación, activación, emparejamiento y ajustes.

Objetivo: mostrar CarPlay real e interactivo en un panel de un launcher Android instalado como APK normal.

Servicio exportado: `com.zjinnova.android.zlink.features.service.DaemonService`. Permiso normal declarado: `zjinnova.android.permission.ZLINK_SERVICE`. La vinculación al servicio ya activo funcionó y devolvió el descriptor `com.zjinnova.zlink.android.core.codec.IPhoneContentPresenter`.

Solicitar al fabricante/proveedor:

1. AIDL o SDK compatible con esa interfaz y versión, con definición de métodos y callbacks.
2. Si permite entregar una `Surface` externa a un launcher: método, ciclo de vida, permisos, tamaños admitidos y retorno a la actividad original.
3. Si admite una segunda superficie o si transferirla interrumpe la actividad principal, audio, llamadas o navegación.
4. Contrato para entrada táctil y estados de conexión, sin accesibilidad ni captura de pantalla.
5. Interfaces autorizadas para metadatos multimedia e indicaciones de navegación. La MediaSession observada publica acciones 0 y ningún campo de canción en esta prueba.
6. Ejemplo mínimo de integración para APK sin root, firma OEM ni modificación del sistema, o confirmación de que este escenario no está soportado.

La actividad principal declara redimensionamiento/PiP desactivados y el servicio de pantalla secundaria no está exportado. La solución debe usar una integración admitida por el proveedor y conservar el funcionamiento existente.

[Información oficial del producto y solicitud de guías de integración](https://zjinnova.com/android-zlink.html). Este documento es un borrador local; no se ha enviado a terceros.
