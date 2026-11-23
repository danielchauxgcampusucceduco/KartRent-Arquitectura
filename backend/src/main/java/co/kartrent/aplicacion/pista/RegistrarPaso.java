package co.kartrent.aplicacion.pista;

import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.PublicadorEventos;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.eventos.VueltaRegistrada;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.pista.VueltaTelemetria;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/** HU-03 — interfaz RegistrarPaso: la invoca el ingestor de telemetría por cada paso por meta. */
public class RegistrarPaso {
    private final RepositorioSesiones sesiones;
    private final ParametrosOperacion parametros;
    private final PublicadorEventos eventos;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public RegistrarPaso(RepositorioSesiones sesiones, ParametrosOperacion parametros, PublicadorEventos eventos,
                         UnidadDeTrabajo uow, Clock reloj) {
        this.sesiones = sesiones;
        this.parametros = parametros;
        this.eventos = eventos;
        this.uow = uow;
        this.reloj = reloj;
    }

    public Optional<VueltaRegistrada> ejecutar(String codigoTransponder, Instant marcaTiempo) {
        return uow.ejecutarConReintentos(() -> {
            SesionPista sesion = sesiones.enCursoConTransponder(codigoTransponder)
                    .orElseThrow(() -> new ConflictoException("El transponder " + codigoTransponder
                            + " no está en una tanda en curso"));
            AsignacionKart asignacion = sesion.asignacionPorTransponder(codigoTransponder).orElseThrow();
            Optional<VueltaTelemetria> vuelta = asignacion.registrarPaso(marcaTiempo, parametros.tiempoMinimoVuelta());
            sesiones.guardar(sesion);
            return vuelta.map(v -> {
                VueltaRegistrada evento = new VueltaRegistrada(sesion.getCodigo(), asignacion.getNumeroKart(),
                        v.numeroVuelta(), v.milisegundos(), LocalDateTime.now(reloj));
                eventos.publicar(evento);
                return evento;
            });
        });
    }
}
