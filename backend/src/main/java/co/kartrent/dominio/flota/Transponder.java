package co.kartrent.dominio.flota;

import co.kartrent.dominio.comun.ReglaNegocioException;

/** Agregado al kart: se instala y puede trasladarse a otro. */
public class Transponder {
    private final String codigoTransponder;
    private int nivelBateria;
    private boolean activo;

    public Transponder(String codigoTransponder, int nivelBateria, boolean activo) {
        if (codigoTransponder == null || codigoTransponder.isBlank())
            throw new ReglaNegocioException("Código de transponder obligatorio");
        this.codigoTransponder = codigoTransponder;
        this.nivelBateria = nivelBateria;
        this.activo = activo;
    }

    public void vincular(Kart kart) {
        if (!activo) throw new ReglaNegocioException("Transponder " + codigoTransponder + " inactivo");
        kart.vincularTransponder(codigoTransponder);
    }

    public void desvincular(Kart kart) {
        kart.vincularTransponder(null);
    }

    public boolean requiereCarga(int umbral) { return nivelBateria < umbral; }
    public String getCodigoTransponder() { return codigoTransponder; }
    public int getNivelBateria() { return nivelBateria; }
    public boolean isActivo() { return activo; }
}
