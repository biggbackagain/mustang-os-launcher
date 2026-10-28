# Mustang OS 0.4.1 · Mi Mustang

Entrega solicitada por el usuario el 28 de octubre de 2026 para instalar y revisar los avances en su pantalla. Revoca la retención anterior; los puntos CarPlay pendientes se mantienen identificados. Paquete `com.mustangos.launcher`, código de versión 10, min SDK 26 y target SDK 35.

## Cambios

- Azul medianoche, iconos propios de trazo más limpio y caballo grande con tribar vino/blanco/azul detrás de Ajustes. Se conserva la organización de ajustes aprobada.
- Ajustes → **Mi Mustang** permite guardar año, versión, carrocería y color, y elegir una foto de referencia. El perfil se conserva al reiniciar y aparece en el tablero y los ajustes. La foto se elige aparte: cambiar año/versión/color no transforma el automóvil de la imagen incluida.
- La foto propia se copia al almacenamiento privado de Mustang OS, orientada y reducida a un máximo de 1280 px, sin conservar metadatos EXIF. La imagen original admite hasta 12 MiB. Restaurar la imagen incluida cancela importaciones pendientes. Los faros superpuestos se reservan para la imagen incluida, donde están calibrados.
- Selección multimedia más estable entre sesiones de una misma fuente. Conserva los controles de CarPlay que el usuario confirmó y los datos de un mismo controlador. Una fuente ausente no se sustituye silenciosamente por Bluetooth.
- Consulta inicial del perfil MCU con la solicitud de lectura observada en SystemUI/TWCore, una vez por apertura. Si esa consulta es rechazada, las escuchas de voltaje y temperatura continúan. Solo un evento recibido puede establecer el perfil.
- El exportador de compatibilidad prioriza el launcher HOME de sistema configurado y evita llenar su cuota con componentes auxiliares de Android.

## Primer uso

1. Descarga la APK nueva desde la página local y elige **Actualizar**, conservando Mustang OS y sus preferencias anteriores.
2. En Ajustes → **Mi Mustang**, guarda tu perfil. Usa **Guardar y elegir foto** para poner una imagen que corresponda al año, versión y color elegidos.
3. Con el coche estacionado, revisa Ajustes, Apps, selección multimedia y las lecturas de Motor. El mapa Android necesita permiso de ubicación precisa y una posición válida; Centrar o Ajustes permiten activar la red de cartografía.
4. Para enviar `com.tw.carinfoservice`, si está entre las apps del sistema, usa el apartado de recepción APK de la misma página. También admite un ZIP nuevo de compatibilidad.

## Estado de integración

Los metadatos actuales de canción/portada/tiempo de CarPlay y su mapa dentro del panel **siguen pendientes**. El mapa del panel es Android/OpenStreetMap; la superficie del iPhone permanece en ZLink. El análisis del APK completo identificó formatos internos, pero no un contrato Android utilizable para ese acceso. DashMate no muestra la canción del iPhone y la ventana OEM no admite ZLink, según las pruebas del usuario. [Evidencia y siguiente paso](OEM_APPS_20261028.md).

La temperatura ambiente, los eventos OEM, ACC y la calidad GPS requieren comprobarse físicamente. Esta versión no garantiza nuevos datos del vehículo por añadir una consulta del perfil MCU; conserva antigüedad/origen y deja ausentes los valores no recibidos.

El instalador release conserva la firma local existente y la depuración desactivada. No cambia la instalación de ZLink ni CAN/MCU. No agrega permisos. Las pruebas y la suma del instalador se registran en [TEST_RESULTS.md](../TEST_RESULTS.md).
