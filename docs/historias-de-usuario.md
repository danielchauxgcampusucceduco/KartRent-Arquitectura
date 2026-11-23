# Historias de usuario — KartRent

Sistema Integral de Renta y Gestión Operativa de Karting.
Las historias se escribieron **antes** de los diagramas y del código: cada flujo del diagrama de contexto, cada componente, cada clase y cada endpoint se justifica en una de ellas (ver la [matriz de trazabilidad](#matriz-de-trazabilidad)).
Los criterios de aceptación de cada historia son **los mismos escenarios Gherkin que ejecuta la suite** (`backend/src/test/resources/features`); aquí se copian sin cambios (el archivo `HU05_administracion.feature` se reparte entre HU-05A y HU-05B) para que el documento y las pruebas no se separen.

## Marco de calidad

| Criterio INVEST | Cómo se verificó |
| --- | --- |
| Independiente | Cada historia se entrega sola; las dependencias entre componentes pasan por interfaces (puertos). |
| Negociable | Describen el *qué*; el *cómo* queda en «Datos técnicos». |
| Valiosa | Cada una dice qué gana el rol que la pide. |
| Estimable | Reglas de negocio y sistemas externos identificados. |
| Pequeña | HU-05 se dividió en HU-05A y HU-05B para que cada parte quepa en un sprint. |
| Testeable | Escenarios Gherkin automatizados en Cucumber (HU-01 a HU-05). |

- **Corte vertical:** cada historia atraviesa frontend → API REST → caso de uso → dominio → base de datos.
- Las **reglas de negocio (RN)** no mencionan tecnología; la lógica técnica va en «Datos técnicos».
- Los valores numéricos (10 minutos de retención, 25 s de vuelta mínima, 20 h de umbral) son **parámetros de ejemplo configurables**, pendientes de validar con el kartódromo. El sistema no mide ni promete indicadores de negocio (ausentismo, tiempo de salida, disponibilidad de flota): no hay instrumentación para ello.

| ID | Rol | Historia | Componente principal | Usuario del sistema |
| --- | --- | --- | --- | --- |
| HU-01 | Piloto / cliente | Reservar y pagar una tanda | Reservas y Clientes · Pagos y Facturación | Público (sin sesión) |
| HU-02 | Jefe de boxes | Asignar karts y controlar la tanda | Control de Pista y Telemetría | `boxes` o `admin` |
| HU-03 | Piloto | Ver tiempos y clasificación en vivo | Control de Pista y Telemetría | Público (lectura); el decodificador usa `cronometraje` |
| HU-04 | Mecánico / jefe de taller | Controlar el desgaste y atender órdenes preventivas | Flota y Mantenimiento | Mecánico (su código de empleado), `boxes`, `admin` |
| HU-05A | Administrador | Configurar tarifas, tandas y umbral; cancelar tandas | Reservas y Clientes · Flota | `admin` |
| HU-05B | Administrador | Consultar ocupación e ingresos | Reservas y Clientes | `admin` |

**Sistemas externos (no son historias de usuario):** el *decodificador de meta* envía los pasos de los transponders y la *pasarela de pagos* cobra y confirma. Participan en HU-03 y HU-01, pero no son roles humanos.

**Estados de una tanda (RN-06):** `PROGRAMADA → EN_PARRILLA → EN_CURSO → FINALIZADA`. `EN_PARRILLA` es la tanda con al menos un kart asignado; se puede volver a `PROGRAMADA` retirando todos los karts. Una tanda no iniciada puede pasar a `CANCELADA`. Las reservas se aceptan en `PROGRAMADA` y `EN_PARRILLA`.

---

## HU-01 — Reservar y pagar una tanda en línea

**Como** piloto, **quiero** reservar cupos en una tanda y pagarlos en línea, **para** asegurar mi turno sin hacer fila en el kartódromo.

**Precondiciones:** cliente registrado con exoneración de responsabilidad firmada; tanda `PROGRAMADA` o `EN_PARRILLA` con cupos libres.
**Postcondiciones:** reserva `CONFIRMADA`, cupos descontados, pago aprobado registrado y factura emitida. Si el pago se rechaza, la reserva sigue `PENDIENTE_PAGO` y se puede reintentar mientras dure la retención.

**Reglas de negocio**
- RN-01: un cupo retenido sin pago expira a los 10 minutos y vuelve a la tanda.
- RN-02: la edad del piloto debe estar en el rango del tipo de kart de la tanda (adulto: 16 años o más; infantil: de 8 a 15).
- RN-03: sin exoneración firmada no se puede reservar.
- RN-14: una reserva pendiente o pagada se puede cancelar mientras no tenga kart asignado y la tanda no haya iniciado. Pendiente: libera los cupos (`CANCELADA`). Pagada: reembolsa el pago (`REEMBOLSADA`).

```gherkin
# language: es
Característica: HU-01 Reservar y pagar una tanda en línea
  Como piloto aficionado
  Quiero reservar cupos en una tanda y pagarlos en línea
  Para asegurar mi turno sin hacer fila en el kartódromo

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-1800" de karts ADULTO con 4 cupos
    Y el cliente "1085000001" de 24 años con exoneración firmada

  Escenario: Reserva y pago exitoso con factura electrónica
    Cuando el cliente "1085000001" reserva 2 cupos en la tanda "T-1800"
    Entonces la tanda "T-1800" tiene 2 cupos disponibles
    Cuando paga la reserva con "TARJETA" y la clave "pago-1"
    Entonces la reserva queda "CONFIRMADA"
    Y se emite una factura por 90000 pesos con CUFE

  Escenario: El pago es idempotente (no se cobra dos veces)
    Cuando el cliente "1085000001" reserva 1 cupos en la tanda "T-1800"
    Y paga la reserva con "TARJETA" y la clave "repetida"
    Y paga la reserva con "TARJETA" y la clave "repetida"
    Entonces la pasarela registró 1 cobro

  Escenario: RN-01 la reserva no pagada expira y libera los cupos
    Cuando el cliente "1085000001" reserva 3 cupos en la tanda "T-1800"
    Y pasan 11 minutos
    Y se ejecuta la expiración de reservas
    Entonces la reserva queda "EXPIRADA"
    Y la tanda "T-1800" tiene 4 cupos disponibles

  Escenario: No se venden más cupos de los disponibles
    Cuando el cliente "1085000001" intenta reservar 5 cupos en la tanda "T-1800"
    Entonces se rechaza con el mensaje "Cupos insuficientes"

  Escenario: RN-03 sin exoneración firmada no puede reservar
    Dado el cliente "1085000009" de 30 años sin exoneración firmada
    Cuando el cliente "1085000009" intenta reservar 1 cupos en la tanda "T-1800"
    Entonces se rechaza indicando "exoneración"

  Escenario: Cancelar una reserva pendiente libera los cupos retenidos
    Cuando el cliente "1085000001" reserva 3 cupos en la tanda "T-1800"
    Y cancela la reserva
    Entonces la reserva queda "CANCELADA"
    Y la tanda "T-1800" tiene 4 cupos disponibles

  Escenario: Cancelar una reserva pagada reembolsa el pago
    Cuando el cliente "1085000001" reserva 2 cupos en la tanda "T-1800"
    Y paga la reserva con "TARJETA" y la clave "pago-2"
    Y cancela la reserva
    Entonces la reserva queda "REEMBOLSADA"
    Y la tanda "T-1800" tiene 4 cupos disponibles
    Y la pasarela reembolsó 1 pago
```

**Datos técnicos**
- `POST /api/v1/clientes` · `POST /api/v1/reservas` · `GET /api/v1/reservas/{codigo}` · `PATCH /api/v1/reservas/{codigo}` (cancelar) · `POST /api/v1/reservas/{codigo}/pagos` · `GET /api/v1/reservas/{codigo}/factura` · `POST /api/v1/pagos/webhook`.
- Evento de dominio: `ReservaConfirmada` (dispara la notificación al cliente).
- Mecanismos: cabecera `Idempotency-Key` en el pago, firma HMAC-SHA256 del webhook (`X-Firma`), bloqueo optimista de la tanda y expiración con una tarea `@Scheduled`.
- La pantalla del piloto no inicia sesión: guarda en el navegador los códigos (UUID) de sus reservas y las consulta una a una.

---

## HU-02 — Asignar karts y controlar la tanda desde boxes

**Como** jefe de boxes, **quiero** armar la parrilla asignando un kart disponible a cada piloto confirmado, y dar inicio y fin a la tanda, **para** que ningún kart en mantenimiento salga a pista.

**Precondiciones:** tanda con reservas `CONFIRMADAS`; karts `DISPONIBLE` con transponder.
**Postcondiciones:** tanda `EN_CURSO` y karts `EN_PISTA`; al finalizar, las horas y el kilometraje de cada kart quedan registrados en la flota y los karts vuelven a `DISPONIBLE` (o a `EN_MANTENIMIENTO`, ver HU-04).

**Reglas de negocio**
- RN-04: solo se asignan karts `DISPONIBLE`.
- RN-05: el kart debe corresponder a la edad del piloto (un kart infantil no se asigna a un adulto ni uno de adultos a un menor de 16 años).
- RN-06: estados de la tanda `PROGRAMADA → EN_PARRILLA → EN_CURSO → FINALIZADA`; un kart se puede retirar de la parrilla solo antes de iniciar.

```gherkin
# language: es
Característica: HU-02 Asignar karts y armar la parrilla
  Como jefe de boxes
  Quiero asignar un kart disponible a cada piloto confirmado
  Para sacar la tanda a pista a tiempo y con karts en buen estado

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-1900" de karts ADULTO con 4 cupos
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y la flota tiene los karts adultos 1 a 6 con transponder
    Y el cliente "1085000001" tiene 1 cupos pagados en la tanda "T-1900"

  Escenario: Asignación correcta e inicio de la tanda
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y inicia la tanda "T-1900"
    Entonces la tanda "T-1900" queda "EN_CURSO"
    Y el kart 3 queda "EN_PISTA"

  Escenario: RN-04 un kart en mantenimiento no se puede asignar
    Dado el kart 6 está en mantenimiento
    Cuando el jefe de boxes intenta asignar el kart 6 al piloto "1085000001" en la tanda "T-1900"
    Entonces se rechaza con el mensaje "Kart 6 no disponible"

  Escenario: No se puede iniciar una tanda sin parrilla
    Cuando el jefe de boxes intenta iniciar la tanda "T-1900"
    Entonces se rechaza indicando "parrilla"

  Escenario: Retirar un kart de la parrilla lo deja disponible y reabre la asignación
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y el jefe de boxes retira el kart 3 de la tanda "T-1900"
    Entonces la tanda "T-1900" queda "PROGRAMADA"
    Y el kart 3 queda "DISPONIBLE"
    Cuando el jefe de boxes asigna el kart 4 al piloto "1085000001" en la tanda "T-1900"
    Entonces el kart 4 queda "EN_PISTA"

  Escenario: No se retira un kart de una tanda que ya inició
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y inicia la tanda "T-1900"
    Y el jefe de boxes intenta retirar el kart 3 de la tanda "T-1900"
    Entonces se rechaza indicando "parrilla"

  Escenario: Una reserva con kart asignado no se puede cancelar
    Cuando el jefe de boxes asigna el kart 3 al piloto "1085000001" en la tanda "T-1900"
    Y el cliente "1085000001" intenta cancelar su reserva en la tanda "T-1900"
    Entonces se rechaza indicando "asignados"
    Y la reserva queda "CONFIRMADA"
```

**Datos técnicos**
- `GET /api/v1/sesiones/{codigo}/parrilla` · `POST /api/v1/sesiones/{codigo}/asignaciones` · `DELETE /api/v1/sesiones/{codigo}/asignaciones/{numeroKart}` · `POST /api/v1/sesiones/{codigo}/iniciar` · `POST /api/v1/sesiones/{codigo}/finalizar`.
- Evento de dominio: `SesionFinalizada`.

---

## HU-03 — Ver tiempos y clasificación en vivo

**Como** piloto, **quiero** ver mis tiempos por vuelta y mi posición en la pantalla de pista, **para** comparar mi rendimiento con el de los demás pilotos.

**Precondiciones:** tanda `EN_CURSO`; cada kart asignado tiene transponder.
**Postcondiciones:** cada vuelta válida queda registrada y la clasificación se actualiza en las pantallas suscritas.

**Reglas de negocio**
- RN-07: una vuelta es válida solo si dura más que el tiempo mínimo del trazado (25 s por defecto); por debajo es una doble lectura y se descarta.
- RN-08: la clasificación se ordena por mejor vuelta.

```gherkin
# language: es
Característica: HU-03 Cronometraje en vivo
  Como piloto en pista
  Quiero ver mis tiempos por vuelta en la pantalla en tiempo real
  Para comparar mi rendimiento con el de los demás pilotos

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-2000" de karts ADULTO con 4 cupos
    Y la flota tiene los karts adultos 1 a 6 con transponder
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y el cliente "1085000002" de 27 años con exoneración firmada
    Y el cliente "1085000001" tiene 1 cupos pagados en la tanda "T-2000"
    Y el cliente "1085000002" tiene 1 cupos pagados en la tanda "T-2000"
    Y el piloto "1085000001" corre en el kart 1 y el piloto "1085000002" en el kart 2 en la tanda "T-2000" iniciada

  Escenario: Se calcula el tiempo de vuelta a partir de dos pasos por meta
    Cuando el transponder "TR-101" pasa por meta en "23:03:10.250"
    Y el transponder "TR-101" pasa por meta en "23:03:42.870"
    Entonces se registra una vuelta de 32620 milisegundos

  Escenario: RN-07 una lectura menor a 25 segundos se descarta
    Cuando el transponder "TR-101" pasa por meta en "23:03:10.250"
    Y el transponder "TR-101" pasa por meta en "23:03:20.000"
    Entonces no se registra ninguna vuelta

  Escenario: RN-08 la clasificación se ordena por mejor vuelta
    Cuando el transponder "TR-101" pasa por meta en "23:03:00.000"
    Y el transponder "TR-102" pasa por meta en "23:03:01.000"
    Y el transponder "TR-101" pasa por meta en "23:03:35.000"
    Y el transponder "TR-102" pasa por meta en "23:03:31.500"
    Entonces el líder de la tanda "T-2000" es el kart 2
```

**Datos técnicos**
- Entrada: `POST /api/v1/telemetria/pasos` con el código del transponder. En esta versión lo envía el decodificador de meta (simulado en la pantalla *En vivo*) con el usuario `cronometraje`.
- Salida: `GET /api/v1/sesiones/{codigo}/clasificacion` y WebSocket STOMP en `/ws`, tópico `/topic/sesiones/{codigo}/clasificacion`. Se publica después de confirmar la transacción.
- Evento de dominio: `VueltaRegistrada`.

---

## HU-04 — Controlar el desgaste y atender órdenes preventivas

**Como** mecánico jefe, **quiero** que el sistema acumule las horas de motor de cada kart y abra una orden preventiva al llegar al umbral, **para** retirar de pista los karts que lo necesitan antes de que fallen.

**Precondiciones:** umbral configurado (20 h desde el último servicio por defecto); tanda finalizada.
**Postcondiciones:** si se supera el umbral, orden `ABIERTA` y kart `EN_MANTENIMIENTO`; al cerrarla, el contador vuelve a 0 y el kart queda `DISPONIBLE`.

**Reglas de negocio**
- RN-09: al superar el umbral se abre una orden preventiva y el kart se bloquea (queda `EN_MANTENIMIENTO`, por lo que no se puede asignar, RN-04).
- RN-10: solo el mecánico asignado puede cerrar la orden.
- RN-11: no se abren dos órdenes abiertas para el mismo kart (la base de datos lo garantiza con un índice único parcial).

```gherkin
# language: es
Característica: HU-04 Mantenimiento preventivo por horas de motor
  Como mecánico jefe
  Quiero que el sistema bloquee y genere una orden cuando un kart supera el umbral de horas
  Para evitar fallas en pista y alargar la vida útil de los motores

  Antecedentes:
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-2100" de karts ADULTO con 4 cupos
    Y la flota tiene los karts adultos 1 a 6 con transponder
    Y el kart 3 lleva "19.9" horas desde el último servicio
    Y el mecánico "M-02" trabaja en el taller
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y el cliente "1085000001" tiene 1 cupos pagados en la tanda "T-2100"

  Escenario: RN-09 al superar el umbral se abre una orden y se bloquea el kart
    Dado el piloto "1085000001" corre en el kart 3 en la tanda "T-2100" iniciada
    Cuando pasan 10 minutos
    Y finaliza la tanda "T-2100"
    Entonces se genera 1 orden preventiva para el kart 3
    Y el kart 3 queda "EN_MANTENIMIENTO"

  Escenario: RN-10 solo el mecánico asignado cierra la orden y el kart vuelve a estar disponible
    Dado el piloto "1085000001" corre en el kart 3 en la tanda "T-2100" iniciada
    Y pasan 10 minutos
    Y finaliza la tanda "T-2100"
    Cuando se asigna la orden del kart 3 al mecánico "M-02"
    Y el mecánico "M-01" intenta cerrar la orden del kart 3
    Entonces se rechaza indicando "mecánico asignado"
    Cuando el mecánico "M-02" cierra la orden del kart 3 con "Cambio de bujía y aceite"
    Entonces el kart 3 queda "DISPONIBLE"
    Y el kart 3 tiene 0 horas desde el último servicio

  Escenario: Un kart por debajo del umbral no genera orden
    Dado el piloto "1085000001" corre en el kart 1 en la tanda "T-2100" iniciada
    Cuando pasan 10 minutos
    Y finaliza la tanda "T-2100"
    Entonces se generan 0 órdenes preventivas
```

**Datos técnicos**
- Disparo interno: `RegistrarUso`, al finalizar la tanda (HU-02).
- `GET /api/v1/flota/karts` · `GET /api/v1/flota/umbral` · `GET /api/v1/ordenes` · `GET /api/v1/mecanicos` · `POST /api/v1/ordenes/{numero}/asignacion` (administrador) · `POST /api/v1/ordenes/{numero}/cierre`.
- El mecánico inicia sesión con su código de empleado (`M-01`, `M-02`…); el servidor comprueba que coincide con el mecánico asignado a la orden.
- Evento de dominio: `MantenimientoRequerido`.

---

## HU-05A — Configurar la operación

**Como** administrador, **quiero** definir tarifas por tipo de kart, programar tandas, fijar el umbral de mantenimiento y cancelar tandas, **para** ajustar precios y horarios con datos y no por intuición.

**Precondiciones:** usuario `admin`.
**Postcondiciones:** tarifas y tandas disponibles para nuevas reservas; una tanda cancelada cancela sus reservas pendientes y reembolsa las pagadas.

**Reglas de negocio**
- RN-12: una tarifa nueva no altera reservas ya creadas (cada reserva guarda su valor total).
- RN-15: no se programa una tanda de un tipo de kart sin tarifa vigente.

```gherkin
# language: es
Característica: HU-05 Configurar la operación y consultar la ocupación
  Como administrador del kartódromo
  Quiero gestionar tarifas, tandas y el umbral de mantenimiento, y ver la ocupación
  Para tomar decisiones comerciales con datos

  Escenario: No se puede programar una tanda sin tarifa vigente
    Cuando el administrador intenta programar la tanda "T-X" de karts INFANTIL
    Entonces se rechaza indicando "tarifa"

  Escenario: El administrador cambia el umbral de mantenimiento
    Cuando el administrador fija el umbral de mantenimiento en 25 horas
    Entonces el umbral vigente es 25 horas

  Escenario: Cancelar una tanda cancela las reservas y reembolsa los pagos
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-2300" de karts ADULTO con 4 cupos
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y el cliente "1085000001" tiene 2 cupos pagados en la tanda "T-2300"
    Cuando el administrador cancela la tanda "T-2300"
    Entonces la tanda "T-2300" queda "CANCELADA"
    Y la reserva queda "REEMBOLSADA"
    Y la pasarela reembolsó 1 pago
```

**Datos técnicos**
- `GET /api/v1/tarifas` · `POST /api/v1/tarifas` · `POST /api/v1/sesiones` · `PATCH /api/v1/sesiones/{codigo}` (cancelar) · `PUT /api/v1/flota/umbral`.

---

## HU-05B — Consultar ocupación e ingresos

**Como** administrador, **quiero** ver la ocupación y los ingresos confirmados de un rango de fechas, **para** decidir con datos qué tandas abrir.

**Precondiciones:** usuario `admin`.
**Postcondiciones:** reporte calculado sobre las reservas confirmadas del rango.

**Reglas de negocio**
- RN-13: ocupación = cupos vendidos / cupos ofrecidos de las tandas del periodo.

```gherkin
# language: es
Característica: HU-05 Configurar la operación y consultar la ocupación

  Escenario: RN-13 la ocupación es cupos vendidos sobre cupos ofrecidos
    Dado una tarifa ADULTO de 45000 pesos por cupo
    Y una tanda "T-2200" de karts ADULTO con 4 cupos
    Y el cliente "1085000001" de 24 años con exoneración firmada
    Y el cliente "1085000001" tiene 2 cupos pagados en la tanda "T-2200"
    Cuando el administrador consulta el reporte de ocupación del mes
    Entonces la ocupación total es 50 por ciento
    Y los ingresos confirmados son 90000 pesos
```

**Datos técnicos**
- `GET /api/v1/reportes/ocupacion?desde=AAAA-MM-DD&hasta=AAAA-MM-DD`.

---

## Matriz de trazabilidad

| HU | Contexto (flujo) | Funcional (componente · interfaz) | Lógica (clases de dominio) | Desarrollo (caso de uso) | Despliegue | Código y pruebas |
| --- | --- | --- | --- | --- | --- | --- |
| HU-01 | Piloto ↔ KartRent ↔ Pasarela | Reservas · `ReservarTanda`; Pagos · `PagarReserva` | `Cliente`, `Reserva`, `Tarifa`, `Pago`, `Factura` | `RegistrarCliente`, `ReservarTanda`, `PagarReserva`, `CancelarReserva`, `ExpirarReservas` | Navegador → nginx → API → pasarela | `aplicacion/reservas` · `HU01_reservar_y_pagar.feature` · página `Piloto` |
| HU-02 | Jefe de boxes ↔ KartRent | Pista · `ControlarSesion` → Flota · `ConsultarFlota` | `SesionPista`, `AsignacionKart`, `Kart` | `ControlarSesion` | Tablet de boxes → nginx → API | `aplicacion/pista` · `HU02_boxes_parrilla.feature` · página `Boxes` |
| HU-03 | Decodificador → KartRent → Piloto | Pista · `RegistrarPaso` | `VueltaTelemetria`, `LineaClasificacion` | `RegistrarPaso` | Decodificador → HTTP → API → WebSocket | `aplicacion/pista`, `infraestructura/mensajeria` · `HU03_cronometraje.feature` · página `EnVivo` |
| HU-04 | KartRent ↔ Mecánico | Flota · `RegistrarUso`, `GestionarOrdenes` | `Kart`, `OrdenMantenimiento`, `Mecanico` | `RegistrarUso`, `GestionarOrdenes`, `ConsultarFlota` | API → PostgreSQL | `aplicacion/flota` · `HU04_mantenimiento.feature` · página `Taller` |
| HU-05A | Administrador ↔ KartRent | Reservas · `ConfigurarOperacion` | `Tarifa`, `SesionPista` | `ConfigurarOperacion`, `CancelarTanda` | API → PostgreSQL | `aplicacion/administracion` · `HU05_administracion.feature` · página `Admin` |
| HU-05B | Administrador ↔ KartRent | Reservas · `ReporteOcupacion` | `SesionPista`, `Reserva` | `ReporteOcupacion` | API → PostgreSQL | `aplicacion/administracion` · `HU05_administracion.feature` · página `Admin` |
