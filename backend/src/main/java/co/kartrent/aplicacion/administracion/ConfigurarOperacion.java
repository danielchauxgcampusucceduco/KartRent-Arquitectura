package co.kartrent.aplicacion.administracion;

import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.aplicacion.puertos.RepositorioTarifas;
import co.kartrent.aplicacion.puertos.UnidadDeTrabajo;
import co.kartrent.dominio.comun.ConflictoException;
import co.kartrent.dominio.comun.ReglaNegocioException;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.reservas.Tarifa;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** HU-05 — puertos de gestión del Administrador: tarifas, tandas y umbral de mantenimiento. */
public class ConfigurarOperacion {
    private final RepositorioTarifas tarifas;
    private final RepositorioSesiones sesiones;
    private final ParametrosOperacion parametros;
    private final UnidadDeTrabajo uow;
    private final Clock reloj;

    public ConfigurarOperacion(RepositorioTarifas tarifas, RepositorioSesiones sesiones, ParametrosOperacion parametros,
                               UnidadDeTrabajo uow, Clock reloj) {
        this.tarifas = tarifas;
        this.sesiones = sesiones;
        this.parametros = parametros;
        this.uow = uow;
        this.reloj = reloj;
    }

    public Tarifa registrarTarifa(TipoKart tipoKart, BigDecimal valorPorCupo, LocalDate vigenteDesde) {
        return uow.ejecutar(() -> {
            Tarifa tarifa = new Tarifa(tipoKart, valorPorCupo, vigenteDesde);
            tarifas.guardar(tarifa);
            return tarifa;
        });
    }

    public SesionPista programarTanda(String codigo, LocalDateTime inicio, int duracionMinutos, int cupos, TipoKart tipoKart) {
        return uow.ejecutar(() -> {
            if (sesiones.buscar(codigo).isPresent()) throw new ConflictoException("Ya existe la tanda " + codigo);
            if (!inicio.isAfter(LocalDateTime.now(reloj))) throw new ReglaNegocioException("La tanda debe programarse a futuro");
            if (tarifas.vigente(tipoKart, inicio.toLocalDate()).isEmpty()) {
                throw new ConflictoException("Registre primero una tarifa vigente para karts " + tipoKart);
            }
            SesionPista sesion = SesionPista.programar(codigo, inicio, duracionMinutos, cupos, tipoKart);
            sesiones.guardar(sesion);
            return sesion;
        });
    }

    public double actualizarUmbral(double horas) {
        if (horas <= 0) throw new ReglaNegocioException("El umbral debe ser positivo");
        uow.ejecutar(() -> parametros.actualizarUmbralHoras(horas));
        return horas;
    }

    public List<Tarifa> tarifas() {
        return tarifas.listar();
    }

    public double umbralActual() {
        return parametros.umbralHorasPreventivo();
    }
}
