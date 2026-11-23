package co.kartrent.aplicacion.flota;

import co.kartrent.aplicacion.puertos.RepositorioKarts;
import co.kartrent.dominio.flota.Kart;

import java.util.Comparator;
import java.util.List;

/** Interfaz ConsultarDisponibilidad del componente Flota. */
public class ConsultarFlota {
    private final RepositorioKarts karts;

    public ConsultarFlota(RepositorioKarts karts) {
        this.karts = karts;
    }

    public List<Kart> todos() {
        return karts.listar().stream().sorted(Comparator.comparingInt(Kart::getNumeroKart)).toList();
    }

    public List<Kart> disponibles(String tipoKart) {
        return todos().stream().filter(Kart::estaDisponible).filter(k -> k.tipo().equals(tipoKart)).toList();
    }
}
