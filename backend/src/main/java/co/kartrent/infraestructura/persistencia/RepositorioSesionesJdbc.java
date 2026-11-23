package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.RepositorioSesiones;
import co.kartrent.dominio.flota.TipoKart;
import co.kartrent.dominio.pista.AsignacionKart;
import co.kartrent.dominio.pista.EstadoSesion;
import co.kartrent.dominio.pista.SesionPista;
import co.kartrent.dominio.pista.VueltaTelemetria;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Persiste el agregado SesionPista con sus asignaciones y vueltas (composición en la vista lógica). */
@Repository
public class RepositorioSesionesJdbc implements RepositorioSesiones {
    private final JdbcClient jdbc;

    public RepositorioSesionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(SesionPista s) {
        int filas = jdbc.sql("""
                        UPDATE sesiones SET cupos_vendidos = :vendidos, estado = :estado, inicio_real = :ini, fin_real = :fin
                        WHERE codigo = :codigo""")
                .param("vendidos", s.getCuposVendidos()).param("estado", s.getEstado().name())
                .param("ini", s.getInicioReal()).param("fin", s.getFinReal()).param("codigo", s.getCodigo()).update();
        if (filas == 0) {
            jdbc.sql("""
                            INSERT INTO sesiones (codigo, hora_inicio, duracion_minutos, cupos_maximos, cupos_vendidos,
                                                  tipo_kart, estado, inicio_real, fin_real)
                            VALUES (:codigo, :hora, :dur, :max, :vendidos, :tipo, :estado, :ini, :fin)""")
                    .param("codigo", s.getCodigo()).param("hora", s.getHoraInicioProgramada())
                    .param("dur", s.getDuracionMinutos()).param("max", s.getCuposMaximos())
                    .param("vendidos", s.getCuposVendidos()).param("tipo", s.getTipoKart().name())
                    .param("estado", s.getEstado().name()).param("ini", s.getInicioReal()).param("fin", s.getFinReal())
                    .update();
        }
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
        jdbc.sql("DELETE FROM vueltas WHERE codigo_sesion = :s").param("s", s.getCodigo()).update();
        for (AsignacionKart a : s.getAsignaciones()) {
            for (VueltaTelemetria v : a.getVueltas()) {
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
        return jdbc.sql("SELECT codigo FROM sesiones WHERE codigo = :c").param("c", codigo).query(String.class).optional()
                .map(this::cargar);
    }

    @Override
    public List<SesionPista> listar() {
        return jdbc.sql("SELECT codigo FROM sesiones ORDER BY hora_inicio").query(String.class).list().stream()
                .map(this::cargar).toList();
    }

    @Override
    public Optional<SesionPista> enCursoConTransponder(String codigoTransponder) {
        return jdbc.sql("""
                        SELECT s.codigo FROM sesiones s JOIN asignaciones a ON a.codigo_sesion = s.codigo
                        WHERE s.estado = 'EN_CURSO' AND a.codigo_transponder = :tr""")
                .param("tr", codigoTransponder).query(String.class).list().stream().findFirst().map(this::cargar);
    }

    private SesionPista cargar(String codigo) {
        Map<Integer, List<VueltaTelemetria>> vueltas = jdbc.sql(
                        "SELECT * FROM vueltas WHERE codigo_sesion = :s ORDER BY numero_parrilla, numero_vuelta")
                .param("s", codigo)
                .query((rs, i) -> Map.entry(rs.getInt("numero_parrilla"), new VueltaTelemetria(rs.getInt("numero_vuelta"),
                        Jdbc.instante(rs, "marca_tiempo"), Duration.ofMillis(rs.getLong("tiempo_ms")))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        List<AsignacionKart> asignaciones = new ArrayList<>(jdbc.sql(
                        "SELECT * FROM asignaciones WHERE codigo_sesion = :s ORDER BY numero_parrilla")
                .param("s", codigo)
                .query((rs, i) -> new AsignacionKart(rs.getInt("numero_parrilla"), rs.getObject("codigo_reserva", UUID.class),
                        rs.getString("documento_piloto"), rs.getString("nombre_piloto"), rs.getInt("numero_kart"),
                        rs.getString("codigo_transponder"), Jdbc.instante(rs, "ultimo_paso"),
                        vueltas.getOrDefault(rs.getInt("numero_parrilla"), List.of())))
                .list());
        return jdbc.sql("SELECT * FROM sesiones WHERE codigo = :c").param("c", codigo)
                .query((rs, i) -> new SesionPista(rs.getString("codigo"), Jdbc.fechaHora(rs, "hora_inicio"),
                        rs.getInt("duracion_minutos"), rs.getInt("cupos_maximos"), rs.getInt("cupos_vendidos"),
                        TipoKart.valueOf(rs.getString("tipo_kart")), EstadoSesion.valueOf(rs.getString("estado")),
                        Jdbc.fechaHora(rs, "inicio_real"), Jdbc.fechaHora(rs, "fin_real"), asignaciones))
                .single();
    }
}
