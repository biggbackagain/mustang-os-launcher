# Mustang OS · Guía de instalación

Mustang OS es una pantalla de inicio (launcher) para estéreos Android de coche con estilo Ford Mustang. Muestra música y portada de CarPlay, la guía de ruta de CarPlay, un mapa, la velocidad y un modelo 3D de tu Mustang.

![Mustang OS](render-3d-s197.png)

> Proyecto independiente. No es de Ford ni de Apple, no modifica el coche, el CAN ni la configuración de CarPlay. Instálalo y configúralo **con el coche estacionado**.

## 1. ¿Funciona en mi pantalla?

| Necesitas | Detalle |
|---|---|
| Estéreo Android | Android 8.0 o superior. Probado en una pantalla Allwinner A133 (Android 10, 1280×720). |
| CarPlay por ZLink | Música, portada y guía de ruta de CarPlay en las tarjetas requieren la app **ZLink** del estéreo (probado con ZLink 6.1.06). Con otra app de CarPlay, el botón CarPlay la abre, pero las tarjetas no reciben sus datos. |
| Datos del coche | Voltaje, puertas, luces y RPM solo aparecen en estéreos con los servicios del fabricante (TW / CarInfoService). En otros estéreos esas lecturas se quedan en «—». |
| Gráficos | El modelo 3D necesita OpenGL ES 2.0 (casi todos los estéreos lo tienen). Si no, se muestra una foto. |

## 2. Descargar la aplicación

**Versión actual: 0.8.1.** La forma más fácil es descargarla directo desde GitHub en el navegador del estéreo:

**[Descargar MustangOS.apk](https://github.com/biggbackagain/mustang-os-launcher/raw/main/apk/MustangOS.apk)**

Si el estéreo no tiene internet, también puedes recibir el archivo **MustangOS.apk** de estas formas:

- **Enlace por Wi-Fi:** quien te lo comparte abre un enlace del tipo `http://192.168.x.x:8765/` desde su computadora. Conecta el estéreo a la **misma red Wi-Fi**, abre ese enlace en el navegador del estéreo y pulsa **Descargar Mustang OS**.
- **Memoria USB:** copia `MustangOS.apk` a la memoria, conéctala al estéreo y abre el archivo desde el administrador de archivos.
- **Desde una computadora o celular:** descarga el archivo con el enlace de arriba y pásalo al estéreo por memoria USB, Drive o Telegram.

## 3. Instalar

1. Abre **MustangOS.apk** desde Descargas o desde la memoria USB.
2. Si Android avisa que el navegador o el administrador de archivos no puede instalar apps, pulsa **Ajustes**, activa **Permitir de esta fuente** y regresa.
3. Pulsa **Instalar**. Si ya tenías una versión anterior, pulsa **Actualizar**: se conservan tus ajustes.
4. Pulsa **Abrir**.

Si el estéreo pide una contraseña para instalar apps, es un bloqueo del fabricante; consulta con tu vendedor.

## 4. Primer arranque

Mustang OS se abre como una app normal; **no reemplaza** la pantalla de inicio del estéreo hasta que tú lo decidas (paso 6).

1. **Ubicación:** acepta **Permitir** (ubicación precisa) para la velocidad y el mapa.
2. **Mi Mustang:** en **Ajustes (engrane) → Mi Mustang**, elige año, versión, carrocería y color. Toca **Confirmar vehículo**. El modelo 3D se puede girar con el dedo; doble toque para volver.
3. **CarPlay:** pulsa **CarPlay** en la barra inferior. Si hay varias apps parecidas, elige **ZLink**. Conecta tu iPhone como siempre.
4. **Barra sobre CarPlay (opcional):** en **Ajustes → CarPlay con barra Mustang**, acepta el permiso **Mostrar sobre otras apps**. Sobre CarPlay aparece un caballito en el borde: tócalo para ver Inicio y los controles de música, arrástralo para moverlo. Se esconde solo para no tapar el mapa.
5. **Música de otras apps (opcional):** en la tarjeta Audio pulsa **Fuentes** y concede **Acceso a notificaciones** a Mustang OS. Solo sirve para leer qué canción suena y controlarla; no lee tus mensajes.
6. **Datos del coche (opcional):** en **Ajustes → Datos del vehículo y luces**. Para que el 3D abra las puertas correctas usa **Ajustes → Puertas del modelo 3D** y sigue los pasos.

## 5. Uso diario

- **Audio:** canción, artista y portada de CarPlay. Los botones anterior / reproducir / siguiente controlan CarPlay.
- **Coche:** modelo 3D con luces y puertas, velocidad (OEM o GPS) y RPM si el estéreo las envía.
- **Navegación:** cuando el iPhone guía una ruta (Apple Maps, Google Maps, Waze), la tarjeta muestra la flecha, la distancia, la siguiente indicación y la hora de llegada. Tócala o pulsa **Mapa CarPlay** para ver el mapa completo.
- **Mapa sin internet de México (opcional):** **Ajustes → Mapas sin conexión** e importa el archivo `mexico.map` que te compartan (unos 690 MB).

> El **mapa de CarPlay** (el dibujo de Google Maps o Apple Maps) solo se ve dentro de CarPlay. Android no permite meterlo en una tarjeta de otra app; la tarjeta Navegación muestra las indicaciones de esa misma ruta.

## 6. Hacerlo pantalla de inicio (opcional)

Prueba primero varios días como app normal. Luego:

1. Ve a **Ajustes → Aplicaciones predeterminadas → Elegir launcher original** y marca el launcher de tu estéreo, para tener cómo volver.
2. En **Ajustes del equipo** de Android busca **Apps → Apps predeterminadas → App de inicio** y elige **MUSTANG OS**.

Para volver a la pantalla original: **Ajustes → Interfaz original** (la abre sin cambiar nada) o elige el launcher del fabricante en esa misma opción de Android. Algunos estéreos no ofrecen esa opción; en ese caso usa Mustang OS como app normal.

## 7. Problemas comunes

| Problema | Qué hacer |
|---|---|
| No aparece la portada o la canción de CarPlay | Revisa que **Ajustes → Datos de CarPlay** esté activo. Cambia de canción una vez con CarPlay abierto. |
| No aparece la guía de ruta | Inicia la ruta en el iPhone con CarPlay conectado y regresa a Mustang OS. Si nunca aparece, tu ZLink no publica la ruta. |
| Velocidad en «—» y mapa sin tu posición | Tu estéreo no está entregando GPS. Prueba al aire libre, revisa **Ajustes → Ubicación GPS** y la antena GPS del estéreo. |
| No se ve el modelo 3D | Tu estéreo no ofrece OpenGL ES 2.0; Mustang OS muestra una foto en su lugar. |
| Algo no funciona y quieres reportarlo | **Ajustes → Diagnóstico y exportación → Compatibilidad OEM → Guardar ZIP** y envía ese archivo a quien te compartió la app. No incluye tus canciones, rutas ni contactos. |

## 8. Desinstalar

Primero regresa a la pantalla de inicio original (paso 6). Luego en Android ve a **Ajustes → Apps → MUSTANG OS → Desinstalar**. No borres ni desinstales ZLink ni apps del fabricante.
