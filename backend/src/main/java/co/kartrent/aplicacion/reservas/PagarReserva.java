package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.Notificador;
import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.PasarelaPagos;
import co.kartrent.aplicacion.puertos.PublicadorEventos;
import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.aplicacion.puertos.RepositorioFacturacion;
import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.eventos.ReservaConfirmada;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.reservas.Reserva;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-01 — cobra por la pasarela (Variaciones Protegidas) y, si se aprueba, confirma, factura y notifica.
 * <p>
 * El cobro se hace en tres pasos para no retener una conexión ni bloqueos de base de datos mientras se espera a un
 * servicio externo: (1) transacción corta que valida, (2) llamada a la pasarela sin transacción, (3) transacción corta
 * que registra el resultado. Si entre (1) y (3) la reserva dejó de ser pagable, el cobro aprobado se reembolsa.
 * Idempotente: la misma clave de idempotencia nunca cobra dos veces.
 */
public class PagarReserva {
    private final RepositorioReservas reservas;
    private final RepositorioClientes clientes;
    private final RepositorioFacturacion facturacion;
    private final PasarelaPagos pasarela;
    private final Reembolsos reembolsos;
    private final Notificador notificador;
    private final PublicadorEventos eventos;
    private final ParametrosOperacion parametros;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public PagarReserva(RepositorioReservas reservas, RepositorioClientes clientes, RepositorioFacturacion facturacion,
                        PasarelaPagos pasarela, Reembolsos reembolsos, Notificador notificador, PublicadorEventos eventos,
                        ParametrosOperacion parametros, UnidadDeTrabajo uow, Clock reloj) {
        this.reservas = reservas;
        this.clientes = clientes;
        this.facturacion = facturacion;
        this.pasarela = pasarela;
        this.reembolsos = reembolsos;
        this.notificador = notificador;
        this.eventos = eventos;
        this.parametros = parametros;
        this.uow = uow;
        this.reloj = reloj;
    }

    /**
     * @param claveIdempotencia clave enviada por el cliente; si es nula o vacía, cada intento sobre la reserva comparte
     *                          la referencia {@code KR-<reserva>} y solo un pago aprobado se considera repetición
     */
    public Resultado ejecutar(UUID codigoReserva, MetodoPago metodo, String claveIdempotencia) {
        boolean claveExplicita = claveIdempotencia != null && !claveIdempotencia.isBlank();
        String referencia = "KR-" + (claveExplicita ? claveIdempotencia : codigoReserva);

        Preparacion preparacion = uow.ejecutar(() -> preparar(codigoReserva, referencia, claveExplicita));
        if (preparacion.repeticion() != null) return preparacion.repeticion();

        PasarelaPagos.ResultadoPago respuesta = pasarela.cobrar(referencia, preparacion.monto(), metodo);

        Registro registro = uow.ejecutarConReintentos(
                () -> registrar(codigoReserva, referencia, metodo, respuesta.aprobado()));
        if (registro.cobroSinServicio() != null) {
            reembolsos.devolver(List.of(registro.cobroSinServicio()));
            throw new ConflictoException("La reserva dejó de estar disponible mientras se procesaba el pago; "
                    + "el cobro fue reembolsado");
        }
        return registro.resultado();
    }

    /** Paso 1: nunca se cobra una reserva que ya no se puede pagar. */
    private Preparacion preparar(UUID codigoReserva, String referencia, boolean claveExplicita) {
        Reserva reserva = reservas.buscar(codigoReserva)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva " + codigoReserva + " no existe"));
        Optional<Pago> previo = facturacion.pagoPorReferencia(referencia);
        if (previo.isPresent()) {
            Pago pago = previo.get();
            if (!pago.codigoReserva().equals(codigoReserva)) {
                throw new ConflictoException("La clave de idempotencia ya se usó en otra reserva");
            }
            // Una clave explícita siempre repite su resultado; sin clave, un intento rechazado se puede volver a hacer.
            if (pago.aprobado() || claveExplicita) {
                return new Preparacion(null, new Resultado(reserva, pago,
                        facturacion.facturaDeReserva(codigoReserva).orElse(null), true));
            }
        }
        reserva.verificarPagable(LocalDateTime.now(reloj));
        return new Preparacion(reserva.getValorTotal(), null);
    }

