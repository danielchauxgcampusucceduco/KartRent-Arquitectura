# Historias de usuario — KartRent

Sistema Integral de Renta y Gestión Operativa de Karting.
Las historias se escribieron **antes** de los diagramas y del código: cada flujo del diagrama de contexto, cada componente, cada clase y cada endpoint del backend se justifica en una de ellas (ver la [matriz de trazabilidad](#matriz-de-trazabilidad)).

## Marco de calidad

| Criterio INVEST | Cómo se verificó |
| --- | --- |
| Independiente | Cada HU se entrega sin esperar otra del mismo sprint (las dependencias van por interfaz). |
| Negociable | Describen el *qué*; el *cómo* queda en «Datos técnicos». |
| Valiosa | Cada una tiene un beneficio medible para el kartódromo. |
| Estimable | Reglas y sistemas externos identificados. |
| Pequeña | Cabe en un sprint de 2 semanas. |
| Testeable | Criterios en Gherkin, automatizados en `backend/src/test/resources/features`. |

- **Corte vertical:** cada HU atraviesa frontend → API REST → caso de uso → dominio → base de datos.
- **Reglas de negocio (RN)** sin tecnología; **lógica técnica** en «Datos técnicos».
- Los valores numéricos (10 min, 25 s, 20 h) son **parámetros de ejemplo configurables**, pendientes de validar con el kartódromo.

| ID | Rol | Historia | Componente principal |
| --- | --- | --- | --- |
| HU-01 | Piloto / Cliente | Reservar y pagar una tanda | Reservas y Clientes · Pagos y Facturación |
| HU-02 | Operador de Pista | Asignar karts y controlar la tanda | Control de Pista y Telemetría |
| HU-03 | Sistema de Transponders / Piloto | Capturar y mostrar tiempos en vivo | Control de Pista y Telemetría |
| HU-04 | Mecánico / Jefe de Taller | Controlar el desgaste y generar órdenes preventivas | Flota y Mantenimiento |
| HU-05 | Administrador | Configurar tarifas, tandas y umbrales; consultar reportes | Reservas y Clientes · Flota |

---

## HU-01 — Reservar y pagar una tanda en línea

**Como** piloto/cliente, **quiero** reservar cupos en una tanda y pagarlos en línea, **para** asegurar mi turno sin filas y que el kartódromo reduzca las ausencias a menos del 5 % de los cupos vendidos.

**Precondiciones:** cliente registrado con exoneración de responsabilidad firmada; tanda PROGRAMADA con cupos libres.
**Postcondiciones:** reserva CONFIRMADA, cupos descontados, pago aprobado registrado, factura emitida y notificación enviada.

**Reglas de negocio**
- RN-01: un cupo retenido sin pago expira a los 10 minutos y vuelve a la tanda.
- RN-02: la edad del piloto debe cumplir la edad mínima del tipo de kart de la tanda (adulto ≥ 16 años, infantil ≥ 8 años).
- RN-03: sin exoneración firmada no se puede reservar.

```gherkin
# language: es
Característica: Reserva y pago de tandas

  Escenario: Reserva pagada con éxito
    Dado que la tanda "T-1800" tiene 4 cupos disponibles
    Y el cliente "1085000001" tiene la exoneración firmada
    Cuando reserva 2 cupos y la pasarela aprueba el pago
    Entonces la reserva queda en estado "CONFIRMADA"
    Y la tanda "T-1800" queda con 2 cupos disponibles
    Y se emite una factura para la reserva

  Escenario: El pago no se completa a tiempo
    Dado que el cliente "1085000001" retuvo 2 cupos de la tanda "T-1800"
    Cuando pasan 11 minutos sin pago aprobado
    Entonces la reserva queda en estado "EXPIRADA"
    Y la tanda "T-1800" queda con 4 cupos disponibles

  Escenario: Tanda sin cupos suficientes
    Dado que la tanda "T-1800" tiene 1 cupo disponible
    Cuando el cliente "1085000001" intenta reservar 3 cupos
    Entonces el sistema rechaza la reserva con el mensaje "Cupos insuficientes"
```

**Datos técnicos**
- `POST /api/v1/clientes` · `POST /api/v1/reservas` · `POST /api/v1/reservas/{codigo}/pagos` · `POST /api/v1/pagos/webhook`
- Eventos: `ReservaCreada`, `ReservaConfirmada`, `ReservaExpirada`.
- Validaciones: cabecera `Idempotency-Key` en el pago, firma HMAC del webhook, descuento de cupos atómico en la base de datos.

---

## HU-02 — Asignar karts y controlar la tanda desde boxes

**Como** operador de pista (comisario), **quiero** armar la parrilla asignando un kart operativo a cada piloto confirmado y dar inicio y fin a la tanda, **para** dar salida en menos de 3 minutos y garantizar que ningún kart bloqueado salga a pista.

**Precondiciones:** tanda con reservas CONFIRMADAS; karts DISPONIBLES con transponder.
**Postcondiciones:** tanda EN_CURSO; karts EN_PISTA; al finalizar, minutos y km de cada kart registrados en Flota y karts de vuelta a DISPONIBLE (o EN_MANTENIMIENTO, ver HU-04).

**Reglas de negocio**
- RN-04: solo se asignan karts DISPONIBLES.
- RN-05: un kart infantil no se asigna a un adulto ni un kart adulto a un menor de 16 años.
- RN-06: estados válidos: PROGRAMADA → EN_CURSO → FINALIZADA.

```gherkin
# language: es
Característica: Control de tandas en boxes

  Escenario: Parrilla completa y salida
    Dado que la tanda "T-1800" tiene 2 pilotos confirmados
    Y hay karts adultos disponibles
    Cuando el operador asigna un kart a cada piloto e inicia la tanda
    Entonces la tanda queda en estado "EN_CURSO"
    Y los karts asignados quedan en estado "EN_PISTA"

  Escenario: Intento de asignar un kart en mantenimiento
    Dado que el kart 7 está en estado "EN_MANTENIMIENTO"
    Cuando el operador intenta asignarlo a un piloto de la tanda "T-1800"
    Entonces el sistema rechaza la asignación con el mensaje "Kart 7 no disponible"

  Escenario: Cierre de tanda
    Dado que la tanda "T-1800" está en curso con el kart 5 asignado
    Cuando el operador finaliza la tanda
    Entonces la tanda queda en estado "FINALIZADA"
    Y se registran los minutos de uso del kart 5 en la flota
```

**Datos técnicos**
- `GET /api/v1/sesiones/{codigo}/parrilla` · `POST /api/v1/sesiones/{codigo}/asignaciones` · `POST /api/v1/sesiones/{codigo}/iniciar` · `POST /api/v1/sesiones/{codigo}/finalizar`
- Eventos: `SesionIniciada`, `SesionFinalizada`.

---

## HU-03 — Capturar y mostrar tiempos en vivo

**Como** piloto, **quiero** ver mis tiempos por vuelta y mi posición en vivo, **para** comparar mi rendimiento y volver a comprar tandas.
*Rol técnico asociado:* el sistema de transponders publica cada paso por el bucle de meta.

**Precondiciones:** tanda EN_CURSO; cada kart asignado tiene transponder activo.
**Postcondiciones:** cada vuelta válida queda registrada y la clasificación en vivo se actualiza en las pantallas suscritas.

**Reglas de negocio**
- RN-07: una vuelta es válida solo si dura más que el tiempo mínimo del trazado (25 s por defecto); por debajo es una doble lectura y se descarta.
- RN-08: la clasificación se ordena por mejor vuelta.

```gherkin
# language: es
Característica: Cronometraje en tiempo real

  Escenario: Vuelta válida publicada en vivo
    Dado que el transponder "TR-105" está en un kart de la tanda en curso "T-1800"
    Y su paso anterior por meta fue a las "18:03:10.250"
    Cuando el bucle de meta detecta "TR-105" a las "18:03:42.870"
    Entonces se registra una vuelta de 32620 milisegundos
    Y la clasificación de la tanda "T-1800" muestra esa vuelta como la mejor

  Escenario: Doble lectura descartada
    Dado que el transponder "TR-105" está en un kart de la tanda en curso "T-1800"
    Y su paso anterior por meta fue a las "18:03:42.870"
    Cuando el bucle de meta detecta "TR-105" a las "18:03:43.100"
    Entonces no se registra ninguna vuelta
```

**Datos técnicos**
- Entrada: `POST /api/v1/telemetria/pasos` (adaptador del decodificador; en producción llega por MQTT `kartodromo/pista/{bucleId}/pasos`).
- Salida: WebSocket STOMP `/ws` → tópico `/topic/sesiones/{codigo}/clasificacion` · `GET /api/v1/sesiones/{codigo}/clasificacion`.
- Validaciones: deduplicación por tiempo mínimo, latencia objetivo < 1 s (p95).

---

## HU-04 — Controlar el desgaste y generar órdenes preventivas

**Como** jefe de taller, **quiero** que el sistema acumule horas de motor y km de cada kart y abra una orden preventiva al llegar al umbral, **para** evitar fallas en pista y mantener al menos el 90 % de la flota disponible en horas pico.

**Precondiciones:** umbral configurado (20 h de motor por defecto); tanda FINALIZADA.
**Postcondiciones:** si se supera el umbral, OrdenMantenimiento ABIERTA y kart EN_MANTENIMIENTO; al cerrarla, contador en 0 y kart DISPONIBLE.

**Reglas de negocio**
- RN-09: un kart con orden abierta no puede asignarse.
- RN-10: solo el mecánico asignado puede cerrar la orden.
- RN-11: no se abren dos órdenes preventivas para el mismo kart.

```gherkin
# language: es
Característica: Mantenimiento preventivo de la flota

  Escenario: Umbral alcanzado al cerrar una tanda
    Dado que el kart 7 acumula 19.9 horas desde su último servicio
    Y el umbral preventivo es de 20 horas
    Cuando se registran 10 minutos de uso del kart 7
    Entonces se crea una orden "PREVENTIVO" en estado "ABIERTA" para el kart 7
    Y el kart 7 queda en estado "EN_MANTENIMIENTO"

  Escenario: Cierre de la orden y habilitación
    Dado que el kart 7 tiene una orden preventiva asignada al mecánico "M-02"
    Cuando "M-02" cierra la orden con observaciones "Cambio de bujía y cadena"
    Entonces el kart 7 queda en estado "DISPONIBLE"
    Y su contador de horas desde el último servicio vuelve a 0
```

**Datos técnicos**
- Disparo interno: `RegistrarUso` al finalizar la tanda (HU-02).
- `GET /api/v1/flota/karts` · `GET /api/v1/ordenes` · `POST /api/v1/ordenes/{numero}/asignacion` · `POST /api/v1/ordenes/{numero}/cierre`
- Eventos: `MantenimientoRequerido`, `KartHabilitado`.

---

## HU-05 — Configurar la operación y consultar reportes

**Como** administrador del kartódromo, **quiero** definir tarifas por tipo de kart, programar tandas y fijar el umbral de mantenimiento, y consultar la ocupación y los ingresos, **para** ajustar precios y horarios con datos y no por intuición.

**Precondiciones:** usuario con rol ADMINISTRADOR.
**Postcondiciones:** tarifas y tandas disponibles para nuevas reservas; reporte calculado sobre reservas confirmadas.

**Reglas de negocio**
- RN-12: una tarifa nueva no altera reservas ya confirmadas.
- RN-13: ocupación = cupos vendidos / cupos ofrecidos de las tandas del periodo.

```gherkin
# language: es
Característica: Configuración y reportes del kartódromo

  Escenario: Programar una tanda con su tarifa
    Dado que la tarifa vigente para kart "ADULTO" es de 45000 pesos por cupo
    Cuando el administrador programa la tanda "T-2000" con 8 cupos
    Entonces la tanda "T-2000" queda "PROGRAMADA" con 8 cupos disponibles

  Escenario: Reporte de ocupación
    Dado que la tanda "T-2000" tiene 8 cupos y 6 vendidos
    Cuando el administrador consulta el reporte de ocupación
    Entonces la ocupación de la tanda "T-2000" es 75 por ciento
```

**Datos técnicos**
- `GET/POST /api/v1/tarifas` · `POST /api/v1/sesiones` · `GET /api/v1/reportes/ocupacion` · `PUT /api/v1/flota/umbral`

---

## Matriz de trazabilidad

| HU | Contexto (flujo) | Funcional (componente · interfaz) | Lógica (clases) | Desarrollo (caso de uso) | Despliegue | Código |
| --- | --- | --- | --- | --- | --- | --- |
| HU-01 | Piloto ↔ KartRent ↔ Pasarela | Reservas · `ReservarTanda`; Pagos · `ProcesarPago` | Cliente, Reserva, Tarifa, Pago, Factura | `ReservarTanda`, `PagarReserva`, `ExpirarReservas` | Navegador → Gateway HTTPS; API → Pasarela | `aplicacion/reservas` |
| HU-02 | Operador ↔ KartRent | Pista · `ControlarSesion` → Flota · `ConsultarDisponibilidad` | SesionPista, AsignacionKart, Kart | `AsignarKart`, `IniciarTanda`, `FinalizarTanda` | Tablet de boxes → Gateway | `aplicacion/pista` |
| HU-03 | Transponders → KartRent → Piloto | Pista · `RegistrarPaso`, `CronometroEnVivo` | VueltaTelemetria, Transponder | `RegistrarPaso` | Decodificador → MQTT → API → WebSocket | `aplicacion/pista`, `presentacion/websocket` |
| HU-04 | KartRent ↔ Mecánico | Flota · `RegistrarUso`, `GestionarOrdenes` | Kart, OrdenMantenimiento, Mecanico | `RegistrarUso`, `GestionarOrdenes` | API → PostgreSQL | `aplicacion/flota` |
| HU-05 | Administrador ↔ KartRent | Reservas · `ConfigurarTarifas` | Tarifa, SesionPista | `ConfigurarOperacion`, `ReporteOcupacion` | API → PostgreSQL | `aplicacion/administracion` |
