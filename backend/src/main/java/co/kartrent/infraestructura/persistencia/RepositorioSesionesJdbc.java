package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.dominio.comun.ConflictoConcurrenciaException;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.EstadoSesion;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.pista.VueltaTelemetria;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persiste el agregado SesionPista con sus asignaciones y vueltas (composición en la vista lógica).
 * Bloqueo optimista: el UPDATE exige la versión leída; si otra transacción guardó antes, no actualiza ninguna
 * fila y se lanza {@link ConflictoConcurrenciaException} (409). Las vueltas son de solo-anexar: se insertan únicamente
 * las nuevas, y las consultas cargan todas las tandas en tres sentencias (sin N+1).
 */
@Repository
public class RepositorioSesionesJdbc implements RepositorioSesiones {
    private final JdbcClient jdbc;

    public RepositorioSesionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(SesionPista s) {
        try {
            guardarCabecera(s);
        } catch (TransientDataAccessException e) {
            // El motor detectó una escritura concurrente sobre la misma fila: es el mismo conflicto de versión.
            throw new ConflictoConcurrenciaException("La tanda " + s.getCodigo()
                    + " fue modificada por otra operación; vuelve a intentarlo");
        }
        guardarAsignaciones(s);
        guardarVueltasNuevas(s);
    }

    private void guardarCabecera(SesionPista s) {
        int filas = jdbc.sql("""
                        UPDATE sesiones SET cupos_vendidos = :vendidos, estado = :estado, inicio_real = :ini,
                               fin_real = :fin, version = version + 1
                        WHERE codigo = :codigo AND version = :version""")
                .param("vendidos", s.getCuposVendidos()).param("estado", s.getEstado().name())
                .param("ini", s.getInicioReal()).param("fin", s.getFinReal()).param("codigo", s.getCodigo())
                .param("version", s.getVersion()).update();
        if (filas == 1) {
            s.registrarVersionPersistida(s.getVersion() + 1);
            return;
        }
        long existe = jdbc.sql("SELECT COUNT(*) FROM sesiones WHERE codigo = :c").param("c", s.getCodigo())
                .query(Long.class).single();
        if (existe > 0) {
            throw new ConflictoConcurrenciaException("La tanda " + s.getCodigo()
                    + " fue modificada por otra operación; vuelve a intentarlo");
        }
        jdbc.sql("""
                        INSERT INTO sesiones (codigo, hora_inicio, duracion_minutos, cupos_maximos, cupos_vendidos,
                                              tipo_kart, estado, inicio_real, fin_real, version)
                        VALUES (:codigo, :hora, :dur, :max, :vendidos, :tipo, :estado, :ini, :fin, 0)""")
                .param("codigo", s.getCodigo()).param("hora", s.getHoraInicioProgramada())
                .param("dur", s.getDuracionMinutos()).param("max", s.getCuposMaximos())
                .param("vendidos", s.getCuposVendidos()).param("tipo", s.getTipoKart().name())
                .param("estado", s.getEstado().name()).param("ini", s.getInicioReal()).param("fin", s.getFinReal())
                .update();
        s.registrarVersionPersistida(0);
    }

    private void guardarAsignaciones(SesionPista s) {
        eliminarAsignacionesRetiradas(s);
        for (AsignacionKart a : s.getAsignaciones()) {
            int act = jdbc.sql("UPDATE asignaciones SET ultimo_paso = :ultimo WHERE codigo_sesion = :s AND numero_parrilla = :n")
                    .param("ultimo", Jdbc.timestamp(a.getUltimoPaso())).param("s", s.getCodigo())
                    .param("n", a.getNumeroParrilla()).update();
            if (act == 0) {
                jdbc.sql("""
                                INSERT INTO asignaciones (codigo_sesion, numero_parrilla, codigo_reserva, documento_piloto,
                                                          nombre_piloto, numero_kart, codigo_transponder, ultimo_paso)
                                VALUES (:s, :n, :reserva, :doc, :nombre, :kart, :tr, :ultimo)""")
                        .param("s", s.getCodigo()).param("n", a.getNumeroParrilla()).param("reserva", a.getCodigoReserva())
                        .param("doc", a.getDocumentoPiloto()).param("nombre", a.getNombrePiloto())
                        .param("kart", a.getNumeroKart()).param("tr", a.getCodigoTransponder())
                        .param("ultimo", Jdbc.timestamp(a.getUltimoPaso())).update();
            }
        }
    }

    /** Una asignación retirada de la parrilla (o toda la parrilla de una tanda cancelada) deja de existir en la base. */
    private void eliminarAsignacionesRetiradas(SesionPista s) {
        // Con la tanda en curso o terminada la parrilla ya no cambia: se evita una consulta por cada paso por meta.
        if (s.getEstado() == EstadoSesion.EN_CURSO || s.getEstado() == EstadoSesion.FINALIZADA) return;
        List<Integer> persistidas = jdbc.sql("SELECT numero_parrilla FROM asignaciones WHERE codigo_sesion = :s")
                .param("s", s.getCodigo()).query(Integer.class).list();
        List<Integer> vigentes = s.getAsignaciones().stream().map(AsignacionKart::getNumeroParrilla).toList();
        for (Integer numero : persistidas) {
            if (vigentes.contains(numero)) continue;
            jdbc.sql("DELETE FROM vueltas WHERE codigo_sesion = :s AND numero_parrilla = :n")
                    .param("s", s.getCodigo()).param("n", numero).update();
            jdbc.sql("DELETE FROM asignaciones WHERE codigo_sesion = :s AND numero_parrilla = :n")
                    .param("s", s.getCodigo()).param("n", numero).update();
        }
    }

