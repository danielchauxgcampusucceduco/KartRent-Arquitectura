-- RN-11: nunca dos órdenes abiertas para el mismo kart. H2 no tiene índices parciales: se obtiene el mismo
-- efecto con una columna generada que es NULL cuando la orden está cerrada (los NULL no chocan en un índice único).
ALTER TABLE ordenes_mantenimiento ADD COLUMN kart_con_orden_abierta INT
    GENERATED ALWAYS AS (CASE WHEN estado <> 'CERRADA' THEN numero_kart END);
CREATE UNIQUE INDEX ux_orden_abierta_por_kart ON ordenes_mantenimiento (kart_con_orden_abierta);
