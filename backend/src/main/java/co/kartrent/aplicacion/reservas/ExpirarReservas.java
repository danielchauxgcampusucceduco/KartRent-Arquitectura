package co.kartrent.aplicacion.reservas;

import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.reservas.Reserva;

import java.time.Clock;
import java.time.LocalDateTime;

/** RN-01: libera los cupos de las reservas que no se pagaron a tiempo. */
public class ExpirarReservas {
    private final RepositorioReservas reservas;
    private final RepositorioSesiones sesiones;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public ExpirarReservas(RepositorioReservas reservas, RepositorioSesiones sesiones, UnidadDeTrabajo uow, Clock reloj) {
        this.reservas = reservas;
        this.sesiones = sesiones;
        this.uow = uow;
        this.reloj = reloj;
    }

    public int ejecutar() {
        return uow.ejecutarConReintentos(() -> {
            LocalDateTime ahora = LocalDateTime.now(reloj);
            int expiradas = 0;
            for (Reserva r : reservas.pendientesVencidas(ahora)) {
                if (r.expirar(ahora)) {
                    reservas.guardar(r);
                    sesiones.buscar(r.getCodigoSesion()).ifPresent(s -> {
                        s.liberarCupos(r.getCantidadCupos());
                        sesiones.guardar(s);
                    });
                    expiradas++;
                }
            }
            return expiradas;
        });
    }
}
