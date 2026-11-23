# Guía breve de defensa

Cada idea, en lenguaje simple y con el lugar del código donde se ve.

## Arquitectura hexagonal (puertos y adaptadores)
**Idea:** el centro del sistema (las reglas del karting) no sabe nada de la base de datos, de la web ni de Spring. Habla con el mundo por **puertos** (interfaces) y la tecnología se conecta con **adaptadores**.
**Ejemplo:** `ReservarTanda` pide un `RepositorioReservas` (interfaz); `RepositorioReservasJdbc` lo implementa con SQL. Cambiar PostgreSQL por otra base no toca las reglas.
**Equivalencia con el curso:** Controller = controladores REST · Service = casos de uso · Repository = puerto + adaptador JDBC · Model = dominio · DTO = `Dtos`. (Tabla en el README.)
**Pregunta típica:** «¿Por qué no pusieron todo en el controlador?» → Las reglas se prueban sin levantar un servidor (las pruebas de dominio corren en milisegundos).

## STOMP sobre WebSocket
**Idea:** HTTP es «pregunto y me responden». Para los tiempos en vivo necesitamos que el servidor **empuje** datos. WebSocket mantiene una conexión abierta; STOMP le pone «canales» (tópicos) encima.
**Ejemplo:** la pantalla se suscribe a `/topic/sesiones/{codigo}/clasificacion`; al registrarse una vuelta, el servidor publica la tabla nueva. Se publica **después** de confirmar la transacción para no mostrar algo que luego se revierta (`DifusorTiemposEnVivo`).

## Firma HMAC del webhook
**Idea:** la pasarela avisa de un pago con una petición a nuestro servidor; cualquiera podría fingir ser la pasarela. Por eso la petición trae una **firma**: un hash del cuerpo calculado con un secreto que solo conocen ambos (HMAC-SHA256, cabecera `X-Firma`).
**Cómo:** recalculamos la firma y la comparamos en **tiempo constante** (para que no se pueda adivinar por cuánto tarda). Si no coincide: `401`. El secreto sale de `WEBHOOK_SECRET`, sin valor por defecto.

## Idempotencia del pago
**Idea:** si el cliente pulsa dos veces «Pagar» o la red repite la petición, **no** se cobra dos veces.
**Cómo:** cada intento lleva una cabecera `Idempotency-Key`. Si ya existe un pago con esa clave, se devuelve el resultado anterior (`200`) en lugar de cobrar. Un pago **rechazado** se reintenta con otra clave (la pantalla genera una nueva por intento).
**Extra que conviene decir:** el cobro a la pasarela se hace **fuera** de la transacción de base de datos (no retenemos una conexión esperando a un tercero) y, si se cobró pero la reserva ya no existe, se **reembolsa** (simulado: no hay nota crédito).

## Bloqueo optimista
**Idea:** varias personas compiten por el último cupo. En vez de bloquear la fila para todos, cada tanda lleva un número de **versión**. Al guardar, el `UPDATE` exige «versión = la que leí». Si otro guardó antes, no actualiza nada, se lanza `ConflictoConcurrenciaException` y el caso de uso **se repite** (hasta 10 veces).
**Por qué:** casi nunca chocan, así que es más rápido que bloquear. Si chocan de verdad, el perdedor recibe `409`.
**Red de seguridad:** `CHECK` y `UNIQUE` en la base impiden cupos negativos, un kart repetido en la parrilla o dos órdenes abiertas del mismo kart.

## Flyway
**Idea:** control de versiones **de la base de datos**. Cada cambio es un archivo `V1__…`, `V2__…` que se ejecuta una sola vez y en orden; Flyway recuerda cuáles ya corrieron.
**Ejemplo:** `V3` añade `sesiones.version`; `V5` añade el reembolso. `V4` existe en dos versiones (PostgreSQL usa un índice parcial; H2, para las pruebas, una columna generada), elegidas por motor (`db/vendor/{motor}`).
**Ventaja:** cualquiera levanta el sistema desde cero con el mismo esquema, y la CI lo prueba contra un PostgreSQL real.

## Seguridad en una frase
HTTP Basic sin estado con cuatro roles (`ADMIN`, `BOXES`, `CRONOMETRAJE`, `MECANICO`). El piloto no inicia sesión: su reserva se identifica con un UUID. Las claves vienen de variables de entorno, nunca del código.

## Límites que conviene decir antes de que pregunten
- La pasarela de pagos y el decodificador de meta son **simulados**; en el despliegue aparecen como escenario hipotético.
- Los reembolsos no emiten nota crédito.
- Los parámetros (10 min de retención, 25 s de vuelta mínima, 20 h de umbral) son ejemplos configurables, no validados con un kartódromo real.
- Docker Compose publica HTTP; para producción habría que poner TLS delante.
