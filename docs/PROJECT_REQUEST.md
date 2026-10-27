# PROYECTO: MUSTANG OS — CUSTOM ANDROID CAR LAUNCHER

## 1. TU ROL

Actúa como un equipo de ingeniería de software de nivel senior, especializado en:

- Desarrollo Android nativo con Kotlin.
- Launchers automotrices personalizados.
- Sistemas multimedia Android aftermarket.
- Integración con ZLink, Apple CarPlay y Android Auto.
- MediaSession, MediaController, servicios Android e IPC.
- Integración con GPS y telemetría OBD2.
- Optimización de dispositivos Android con hardware limitado.
- Diseño UI/UX automotriz y seguridad durante la conducción.
- Pruebas automatizadas, depuración y generación de APK.

**Tu misión es desarrollar una aplicación Android funcional llamada MUSTANG OS, diseñada específicamente para mi Ford Mustang 2010 V6 4.0L convertible rojo con capota de lona negra.**

No quiero únicamente un prototipo visual. Quiero un proyecto Android real, mantenible, compilable e instalable en mi pantalla multimedia.

Trabaja de forma autónoma, investiga la documentación técnica necesaria, implementa el código, ejecuta pruebas y entrega resultados verificables.

No afirmes que una integración funciona si no existen pruebas técnicas que la respalden.

---

## 2. HARDWARE REAL DE MI EQUIPO

Estas especificaciones fueron obtenidas directamente desde CPU-Z y la información del fabricante.

**Pantalla Android:**

- Equipo: T10S / plataforma T100.
- Fabricante del procesador: Allwinner.
- SoC detectado: Allwinner A133.
- Hardware reportado: sun50iw10p1.
- Arquitectura: ARMv8 / Cortex-A53, 4 núcleos.
- GPU: PowerVR Rogue GE8300.
- RAM: 2 GB.
- Almacenamiento comercial: 32 GB.
- Pantalla: aproximadamente 10.59 pulgadas.
- Resolución física: 1280 × 720.
- Orientación principal: horizontal.
- Android anunciado: 13.
- API detectada por CPU-Z: 32.
- Acceso root: no.
- Servicios de Google Play disponibles.

Existe una discrepancia entre Android 13 y API 32. Verifica la versión real de ejecución y las APIs disponibles. No confíes únicamente en la etiqueta que muestra el fabricante.

**Firmware y componentes:**

- Plataforma: T100.
- Versión MCU: T13.1.1-100-412-A6939D-241016(6).
- Sistema: V13.1.1_20251029.180244_OS-EN.
- Aplicación del fabricante: V13_20251029.175328_APP-THEME1.
- Versión CAN: FTYM0423V633.
- Procesador de sonido: PT2313.
- Radio: QN8035.
- Bluetooth anunciado: 5.4.

No modificar firmware, MCU, bootloader, servicios CANBUS ni aplicaciones originales del fabricante.

---

## 3. INFORMACIÓN DEL AUTOMÓVIL

El vehículo es un:

**Ford Mustang 2010 Convertible V6 4.0L**

Características importantes:

- Carrocería convertible.
- Pintura exterior roja.
- Capota plegable de lona negra.
- Motor V6 4.0L.
- No es Mustang GT.
- No es motor V8 5.0L.
- No utilizar imágenes de versiones 2015 o posteriores.

La imagen central debe representar correctamente un Ford Mustang convertible de la generación 2010, rojo, con techo negro.

Quiero que la imagen del automóvil sea uno de los principales elementos visuales.

---

## 4. OBJETIVO PRINCIPAL: INTEGRACIÓN CON CARPLAY

**ESTA ES LA PRIORIDAD MÁS IMPORTANTE DEL PROYECTO.**

Actualmente utilizo un iPhone conectado mediante Apple CarPlay a través de ZLink.

Aplicación existente:

- Nombre: ZLink.
- Versión: 6.1.06.
- Plataforma ZLink: DINGWEI Ultimate.
- Plataforma de hardware: DINGWEI-A133.
- Compatible con CarPlay inalámbrico según la configuración reportada.

ZLink ya funciona en mi equipo y está activado.

**Es obligatorio conservar ZLink, sus datos, su activación y sus configuraciones.**

