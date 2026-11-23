package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioReservas;
import co.kartrent.dominio.reservas.EstadoReserva;
import co.kartrent.dominio.reservas.Reserva;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RepositorioReservasJdbc implements RepositorioReservas {
    private final JdbcClient jdbc;
    private static final RowMapper<Reserva> MAPEO = (rs, i) -> new Reserva(Jdbc.uuid(rs, "codigo"),
            rs.getString("documento_cliente"), rs.getString("codigo_sesion"), Jdbc.fechaHora(rs, "fecha_creacion"),
            rs.getInt("cantidad_cupos"), rs.getBigDecimal("valor_total"), EstadoReserva.valueOf(rs.getString("estado")),
            Jdbc.fechaHora(rs, "vence_retencion"));

    public RepositorioReservasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(Reserva r) {
        int filas = jdbc.sql("UPDATE reservas SET estado = :estado WHERE codigo = :codigo")
                .param("estado", r.getEstado().name()).param("codigo", r.getCodigo()).update();
        if (filas == 0) {
            jdbc.sql("""
                            INSERT INTO reservas (codigo, documento_cliente, codigo_sesion, fecha_creacion, cantidad_cupos,
                                                  valor_total, estado, vence_retencion)
                            VALUES (:codigo, :doc, :sesion, :fecha, :cupos, :valor, :estado, :vence)""")
                    .param("codigo", r.getCodigo()).param("doc", r.getDocumentoCliente())
                    .param("sesion", r.getCodigoSesion()).param("fecha", r.getFechaCreacion())
                    .param("cupos", r.getCantidadCupos()).param("valor", r.getValorTotal())
                    .param("estado", r.getEstado().name()).param("vence", r.getVenceRetencion()).update();
        }
    }

    @Override
    public Optional<Reserva> buscar(UUID codigo) {
        return jdbc.sql("SELECT * FROM reservas WHERE codigo = :codigo").param("codigo", codigo).query(MAPEO).optional();
    }

    @Override
    public List<Reserva> pendientesVencidas(LocalDateTime ahora) {
        return jdbc.sql("SELECT * FROM reservas WHERE estado = 'PENDIENTE_PAGO' AND vence_retencion < :ahora")
                .param("ahora", ahora).query(MAPEO).list();
    }

    @Override
    public List<Reserva> confirmadasPorSesion(String codigoSesion) {
        return jdbc.sql("SELECT * FROM reservas WHERE codigo_sesion = :s AND estado = 'CONFIRMADA' ORDER BY fecha_creacion")
                .param("s", codigoSesion).query(MAPEO).list();
    }

    @Override
    public List<Reserva> porSesion(String codigoSesion) {
        return jdbc.sql("SELECT * FROM reservas WHERE codigo_sesion = :s ORDER BY fecha_creacion")
                .param("s", codigoSesion).query(MAPEO).list();
    }

    @Override
    public List<Reserva> porCliente(String documento) {
        return jdbc.sql("SELECT * FROM reservas WHERE documento_cliente = :d ORDER BY fecha_creacion DESC")
                .param("d", documento).query(MAPEO).list();
    }
}
