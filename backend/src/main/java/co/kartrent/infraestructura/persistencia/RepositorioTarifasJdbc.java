package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioTarifas;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.reservas.Tarifa;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioTarifasJdbc implements RepositorioTarifas {
    private final JdbcClient jdbc;
    private static final RowMapper<Tarifa> MAPEO = (rs, i) -> new Tarifa(TipoKart.valueOf(rs.getString("tipo_kart")),
            rs.getBigDecimal("valor_por_cupo"), Jdbc.fecha(rs, "vigente_desde"));

    public RepositorioTarifasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(Tarifa t) {
        jdbc.sql("INSERT INTO tarifas (tipo_kart, valor_por_cupo, vigente_desde) VALUES (:tipo, :valor, :desde)")
                .param("tipo", t.tipoKart().name()).param("valor", t.valorPorCupo()).param("desde", t.vigenteDesde()).update();
    }

    @Override
    public Optional<Tarifa> vigente(TipoKart tipoKart, LocalDate fecha) {
        return jdbc.sql("""
                        SELECT * FROM tarifas WHERE tipo_kart = :tipo AND vigente_desde <= :fecha
                        ORDER BY vigente_desde DESC, id DESC""")
                .param("tipo", tipoKart.name()).param("fecha", fecha).query(MAPEO).list().stream().findFirst();
    }

    @Override
    public List<Tarifa> listar() {
        return jdbc.sql("SELECT * FROM tarifas ORDER BY tipo_kart, vigente_desde DESC, id DESC").query(MAPEO).list();
    }
}
