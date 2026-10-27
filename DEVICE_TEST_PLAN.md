# Pruebas en el equipo · vehículo estacionado

Todas las filas comienzan **PENDIENTES**. Ninguna prueba automatizada sustituye esta tabla. Registra fecha, SDK, versión APK, resultado y observaciones, sin datos personales.

Evidencia parcial del usuario: instaló 0.1.0 por Wi-Fi, abrió ZLink y envió fotos del selector; confirmó puertas, temperatura y voltaje en el OEM. Esto no marca como superados los casos completos siguientes. Para 0.2.0, comprobar primero autodetección de CarPlay/radio/teléfono/música, ausencia de apps ajenas en Mapas, exportación del ZIP de compatibilidad y envío desde el navegador. Los datos MCU en Mustang OS y el panel CarPlay siguen pendientes; consulta NATIVE_INTEGRATION.md.

Actualización: el ZIP de compatibilidad fue recibido. La 0.3.0 incorpora el lector de prueba descrito en docs/DEVICE_FINDINGS.md. Con el vehículo estacionado, activar Motor → Activar lectura OEM → Activar escucha; anotar estado y contador. Comparar voltaje/temperatura con la barra OEM y abrir/cerrar una puerta para verificar recepción; no atribuir posiciones antes de correlacionarlas. Después, con ZLink ya activo, usar Ajustes → Inspeccionar servicio ZLink y volver a exportar el ZIP si hace falta. No marcar estos casos como pasados hasta recibir resultados físicos.

Antes de configurar Home: confirma que ZLink, radio, cámara y mandos funcionan con el launcher original; anota cómo volver a él. Instala Mustang OS como APK normal y realiza primero las pruebas sin cambiar Home. Prueba reversa con el vehículo inmovilizado, freno aplicado y las precauciones habituales del fabricante.

| Caso | Pasos / resultado esperado | Estado |
|---|---|---|
| Inicio limpio | Abrir sin permisos. `—` en velocidad, sin canción ni ruta y controles multimedia deshabilitados | PENDIENTE |
| Hardware | Diagnóstico: SDK, resolución y RAM reales. Contrastar SDK 32/33 con etiqueta Android comercial | PENDIENTE |
| Identificar ZLink | Elegir app original; comparar etiqueta, paquete, actividad y versión del diagnóstico con su pantalla Acerca de | PENDIENTE |
| Abrir ZLink | Entrar desde panel y dock; comprobar activación, ajustes y emparejamiento conservados | PENDIENTE |
| CarPlay conectado | Conectar iPhone; ZLink funciona. Mustang OS no declara conectado sin evidencia | PENDIENTE |
| Apple Music reproduciendo | Autorizar acceso multimedia voluntariamente. Volver al launcher. Datos reales si existe sesión; si no, estado no disponible | PENDIENTE |
| Apple Music pausado | Pausar desde iPhone y desde widget si está habilitado; título estable, progreso detenido | PENDIENTE |
| Cambio de canción | Anterior/siguiente si se admiten. Título, álbum y portada coinciden con iPhone; campos ausentes no retienen canción anterior | PENDIENTE |
| Fuente alternativa | Elegir reproductor Android real; comprobar que se identifica por su fuente, sin atribuirlo al iPhone | PENDIENTE |
| Sesión desaparecida | Cerrar/desconectar fuente. Se eliminan datos y controles. Otra app no recibe órdenes por sustitución de fuente explícita | PENDIENTE |
| Reconexión multimedia | Reabrir fuente; callbacks y metadatos vuelven sin duplicar controles | PENDIENTE |
| Apple Maps | Abrir navegación desde CarPlay, calcular ruta. Se mantiene en ZLink sin mapa falso en Mustang OS | PENDIENTE |
| Navegación activa | Escuchar indicaciones y regresar a Home; audio sin interferencia. Volver a ZLink conserva ruta | PENDIENTE |
| Mapas Android | Asignar app instalada; abrir desde tarjeta y volver a Mustang OS | PENDIENTE |
| Llamada entrante | Mientras suena música, recibir llamada. Audio, micrófono y prioridad OEM correctos; colgar y reanudar | PENDIENTE |
| Desconectar iPhone | ZLink mantiene su comportamiento original; launcher retira datos cuando la sesión deja de publicarlos | PENDIENTE |
| Reconexión inalámbrica | Conectar de nuevo sin cambiar Wi-Fi/Bluetooth desde Mustang OS; emparejamiento y activación intactos | PENDIENTE |
| Permiso GPS denegado | Denegar o conceder aproximada: no aparece un cero falso. El panel explica ubicación precisa | PENDIENTE |
| GPS con señal | Al aire libre, comparar recepción/velocidad estacionaria. Cero solo tras lectura real; no se guardan coordenadas | PENDIENTE |
| GPS sin señal | Perder señal o desactivar ubicación: velocidad a `—` después de cinco segundos como máximo. Recuperarla sin reiniciar | PENDIENTE |
| Revocar permisos | Revocar ubicación y multimedia en Ajustes. Volver sin crash ni metadatos de fuente anterior | PENDIENTE |
| Radio / teléfono | Asignar apps OEM y abrirlas. No cambiar canales/ajustes mediante intents no documentados | PENDIENTE |
| Cámara reversa | Probar desde launcher y ZLink: el sistema OEM muestra y retira cámara con normalidad | PENDIENTE |
| Mandos volante | Volumen, pista y llamada en apps originales; confirmar ausencia de interferencia | PENDIENTE |
| Volver al original | Elegir y abrir launcher OEM. Restaurar Home desde ajustes del sistema, si existe | PENDIENTE |
| Home opcional | Tras pruebas anteriores, elegir Mustang OS manualmente. Home regresa a dashboard; evaluar arranque OEM sin prometer reemplazo | PENDIENTE |
| Ciclo de vida | Alternar 20 veces entre ZLink, ajustes y Mustang OS. Sin cierres, duplicación de listeners ni pérdida de asignaciones | PENDIENTE |
| Layout | 1280×720 y densidad real: texto legible, tres tarjetas y ocho accesos sin recortes; probar tamaño de fuente por defecto y grande | PENDIENTE |
| Rendimiento | Medir arranque frío/caliente, memoria y fluidez; no afirmar medidas del PC como medidas del T100 | PENDIENTE |
| Exportación | Revisar .txt antes de compartir: sin coordenadas, canciones, notificaciones, seriales ni IMEI | PENDIENTE |

## Medidas opcionales con ADB

Con autorización ADB ya concedida y la pantalla estacionada:

```powershell
adb shell am start -W -n com.mustangos.launcher/.ui.MainActivity
adb shell dumpsys meminfo com.mustangos.launcher
adb shell dumpsys gfxinfo com.mustangos.launcher
```

Anota PSS en reposo y después de 20 cambios, tiempo de arranque, frames lentos y densidad. Objetivos provisionales: APK <15 MB, sin crecimiento sostenido de memoria tras alternar, acciones táctiles sin bloqueo visible y ningún listener GPS activo cuando el launcher está oculto. La memoria y los tiempos finales dependen del firmware; quedan pendientes hasta medirlos.

## Registro por caso

```text
Fecha / versión APK:
SDK / resolución / densidad:
Caso:
Resultado: PASÓ / FALLÓ / NO DISPONIBLE
Observación sin información privada:
Diagnóstico básico adjunto: sí/no
```
