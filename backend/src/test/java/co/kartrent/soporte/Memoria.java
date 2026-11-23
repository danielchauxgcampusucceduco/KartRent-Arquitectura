package co.kartrent.soporte;

import co.kartrent.aplicacion.puertos.*;
import co.kartrent.dominio.eventos.EventoDominio;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.facturacion.Pago;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.Mecanico;
import co.kartrent.dominio.flota.OrdenMantenimiento;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.Cliente;
import co.kartrent.dominio.reservas.EstadoReserva;
import co.kartrent.dominio.reservas.Reserva;
import co.kartrent.dominio.reservas.Tarifa;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Adaptadores en memoria: dobles de prueba de los puertos de salida (sin base de datos). */
public final class Memoria {
    private Memoria() {
    }

    public static class Clientes implements RepositorioClientes {
        public final Map<String, Cliente> datos = new LinkedHashMap<>();
        public void guardar(Cliente c) { datos.put(c.getDocumento(), c); }
        public Optional<Cliente> buscar(String d) { return Optional.ofNullable(datos.get(d)); }
        public List<Cliente> listar() { return new ArrayList<>(datos.values()); }
    }

    public static class Reservas implements RepositorioReservas {
        public final Map<UUID, Reserva> datos = new LinkedHashMap<>();
        public void guardar(Reserva r) { datos.put(r.getCodigo(), r); }
        public Optional<Reserva> buscar(UUID c) { return Optional.ofNullable(datos.get(c)); }
        public List<Reserva> pendientesVencidas(LocalDateTime ahora) {
            return datos.values().stream().filter(r -> r.getEstado() == EstadoReserva.PENDIENTE_PAGO
                    && ahora.isAfter(r.getVenceRetencion())).toList();
        }
        public List<Reserva> confirmadasPorSesion(String s) {
            return datos.values().stream().filter(r -> r.getCodigoSesion().equals(s)
                    && r.getEstado() == EstadoReserva.CONFIRMADA).toList();
        }
        public List<Reserva> porCliente(String d) {
            return datos.values().stream().filter(r -> r.getDocumentoCliente().equals(d)).toList();
        }
    }

    public static class Sesiones implements RepositorioSesiones {
        public final Map<String, SesionPista> datos = new LinkedHashMap<>();
        public void guardar(SesionPista s) { datos.put(s.getCodigo(), s); }
        public Optional<SesionPista> buscar(String c) { return Optional.ofNullable(datos.get(c)); }
        public List<SesionPista> listar() { return new ArrayList<>(datos.values()); }
        public Optional<SesionPista> enCursoConTransponder(String t) {
            return datos.values().stream().filter(SesionPista::estaEnCurso)
                    .filter(s -> s.asignacionPorTransponder(t).isPresent()).findFirst();
        }
    }

    public static class Karts implements RepositorioKarts {
        public final Map<Integer, Kart> datos = new TreeMap<>();
        public void guardar(Kart k) { datos.put(k.getNumeroKart(), k); }
        public Optional<Kart> buscar(int n) { return Optional.ofNullable(datos.get(n)); }
        public List<Kart> listar() { return new ArrayList<>(datos.values()); }
    }

    public static class Mecanicos implements RepositorioMecanicos {
        public final Map<String, Mecanico> datos = new LinkedHashMap<>();
        public Optional<Mecanico> buscar(String c) { return Optional.ofNullable(datos.get(c)); }
        public List<Mecanico> listar() { return new ArrayList<>(datos.values()); }
    }

    public static class Ordenes implements RepositorioOrdenes {
        public final Map<String, OrdenMantenimiento> datos = new LinkedHashMap<>();
        public void guardar(OrdenMantenimiento o) { datos.put(o.getNumero(), o); }
        public Optional<OrdenMantenimiento> buscar(String n) { return Optional.ofNullable(datos.get(n)); }
        public Optional<OrdenMantenimiento> abiertaPorKart(int k) {
            return datos.values().stream().filter(o -> o.getNumeroKart() == k && o.estaAbierta()).findFirst();
        }
        public List<OrdenMantenimiento> listar() { return new ArrayList<>(datos.values()); }
        public String siguienteNumero() { return String.format("OM-%04d", datos.size() + 1); }
    }

    public static class Tarifas implements RepositorioTarifas {
        public final List<Tarifa> datos = new ArrayList<>();
        public void guardar(Tarifa t) { datos.add(t); }
        public Optional<Tarifa> vigente(TipoKart tipo, LocalDate f) {
            return datos.stream().filter(t -> t.tipoKart() == tipo && t.estaVigente(f))
                    .max(Comparator.comparing(Tarifa::vigenteDesde));
        }
        public List<Tarifa> listar() { return datos; }
    }

    public static class Facturacion implements RepositorioFacturacion {
        public final Map<String, Pago> pagos = new LinkedHashMap<>();
        public final Map<UUID, Factura> facturas = new LinkedHashMap<>();
        public void guardarPago(Pago p) { pagos.put(p.referenciaPasarela(), p); }
        public Optional<Pago> pagoPorReferencia(String r) { return Optional.ofNullable(pagos.get(r)); }
        public void guardarFactura(Factura f) { facturas.put(f.codigoReserva(), f); }
        public Optional<Factura> facturaDeReserva(UUID c) { return Optional.ofNullable(facturas.get(c)); }
        public String siguienteNumeroFactura() { return String.format("KRFE-%06d", facturas.size() + 1); }
    }

    public static class Parametros implements ParametrosOperacion {
        public double umbral = 20;
        public double umbralHorasPreventivo() { return umbral; }
        public void actualizarUmbralHoras(double h) { umbral = h; }
        public Duration tiempoMinimoVuelta() { return Duration.ofSeconds(25); }
        public Duration retencionReserva() { return Duration.ofMinutes(10); }
        public double longitudPistaMetros() { return 850; }
        public String nitEmisor() { return "900123456-7"; }
    }

    public static class PasarelaAprobadora implements PasarelaPagos {
        public boolean aprobar = true;
        public int cobros = 0;
        public ResultadoPago cobrar(String referencia, BigDecimal monto, MetodoPago metodo) {
            cobros++;
            return new ResultadoPago(aprobar, referencia, aprobar ? "APROBADA" : "RECHAZADA");
        }
        public NotificacionPago interpretarNotificacion(String cuerpo, String firma) {
            throw new UnsupportedOperationException("El webhook se prueba con la pasarela simulada");
        }
    }

    public static class Buzon implements Notificador, PublicadorEventos {
        public final List<String> mensajes = new ArrayList<>();
        public final List<EventoDominio> eventos = new ArrayList<>();
        public void enviar(String destino, String mensaje) { mensajes.add(destino + ": " + mensaje); }
        public void publicar(EventoDominio e) { eventos.add(e); }
    }
}
