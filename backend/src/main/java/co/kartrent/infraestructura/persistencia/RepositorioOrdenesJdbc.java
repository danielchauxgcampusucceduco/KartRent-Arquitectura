package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioOrdenes;
import co.kartrent.dominio.flota.EstadoOrden;
import co.kartrent.dominio.flota.OrdenMantenimiento;
import co.kartrent.dominio.flota.TipoMantenimiento;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioOrdenesJdbc implements RepositorioOrdenes {
    private final JdbcClient jdbc;
    private static final RowMapper<OrdenMantenimiento> MAPEO = (rs, i) -> new OrdenMantenimiento(rs.getString("numero"),
            rs.getInt("numero_kart"), TipoMantenimiento.valueOf(rs.getString("tipo")),
            EstadoOrden.valueOf(rs.getString("estado")), Jdbc.fechaHora(rs, "fecha_apertura"),
            Jdbc.fechaHora(rs, "fecha_cierre"), rs.getDouble("horas_motor_al_abrir"), rs.getString("codigo_mecanico"),
            rs.getString("observaciones"));

    public RepositorioOrdenesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(OrdenMantenimiento o) {
        int filas = jdbc.sql("""
                        UPDATE ordenes_mantenimiento SET estado = :estado, fecha_cierre = :cierre,
                               codigo_mecanico = :mecanico, observaciones = :obs WHERE numero = :numero""")
                .param("estado", o.getEstado().name()).param("cierre", o.getFechaCierre())
                .param("mecanico", o.getCodigoMecanico()).param("obs", o.getObservaciones())
                .param("numero", o.getNumero()).update();
        if (filas == 0) {
            jdbc.sql("""
                            INSERT INTO ordenes_mantenimiento (numero, numero_kart, tipo, estado, fecha_apertura, fecha_cierre,
                                                               horas_motor_al_abrir, codigo_mecanico, observaciones)
                            VALUES (:numero, :kart, :tipo, :estado, :apertura, :cierre, :horas, :mecanico, :obs)""")
                    .param("numero", o.getNumero()).param("kart", o.getNumeroKart()).param("tipo", o.getTipo().name())
                    .param("estado", o.getEstado().name()).param("apertura", o.getFechaApertura())
                    .param("cierre", o.getFechaCierre()).param("horas", o.getHorasMotorAlAbrir())
                    .param("mecanico", o.getCodigoMecanico()).param("obs", o.getObservaciones()).update();
        }
    }

    @Override
    public Optional<OrdenMantenimiento> buscar(String numero) {
        return jdbc.sql("SELECT * FROM ordenes_mantenimiento WHERE numero = :n").param("n", numero).query(MAPEO).optional();
    }

    @Override
    public Optional<OrdenMantenimiento> abiertaPorKart(int numeroKart) {
        return jdbc.sql("SELECT * FROM ordenes_mantenimiento WHERE numero_kart = :k AND estado <> 'CERRADA'")
                .param("k", numeroKart).query(MAPEO).list().stream().findFirst();
    }

    @Override
    public List<OrdenMantenimiento> listar() {
        return jdbc.sql("SELECT * FROM ordenes_mantenimiento ORDER BY fecha_apertura DESC").query(MAPEO).list();
    }

    @Override
    public String siguienteNumero() {
        Long n = jdbc.sql("SELECT nextval('seq_orden')").query(Long.class).single();
        return String.format("OM-%04d", n);
    }
}
