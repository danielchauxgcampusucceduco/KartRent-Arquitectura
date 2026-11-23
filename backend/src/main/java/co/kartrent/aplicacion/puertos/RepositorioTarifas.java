package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.reservas.Tarifa;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RepositorioTarifas {
    void guardar(Tarifa tarifa);
    Optional<Tarifa> vigente(String tipoKart, LocalDate fecha);
    List<Tarifa> listar();
}
