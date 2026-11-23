package co.kartrent.dominio.pista;

import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.TipoKart;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tanda programada. Experto en sus cupos y Creador de sus asignaciones (GRASP). */
public class SesionPista {
    private final String codigo;
    private final LocalDateTime horaInicioProgramada;
    private final int duracionMinutos;
    private final int cuposMaximos;
    private int cuposVendidos;
    private final TipoKart tipoKart;
    private EstadoSesion estado;
    private LocalDateTime inicioReal;
    private LocalDateTime finReal;
    private final List<AsignacionKart> asignaciones = new ArrayList<>();

    public SesionPista(String codigo, LocalDateTime horaInicioProgramada, int duracionMinutos, int cuposMaximos,
                       int cuposVendidos, TipoKart tipoKart, EstadoSesion estado, LocalDateTime inicioReal,
                       LocalDateTime finReal, List<AsignacionKart> asignaciones) {
        if (cuposMaximos <= 0) throw new ReglaNegocioException("La tanda debe ofrecer al menos un cupo");
        if (duracionMinutos <= 0) throw new ReglaNegocioException("La duración debe ser positiva");
        this.codigo = codigo;
        this.horaInicioProgramada = horaInicioProgramada;
        this.duracionMinutos = duracionMinutos;
        this.cuposMaximos = cuposMaximos;
        this.cuposVendidos = cuposVendidos;
        this.tipoKart = tipoKart;
        this.estado = estado;
        this.inicioReal = inicioReal;
        this.finReal = finReal;
        if (asignaciones != null) this.asignaciones.addAll(asignaciones);
    }

    public static SesionPista programar(String codigo, LocalDateTime inicio, int duracionMinutos, int cupos, TipoKart tipoKart) {
        return new SesionPista(codigo, inicio, duracionMinutos, cupos, 0, tipoKart, EstadoSesion.PROGRAMADA,
                null, null, List.of());
    }

    public int cuposDisponibles() {
        return cuposMaximos - cuposVendidos;
    }

    public void reservarCupos(int cantidad) {
        if (estado != EstadoSesion.PROGRAMADA && estado != EstadoSesion.EN_PARRILLA) {
            throw new ConflictoException("La tanda " + codigo + " ya no acepta reservas");
        }
        if (cantidad <= 0) throw new ReglaNegocioException("La cantidad de cupos debe ser positiva");
        if (cantidad > cuposDisponibles()) throw new ConflictoException("Cupos insuficientes");
        cuposVendidos += cantidad;
    }

    public void liberarCupos(int cantidad) {
        cuposVendidos = Math.max(0, cuposVendidos - cantidad);
    }

    /** RN-04 y RN-05: kart disponible y apto para la edad del piloto. */
    public AsignacionKart asignarKart(UUID codigoReserva, String documentoPiloto, String nombrePiloto,
                                      int edadPiloto, Kart kart) {
        if (estado != EstadoSesion.PROGRAMADA && estado != EstadoSesion.EN_PARRILLA) {
            throw new ConflictoException("La parrilla de la tanda " + codigo + " está cerrada");
        }
        if (!kart.estaDisponible()) throw new ConflictoException("Kart " + kart.getNumeroKart() + " no disponible");
        if (!kart.tipo().equals(tipoKart)) throw new ConflictoException("La tanda " + codigo + " es para karts " + tipoKart);
        if (edadPiloto < kart.edadMinimaPiloto()) throw new ReglaNegocioException("El piloto no cumple la edad mínima del kart");
        if (edadPiloto > kart.tipo().edadMaxima()) {
            throw new ReglaNegocioException("Un kart infantil no puede asignarse a un adulto");
        }
        boolean karRepetido = asignaciones.stream().anyMatch(a -> a.getNumeroKart() == kart.getNumeroKart());
        if (karRepetido) throw new ConflictoException("Kart " + kart.getNumeroKart() + " ya está en la parrilla");
        boolean pilotoRepetido = asignaciones.stream().anyMatch(a -> a.getDocumentoPiloto().equals(documentoPiloto));
        if (pilotoRepetido) throw new ConflictoException("El piloto ya tiene kart en esta tanda");
        if (asignaciones.size() >= cuposVendidos) throw new ConflictoException("No hay más pilotos confirmados para asignar");
        if (kart.getCodigoTransponder() == null) throw new ConflictoException("El kart no tiene transponder vinculado");

        AsignacionKart asignacion = new AsignacionKart(asignaciones.size() + 1, codigoReserva, documentoPiloto,
                nombrePiloto, kart.getNumeroKart(), kart.getCodigoTransponder(), null, List.of());
        asignaciones.add(asignacion);
        estado = EstadoSesion.EN_PARRILLA;
        return asignacion;
    }

    public void iniciar(LocalDateTime ahora) {
        if (estado != EstadoSesion.EN_PARRILLA) throw new ConflictoException("Solo se inicia una tanda con parrilla armada");
        inicioReal = ahora;
        estado = EstadoSesion.EN_CURSO;
    }

    /** Cierra la tanda y devuelve el uso de cada kart para que Flota lo acumule. */
    public List<UsoKart> finalizar(LocalDateTime ahora, double longitudPistaM) {
        if (estado != EstadoSesion.EN_CURSO) throw new ConflictoException("La tanda " + codigo + " no está en curso");
        finReal = ahora;
        estado = EstadoSesion.FINALIZADA;
        int minutos = (int) Math.max(1, Duration.between(inicioReal, finReal).toMinutes());
        return asignaciones.stream()
                .map(a -> new UsoKart(a.getNumeroKart(), minutos, a.totalVueltas() * longitudPistaM / 1000.0))
                .toList();
    }

    public Optional<AsignacionKart> asignacionPorTransponder(String codigoTransponder) {
        return asignaciones.stream().filter(a -> codigoTransponder.equals(a.getCodigoTransponder())).findFirst();
    }

    /** RN-08: ordena por mejor vuelta; quien no tiene vueltas va al final. */
    public List<LineaClasificacion> obtenerClasificacion() {
        List<AsignacionKart> orden = new ArrayList<>(asignaciones);
        orden.sort(Comparator.comparing((AsignacionKart a) -> a.mejorVuelta().map(v -> v.milisegundos()).orElse(Long.MAX_VALUE))
                .thenComparing(AsignacionKart::getNumeroParrilla));
        List<LineaClasificacion> lineas = new ArrayList<>();
        for (int i = 0; i < orden.size(); i++) {
            AsignacionKart a = orden.get(i);
            lineas.add(new LineaClasificacion(i + 1, a.getNumeroKart(), a.getNombrePiloto(), a.totalVueltas(),
                    a.mejorVuelta().map(VueltaTelemetria::milisegundos).orElse(null),
                    a.ultimaVuelta().map(VueltaTelemetria::milisegundos).orElse(null)));
        }
        return lineas;
    }

    public boolean estaEnCurso() { return estado == EstadoSesion.EN_CURSO; }
    public String getCodigo() { return codigo; }
    public LocalDateTime getHoraInicioProgramada() { return horaInicioProgramada; }
    public int getDuracionMinutos() { return duracionMinutos; }
    public int getCuposMaximos() { return cuposMaximos; }
    public int getCuposVendidos() { return cuposVendidos; }
    public TipoKart getTipoKart() { return tipoKart; }
    public EstadoSesion getEstado() { return estado; }
    public LocalDateTime getInicioReal() { return inicioReal; }
    public LocalDateTime getFinReal() { return finReal; }
    public List<AsignacionKart> getAsignaciones() { return Collections.unmodifiableList(asignaciones); }
}
