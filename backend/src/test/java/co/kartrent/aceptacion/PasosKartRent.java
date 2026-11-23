package co.kartrent.aceptacion;

import co.kartrent.aplicacion.administracion.ReporteOcupacion;
import co.kartrent.aplicacion.reservas.RegistrarCliente;
import co.kartrent.dominio.eventos.VueltaRegistrada;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.flota.EstadoKart;
import co.kartrent.dominio.flota.KartAdulto;
import co.kartrent.dominio.flota.Mecanico;
import co.kartrent.dominio.flota.OrdenMantenimiento;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.Reserva;
import co.kartrent.soporte.Kartodromo;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Pasos Gherkin de HU-01..HU-05 ejecutados contra los casos de uso reales con adaptadores en memoria. */
public class PasosKartRent {
    private final Kartodromo k = new Kartodromo();
    private Reserva reserva;
    private RuntimeException error;
    private Optional<VueltaRegistrada> ultimaVuelta = Optional.empty();
    private List<OrdenMantenimiento> ordenesGeneradas = List.of();
    private ReporteOcupacion.Reporte reporte;
    private int tandas = 0;

    private LocalDate hoy() {
        return LocalDate.now(k.reloj);
    }

    private void intentar(Runnable r) {
        try {
            r.run();
            error = null;
        } catch (RuntimeException e) {
            error = e;
        }
    }

    // ---------- Contexto ----------
    @Dado("una tarifa {word} de {int} pesos por cupo")
    public void tarifa(String tipo, int valor) {
        k.configurarOperacion.registrarTarifa(TipoKart.valueOf(tipo), BigDecimal.valueOf(valor), LocalDate.of(2026, 1, 1));
    }

    @Dado("una tanda {string} de karts {word} con {int} cupos")
    public void tanda(String codigo, String tipo, int cupos) {
        LocalDateTime inicio = LocalDateTime.now(k.reloj).plusHours(1 + tandas++);
        k.configurarOperacion.programarTanda(codigo, inicio, 10, cupos, TipoKart.valueOf(tipo));
    }

    @Dado("el cliente {string} de {int} años con exoneración firmada")
    public void clienteConExoneracion(String doc, int edad) {
        k.registrarCliente.ejecutar(new RegistrarCliente.Comando(doc, "Piloto " + doc, doc + "@correo.co", "300",
                hoy().minusYears(edad).minusDays(10), true));
    }

    @Dado("el cliente {string} de {int} años sin exoneración firmada")
    public void clienteSinExoneracion(String doc, int edad) {
        k.registrarCliente.ejecutar(new RegistrarCliente.Comando(doc, "Piloto " + doc, doc + "@correo.co", "300",
                hoy().minusYears(edad).minusDays(10), false));
    }

    @Dado("la flota tiene los karts adultos {int} a {int} con transponder")
    public void flota(int desde, int hasta) {
        for (int i = desde; i <= hasta; i++) {
            k.karts.guardar(new KartAdulto(i, EstadoKart.DISPONIBLE, 0, 100, 5, String.format("TR-1%02d", i)));
        }
    }

    @Dado("el cliente {string} tiene {int} cupos pagados en la tanda {string}")
    public void cuposPagados(String doc, int cupos, String tanda) {
        reserva = k.reservarTanda.ejecutar(doc, tanda, cupos);
        k.pagarReserva.ejecutar(reserva.getCodigo(), MetodoPago.TARJETA, "pre-" + doc + tanda);
    }

    @Dado("el kart {int} está en mantenimiento")
    public void kartEnMantenimiento(int n) {
        k.karts.buscar(n).orElseThrow().bloquear();
    }

    @Dado("el kart {int} lleva {string} horas desde el último servicio")
    public void horasKart(int n, String texto) {
        double horas = Double.parseDouble(texto);
        k.karts.guardar(new KartAdulto(n, EstadoKart.DISPONIBLE, 0, 100, horas, String.format("TR-1%02d", n)));
    }

    @Dado("el mecánico {string} trabaja en el taller")
    public void mecanico(String codigo) {
        k.mecanicos.datos.put("M-01", new Mecanico("98000001", "Carlos Rosero", "M-01", "GENERAL"));
        k.mecanicos.datos.put(codigo, new Mecanico("98000002", "Luisa Bastidas", codigo, "GENERAL"));
    }

    @Dado("el piloto {string} corre en el kart {int} en la tanda {string} iniciada")
    public void pilotoEnPista(String doc, int kart, String tanda) {
        k.controlarSesion.asignarKart(tanda, reservaDe(doc, tanda), doc, kart);
        k.controlarSesion.iniciar(tanda);
    }

    @Dado("el piloto {string} corre en el kart {int} y el piloto {string} en el kart {int} en la tanda {string} iniciada")
    public void dosPilotos(String doc1, int kart1, String doc2, int kart2, String tanda) {
        k.controlarSesion.asignarKart(tanda, reservaDe(doc1, tanda), doc1, kart1);
        k.controlarSesion.asignarKart(tanda, reservaDe(doc2, tanda), doc2, kart2);
        k.controlarSesion.iniciar(tanda);
    }

