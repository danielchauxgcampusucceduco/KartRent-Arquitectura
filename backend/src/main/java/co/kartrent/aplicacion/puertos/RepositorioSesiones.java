package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.pista.SesionPista;

import java.util.List;
import java.util.Optional;

public interface RepositorioSesiones {
    void guardar(SesionPista sesion);
    Optional<SesionPista> buscar(String codigo);
    List<SesionPista> listar();
    Optional<SesionPista> enCursoConTransponder(String codigoTransponder);
}
