package co.kartrent.dominio.reservas;

import co.kartrent.dominio.comun.Persona;
import co.kartrent.dominio.comun.ReglaNegocioException;

import java.time.LocalDate;
import java.time.Period;

public class Cliente extends Persona {
    private final LocalDate fechaNacimiento;
    private boolean exoneracionFirmada;
    private LocalDate fechaExoneracion;

    public Cliente(String documento, String nombre, String correo, String telefono, LocalDate fechaNacimiento,
                   boolean exoneracionFirmada, LocalDate fechaExoneracion) {
        super(documento, nombre, correo, telefono);
        if (fechaNacimiento == null) throw new ReglaNegocioException("La fecha de nacimiento es obligatoria");
        this.fechaNacimiento = fechaNacimiento;
        this.exoneracionFirmada = exoneracionFirmada;
        this.fechaExoneracion = fechaExoneracion;
    }

    public void firmarExoneracion(LocalDate fecha) {
        this.exoneracionFirmada = true;
        this.fechaExoneracion = fecha;
    }

    public int calcularEdad(LocalDate hoy) {
        return Period.between(fechaNacimiento, hoy).getYears();
    }

    public boolean puedeConducir(int edadMinima, LocalDate hoy) {
        return exoneracionFirmada && calcularEdad(hoy) >= edadMinima;
    }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public boolean isExoneracionFirmada() { return exoneracionFirmada; }
    public LocalDate getFechaExoneracion() { return fechaExoneracion; }
}
