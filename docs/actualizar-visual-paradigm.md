# Qué actualizar en el archivo de Visual Paradigm

`diagramas/KartRent.vpp` se dibujó a mano antes de los últimos cambios. Las fuentes PlantUML (`diagramas/plantuml`, con imágenes en `diagramas/render`) ya están al día; esta lista dice qué retocar en cada diagrama del `.vpp` para que coincida con ellas y con el código. Si prefieres no editarlos, borra los diagramas viejos y vuelve a dibujarlos a partir de las imágenes de `diagramas/render`.

## 01 · Contexto
- Renombrar **Operador de Pista** → **Jefe de boxes**.
- Renombrar **Sistema de Transponders y sensores** → **Decodificador de meta y transponders**.
- Flujo «Solicitud de cobro [HU-01]» → «Solicitud de cobro y de reembolso [HU-01]».
- Flujo «Tarifas, horarios y umbrales de flota [HU-05]» → «Tarifas, tandas, umbral y cancelaciones [HU-05A]».
- Flujo «Reportes de ocupación e ingresos [HU-05]» → «… [HU-05B]».
- Flujo «Vínculo transponder-kart y bucles [HU-03]» → «Vínculo transponder-kart [HU-03]».
- Flujo «Pasos por bucle (transponder, hora)» → «Pasos por meta (transponder, hora)».

## 02 · Funcional
Sin cambios: ya coincide con `02_funcional.puml`.

## 03 · Lógica (modelo de dominio)
- Enumeraciones: añadir `REEMBOLSADA` a `EstadoReserva` y `CANCELADA` a `EstadoSesion`; crear `TipoKart {ADULTO, INFANTIL}` y `MetodoPago {TARJETA, PSE, NEQUI, SIMULAR_RECHAZO}`.
- `Tarifa.tipoKart` y `SesionPista.tipoKart`: de `String` a `TipoKart`.
- `Pago`: `metodo : MetodoPago`; añadir atributo `reembolsado : boolean` y operación `reembolsar() : Pago`.
- `Reserva`: `registrarPago(referencia, metodo : MetodoPago, aprobado, ahora)`; añadir `verificarPagable(ahora)` y `reembolsar()`; `crear(cliente, sesion, cupos, tarifa, ahora, retencion)`.
- `SesionPista`: añadir atributo `version : long` y operaciones `programar(...)` (estática), `liberarCupos(cantidad)`, `desasignarKart(numeroKart)`, `liberarCuposDeReserva(codigoReserva, cupos)` y `cancelar()`.
- `Kart`: añadir `{abstract} tipo() : TipoKart` y `liberar()`; `edadMinimaPiloto()` pasa a ser concreta (deriva de `tipo()`).
- `KartAdulto` y `KartInfantil`: la operación propia es `tipo()`; `KartInfantil` conserva `edadMaximaPiloto()`.
- `OrdenMantenimiento.cerrar(codigoMecanicoQueCierra, observaciones, kart, ahora)`.

## 04 · Desarrollo (clases de implementación)
- **Eliminar `ClienteHttp`**: no existe en el código. El frontend llama a la API desde `api.ts`; represéntalo como un componente `api.ts` (o quita la clase y deja los controladores como punto de entrada).
- Quitar el atributo `rutaBase` de los cuatro controladores (no existe como atributo).
- Añadir casos de uso: `CancelarReserva`, `CancelarTanda`, `Reembolsos`, `ConsultarReservas`, `ConsultarFlota`.
- Añadir operaciones: `ControlarSesion.desasignarKart(codigoSesion, numeroKart)`; `PagarReserva.procesarWebhook(cuerpo, firma)`; el método de `PagarReserva.ejecutar` es `MetodoPago`, no `String`.
- Controladores: `ReservasController` + `reserva`, `cancelar` (PATCH) y `factura`; `PistaController` + `desasignar` (DELETE); `AdministracionController` + `cancelar` (PATCH).
- Puertos: `PasarelaPagos` + `reembolsar(referencia)` e `interpretarNotificacion`; `RepositorioReservas` + `porSesion`; añadir `RepositorioFacturacion` y `UnidadDeTrabajo` (con `ejecutar` y `ejecutarConReintentos`).
- Añadir `ConfiguracionSeguridad` y `UsuariosKartRent` (infraestructura/seguridad), con una dependencia hacia los controladores.

## 05 · Despliegue
- El rectángulo **«node» Servidor Linux (Docker Engine)** → **«device» Servidor Linux** que contiene **«executionEnvironment» Docker Engine · docker compose**.
- `frontend · nginx`, `backend · kartrent-backend.jar` y `db · postgres:16` dejan de ser `«artifact»`: son **«executionEnvironment»** (contenedores). Dentro van los artefactos: `dist/` y `nginx.conf` en el frontend, `kartrent-backend.jar` en el backend y la base `kartrent (Flyway V1 a V5)` en la base de datos.
- En los dispositivos de usuario, poner el navegador como **«executionEnvironment»** que contiene el artefacto `SPA React`.
- Etiquetas: «HTTPS :443» y «HTTPS (Wi-Fi boxes)» → **«HTTP :3000 → :80»** (docker compose no termina TLS); «WSS /ws» → **«WebSocket /ws (ws://)»**; «HTTP :8080 · WebSocket» → **«HTTP :8080 (/api/v1, /ws con Upgrade)»**; «JDBC :5432» → «JDBC / TCP :5432».
- El enlace del decodificador → «HTTP POST /api/v1/telemetria/pasos (usuario cronometraje)».
- Marcar **Decodificador de meta** y **Pasarela de pagos (Wompi / PayU)** como «escenario hipotético», con una nota: en la entrega la pasarela es `PasarelaPagosSimulada` dentro del backend y el decodificador lo imita la pantalla *En vivo*.
- Quitar `firmware-decoder` como artefacto real o indicar que es hipotético.
