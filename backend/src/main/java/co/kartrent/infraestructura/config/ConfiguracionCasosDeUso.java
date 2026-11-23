package co.kartrent.infraestructura.config;

import co.kartrent.aplicacion.administracion.ConfigurarOperacion;
import co.kartrent.aplicacion.administracion.ReporteOcupacion;
import co.kartrent.aplicacion.flota.ConsultarFlota;
import co.kartrent.aplicacion.flota.GestionarOrdenes;
import co.kartrent.aplicacion.flota.RegistrarUso;
import co.kartrent.aplicacion.pista.ControlarSesion;
import co.kartrent.aplicacion.pista.RegistrarPaso;
import co.kartrent.aplicacion.puertos.*;
import co.kartrent.aplicacion.reservas.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Raíz de composición: el único lugar donde se conectan los casos de uso (núcleo) con los adaptadores
 * (infraestructura). Los casos de uso no llevan anotaciones de Spring — arquitectura limpia.
 */
@Configuration
public class ConfiguracionCasosDeUso {

    @Bean
    Clock reloj(@Value("${kartrent.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }

    @Bean
    RegistrarCliente registrarCliente(RepositorioClientes c, UnidadDeTrabajo uow, Clock reloj) {
        return new RegistrarCliente(c, uow, reloj);
    }

    @Bean
    ReservarTanda reservarTanda(RepositorioClientes c, RepositorioSesiones s, RepositorioTarifas t, RepositorioReservas r,
                                ParametrosOperacion p, UnidadDeTrabajo uow, Clock reloj) {
        return new ReservarTanda(c, s, t, r, p, uow, reloj);
    }

    @Bean
    PagarReserva pagarReserva(RepositorioReservas r, RepositorioClientes c, RepositorioFacturacion f, PasarelaPagos pasarela,
                              Notificador n, PublicadorEventos e, ParametrosOperacion p, UnidadDeTrabajo uow, Clock reloj) {
        return new PagarReserva(r, c, f, pasarela, n, e, p, uow, reloj);
    }

    @Bean
    ExpirarReservas expirarReservas(RepositorioReservas r, RepositorioSesiones s, UnidadDeTrabajo uow, Clock reloj) {
        return new ExpirarReservas(r, s, uow, reloj);
    }

    @Bean
    ConsultarReservas consultarReservas(RepositorioReservas r, RepositorioClientes c, RepositorioFacturacion f) {
        return new ConsultarReservas(r, c, f);
    }

    @Bean
    ConsultarFlota consultarFlota(RepositorioKarts k) {
        return new ConsultarFlota(k);
    }

    @Bean
    RegistrarUso registrarUso(RepositorioKarts k, RepositorioOrdenes o, ParametrosOperacion p, Notificador n,
                              PublicadorEventos e, Clock reloj) {
        return new RegistrarUso(k, o, p, n, e, reloj);
    }

    @Bean
    GestionarOrdenes gestionarOrdenes(RepositorioOrdenes o, RepositorioKarts k, RepositorioMecanicos m,
                                      UnidadDeTrabajo uow, Clock reloj) {
        return new GestionarOrdenes(o, k, m, uow, reloj);
    }

    @Bean
    ControlarSesion controlarSesion(RepositorioSesiones s, RepositorioReservas r, RepositorioClientes c, RepositorioKarts k,
                                    ConsultarFlota flota, RegistrarUso uso, PublicadorEventos e, ParametrosOperacion p,
                                    UnidadDeTrabajo uow, Clock reloj) {
        return new ControlarSesion(s, r, c, k, flota, uso, e, p, uow, reloj);
    }

    @Bean
    RegistrarPaso registrarPaso(RepositorioSesiones s, ParametrosOperacion p, PublicadorEventos e, UnidadDeTrabajo uow,
                                Clock reloj) {
        return new RegistrarPaso(s, p, e, uow, reloj);
    }

    @Bean
    ConfigurarOperacion configurarOperacion(RepositorioTarifas t, RepositorioSesiones s, ParametrosOperacion p,
                                            UnidadDeTrabajo uow) {
        return new ConfigurarOperacion(t, s, p, uow);
    }

    @Bean
    ReporteOcupacion reporteOcupacion(RepositorioSesiones s, RepositorioReservas r) {
        return new ReporteOcupacion(s, r);
    }
}
