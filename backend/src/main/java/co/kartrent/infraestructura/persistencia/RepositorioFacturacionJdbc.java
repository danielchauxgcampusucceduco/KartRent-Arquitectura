package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioFacturacion;
import co.kartrent.dominio.facturacion.Factura;
import co.kartrent.dominio.facturacion.MetodoPago;
import co.kartrent.dominio.facturacion.Pago;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RepositorioFacturacionJdbc implements RepositorioFacturacion {
    private final JdbcClient jdbc;
    private static final RowMapper<Pago> PAGO = (rs, i) -> new Pago(rs.getString("referencia"),
            Jdbc.uuid(rs, "codigo_reserva"), rs.getBigDecimal("monto"), MetodoPago.valueOf(rs.getString("metodo")), rs.getBoolean("aprobado"),
            Jdbc.fechaHora(rs, "fecha_pago"), rs.getBoolean("reembolsado"));
    private static final RowMapper<Factura> FACTURA = (rs, i) -> new Factura(rs.getString("numero"), rs.getString("cufe"),
            Jdbc.uuid(rs, "codigo_reserva"), Jdbc.fechaHora(rs, "fecha_emision"), rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("iva"), rs.getBigDecimal("total"));

    public RepositorioFacturacionJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardarPago(Pago p) {
        int filas = jdbc.sql("""
                        UPDATE pagos SET aprobado = :aprobado, fecha_pago = :fecha, metodo = :metodo,
                               reembolsado = :reembolsado
                        WHERE referencia = :ref""")
                .param("aprobado", p.aprobado()).param("fecha", p.fechaPago()).param("metodo", p.metodo().name())
                .param("reembolsado", p.reembolsado()).param("ref", p.referenciaPasarela()).update();
        if (filas == 0) {
            jdbc.sql("""
                            INSERT INTO pagos (referencia, codigo_reserva, monto, metodo, aprobado, fecha_pago, reembolsado)
                            VALUES (:ref, :reserva, :monto, :metodo, :aprobado, :fecha, :reembolsado)""")
                    .param("ref", p.referenciaPasarela()).param("reserva", p.codigoReserva()).param("monto", p.monto())
                    .param("metodo", p.metodo().name()).param("aprobado", p.aprobado()).param("fecha", p.fechaPago())
                    .param("reembolsado", p.reembolsado()).update();
        }
    }

    @Override
    public Optional<Pago> pagoPorReferencia(String referencia) {
        return jdbc.sql("SELECT * FROM pagos WHERE referencia = :r").param("r", referencia).query(PAGO).optional();
    }

    @Override
    public List<Pago> pagosDeReserva(UUID codigoReserva) {
        return jdbc.sql("SELECT * FROM pagos WHERE codigo_reserva = :r ORDER BY fecha_pago")
                .param("r", codigoReserva).query(PAGO).list();
    }

    @Override
    public void guardarFactura(Factura f) {
        jdbc.sql("""
                        INSERT INTO facturas (numero, cufe, codigo_reserva, fecha_emision, subtotal, iva, total)
                        VALUES (:numero, :cufe, :reserva, :fecha, :subtotal, :iva, :total)""")
                .param("numero", f.numero()).param("cufe", f.cufe()).param("reserva", f.codigoReserva())
                .param("fecha", f.fechaEmision()).param("subtotal", f.subtotal()).param("iva", f.iva())
                .param("total", f.total()).update();
    }

    @Override
    public Optional<Factura> facturaDeReserva(UUID codigoReserva) {
        return jdbc.sql("SELECT * FROM facturas WHERE codigo_reserva = :r").param("r", codigoReserva).query(FACTURA).optional();
    }

    @Override
    public String siguienteNumeroFactura() {
        Long n = jdbc.sql("SELECT nextval('seq_factura')").query(Long.class).single();
        return String.format("KRFE-%06d", n);
    }
}