    /** Paso 3: registra el resultado del cobro; si ya no hay reserva que confirmar, marca el cobro para reembolsarlo. */
    private Registro registrar(UUID codigoReserva, String referencia, MetodoPago metodo, boolean aprobado) {
        Reserva reserva = reservas.buscar(codigoReserva)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva " + codigoReserva + " no existe"));
        Optional<Pago> yaAprobado = facturacion.pagoPorReferencia(referencia).filter(Pago::aprobado);
        if (yaAprobado.isPresent()) {
            // Dos solicitudes simultáneas con la misma referencia: la primera ya registró el pago.
            return new Registro(new Resultado(reserva, yaAprobado.get(),
                    facturacion.facturaDeReserva(codigoReserva).orElse(null), true), null);
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Pago pago;
        try {
            pago = reserva.registrarPago(referencia, metodo, aprobado, ahora);
        } catch (ConflictoException e) {
            if (!aprobado) throw e;
            Pago cobrado = new Pago(referencia, codigoReserva, reserva.getValorTotal(), metodo, true, ahora);
            facturacion.guardarPago(cobrado);
            return new Registro(null, cobrado);
        }
        facturacion.guardarPago(pago);
        Factura factura = pago.esAprobado() ? confirmarYFacturar(reserva, pago, ahora) : null;
        return new Registro(new Resultado(reserva, pago, factura), null);
    }

    /** Webhook de la pasarela: la pasarela verifica la firma y lee el cuerpo; aquí solo se confirma el pago. */
    public Resultado procesarWebhook(String cuerpo, String firma) {
        PasarelaPagos.NotificacionPago n = pasarela.interpretarNotificacion(cuerpo, firma);
        return confirmarDesdePasarela(n.referencia(), n.aprobado());
    }

    /**
     * Aprobación asíncrona de la pasarela. Si la reserva ya no es pagable (expiró o se canceló mientras tanto), el
     * dinero cobrado se devuelve en lugar de ignorarse. Repetir el aviso no tiene efecto.
     */
    public Resultado confirmarDesdePasarela(String referencia, boolean aprobado) {
        Registro registro = uow.ejecutarConReintentos(() -> {
            Pago pendiente = facturacion.pagoPorReferencia(referencia)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Pago " + referencia + " no existe"));
            Reserva reserva = reservas.buscar(pendiente.codigoReserva()).orElseThrow();
            Factura existente = facturacion.facturaDeReserva(reserva.getCodigo()).orElse(null);
            if (!aprobado || pendiente.aprobado()) {
                return new Registro(new Resultado(reserva, pendiente, existente), null);
            }
            LocalDateTime ahora = LocalDateTime.now(reloj);
            Pago cobrado = pendiente.aprobadoEn(ahora);
            facturacion.guardarPago(cobrado);
            try {
                reserva.verificarPagable(ahora);
            } catch (ConflictoException e) {
                return new Registro(new Resultado(reserva, cobrado, existente), cobrado);
            }
            return new Registro(new Resultado(reserva, cobrado, confirmarYFacturar(reserva, cobrado, ahora)), null);
        });
        if (registro.cobroSinServicio() == null) return registro.resultado();
        Pago devuelto = reembolsos.devolver(List.of(registro.cobroSinServicio())).get(0);
        return new Resultado(registro.resultado().reserva(), devuelto, registro.resultado().factura());
    }

    private Factura confirmarYFacturar(Reserva reserva, Pago pago, LocalDateTime ahora) {
        reserva.confirmar(pago);
        reservas.guardar(reserva);
        Factura factura = Factura.emitir(facturacion.siguienteNumeroFactura(), reserva.getCodigo(),
                reserva.getValorTotal(), parametros.nitEmisor(), ahora);
        facturacion.guardarFactura(factura);
        clientes.buscar(reserva.getDocumentoCliente()).ifPresent(c -> notificador.enviar(
                c.getCorreo() != null ? c.getCorreo() : c.getDocumento(),
                "Reserva confirmada para la tanda " + reserva.getCodigoSesion() + ". Factura " + factura.numero()));
        eventos.publicar(new ReservaConfirmada(reserva.getCodigo(), reserva.getDocumentoCliente(),
                reserva.getCodigoSesion(), ahora));
        return factura;
    }

    private record Preparacion(BigDecimal monto, Resultado repeticion) {
    }

    /** {@code cobroSinServicio}: pago aprobado que debe devolverse porque la reserva ya no se puede confirmar. */
    private record Registro(Resultado resultado, Pago cobroSinServicio) {
    }

    /** {@code repeticion} es true cuando la clave de idempotencia ya se había usado: no se cobró de nuevo. */
    public record Resultado(Reserva reserva, Pago pago, Factura factura, boolean repeticion) {
        public Resultado(Reserva reserva, Pago pago, Factura factura) {
            this(reserva, pago, factura, false);
        }
    }
}
