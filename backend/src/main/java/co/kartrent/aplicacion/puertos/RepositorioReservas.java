package co.kartrent.aplicacion.puertos;

import co.kartrent.dominio.reservas.Reserva;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioReservas {
    void guardar(Reserva reserva);
    Optional<Reserva> buscar(UUID codigo);
    List<Reserva> pendientesVencidas(LocalDateTime ahora);
    List<Reserva> confirmadasPorSesion(String codigoSesion);
    List<Reserva> porCliente(String documento);
    List<Reserva> porSesion(String codigoSesion);
}