    private java.util.UUID reservaDe(String doc, String tanda) {
        return k.reservas.porCliente(doc).stream().filter(r -> r.getCodigoSesion().equals(tanda)).findFirst()
                .orElseThrow().getCodigo();
    }

    // ---------- HU-01 ----------
    @Cuando("el cliente {string} reserva {int} cupos en la tanda {string}")
    public void reservar(String doc, int cupos, String tanda) {
        reserva = k.reservarTanda.ejecutar(doc, tanda, cupos);
    }

    @Cuando("el cliente {string} intenta reservar {int} cupos en la tanda {string}")
    public void intentaReservar(String doc, int cupos, String tanda) {
        intentar(() -> k.reservarTanda.ejecutar(doc, tanda, cupos));
    }

    @Cuando("paga la reserva con {string} y la clave {string}")
    public void pagar(String metodo, String clave) {
        k.pagarReserva.ejecutar(reserva.getCodigo(), MetodoPago.valueOf(metodo), clave);
    }

    @Cuando("cancela la reserva")
    public void cancelarReserva() {
        k.cancelarReserva.ejecutar(reserva.getCodigo());
    }

    @Cuando("el cliente {string} intenta cancelar su reserva en la tanda {string}")
    public void intentaCancelarReserva(String doc, String tanda) {
        java.util.UUID codigo = reservaDe(doc, tanda);
        intentar(() -> k.cancelarReserva.ejecutar(codigo));
    }

    @Entonces("la pasarela reembolsó {int} pago")
    public void reembolsos(int n) {
        assertEquals(n, k.pasarela.reembolsos.size());
    }

    @Cuando("pasan {int} minutos")
    public void pasan(int minutos) {
        k.reloj.avanzar(Duration.ofMinutes(minutos));
    }

    @Cuando("se ejecuta la expiración de reservas")
    public void expirar() {
        k.expirarReservas.ejecutar();
    }

    @Entonces("la tanda {string} tiene {int} cupos disponibles")
    public void cuposDisponibles(String tanda, int esperados) {
        assertEquals(esperados, k.sesiones.buscar(tanda).orElseThrow().cuposDisponibles());
    }

    @Entonces("la reserva queda {string}")
    public void estadoReserva(String estado) {
        assertEquals(estado, k.reservas.buscar(reserva.getCodigo()).orElseThrow().getEstado().name());
    }

    @Entonces("se emite una factura por {int} pesos con CUFE")
    public void factura(int total) {
        var f = k.facturacion.facturaDeReserva(reserva.getCodigo()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(total).compareTo(f.total()));
        assertEquals(96, f.cufe().length(), "CUFE SHA-384 en hexadecimal");
    }

    @Entonces("la pasarela registró {int} cobro")
    public void cobros(int n) {
        assertEquals(n, k.pasarela.cobros);
    }

    @Entonces("se rechaza con el mensaje {string}")
    public void rechazoExacto(String mensaje) {
        assertNotNull(error, "Se esperaba un rechazo");
        assertEquals(mensaje, error.getMessage());
    }

    @Entonces("se rechaza indicando {string}")
    public void rechazoContiene(String texto) {
        assertNotNull(error, "Se esperaba un rechazo");
        assertTrue(error.getMessage().toLowerCase().contains(texto.toLowerCase()), error.getMessage());
    }

    // ---------- HU-02 ----------
    @Cuando("el jefe de boxes asigna el kart {int} al piloto {string} en la tanda {string}")
    public void asignar(int kart, String doc, String tanda) {
        k.controlarSesion.asignarKart(tanda, reservaDe(doc, tanda), doc, kart);
    }

    @Cuando("el jefe de boxes intenta asignar el kart {int} al piloto {string} en la tanda {string}")
    public void intentaAsignar(int kart, String doc, String tanda) {
        intentar(() -> k.controlarSesion.asignarKart(tanda, reservaDe(doc, tanda), doc, kart));
    }

    @Cuando("el jefe de boxes retira el kart {int} de la tanda {string}")
    public void desasignar(int kart, String tanda) {
        k.controlarSesion.desasignarKart(tanda, kart);
    }

    @Cuando("el jefe de boxes intenta retirar el kart {int} de la tanda {string}")
    public void intentaDesasignar(int kart, String tanda) {
        intentar(() -> k.controlarSesion.desasignarKart(tanda, kart));
    }

    @Cuando("inicia la tanda {string}")
    public void iniciar(String tanda) {
        k.controlarSesion.iniciar(tanda);
    }

    @Cuando("el jefe de boxes intenta iniciar la tanda {string}")
    public void intentaIniciar(String tanda) {
        intentar(() -> k.controlarSesion.iniciar(tanda));
    }

