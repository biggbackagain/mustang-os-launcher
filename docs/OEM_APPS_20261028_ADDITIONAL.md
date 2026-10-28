# Interfaces OEM adicionales recibidas el 28 de octubre

Se recibieron seis APK distintos y tres duplicados. El inventario privado, hashes y análisis estático están en `.downloads/apps-20261028-new/inventory.json`. No se ejecutaron APK/DEX ni se copiaron librerías o recursos OEM a la entrega. Los archivos de diagnóstico permanecen excluidos de Git y de la distribución.

| Paquete | Versión | Archivo inicial |
|---|---|---|
| com.tw.carinfoservice | 251014 | 20261028-092352-58dd944c.zip |
| com.tw.carchoose | 250811 | 20261028-092553-2a3fe2e7.zip |
| com.tw.radio | T100_master.20251016 | 20261028-092600-d8591359.zip |
| com.tw.coreservice | v1.0.0.220302 | 20261028-092606-75d54c22.zip |
| com.tw.service | 1.0 | 20261028-092611-e0d5a510.zip |
| com.tw.service.xt | 2022.02.17 | 20261028-092616-4ad76141.zip |

## CarInfoService

El manifiesto identifica `com.tw.carinfoservice.CarService`, exportado, sin permiso obligatorio en ese componente; declara `com.tw.carinfoservice.permission.USE_AIDL` como permiso normal. La acción es `com.tw.carinfoservice.CarService.Bind`, descriptor `com.tw.carinfoservice.CarServiceAidl`.

El servidor legible confirma cuatro getters puramente de caché utilizados: transacciones 4, 5, 7 y 20. Los arrays se leen con longitud exactamente conocida, valores/sentinelas y unidades comprobados. La app valida componente, estado habilitado/exportado, permiso si lo exige y descriptor, con binding flags 0. Los getters se ejecutan fuera de Main, con cola/trabajador acotados, mientras la pantalla es visible y la lectura OEM está habilitada. Los callbacks tardíos de una conexión cerrada se descartan.

Los snapshots no publican una fecha física. El tiempo de consulta no se presenta como edad de medición. Valores anteriores pueden conservarse con disponibilidad falsa cuando una consulta deja de aportar datos. Getter 20 no proporciona evidencia inequívoca de refrigerante; se etiqueta sensor no identificado. Getter 21 ambiente tiene una ruta con valor predeterminado defectuoso y se excluye; sigue usándose el canal TW 1288 comprobado.

Los getters 18/19 y el desregistro de callback incluyen escrituras de activación/desactivación MCU 1291. No se copió esa secuencia. El código del servidor también demuestra recepción de TW 1291: arg1 como velocidad escalada por 100 y arg2 como RPM, con 65535 como no disponible. Mustang añade solo su escucha pasiva, rechaza campos no plausibles y caduca valores a los cinco segundos. No se envía ninguna solicitud de activación 1291 ni se infiere estado de encendido a partir de esos valores.

## Radio y XT

`com.tw.radio.RadioService` exporta un Binder local sin contrato AIDL útil para un cliente externo. El modelo de la aplicación sí demuestra un contrato TWUtil para datos: 769 fuente, 1025 banda/frecuencia, 1026 presintonías, 1028 PS/RDS, 1029 RadioText y 1030 región/límites. Se verificaron FM en centésimas de MHz y AM en kHz, y las solicitudes de lectura iniciales 769/1025/1028 con argumento 255.

XT declara `com.tw.service.xt.CommandService`, descriptor `com.tw.service.xt.aidl.ITWCommandAidl`, con operaciones demostradas para anterior/siguiente, búsqueda y banda. El control es fan-out hacia clientes OEM: un acuse no demuestra que el sintonizador haya cambiado. Se utiliza el endpoint público ya activo, sin BIND_AUTO_CREATE ni arranque automático. Solo botones explícitos y con fuente radio vigente pueden enviar esas órdenes; una orden en cola se rechaza si la fuente cambia antes de despacharla. No se cambia la fuente durante CarPlay al abrir el panel.

Las presintonías observadas se muestran como datos, sin asignar semántica no comprobada a sus bits. Al cambiar banda se descartan valores de la anterior para evitar atribuirle otra unidad/frecuencia.

## Otros componentes

CarChoose y los servicios centrales forman parte de la configuración/transporte del fabricante. El análisis no acredita otra interfaz pública que sea necesaria para este alcance. Se conserva su implementación OEM: el perfil visual Mi Mustang no escribe configuración de vehículo/CAN/MCU. No se intenta reemplazar firmware, activación, conductor serial o servicios privilegiados.

Las rutas de lectura implementadas y sus límites están reflejados en [RELEASE_050.md](RELEASE_050.md). La validación con mocks comprueba contrato/límites/ciclo de vida; la disponibilidad de los valores y controles físicos debe comprobarse en la pantalla.
