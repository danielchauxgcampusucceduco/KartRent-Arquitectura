package co.kartrent.dominio.pista;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turno de un piloto en una tanda. Guarda la reserva y el piloto por identidad (no por referencia)
 * para que el módulo de pista no dependa del de reservas. Creador de sus vueltas (GRASP).
 */
public class AsignacionKart {
    private final int numeroParrilla;
    private final UUID codigoReserva;
    private final String documentoPiloto;
    private final String nombrePiloto;
    private final int numeroKart;
    private final String codigoTransponder;
    private Instant ultimoPaso;
    private final List<VueltaTelemetria> vueltas = new ArrayList<>();

    public AsignacionKart(int numeroParrilla, UUID codigoReserva, String documentoPiloto, String nombrePiloto,
                          int numeroKart, String codigoTransponder, Instant ultimoPaso, List<VueltaTelemetria> vueltas) {
        this.numeroParrilla = numeroParrilla;
        this.codigoReserva = codigoReserva;
        this.documentoPiloto = documentoPiloto;
        this.nombrePiloto = nombrePiloto;
        this.numeroKart = numeroKart;
        this.codigoTransponder = codigoTransponder;
        this.ultimoPaso = ultimoPaso;
        if (vueltas != null) this.vueltas.addAll(vueltas);
    }

    /**
     * Registra un paso por meta. El primer paso abre la vuelta 1; los siguientes cierran una vuelta.
     * RN-07: un intervalo menor al tiempo mínimo es una doble lectura y se descarta.
     */
    public Optional<VueltaTelemetria> registrarPaso(Instant marca, Duration tiempoMinimo) {
        if (ultimoPaso == null) {
            ultimoPaso = marca;
            return Optional.empty();
        }
        if (!marca.isAfter(ultimoPaso)) return Optional.empty();
        Duration intervalo = Duration.between(ultimoPaso, marca);
        VueltaTelemetria candidata = new VueltaTelemetria(vueltas.size() + 1, marca, intervalo);
        if (!candidata.esValida(tiempoMinimo)) return Optional.empty();
        vueltas.add(candidata);
        ultimoPaso = marca;
        return Optional.of(candidata);
    }

    public Optional<VueltaTelemetria> mejorVuelta() {
        return vueltas.stream().min(Comparator.comparing(VueltaTelemetria::tiempoVuelta));
    }

    public Optional<VueltaTelemetria> ultimaVuelta() {
        return vueltas.isEmpty() ? Optional.empty() : Optional.of(vueltas.get(vueltas.size() - 1));
    }

    public int totalVueltas() { return vueltas.size(); }
    public int getNumeroParrilla() { return numeroParrilla; }
    public UUID getCodigoReserva() { return codigoReserva; }
    public String getDocumentoPiloto() { return documentoPiloto; }
    public String getNombrePiloto() { return nombrePiloto; }
    public int getNumeroKart() { return numeroKart; }
    public String getCodigoTransponder() { return codigoTransponder; }
    public Instant getUltimoPaso() { return ultimoPaso; }
    public List<VueltaTelemetria> getVueltas() { return Collections.unmodifiableList(vueltas); }
}
