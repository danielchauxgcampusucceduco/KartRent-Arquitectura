package co.kartrent.aplicacion.flota;

import co.kartrent.aplicacion.puertos.Notificador;
import co.kartrent.aplicacion.puertos.ParametrosOperacion;
import co.kartrent.aplicacion.puertos.PublicadorEventos;
import co.kartrent.aplicacion.puertos.RepositorioKarts;
import co.kartrent.aplicacion.puertos.RepositorioOrdenes;
import co.kartrent.dominio.eventos.MantenimientoRequerido;
import co.kartrent.dominio.flota.Kart;
import co.kartrent.dominio.flota.OrdenMantenimiento;
import co.kartrent.dominio.pista.UsoKart;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HU-04 — interfaz RegistrarUso del componente Flota. Acumula horas y abre una orden preventiva
 * al alcanzar el umbral (RN-11: nunca dos órdenes abiertas para el mismo kart).
 * Se ejecuta dentro de la transacción de quien la llama (FinalizarTanda).
 */
public class RegistrarUso {
    private final RepositorioKarts karts;
    private final RepositorioOrdenes ordenes;
    private final ParametrosOperacion parametros;
    private final Notificador notificador;
    private final PublicadorEventos eventos;
    private final Clock reloj;

    public RegistrarUso(RepositorioKarts karts, RepositorioOrdenes ordenes, ParametrosOperacion parametros,
                        Notificador notificador, PublicadorEventos eventos, Clock reloj) {
        this.karts = karts;
        this.ordenes = ordenes;
        this.parametros = parametros;
        this.notificador = notificador;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    public List<OrdenMantenimiento> ejecutar(List<UsoKart> usos) {
        List<OrdenMantenimiento> nuevas = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        for (UsoKart uso : usos) {
            Kart kart = karts.buscar(uso.numeroKart()).orElseThrow();
            kart.registrarUso(uso.minutos(), uso.kilometros());
            if (kart.requiereMantenimiento(parametros.umbralHorasPreventivo())
                    && ordenes.abiertaPorKart(kart.getNumeroKart()).isEmpty()) {
                OrdenMantenimiento orden = OrdenMantenimiento.preventiva(ordenes.siguienteNumero(), kart, ahora);
                ordenes.guardar(orden);
                nuevas.add(orden);
                notificador.enviar("taller", "Kart " + kart.getNumeroKart() + " requiere mantenimiento preventivo ("
                        + orden.getNumero() + ")");
                eventos.publicar(new MantenimientoRequerido(kart.getNumeroKart(), orden.getNumero(), ahora));
            }
            karts.guardar(kart);
        }
        return nuevas;
    }
}
