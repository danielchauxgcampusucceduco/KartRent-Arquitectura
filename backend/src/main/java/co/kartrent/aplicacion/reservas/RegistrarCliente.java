package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.reservas.Cliente;

import java.time.Clock;
import java.time.LocalDate;

/** HU-01: registro del piloto con su exoneración de responsabilidad. */
public class RegistrarCliente {
    private final RepositorioClientes clientes;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public RegistrarCliente(RepositorioClientes clientes, UnidadDeTrabajo uow, Clock reloj) {
        this.clientes = clientes;
        this.uow = uow;
        this.reloj = reloj;
    }

    public Cliente ejecutar(Comando c) {
        return uow.ejecutar(() -> {
            if (clientes.buscar(c.documento()).isPresent()) {
                throw new ConflictoException("Ya existe un cliente con documento " + c.documento());
            }
            Cliente cliente = new Cliente(c.documento(), c.nombre(), c.correo(), c.telefono(), c.fechaNacimiento(),
                    false, null);
            if (c.aceptaExoneracion()) cliente.firmarExoneracion(LocalDate.now(reloj));
            clientes.guardar(cliente);
            return cliente;
        });
    }

    public record Comando(String documento, String nombre, String correo, String telefono,
                          LocalDate fechaNacimiento, boolean aceptaExoneracion) {
    }
}
