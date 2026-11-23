package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.aplicacion.puertos.RepositorioFacturacion;
import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.reservas.Cliente;
import co.kartrent.dominio.reservas.Reserva;

import java.util.List;
import java.util.UUID;

public class ConsultarReservas {
    private final RepositorioReservas reservas;
    private final RepositorioClientes clientes;
    private final RepositorioFacturacion facturacion;

    public ConsultarReservas(RepositorioReservas reservas, RepositorioClientes clientes, RepositorioFacturacion facturacion) {
        this.reservas = reservas;
        this.clientes = clientes;
        this.facturacion = facturacion;
    }

    public Reserva reserva(UUID codigo) {
        return reservas.buscar(codigo).orElseThrow(() -> new RecursoNoEncontradoException("Reserva " + codigo + " no existe"));
    }

    public Factura factura(UUID codigoReserva) {
        return facturacion.facturaDeReserva(codigoReserva).orElseThrow(
                () -> new RecursoNoEncontradoException("La reserva " + codigoReserva + " no tiene factura"));
    }

    public List<Reserva> deCliente(String documento) {
        return reservas.porCliente(documento);
    }

    public Cliente cliente(String documento) {
        return clientes.buscar(documento).orElseThrow(() -> new RecursoNoEncontradoException("Cliente " + documento + " no registrado"));
    }

    public List<Cliente> clientes() {
        return clientes.listar();
    }
}
