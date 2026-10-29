# Mustang OS 0.8.1 · Lo que mostró el diagnóstico de 0.8.0

Revisión del ZIP `20261029-044447-d550b8cd.zip` enviado desde la unidad (Allwinner A133, Android 10, ZLink 6.1.06) con CarPlay, música y una ruta de Google Maps.

## Resultado del diagnóstico

| Dato | Resultado |
|---|---|
| Música | 1109 tramas válidas, 0 rechazadas. El usuario confirma que el reproductor funciona. |
| Portadas | 15 imágenes recibidas, 14 decodificadas, 0 fuera de ventana. Una trama vacía (`00000000`). |
| Guía de ruta | 130 tramas de ruta y 19 de maniobra, todas válidas. Los tamaños de campo coinciden con iAP2: calle actual, destino, hora de llegada (8 bytes), distancias (4 bytes), textos de distancia, unidades y lista de maniobras. Al exportar, la ruta ya había terminado (estado 0). |
| GPS de la pantalla | 2 posiciones guardadas y antiguas, rechazadas; ninguna posición en vivo ni estado de satélites. |
| Errores | Ninguno; el render 3D funcionó. |

## Por qué el mapa de Google Maps no aparece dentro de la tarjeta

El mapa es vídeo que ZLink dibuja en su propia ventana. Su manifiesto declara `resizeableActivity=false`, no admite imagen en imagen y no declara `allowEmbedded`. En Android 10 eso impide abrirlo dentro de otra app o en una pantalla virtual de Mustang OS sin permisos de sistema. Lo que sí llega por el canal de datos es la guía (instrucciones, distancias, calles y llegada), que ya se muestra en la tarjeta.

Además, el mapa Android de la tarjeta se veía vacío porque la pantalla no recibió posición GPS en vivo durante la prueba.

## Cambios

- **Ruta a tarjeta completa sin GPS.** Si la pantalla no tiene posición, la guía de CarPlay ocupa toda la tarjeta: maniobra, distancia, instrucción, «Después: …» con el paso siguiente, llegada y «Vas por …». Con posición, vuelve a ser un recuadro sobre el mapa.
- **Estado de reproducción entre canciones.** El diagnóstico mostró que el estado se perdía al cambiar de canción porque el iPhone solo lo reenvía cuando cambia. Ahora se conserva.

## Probar

1. Instala 0.8.1 encima de 0.8.0.
2. Inicia una ruta en Google Maps del iPhone y vuelve a Mustang OS: la tarjeta Navegación muestra la guía completa.
3. Para el mapa visual, toca la guía o «Mapa CarPlay»; la barra se reduce sola para no taparlo.
4. Para el GPS de la pantalla: con el coche al aire libre, abre Ajustes → Ubicación GPS y espera un minuto. Si el diagnóstico sigue sin satélites, revisa la antena GPS de la unidad.
