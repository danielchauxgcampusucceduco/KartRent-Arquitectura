package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.reservas.Tarifa;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RepositorioTarifas {
    void guardar(Tarifa tarifa);
    Optional<Tarifa> vigente(TipoKart tipoKart, LocalDate fecha);
    List<Tarifa> listar();
}
