-- RN-11: nunca dos órdenes abiertas para el mismo kart. Índice único parcial (PostgreSQL).
CREATE UNIQUE INDEX ux_orden_abierta_por_kart ON ordenes_mantenimiento (numero_kart) WHERE estado <> 'CERRADA';
