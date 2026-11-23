package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioClientes;
import co.kartrent.dominio.reservas.Cliente;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioClientesJdbc implements RepositorioClientes {
    private final JdbcClient jdbc;
    private static final RowMapper<Cliente> MAPEO = (rs, i) -> new Cliente(rs.getString("documento"),
            rs.getString("nombre"), rs.getString("correo"), rs.getString("telefono"), Jdbc.fecha(rs, "fecha_nacimiento"),
            rs.getBoolean("exoneracion_firmada"), Jdbc.fecha(rs, "fecha_exoneracion"));

    public RepositorioClientesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(Cliente c) {
        int filas = jdbc.sql("""
                        UPDATE clientes SET nombre = :nombre, correo = :correo, telefono = :telefono,
                               exoneracion_firmada = :exo, fecha_exoneracion = :fexo WHERE documento = :doc""")
                .param("nombre", c.getNombre()).param("correo", c.getCorreo()).param("telefono", c.getTelefono())
                .param("exo", c.isExoneracionFirmada()).param("fexo", c.getFechaExoneracion())
                .param("doc", c.getDocumento()).update();
        if (filas == 0) {
            jdbc.sql("""
                            INSERT INTO clientes (documento, nombre, correo, telefono, fecha_nacimiento,
                                                  exoneracion_firmada, fecha_exoneracion)
                            VALUES (:doc, :nombre, :correo, :telefono, :nac, :exo, :fexo)""")
                    .param("doc", c.getDocumento()).param("nombre", c.getNombre()).param("correo", c.getCorreo())
                    .param("telefono", c.getTelefono()).param("nac", c.getFechaNacimiento())
                    .param("exo", c.isExoneracionFirmada()).param("fexo", c.getFechaExoneracion()).update();
        }
    }

    @Override
    public Optional<Cliente> buscar(String documento) {
        return jdbc.sql("SELECT * FROM clientes WHERE documento = :doc").param("doc", documento).query(MAPEO).optional();
    }

    @Override
    public List<Cliente> listar() {
        return jdbc.sql("SELECT * FROM clientes ORDER BY nombre").query(MAPEO).list();
    }
}
