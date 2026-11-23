package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.flota.Mecanico;

import java.util.List;
import java.util.Optional;

public interface RepositorioMecanicos {
    Optional<Mecanico> buscar(String codigoEmpleado);
    List<Mecanico> listar();
}
