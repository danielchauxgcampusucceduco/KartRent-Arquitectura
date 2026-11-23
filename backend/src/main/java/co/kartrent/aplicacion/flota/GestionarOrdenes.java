package co.kartrent.aplicacion.flota;

import co.kartrent.aplicacion.puertos.RepositorioKarts;
import co.kartrent.aplicacion.puertos.RepositorioMecanicos;
import co.kartrent.aplicacion.puertos.RepositorioOrdenes;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.RecursoNoEncontradoException;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.Mecanico;
import co.kartrent.dominio.flota.OrdenMantenimiento;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** HU-04 — interfaz GestionarOrdenes (puerto de gestión del taller). */
public class GestionarOrdenes {
    private final RepositorioOrdenes ordenes;
    private final RepositorioKarts karts;
    private final RepositorioMecanicos mecanicos;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public GestionarOrdenes(RepositorioOrdenes ordenes, RepositorioKarts karts, RepositorioMecanicos mecanicos,
                            UnidadDeTrabajo uow, Clock reloj) {
        this.ordenes = ordenes;
        this.karts = karts;
        this.mecanicos = mecanicos;
        this.uow = uow;
        this.reloj = reloj;
    }

    public OrdenMantenimiento asignar(String numeroOrden, String codigoMecanico) {
        return uow.ejecutar(() -> {
            OrdenMantenimiento orden = orden(numeroOrden);
            Mecanico mecanico = mecanicos.buscar(codigoMecanico)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Mecánico " + codigoMecanico + " no existe"));
            orden.asignar(mecanico);
            ordenes.guardar(orden);
            return orden;
        });
    }

    public OrdenMantenimiento cerrar(String numeroOrden, String codigoMecanico, String observaciones) {
        return uow.ejecutar(() -> {
            OrdenMantenimiento orden = orden(numeroOrden);
            Kart kart = karts.buscar(orden.getNumeroKart()).orElseThrow();
            orden.cerrar(codigoMecanico, observaciones, kart, LocalDateTime.now(reloj));
            ordenes.guardar(orden);
            karts.guardar(kart);
            return orden;
        });
    }

    public List<OrdenMantenimiento> listar() {
        return ordenes.listar();
    }

    public List<Mecanico> mecanicos() {
        return mecanicos.listar();
    }

    private OrdenMantenimiento orden(String numero) {
        return ordenes.buscar(numero).orElseThrow(() -> new RecursoNoEncontradoException("Orden " + numero + " no existe"));
    }
}
