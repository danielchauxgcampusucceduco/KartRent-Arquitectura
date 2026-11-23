package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.flota.Kart;

import java.util.List;
import java.util.Optional;

public interface RepositorioKarts {
    void guardar(Kart kart);
    Optional<Kart> buscar(int numero);
    List<Kart> listar();
}
