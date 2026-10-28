# Trabajo local sin publicar

Por instrucción del usuario no se genera ni entrega otra APK mientras sigan pendientes metadatos actuales y ruta/superficie CarPlay. La entrega publicada permanece 0.4.0 y su archivo congelado conserva SHA-256 `205FFCC228C6B923DF2BDA832608EFD66955917A5E4470637F1B6B02058D7399`.

Cambios implementados localmente:

- Azul medianoche más visible y sombrío, pictogramas propios refinados y caballo grande con tribar vino/blanco/azul como fondo de Ajustes, conservando filas y contraste.
- **Mi Mustang**: año, versión, carrocería y color independientes, con perfil persistente. No configura CAN/MCU ni inventa motorización. El perfil aparece en home, ajustes y título de Motor.
- Imagen de referencia propia mediante selector Android, copia privada limitada a 12 MiB/1280 px, orientación EXIF y recomprensión sin conservar metadatos. La imagen se elige aparte; seleccionar versión/color no transforma la foto incluida.
- Los faros de la imagen incluida están calibrados a esa fotografía. La capa se desactiva para fotos propias para no dibujar luces en posiciones incorrectas.
- Restaurar la imagen incluida invalida/cancela importaciones pendientes; una regresión con lectura bloqueada reprodujo la reaparición de una foto eliminada antes de corregirla.
- Selección multimedia estable por token dentro del paquete elegido, wrapper actualizado, diagnóstico de preferencia/motivo, sin mezclar BT y CarPlay.
- Corrección de la selección de HOME exportables para conservar el launcher original y no llenar la cuota con auxiliares Android.
- Consulta inicial del perfil MCU con la misma solicitud de lectura `266/255` observada en SystemUI/TWCore. Se realiza una vez por apertura correcta, sin polling; si falla se conservan las otras escuchas. Solo un evento real puede establecer el perfil.

Verificación local tras la consulta MCU: 188 pruebas app/núcleo, cero fallos/errores/omitidas; lint debug cero errores y 116 advertencias. La regresión de consulta MCU se reprodujo antes de la corrección y pasó después. El servidor conserva sus ocho pruebas aprobadas de la revisión anterior y no cambió en esta revisión. Se ejecutaron tests/lint, no assemble/packaging de una nueva entrega. Los renders fueron revisados localmente, sin publicarlos como producto terminado. La página solo amplía la recepción de archivos APK; sigue sirviendo la entrega congelada anterior.

Después se recibieron nueve APK completos, incluido Tema. El análisis y las pruebas físicas posteriores están en [OEM_APPS_20261028.md](OEM_APPS_20261028.md): ni DashMate muestra la canción de CarPlay ni la ventana OEM permite ZLink. Hay formatos CP internos, pero el contrato utilizable desde otro launcher sigue pendiente. Se preparó una solicitud técnica para el fabricante, sin enviarla. Las mejoras de perfil/interfaz no se presentan como recuperación de metadatos ni mapa del iPhone.
