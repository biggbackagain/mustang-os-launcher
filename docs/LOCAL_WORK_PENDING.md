# Avances incorporados y trabajo pendiente

**Actualización 0.5.0:** se incorporaron fondo común, catálogo de 30 fotografías, mapa de México sin conexión, radio propia y lecturas adicionales OEM. [Entrega actual](RELEASE_050.md), [interfaces verificadas](OEM_APPS_20261028_ADDITIONAL.md). La entrega autorizada está compilada y sus descargas locales están verificadas. CarPlay mantiene sus límites indicados abajo.

El usuario autorizó el 28 de octubre de 2026 una nueva APK para instalar y revisar los avances, revocando la retención anterior. Los cambios siguientes se incorporan a la [0.4.1](RELEASE_041.md). Los metadatos actuales y la ruta/superficie CarPlay continúan pendientes. El archivo histórico 0.4.0 conserva SHA-256 `205FFCC228C6B923DF2BDA832608EFD66955917A5E4470637F1B6B02058D7399`.

Cambios implementados localmente:

- Azul medianoche más visible y sombrío, pictogramas propios refinados y caballo grande con tribar vino/blanco/azul como fondo de Ajustes, conservando filas y contraste.
- **Mi Mustang**: año, versión, carrocería y color independientes, con perfil persistente. No configura CAN/MCU ni inventa motorización. El perfil aparece en home, ajustes y título de Motor.
- Imagen de referencia propia mediante selector Android, copia privada limitada a 12 MiB/1280 px, orientación EXIF y recomprensión sin conservar metadatos. La imagen se elige aparte; seleccionar versión/color no transforma la foto incluida.
- Los faros de la imagen incluida están calibrados a esa fotografía. La capa se desactiva para fotos propias para no dibujar luces en posiciones incorrectas.
- Restaurar la imagen incluida invalida/cancela importaciones pendientes; una regresión con lectura bloqueada reprodujo la reaparición de una foto eliminada antes de corregirla.
- Selección multimedia estable por token dentro del paquete elegido, wrapper actualizado, diagnóstico de preferencia/motivo, sin mezclar BT y CarPlay.
- Corrección de la selección de HOME exportables para conservar el launcher original y no llenar la cuota con auxiliares Android.
- Consulta inicial del perfil MCU con la misma solicitud de lectura `266/255` observada en SystemUI/TWCore. Se realiza una vez por apertura correcta, sin polling; si falla se conservan las otras escuchas. Solo un evento real puede establecer el perfil.

Verificación integral de la 0.4.1: 188 pruebas app/núcleo y ocho del servidor, cero fallos/errores/omitidas; lint debug/release cero errores y 118 advertencias; ambos instaladores compilados. Firma anterior, actualización de versión, ausencia de depuración y permisos idénticos comprobados. La descarga local coincide con la APK firmada. [Registro de entrega](../TEST_RESULTS.md). La regresión de consulta MCU y la descripción incorrecta de la imagen stock se reprodujeron antes de sus correcciones y pasaron después.

Después se recibieron nueve APK completos, incluido Tema. El análisis y las pruebas físicas posteriores están en [OEM_APPS_20261028.md](OEM_APPS_20261028.md): ni DashMate muestra la canción de CarPlay ni la ventana OEM permite ZLink. Hay formatos CP internos, pero el contrato utilizable desde otro launcher sigue pendiente. Se preparó una solicitud técnica para el fabricante, sin enviarla. Las mejoras de perfil/interfaz no se presentan como recuperación de metadatos ni mapa del iPhone.
