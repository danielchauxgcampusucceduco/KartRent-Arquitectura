package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.reservas.Cliente;

import java.util.List;
import java.util.Optional;

public interface RepositorioClientes {
    void guardar(Cliente cliente);
    Optional<Cliente> buscar(String documento);
    List<Cliente> listar();
}
