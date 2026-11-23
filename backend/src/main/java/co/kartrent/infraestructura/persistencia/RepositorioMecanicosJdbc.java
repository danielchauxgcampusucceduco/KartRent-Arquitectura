package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioMecanicos;
import co.kartrent.dominio.flota.Mecanico;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioMecanicosJdbc implements RepositorioMecanicos {
    private final JdbcClient jdbc;
    private static final RowMapper<Mecanico> MAPEO = (rs, i) -> new Mecanico(rs.getString("documento"),
            rs.getString("nombre"), rs.getString("codigo_empleado"), rs.getString("especialidad"));

    public RepositorioMecanicosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Mecanico> buscar(String codigo) {
        return jdbc.sql("SELECT * FROM mecanicos WHERE codigo_empleado = :c").param("c", codigo).query(MAPEO).optional();
    }

    @Override
    public List<Mecanico> listar() {
        return jdbc.sql("SELECT * FROM mecanicos ORDER BY codigo_empleado").query(MAPEO).list();
    }
}
