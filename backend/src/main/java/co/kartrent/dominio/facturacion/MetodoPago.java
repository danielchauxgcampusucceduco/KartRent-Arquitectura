package co.kartrent.dominio.facturacion;

/** Medios de pago aceptados. SIMULAR_RECHAZO solo existe para demostrar el flujo de pago rechazado en la pasarela simulada. */
public enum MetodoPago {
    TARJETA,
    PSE,
    NEQUI,
    SIMULAR_RECHAZO
}
