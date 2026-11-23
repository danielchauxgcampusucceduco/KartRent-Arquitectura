# Decisiones de diseño — KartRent

Una página con las decisiones de persistencia y concurrencia, y por qué se tomaron.

| Decisión | Qué se hizo | Por qué |
| --- | --- | --- |
| **Relacional, no documental** | PostgreSQL 16 con migraciones Flyway (`V1`–`V5`). H2 en modo PostgreSQL solo para las pruebas. | Reservar un cupo, cobrar y facturar deben ser **atómicos** y los datos están muy relacionados (reserva → tanda → asignación → kart). Las restricciones `CHECK`, `UNIQUE` y las claves foráneas protegen las reglas aunque falle el código. Una base de documentos solo se justificaría para la telemetría masiva, y un kartódromo genera pocas vueltas por tanda. |
| **Herencia de `Kart`** | En el dominio: `Kart` con `KartAdulto` y `KartInfantil` (polimorfismo de `edadMinimaPiloto()`). En la base: **una sola tabla** `karts` con la columna `tipo` como discriminador. | Las subclases difieren en comportamiento, no en columnas; una tabla por subclase añadiría `JOIN`s sin ganar nada. |
| **Herencia de `Persona`** | `Persona` es abstracta; `Cliente` y `Mecanico` tienen **tabla propia** (`clientes`, `mecanicos`), sin tabla `personas`. | No comparten consultas ni identificador (documento frente a código de empleado). Una tabla común mezclaría dos agregados. |
| **Referencias entre agregados** | Por identificador: `Reserva` guarda `documentoCliente` y `codigoSesion`; `AsignacionKart` guarda `codigoReserva`. La `SesionPista` **contiene** sus asignaciones y vueltas (claves compuestas por tanda). | Bajo acoplamiento: ningún agregado carga a otro completo. Lo que nace y muere con la tanda va dentro de ella. |
| **Bloqueo optimista** | Columna `sesiones.version`; el `UPDATE` exige la versión leída. Si otra transacción cambió la tanda, lanza `ConflictoConcurrenciaException` y `UnidadDeTrabajo.ejecutarConReintentos` repite el caso de uso (hasta 10 intentos). | Muchos pilotos compiten por los mismos cupos y casi nunca chocan; un bloqueo pesimista serializaría todas las reservas. Una prueba de concurrencia lo verifica. |
| **Restricciones como última defensa** | `UNIQUE (tanda, kart)` y `UNIQUE (tanda, piloto)` en `asignaciones`; índice único parcial «una orden abierta por kart» (`V4`, con una versión por motor); `CHECK` de cupos. | Si dos peticiones pasan la validación a la vez, la base rechaza la segunda y la API responde **409**. |
| **Transacciones** | El núcleo no conoce Spring: cada caso de uso corre dentro del puerto `UnidadDeTrabajo` (implementado con `TransactionTemplate` de Spring). | Mantiene el dominio sin dependencias de framework. |
| **Pago en tres fases** | 1) validar y guardar en una transacción; 2) llamar a la pasarela **fuera** de la transacción; 3) registrar el resultado en otra. | No se retiene una conexión de base de datos mientras se espera a un sistema externo. |
| **Reembolsos** | Si se cobra y la reserva ya no existe (expiró o se canceló), el pago queda aprobado, se llama a `reembolsar` en la pasarela (idempotente por referencia) y se marca `reembolsado = true`. | Nunca se retiene dinero de un servicio no prestado. Es una **simulación**: no se emite nota crédito; la factura emitida no se anula. |

Credenciales y secretos no tienen valores por defecto en `application.yml`: se definen por variables de entorno (`.env.example`) o con el perfil `dev` para el computador propio.
