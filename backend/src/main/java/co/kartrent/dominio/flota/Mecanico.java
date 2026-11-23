package co.kartrent.dominio.flota;

import co.kartrent.dominio.comun.Persona;

public class Mecanico extends Persona {
    private final String codigoEmpleado;
    private final String especialidad;

    public Mecanico(String documento, String nombre, String codigoEmpleado, String especialidad) {
        super(documento, nombre, null, null);
        this.codigoEmpleado = codigoEmpleado;
        this.especialidad = especialidad;
    }

    public boolean puedeAtender(TipoMantenimiento tipo) {
        return especialidad == null || especialidad.isBlank() || especialidad.equalsIgnoreCase("GENERAL")
                || especialidad.equalsIgnoreCase(tipo.name());
    }

    public String getCodigoEmpleado() { return codigoEmpleado; }
    public String getEspecialidad() { return especialidad; }
}