No quiero desarrollar un sustituto de CarPlay ni reemplazar ZLink.

Quiero que Mustang OS sea una interfaz principal que interactúe con ZLink de la manera más profunda que permita técnicamente mi hardware.

### 4.1 Investigación obligatoria de ZLink

Antes de implementar la integración:

1. Investiga las interfaces públicas o legítimamente disponibles de ZLink 6.x.
2. Identifica el paquete Android instalado y la actividad de lanzamiento.
3. Verifica si expone intents, servicios, broadcasts, proveedores de contenido o interfaces de integración utilizables.
4. Comprueba si publica sesiones multimedia mediante Android MediaSession.
5. Determina si permite obtener metadatos, estados de conexión y controles multimedia.
6. Determina si existe un mecanismo documentado para integrar elementos de navegación o controlar la presentación de CarPlay.
7. Comprueba qué opciones existen sin root y sin permisos privilegiados.
8. Documenta qué posibilidades están confirmadas, cuáles necesitan probarse en mi pantalla y cuáles están bloqueadas.

No inventes APIs de ZLink. No des por hecho que el identificador del paquete es `com.zjinnova.zlink`; compruébalo.

No utilices modificaciones de licencias, eliminación de comprobaciones de activación ni mecanismos inseguros para acceder a funcionalidades privadas.

### 4.2 Integración de Apple Music

**Yo utilizo Apple Music desde mi iPhone mediante CarPlay.**

Quiero que el panel izquierdo del launcher muestre, siempre que sea técnicamente posible:

- Título real de la canción.
- Nombre del artista.
- Álbum.
- Portada del álbum.
- Duración.
- Progreso de reproducción.
- Estado de reproducción.
- Botón anterior.
- Botón reproducir/pausar.
- Botón siguiente.

Quiero que esos controles funcionen con Apple Music en mi iPhone cuando esté conectado a CarPlay.

Implementa una capa modular llamada `MediaIntegrationManager`.

Investiga y prueba MediaSessionManager, MediaController, MediaMetadata, PlaybackState y NotificationListenerService cuando proceda.

**Importante:** una sesión de Apple Music reproducida en el iPhone no necesariamente se expone como una sesión multimedia Android. No presentes MediaSession como una solución garantizada.

Si ZLink no expone metadatos ni controles:

- Documenta esa limitación.
- Conserva un botón para abrir CarPlay directamente.
- No muestres canciones, portadas ni progresos inventados.
- Considera fuentes Android alternativas solamente si funcionan de verdad.
- Diseña el widget para mostrar claramente el estado disponible o no disponible.

Los controles deben funcionar de verdad y solo habilitarse cuando la fuente permita controlarlos.

### 4.3 Integración de Mapas

Utilizo navegación desde CarPlay, principalmente Apple Maps y potencialmente Google Maps o Waze.

Quiero una tarjeta de navegación en la pantalla principal que, cuando exista una integración técnica válida, muestre:

- Mapa o vista de navegación.
- Ruta activa.
- Próxima maniobra.
- Distancia al siguiente giro.
- Tiempo estimado de llegada.
- Distancia restante.
- Estado de navegación.

Investiga si ZLink expone legal y técnicamente esta información a otras aplicaciones Android.

Verifica si se puede integrar realmente alguna vista de navegación CarPlay dentro de un panel del launcher.

No asumas que es posible incrustar una aplicación de terceros dentro de una vista Android.

No confundas el framework CarPlay para aplicaciones iOS con una API de integración de ZLink para Android.

Si no existe acceso a los datos de navegación de CarPlay, implementa la alternativa más funcional:

- Tarjeta claramente identificada para abrir ZLink.
- Acceso rápido a CarPlay.
- Opción de abrir una aplicación de mapas Android instalada.
- Posible navegación Android nativa como módulo separado y claramente diferenciado.
- Retorno sencillo a Mustang OS.

Nunca muestres rutas, instrucciones o mapas simulados como si fueran datos reales.

### 4.4 Experiencia de conexión CarPlay

Quiero una experiencia fluida:

- Abrir ZLink desde el launcher.
- Conservar el emparejamiento inalámbrico existente.
- Mostrar un estado de conexión cuando pueda comprobarse.
- Permitir regresar fácilmente a Mustang OS.
- Reanudar ZLink sin reinicializaciones innecesarias.
- Evitar interferir con llamadas, música, micrófono y audio de navegación.
- No forzar Bluetooth, Wi-Fi ni permisos protegidos mediante mecanismos incompatibles.

