package co.kartrent.infraestructura.persistencia;

import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class ParametrosOperacionJdbc implements ParametrosOperacion {
    private final JdbcClient jdbc;

    public ParametrosOperacionJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private String valor(String clave) {
        return jdbc.sql("SELECT valor FROM parametros WHERE clave = :c").param("c", clave).query(String.class).single();
    }

    @Override public double umbralHorasPreventivo() { return Double.parseDouble(valor("umbral_horas_preventivo")); }
    @Override public Duration tiempoMinimoVuelta() { return Duration.ofSeconds(Long.parseLong(valor("tiempo_minimo_vuelta_s"))); }
    @Override public Duration retencionReserva() { return Duration.ofMinutes(Long.parseLong(valor("retencion_reserva_min"))); }
    @Override public double longitudPistaMetros() { return Double.parseDouble(valor("longitud_pista_m")); }
    @Override public String nitEmisor() { return valor("nit_emisor"); }

    @Override
    public void actualizarUmbralHoras(double horas) {
        jdbc.sql("UPDATE parametros SET valor = :v WHERE clave = 'umbral_horas_preventivo'")
                .param("v", String.valueOf(horas)).update();
    }
}
