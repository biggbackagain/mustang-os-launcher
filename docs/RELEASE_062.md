# Mustang OS 0.6.2 · Lector musical de CarPlay

El diagnóstico recibido el 28 de octubre a las 18:08 confirmó una conexión local con **17 mensajes tipo 10 y 204 bytes de contenido**. La 0.6.1 descartaba esos bytes; no conocemos sus valores. Cada cuerpo medía 12 bytes, tamaño compatible con una actualización de tiempo, pero eso es una inferencia, no una posición capturada. No se recibieron mensajes 8, 9 ni 14 en esa muestra.

La 0.6.2 añade el lector del formato multimedia identificado mediante análisis estático del cliente nativo de ZLink 6.1.06. Se leen título, artista, álbum, duración y tiempo transcurrido si el servidor los publica. La tarjeta y la barra Mustang usan esos datos en memoria; no los guardan en archivos ni los incluyen en el diagnóstico.

## Probar en la pantalla

1. Actualiza la APK conservando la instalación anterior. En **Fuentes**, elige **ZLink / CarPlay**, especialmente si quedó seleccionado Bluetooth o Radio de una prueba anterior.
2. Activa **Ajustes → Datos musicales de CarPlay**. Es experimental y viene desactivado.
3. Abre **CarPlay con barra Mustang**, reproduce música y cambia dos veces de canción. Deja la barra visible unos 30 segundos. La conexión se comparte con Mustang y no se duplica al volver a Home.
4. Vuelve a Mustang. Comprueba el título, artista y tiempo de la tarjeta. Si solo llega el tiempo, se muestra ese tiempo y se mantiene el título sin datos.
5. Abre **Comprobar metadatos de ZLink** y exporta el diagnóstico **antes de detener la lectura**. El informe distinguirá campos recibidos, mensajes inválidos y cabeceras de imágenes. Si falla la conexión, pulsa **Iniciar lectura** para un intento manual nuevo.

Al desactivar la opción o cerrar tanto Mustang como su barra, se cierra el socket y se eliminan los datos musicales de memoria. El canal atiende un consumidor; otro HUD puede mantenerlo ocupado. No hay reconexión automática en bucle ni comandos al servidor. Cambiar de otra aplicación a Mustang puede iniciar una nueva sesión si la opción sigue activa.

## Qué cambia y qué falta

- Decodificación TLV con tamaños y tipos comprobados antes de aplicar una actualización completa. Las pruebas usan mensajes sintéticos basados en el formato inspeccionado, no canciones capturadas del iPhone.
- Título, artista y duración anteriores se eliminan al cambiar de identificador/título. Un reloj que retrocede sin atributos de canción también descarta la canción anterior por precaución.
- La posición muestra únicamente el valor enviado. No se interpreta el código nativo de reproducción como «reproduciendo» ni se hace avanzar el reloj artificialmente. Los controles siguen su ruta Android dirigida a ZLink, ya confirmada por el usuario.
- La tarjeta conserva los metadatos Android si esa sesión tiene título; nunca combina artista de Bluetooth con título de CarPlay. En modo automático, una fuente Android distinta seleccionada conserva sus datos.
- La barra puede mostrar los datos de canción recibidos o el tiempo recibido. Se ajusta al ancho disponible y se puede mover o cerrar.
- El informe contiene conteos e identificadores/tamaños de campos; omite títulos, artistas, imágenes, rutas y coordenadas. No existe autenticación del proceso servidor por este puerto; se mantiene la indicación de canal experimental.
- **Portada pendiente:** se detecta la firma inicial PNG de mensajes tipo 14, pero no se muestra una imagen sin un contrato de asociación con la canción. El callback nativo inspeccionado no transporta un identificador de canción junto a esos bytes.
- **Mapas de CarPlay pendientes:** el lector no extrae el vídeo ni la ruta del iPhone. ZLink sigue mostrando la navegación real junto con la barra. La cartografía offline de México sigue siendo un mapa Android separado; el último informe sigue sin acreditar una posición GPS válida del equipo.

Las medidas de seguridad de transporte son: solo 127.0.0.1:1555, timeout de conexión de 800 ms, cierre/cancelación explícitos, máximo 256 KiB por mensaje. La sesión continua termina a las cuatro horas, 100 000 mensajes o unos 64 MiB, lo que ocurra primero. La muestra manual conserva ocho segundos, 64 mensajes y aproximadamente 1 MiB. Los mensajes ajenos se descartan; no se modifica ZLink, su activación, MCU, CAN ni las conexiones Bluetooth/Wi-Fi.

La entrega utiliza la firma anterior y los mismos permisos. El resultado de compilación y descarga se registra en [TEST_RESULTS.md](../TEST_RESULTS.md). La recuperación real de los nuevos campos necesita la prueba anterior en el equipo.