Investiga si hay una manera legítima de ofrecer una experiencia de panel unificado. Si no es posible integrar contenido interactivo de CarPlay en el launcher, utiliza el ZLink original como pantalla de CarPlay y explica la limitación de forma clara.

---

## 5. DISEÑO DE LA PANTALLA PRINCIPAL

Nombre visual: **MUSTANG OS**

Resolución objetivo: **1280 × 720 píxeles**.

El diseño debe ser elegante, deportivo, moderno y optimizado para uso táctil dentro de un automóvil.

Paleta:

- Negro profundo.
- Gris grafito.
- Rojo deportivo.
- Blanco para textos.
- Efectos luminosos rojos muy moderados.

Quiero un estilo deportivo premium, con tarjetas redondeadas, diseño limpio y buena legibilidad.

La referencia gráfica de Mustang OS que adjunte debe utilizarse como guía de composición, pero el resultado debe ser una interfaz Android nativa funcional, no una imagen estática con botones falsos.

### Distribución

**Barra superior**

- Logo Mustang OS.
- Hora real.
- Fecha opcional.
- Temperatura exterior únicamente si existe fuente real.
- Estado de conectividad.
- Acceso a ajustes.

**Panel izquierdo: Apple Music / Multimedia**

Mostrar datos musicales reales desde una fuente compatible.

**Panel central: Mustang**

- Imagen del Ford Mustang 2010 convertible rojo con techo negro.
- Velocímetro digital.
- Indicador km/h.
- Animación ligera del velocímetro.
- Información del motor V6 4.0L.
- Kilometraje solamente si puede obtenerse una lectura real y fiable.

**Panel derecho: Navegación**

- Estado de navegación.
- Vista de mapa real cuando sea posible.
- Acceso inmediato a ZLink.
- Acceso a aplicaciones de mapas Android.
- Instrucciones reales cuando exista integración comprobada.

**Barra inferior**

Botones grandes:

- Música.
- CarPlay / ZLink.
- Navegación.
- Teléfono.
- Radio.
- Apps.
- Motor / OBD2.
- Ajustes.

Todos los botones deben tener funcionalidades reales.

No incluir controles decorativos que parezcan activos sin hacer nada.

---

## 6. RENDERIZADO DEL MUSTANG

El vehículo debe aparecer de manera elegante y realista.

Requisitos:

- Modelo visual Mustang 2010 convertible.
- Color rojo.
- Capota de lona negra, visible.
- Perspectiva frontal de tres cuartos.
- Aspecto deportivo.
- Fondo oscuro integrado.
- Buena calidad visual sin afectar demasiado al rendimiento.

Preparar assets optimizados para la pantalla.

No utilizar un motor 3D pesado de manera predeterminada.

Si existe una alternativa eficiente, permitir una animación sutil del vehículo o transiciones entre vistas.

---

## 7. VELOCÍMETRO Y TELEMETRÍA

Implementar un velocímetro real.

Fuente inicial:

- GPS del dispositivo Android.

Requisitos:

- Mostrar velocidad en km/h.
- Gestionar pérdida de señal.
- Filtrar fluctuaciones bruscas.
- Actualizaciones eficientes.
- Distinguir ausencia de señal de velocidad cero.
- Gestionar permisos de ubicación.

Crear una arquitectura preparada para integrar posteriormente OBD2.

Módulo futuro:

`VehicleTelemetryManager`

Posibles lecturas:

- RPM.
- Temperatura del refrigerante.
- Voltaje.
- Velocidad OBD2.
- Carga del motor.
- Otros parámetros realmente disponibles.

No inventar valores ni asumir que el CANBUS del fabricante permite leer información desde una APK convencional.

El velocímetro no sustituye los instrumentos originales del automóvil.

---

## 8. ARQUITECTURA Y TECNOLOGÍA

Preferencia de desarrollo:

- Kotlin.
- Android SDK nativo.
- Android Studio.
- Gradle.
- Arquitectura MVVM o equivalente bien justificada.
- StateFlow y corrutinas cuando correspondan.
- Separación estricta entre UI, datos e integraciones.

