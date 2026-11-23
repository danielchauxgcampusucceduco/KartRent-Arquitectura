package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.flota.OrdenMantenimiento;

import java.util.List;
import java.util.Optional;

public interface RepositorioOrdenes {
    void guardar(OrdenMantenimiento orden);
    Optional<OrdenMantenimiento> buscar(String numero);
    Optional<OrdenMantenimiento> abiertaPorKart(int numeroKart);
    List<OrdenMantenimiento> listar();
    String siguienteNumero();
}
