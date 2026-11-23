package co.kartrent.aplicacion.flota;

import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.RepositorioKarts;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.TipoKart;

import java.util.Comparator;
import java.util.List;

/** Interfaz ConsultarDisponibilidad del componente Flota. */
public class ConsultarFlota {
    private final RepositorioKarts karts;
    private final ParametrosOperacion parametros;

    public ConsultarFlota(RepositorioKarts karts, ParametrosOperacion parametros) {
        this.karts = karts;
        this.parametros = parametros;
    }

    /** Horas de motor desde el último servicio a partir de las cuales un kart requiere preventivo (RN-09). */
    public double umbralHoras() {
        return parametros.umbralHorasPreventivo();
    }

    public List<Kart> todos() {
        return karts.listar().stream().sorted(Comparator.comparingInt(Kart::getNumeroKart)).toList();
    }

    public List<Kart> disponibles(TipoKart tipoKart) {
        return todos().stream().filter(Kart::estaDisponible).filter(k -> k.tipo() == tipoKart).toList();
    }
}