Con 2 GB de RAM, prioriza un diseño ligero. Prefiero Android Views/XML optimizados si eso resulta más eficiente y compatible que una interfaz pesada.

Evita dependencias innecesarias y procesos constantes en segundo plano.

Módulos o responsabilidades sugeridas:

- `launcher`
- `ui`
- `settings`
- `appintegration`
- `carplay`
- `media`
- `navigation`
- `gps`
- `vehicle`
- `diagnostics`

Puedes ajustar la arquitectura según los requisitos reales.

La integración con aplicaciones externas deberá usar PackageManager, intents e interfaces Android autorizadas.

No utilizar accesibilidad para automatizar pantallas ajenas ni captura de pantalla para simular una integración CarPlay.

---

## 9. COMPATIBILIDAD Y SEGURIDAD

Condiciones obligatorias:

1. No requerir root.
2. No modificar MCU.
3. No reemplazar ZLink.
4. No borrar datos del fabricante.
5. No modificar la configuración CANBUS.
6. No interrumpir la cámara de reversa.
7. No interferir con controles del volante.
8. No bloquear aplicaciones originales del vehículo.
9. No solicitar permisos privilegiados imposibles de conceder.
10. No alterar la configuración del sistema sin consentimiento explícito.

Primero la aplicación debe instalarse como una APK normal.

Después de probarla, permitir que el usuario elija Mustang OS como launcher predeterminado si el firmware admite esa configuración.

Añadir un método claro para regresar al launcher original.

No asumir que configurarse como Home en Android garantiza reemplazar la interfaz de arranque impuesta por el fabricante.

Optimizar para evitar bloqueos y mantener controles adecuados al conducir.

---

## 10. PANEL DE DIAGNÓSTICO

Crear una pantalla técnica de diagnóstico.

Debe mostrar:

- Android SDK detectado en tiempo de ejecución.
- Resolución.
- RAM.
- Estado GPS.
- Aplicaciones compatibles instaladas.
- Paquete ZLink detectado.
- Disponibilidad de integración multimedia.
- Sesiones multimedia visibles con los permisos apropiados.
- Estado de conexión CarPlay, solamente si se puede determinar.
- Estado de integraciones de navegación.
- Permisos necesarios.
- Errores recientes de la aplicación.

Mostrar mensajes claros y permitir exportar un diagnóstico básico sin información privada como IMEI, seriales o identificadores del equipo.

Este módulo es especialmente importante porque Codex no necesariamente tendrá acceso físico a mi pantalla.

---

## 11. ESTRATEGIA DE DESARROLLO

Trabaja por fases, pero desarrolla tanto como permita el entorno sin detenerte en una simple propuesta.

### Fase 1 — Investigación y proyecto base

- Examinar el repositorio.
- Determinar herramientas disponibles.
- Investigar compatibilidad Android.
- Crear el proyecto.
- Configurar compilación.
- Preparar recursos gráficos.
- Documentar las restricciones conocidas.

### Fase 2 — Launcher funcional

- Implementar pantalla principal.
- Desarrollar navegación entre pantallas.
- Crear botones reales.
- Permitir abrir aplicaciones instaladas.
- Añadir configuración.
- Incorporar imagen del Mustang.
- Añadir velocímetro GPS.

### Fase 3 — ZLink y CarPlay

Prioridad máxima.

- Detectar ZLink.
- Abrir ZLink.
- Gestionar el regreso al launcher.
- Investigar interfaces disponibles.
- Probar el acceso multimedia.
- Implementar integración verificable.
- Preparar diagnósticos para las pruebas en hardware.

### Fase 4 — Apple Music y navegación

- Implementar widget multimedia conectado a fuentes reales.
- Leer metadatos cuando estén disponibles.
- Implementar controles autorizados.
- Comprobar navegación real.
- Proporcionar alternativas cuando ZLink no exponga la información.

### Fase 5 — Optimización y pruebas

- Revisar memoria.
- Mejorar tiempos de respuesta.
- Probar orientación y resolución.
- Probar ciclo de vida de aplicaciones.
- Manejar desconexiones.
- Verificar permisos.
- Ejecutar pruebas automatizadas.
- Generar APK.

