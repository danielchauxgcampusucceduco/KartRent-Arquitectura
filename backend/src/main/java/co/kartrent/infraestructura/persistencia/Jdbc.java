package co.kartrent.infraestructura.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Utilidades de mapeo comunes a PostgreSQL y H2. */
final class Jdbc {
    private Jdbc() {
    }

    static LocalDateTime fechaHora(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, LocalDateTime.class);
    }

    static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, LocalDate.class);
    }

    static UUID uuid(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, UUID.class);
    }

    static Instant instante(ResultSet rs, String columna) throws SQLException {
        Timestamp ts = rs.getTimestamp(columna);
        return ts == null ? null : ts.toInstant();
    }

    static Timestamp timestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
