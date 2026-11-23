package co.kartrent.dominio.comun;

import java.util.Objects;

/** Generalización de Cliente y Mecanico (vista lógica). */
public abstract class Persona {
    protected final String documento;
    protected String nombre;
    protected String correo;
    protected String telefono;

    protected Persona(String documento, String nombre, String correo, String telefono) {
        if (documento == null || documento.isBlank()) {
            throw new ReglaNegocioException("El documento es obligatorio");
        }
        this.documento = documento;
        this.nombre = Objects.requireNonNull(nombre, "nombre");
        this.correo = correo;
        this.telefono = telefono;
    }

    public String getNombreCompleto() { return nombre; }
    public String getDocumento() { return documento; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getTelefono() { return telefono; }
}
