# Descargar la APK por Wi-Fi

No requiere memoria USB, depuración USB ni contraseña de fábrica. La instalación sigue necesitando la confirmación normal del instalador Android y, cuando corresponda, permiso del navegador para instalar aplicaciones desconocidas. Si el firmware bloquea también ese permiso, habrá que revisar el mensaje exacto que muestre. [Distribución por servidor y permisos de instalación, Android](https://developer.android.com/distribute/marketing-tools/alternative-distribution).

## Usar el enlace

La entrega principal desde 0.4.0 es `MustangOS.apk`, variante release sin depuración y con la firma local anterior. El script toma `output/MustangOS.apk` por defecto; también permite `--apk` para fijar una entrega concreta.

1. Mantén el PC encendido y conectado a la misma red que la pantalla.
2. En el navegador de la pantalla, introduce `http://IP_DEL_PC:8765/` usando la IPv4 local del PC.
3. Pulsa **Descargar Mustang OS**, abre la descarga y confirma **Instalar**. Si Android lo solicita, permite al navegador instalar aplicaciones desconocidas.
4. Abre **MUSTANG OS** desde Apps. Para la primera prueba conserva el launcher habitual. El acceso CarPlay permite elegir el ZLink ya instalado.

El enlace es local: no es una publicación en Internet y deja de funcionar si se apaga el PC, se detiene el servidor o transcurren dos horas desde su inicio. Cambiar de Wi-Fi puede cambiar la IP del PC.

## Iniciar el servidor

Con Python 3 disponible, desde la carpeta del proyecto:

```powershell
python scripts/serve-apk.py --host IP_DEL_PC --port 8765
```

Sustituye `IP_DEL_PC` por una dirección que pertenezca al PC. En este entorno también puede utilizarse el Python integrado que devuelve la herramienta de dependencias de Codex. Para un proceso iniciado en terminal, Ctrl+C lo detiene. Un proceso iniciado como ayudante oculto se detiene al cerrar su proceso o después del límite de dos horas.

El servidor solo permite la página `/` y la descarga `/MustangOS.apk`; no publica carpetas, diagnósticos ni código fuente. No registra direcciones de clientes, cookies ni parámetros de consultas. La respuesta APK lleva su tipo MIME y nombre de archivo de descarga. No modifica reglas de firewall ni opciones de la pantalla.

Desde la 0.2.0, la página también permite al usuario seleccionar y enviar un ZIP de compatibilidad mediante `/upload`; también admite APK extraídos para análisis. El servidor exige el token de la página, rechaza orígenes diferentes, limita a 128 MiB y comprueba la cabecera ZIP. Se guarda en `output/device-reports/` con nombre generado, sin extraerlo ni servirlo a otros clientes. La recepción se realiza desde el navegador; Mustang OS usa Internet únicamente si se activa su mapa Android. El límite de dos horas también se aplica a la recepción.

Pruebas del servidor: `python scripts/test_download_server.py`.