    @Entonces("la tanda {string} queda {string}")
    public void estadoTanda(String tanda, String estado) {
        assertEquals(estado, k.sesiones.buscar(tanda).orElseThrow().getEstado().name());
    }

    @Entonces("el kart {int} queda {string}")
    public void estadoKart(int kart, String estado) {
        assertEquals(estado, k.karts.buscar(kart).orElseThrow().getEstado().name());
    }

    // ---------- HU-03 ----------
    @Cuando("el transponder {string} pasa por meta en {string}")
    public void paso(String tr, String hora) {
        Instant marca = LocalDate.of(2026, 11, 20).atTime(LocalTime.parse(hora)).toInstant(ZoneOffset.UTC);
        ultimaVuelta = k.registrarPaso.ejecutar(tr, marca);
    }

    @Entonces("se registra una vuelta de {long} milisegundos")
    public void vuelta(long ms) {
        assertTrue(ultimaVuelta.isPresent(), "Se esperaba una vuelta");
        assertEquals(ms, ultimaVuelta.get().tiempoMs());
    }

    @Entonces("no se registra ninguna vuelta")
    public void sinVuelta() {
        assertTrue(ultimaVuelta.isEmpty());
    }

    @Entonces("el líder de la tanda {string} es el kart {int}")
    public void lider(String tanda, int kart) {
        SesionPista s = k.sesiones.buscar(tanda).orElseThrow();
        assertEquals(kart, s.obtenerClasificacion().get(0).numeroKart());
    }

    // ---------- HU-04 ----------
    @Cuando("finaliza la tanda {string}")
    public void finalizar(String tanda) {
        ordenesGeneradas = k.controlarSesion.finalizar(tanda).ordenesGeneradas();
    }

    @Entonces("se genera {int} orden preventiva para el kart {int}")
    public void ordenPara(int n, int kart) {
        assertEquals(n, ordenesGeneradas.size());
        assertEquals(kart, ordenesGeneradas.get(0).getNumeroKart());
    }

    @Entonces("se generan {int} órdenes preventivas")
    public void ordenes(int n) {
        assertEquals(n, ordenesGeneradas.size());
    }

    private String ordenDe(int kart) {
        return k.ordenes.abiertaPorKart(kart).orElseThrow().getNumero();
    }

    @Cuando("se asigna la orden del kart {int} al mecánico {string}")
    public void asignarOrden(int kart, String mecanico) {
        k.gestionarOrdenes.asignar(ordenDe(kart), mecanico);
    }

    @Cuando("el mecánico {string} intenta cerrar la orden del kart {int}")
    public void intentaCerrar(String mecanico, int kart) {
        String numero = ordenDe(kart);
        intentar(() -> k.gestionarOrdenes.cerrar(numero, mecanico, "Intento"));
    }

    @Cuando("el mecánico {string} cierra la orden del kart {int} con {string}")
    public void cerrar(String mecanico, int kart, String obs) {
        k.gestionarOrdenes.cerrar(ordenDe(kart), mecanico, obs);
    }

    @Entonces("el kart {int} tiene {int} horas desde el último servicio")
    public void horasServicio(int kart, int horas) {
        assertEquals(horas, k.karts.buscar(kart).orElseThrow().getHorasDesdeUltimoServicio(), 0.001);
    }

    // ---------- HU-05 ----------
    @Cuando("el administrador intenta programar la tanda {string} de karts {word}")
    public void intentaProgramar(String codigo, String tipo) {
        intentar(() -> k.configurarOperacion.programarTanda(codigo, LocalDateTime.now(k.reloj).plusHours(2), 10, 6, TipoKart.valueOf(tipo)));
    }

    @Cuando("el administrador cancela la tanda {string}")
    public void cancelarTanda(String tanda) {
        k.cancelarTanda.ejecutar(tanda);
    }

    @Cuando("el administrador consulta el reporte de ocupación del mes")
    public void consultarReporte() {
        reporte = k.reporteOcupacion.ejecutar(hoy().withDayOfMonth(1), hoy().withDayOfMonth(hoy().lengthOfMonth()));
    }

    @Entonces("la ocupación total es {int} por ciento")
    public void ocupacion(int porcentaje) {
        assertEquals(porcentaje, reporte.ocupacionPorcentaje(), 0.01);
    }

    @Entonces("los ingresos confirmados son {int} pesos")
    public void ingresos(int valor) {
        assertEquals(0, BigDecimal.valueOf(valor).compareTo(reporte.ingresos()));
    }

    @Cuando("el administrador fija el umbral de mantenimiento en {int} horas")
    public void umbral(int horas) {
        k.configurarOperacion.actualizarUmbral(horas);
    }

    @Entonces("el umbral vigente es {int} horas")
    public void umbralVigente(int horas) {
        assertEquals(horas, k.configurarOperacion.umbralActual(), 0.001);
    }
}