---

## 12. PRUEBAS OBLIGATORIAS

Implementa pruebas para:

- Inicio de la aplicación.
- Estado inicial sin permisos.
- Detección de aplicaciones instaladas.
- Ausencia de ZLink.
- Apertura de ZLink cuando se encuentre disponible.
- Estados de sesiones multimedia.
- Metadatos vacíos o incompletos.
- Controles multimedia no compatibles.
- Pérdida de señal GPS.
- Reconexión de fuentes.
- Rendimiento básico.
- Estados de error.
- Restauración de la interfaz después de cambiar entre aplicaciones.

Distingue claramente las pruebas automatizadas de las que requieren mi pantalla real.

Crea un documento de pruebas manuales para comprobar, con el vehículo estacionado:

- CarPlay conectado.
- Apple Music reproduciéndose.
- Apple Music pausado.
- Cambio de canción.
- Apertura de Apple Maps.
- Navegación activa.
- Llamada entrante.
- Desconexión del iPhone.
- Reconexión inalámbrica.
- Cámara de reversa.
- Mandos al volante.
- Regreso al launcher original.

No declares superadas las pruebas de hardware sin resultados reales.

---

## 13. ENTREGABLES

Quiero recibir:

1. Proyecto Android Studio completo con código fuente.
2. APK de depuración compilada, si el entorno lo permite.
3. Instrucciones de instalación mediante USB o ADB.
4. Instrucciones para utilizar el launcher.
5. Procedimiento para volver al launcher original.
6. Código organizado y documentado.
7. Archivo `README.md`.
8. Documento `COMPATIBILITY.md`.
9. Documento `ZLINK_INTEGRATION.md`.
10. Documento `DEVICE_TEST_PLAN.md`.
11. Registro de pruebas ejecutadas.
12. Lista de características funcionales y limitaciones reales.

Si alguna función no puede implementarse sin acceso a hardware o documentación privada, entrega un módulo con manejo correcto del estado no disponible, documentación técnica y pasos concretos para investigarlo en el dispositivo.

No marques funcionalidades incompletas como terminadas.

---

## 14. CRITERIOS DE ACEPTACIÓN

Consideraré que la primera versión está lista cuando:

- La APK compile y pueda instalarse.
- El launcher se adapte a 1280 × 720.
- El diseño represente mi Mustang convertible rojo con capota negra.
- Los botones principales sean funcionales.
- ZLink pueda abrirse sin modificar su instalación.
- Se pueda regresar a Mustang OS.
- El velocímetro funcione con GPS cuando exista señal.
- La interfaz no invente información multimedia.
- Apple Music y los mapas tengan la integración real máxima que permita el equipo.
- Las limitaciones de ZLink estén documentadas.
- Exista una forma segura de regresar al launcher original.

La prioridad es la integración real con CarPlay, sin sacrificar la estabilidad del equipo.

---

## 15. INSTRUCCIONES FINALES PARA CODEX

No te limites a explicar cómo desarrollar el proyecto.

**Comienza a programar.**

Primero examina el entorno y estructura el repositorio. Luego implementa una primera versión funcional y continúa por prioridades.

Si ya existe código, úsalo y mejóralo sin destruir trabajo anterior.

Utiliza documentación oficial y dependencias mantenidas.

Si necesitas datos del dispositivo que no puedes obtener desde tu entorno, crea una herramienta de diagnóstico o proporciona instrucciones ADB específicas, sin solicitar identificadores privados.

No inventes integraciones con ZLink, Apple Music o Apple Maps.

No utilices imágenes estáticas para fingir widgets funcionales.

No sustituyas una funcionalidad solicitada por una simulación sin avisar.

Diferencia explícitamente:

- Funcionalidad implementada y probada.
- Funcionalidad implementada pendiente de pruebas en hardware.
- Funcionalidad viable pero todavía no implementada.
- Funcionalidad bloqueada por restricciones de ZLink, CarPlay o Android.

**META FINAL:** construir Mustang OS como un launcher automotriz personalizado, fluido, atractivo y realmente funcional para mi Ford Mustang 2010 V6 4.0 convertible rojo con capota negra, manteniendo ZLink y ofreciendo la mejor integración técnicamente posible con Apple Music y navegación CarPlay.