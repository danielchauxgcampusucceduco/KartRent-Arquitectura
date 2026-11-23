package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioKarts;
import co.kartrent.dominio.flota.EstadoKart;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.TipoKart;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioKartsJdbc implements RepositorioKarts {
    private final JdbcClient jdbc;
    private static final RowMapper<Kart> MAPEO = (rs, i) -> Kart.reconstruir(TipoKart.valueOf(rs.getString("tipo")), rs.getInt("numero"),
            EstadoKart.valueOf(rs.getString("estado")), rs.getDouble("kilometraje"), rs.getDouble("horas_motor"),
            rs.getDouble("horas_desde_servicio"), rs.getString("codigo_transponder"));

    public RepositorioKartsJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(Kart k) {
        int filas = jdbc.sql("""
                        UPDATE karts SET estado = :estado, kilometraje = :km, horas_motor = :horas,
                               horas_desde_servicio = :servicio, codigo_transponder = :tr WHERE numero = :numero""")
                .param("estado", k.getEstado().name()).param("km", k.getKilometraje()).param("horas", k.getHorasMotor())
                .param("servicio", k.getHorasDesdeUltimoServicio()).param("tr", k.getCodigoTransponder())
                .param("numero", k.getNumeroKart()).update();
        if (filas == 0) {
            jdbc.sql("""
                            INSERT INTO karts (numero, tipo, estado, kilometraje, horas_motor, horas_desde_servicio, codigo_transponder)
                            VALUES (:numero, :tipo, :estado, :km, :horas, :servicio, :tr)""")
                    .param("numero", k.getNumeroKart()).param("tipo", k.tipo().name()).param("estado", k.getEstado().name())
                    .param("km", k.getKilometraje()).param("horas", k.getHorasMotor())
                    .param("servicio", k.getHorasDesdeUltimoServicio()).param("tr", k.getCodigoTransponder()).update();
        }
    }

    @Override
    public Optional<Kart> buscar(int numero) {
        return jdbc.sql("SELECT * FROM karts WHERE numero = :n").param("n", numero).query(MAPEO).optional();
    }

    @Override
    public List<Kart> listar() {
        return jdbc.sql("SELECT * FROM karts ORDER BY numero").query(MAPEO).list();
    }
}
