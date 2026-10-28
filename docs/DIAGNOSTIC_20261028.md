# Diagnóstico recibido el 28 de octubre de 2026

ZIP 0.4.0: `20261028-065553-2546fb47.zip`, 16 967 467 bytes, 39 entradas y 44 076 668 bytes sin comprimir. SHA-256 `a1ef35e2db9b8da864bfe2e786f7c6c6391c3938228df71579a0e584256e383b`. Se validaron rutas, duplicados, tamaños y tipo antes de extraer únicamente manifiestos/DEX y textos permitidos. El ZIP, APK reconstruidos y decompilaciones permanecen fuera de Git y no se ejecutaron.

## Evidencia multimedia

- La captura muestra 14 sesiones del paquete `com.tw.bt`, ninguna seleccionada. Doce se detallan y dos quedan fuera del límite. Algunas contienen TITLE, ARTIST y DURATION y estado PLAYING; ninguna de las detalladas tiene bitmap ni URI de portada.
- En ese momento no aparece una sesión ZLink, aunque el servicio devuelve `com.zjinnova.zlink.android.core.codec.IPhoneContentPresenter`.
- La preferencia `mediaPackage` no se exportaba, por lo que la falta de selección no se atribuye con certeza a una preferencia ZLink ausente. La política ya distinguía paquetes; los duplicados por sí solos no explican ese rechazo.
- La revisión detectó selección arbitraria del primer token dentro del paquete y conservación de un wrapper antiguo para el mismo token. El trabajo local corrige ranking/estabilidad y añade motivo/preferencia al diagnóstico, manteniendo controles y datos de un mismo controlador.
- El usuario probó Bluetooth durante CarPlay y respondió que no recuperaba los datos correctos. También confirmó que Android no muestra ninguna notificación musical. No se usa BT para rellenar datos del iPhone ni se implementa lectura de notificaciones por conjetura.

El código BT inspeccionado publica título, artista y duración del modelo A2DP/TW, sin portada. Los DEX ZLink contienen 20 clases del loader y nueve BuildConfig/R; no incluyen el contrato IPhoneContentPresenter. No se intentó ejecutar/desempaquetar el loader ni adivinar transacciones Binder.

## Archivo pertinente que falta

El HOME original está configurado y visible como `com.dofun.variety/com.dofun.overseasvariety.Launcher`. El exportador anterior ordenaba todos los HOME de sistema y tomaba cuatro: android, managedprovisioning, provision y settings consumieron el límite. Son HOME auxiliares reales, no un fallo de filtros del PackageManager. Se dejaron fuera los clientes del escritorio.

CarSetting referencia `com.dofun.variety/cn.cardoor.desktop.window.DesktopWindowAppSetting`, por lo que obtener el APK instalado de ese paquete es el siguiente paso concreto para estudiar ventanas/posibles callbacks. No demuestra que permita incrustar CarPlay: los selectores PiP/mapas de CarSetting excluyen ZLink expresamente.

El trabajo local prioriza el HOME de sistema configurado y excluye los seis componentes auxiliares observados de la cuota, conservándolos en el inventario. Mantiene límites de número/tamaño y no copia apps HOME de usuario, recursos, licencias ni datos privados.

Para ayudar sin instalar otra versión de Mustang OS: extraer **Tema (`com.dofun.variety`)** con un extractor que muestre apps del sistema y enviar ese APK. [ML Manager, ficha del desarrollador](https://play.google.com/store/apps/details?id=com.javiersantos.mlmanager) documenta extracción de apps de sistema sin root. La página local acepta APK/ZIP como archivos opacos, sin instalarlos ni ejecutarlos. El servidor conserva extensión generada `.zip`; el siguiente archivo puede ser un APK con AndroidManifest en la raíz, no otro diagnóstico con la estructura packages/.

Si el cliente no aporta un contrato legible/utilizable, falta SDK/AIDL Android 6.1.06: callbacks de canción/portada/posición/duración, Surface y entrada táctil, con soporte para APK normal. El fabricante [ofrece documentación de integración](https://zjinnova.com/android-zlink.html) y publica [su contacto oficial](https://zjinnova.com/contact.html). No se ha enviado ningún mensaje a terceros.

## Otras lecturas de la captura

GPS tiene permiso preciso pero no una posición válida. OEM está habilitado/suscrito con cero eventos y sin valores en esta captura; eso no revoca la evidencia anterior de voltaje/aperturas ni prueba que se hayan recibido valores nuevos. No se convierten esos estados en datos del automóvil.
