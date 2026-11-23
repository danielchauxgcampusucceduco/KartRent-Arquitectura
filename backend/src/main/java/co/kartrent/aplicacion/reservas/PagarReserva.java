package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.Notificador;
import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.PasarelaPagos;
import co.kartrent.aplicacion.puertos.PublicadorEventos;
import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.aplicacion.puertos.RepositorioFacturacion;
import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.eventos.ReservaConfirmada;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.reservas.EstadoReserva;
import co.kartrent.dominio.reservas.Reserva;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-01 — cobra por la pasarela (Variaciones Protegidas) y, si se aprueba, confirma, factura y notifica.
 * Idempotente: la misma clave de idempotencia nunca cobra dos veces.
 */
public class PagarReserva {
    private final RepositorioReservas reservas;
    private final RepositorioClientes clientes;
    private final RepositorioFacturacion facturacion;
    private final PasarelaPagos pasarela;
    private final Notificador notificador;
    private final PublicadorEventos eventos;
    private final ParametrosOperacion parametros;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public PagarReserva(RepositorioReservas reservas, RepositorioClientes clientes, RepositorioFacturacion facturacion,
                        PasarelaPagos pasarela, Notificador notificador, PublicadorEventos eventos,
                        ParametrosOperacion parametros, UnidadDeTrabajo uow, Clock reloj) {
        this.reservas = reservas;
        this.clientes = clientes;
        this.facturacion = facturacion;
        this.pasarela = pasarela;
        this.notificador = notificador;
        this.eventos = eventos;
        this.parametros = parametros;
        this.uow = uow;
        this.reloj = reloj;
    }

    public Resultado ejecutar(UUID codigoReserva, MetodoPago metodo, String claveIdempotencia) {
        return uow.ejecutar(() -> {
            Reserva reserva = reservas.buscar(codigoReserva)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Reserva " + codigoReserva + " no existe"));
            String referencia = "KR-" + (claveIdempotencia == null || claveIdempotencia.isBlank()
                    ? codigoReserva : claveIdempotencia);
            Optional<Pago> previo = facturacion.pagoPorReferencia(referencia);
            if (previo.isPresent()) {
                return new Resultado(reserva, previo.get(), facturacion.facturaDeReserva(codigoReserva).orElse(null), true);
            }
            LocalDateTime ahora = LocalDateTime.now(reloj);
            reserva.verificarPagable(ahora);
            PasarelaPagos.ResultadoPago respuesta = pasarela.cobrar(referencia, reserva.getValorTotal(), metodo);
            Pago pago = reserva.registrarPago(referencia, metodo, respuesta.aprobado(), ahora);
            facturacion.guardarPago(pago);
            Factura factura = null;
            if (pago.esAprobado()) {
                factura = confirmarYFacturar(reserva, pago, ahora);
            }
            return new Resultado(reserva, pago, factura);
        });
    }

    /** Webhook de la pasarela: la pasarela verifica la firma y lee el cuerpo; aquí solo se confirma el pago. */
    public Resultado procesarWebhook(String cuerpo, String firma) {
        PasarelaPagos.NotificacionPago n = pasarela.interpretarNotificacion(cuerpo, firma);
        return confirmarDesdePasarela(n.referencia(), n.aprobado());
    }

    /** Llamado por el webhook de la pasarela cuando la aprobación llega de forma asíncrona. */
    public Resultado confirmarDesdePasarela(String referencia, boolean aprobado) {
        return uow.ejecutar(() -> {
            Pago pendiente = facturacion.pagoPorReferencia(referencia)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Pago " + referencia + " no existe"));
            Reserva reserva = reservas.buscar(pendiente.codigoReserva()).orElseThrow();
            if (!aprobado || reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
                return new Resultado(reserva, pendiente, facturacion.facturaDeReserva(reserva.getCodigo()).orElse(null));
            }
            LocalDateTime ahora = LocalDateTime.now(reloj);
            Pago aprobadoPago = new Pago(referencia, pendiente.codigoReserva(), pendiente.monto(), pendiente.metodo(),
                    true, ahora);
            facturacion.guardarPago(aprobadoPago);
            return new Resultado(reserva, aprobadoPago, confirmarYFacturar(reserva, aprobadoPago, ahora));
        });
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

    /** {@code repeticion} es true cuando la clave de idempotencia ya se había usado: no se cobró de nuevo. */
    public record Resultado(Reserva reserva, Pago pago, Factura factura, boolean repeticion) {
        public Resultado(Reserva reserva, Pago pago, Factura factura) {
            this(reserva, pago, factura, false);
        }
    }
}