    /** Las vueltas solo se anexan: se inserta lo que supera la última vuelta ya guardada de cada asignación. */
    private void guardarVueltasNuevas(SesionPista s) {
        Map<Integer, Integer> guardadas = new HashMap<>();
        jdbc.sql("SELECT numero_parrilla, MAX(numero_vuelta) AS ultima FROM vueltas WHERE codigo_sesion = :s GROUP BY numero_parrilla")
                .param("s", s.getCodigo())
                .query((rs, i) -> Map.entry(rs.getInt("numero_parrilla"), rs.getInt("ultima")))
                .list().forEach(e -> guardadas.put(e.getKey(), e.getValue()));
        for (AsignacionKart a : s.getAsignaciones()) {
            int ultimaGuardada = guardadas.getOrDefault(a.getNumeroParrilla(), 0);
            for (VueltaTelemetria v : a.getVueltas()) {
                if (v.numeroVuelta() <= ultimaGuardada) continue;
                jdbc.sql("""
                                INSERT INTO vueltas (codigo_sesion, numero_parrilla, numero_vuelta, marca_tiempo, tiempo_ms)
                                VALUES (:s, :n, :v, :marca, :ms)""")
                        .param("s", s.getCodigo()).param("n", a.getNumeroParrilla()).param("v", v.numeroVuelta())
                        .param("marca", Jdbc.timestamp(v.marcaTiempo())).param("ms", v.milisegundos()).update();
            }
        }
    }

    @Override
    public Optional<SesionPista> buscar(String codigo) {
        return cargar(codigo).stream().findFirst();
    }

    @Override
    public List<SesionPista> listar() {
        return cargar(null);
    }

    @Override
    public Optional<SesionPista> enCursoConTransponder(String codigoTransponder) {
        return jdbc.sql("""
                        SELECT s.codigo FROM sesiones s JOIN asignaciones a ON a.codigo_sesion = s.codigo
                        WHERE s.estado = 'EN_CURSO' AND a.codigo_transponder = :tr""")
                .param("tr", codigoTransponder).query(String.class).list().stream().findFirst()
                .flatMap(this::buscar);
    }

    private record FilaVuelta(String sesion, int parrilla, VueltaTelemetria vuelta) {
    }

    private record FilaAsignacion(String sesion, AsignacionKart base, int parrilla) {
    }

    /** Carga la tanda indicada, o todas si el código es nulo, con tres consultas en total. */
    private List<SesionPista> cargar(String codigo) {
        String where = codigo == null ? "" : " WHERE codigo = :c";
        String whereSesion = codigo == null ? "" : " WHERE codigo_sesion = :c";

        var consultaVueltas = jdbc.sql("SELECT * FROM vueltas" + whereSesion + " ORDER BY numero_parrilla, numero_vuelta");
        var consultaAsignaciones = jdbc.sql("SELECT * FROM asignaciones" + whereSesion + " ORDER BY numero_parrilla");
        var consultaSesiones = jdbc.sql("SELECT * FROM sesiones" + where + " ORDER BY hora_inicio");
        if (codigo != null) {
            consultaVueltas = consultaVueltas.param("c", codigo);
            consultaAsignaciones = consultaAsignaciones.param("c", codigo);
            consultaSesiones = consultaSesiones.param("c", codigo);
        }

        Map<String, Map<Integer, List<VueltaTelemetria>>> vueltas = new HashMap<>();
        for (FilaVuelta f : consultaVueltas.query((rs, i) -> new FilaVuelta(rs.getString("codigo_sesion"),
                rs.getInt("numero_parrilla"), new VueltaTelemetria(rs.getInt("numero_vuelta"),
                Jdbc.instante(rs, "marca_tiempo"), Duration.ofMillis(rs.getLong("tiempo_ms")))))
                .list()) {
            vueltas.computeIfAbsent(f.sesion(), k -> new HashMap<>())
                    .computeIfAbsent(f.parrilla(), k -> new ArrayList<>()).add(f.vuelta());
        }

        Map<String, List<AsignacionKart>> asignaciones = new HashMap<>();
        for (FilaAsignacion f : consultaAsignaciones.query((rs, i) -> new FilaAsignacion(rs.getString("codigo_sesion"),
                new AsignacionKart(rs.getInt("numero_parrilla"), rs.getObject("codigo_reserva", UUID.class),
                        rs.getString("documento_piloto"), rs.getString("nombre_piloto"), rs.getInt("numero_kart"),
                        rs.getString("codigo_transponder"), Jdbc.instante(rs, "ultimo_paso"), List.of()),
                rs.getInt("numero_parrilla"))).list()) {
            List<VueltaTelemetria> propias = vueltas.getOrDefault(f.sesion(), Map.of()).getOrDefault(f.parrilla(), List.of());
            AsignacionKart a = f.base();
            asignaciones.computeIfAbsent(f.sesion(), k -> new ArrayList<>()).add(new AsignacionKart(a.getNumeroParrilla(),
                    a.getCodigoReserva(), a.getDocumentoPiloto(), a.getNombrePiloto(), a.getNumeroKart(),
                    a.getCodigoTransponder(), a.getUltimoPaso(), propias));
        }

        return consultaSesiones.query((rs, i) -> new SesionPista(rs.getString("codigo"),
                Jdbc.fechaHora(rs, "hora_inicio"), rs.getInt("duracion_minutos"), rs.getInt("cupos_maximos"),
                rs.getInt("cupos_vendidos"), TipoKart.valueOf(rs.getString("tipo_kart")),
                EstadoSesion.valueOf(rs.getString("estado")), Jdbc.fechaHora(rs, "inicio_real"),
                Jdbc.fechaHora(rs, "fin_real"), asignaciones.getOrDefault(rs.getString("codigo"), List.of()),
                rs.getLong("version"))).list();
    }
}
