-- Bloqueo optimista del agregado SesionPista: cada UPDATE exige la versión leída y la incrementa.
ALTER TABLE sesiones ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- La parrilla no admite el mismo kart ni el mismo piloto dos veces en una tanda (RN-04), también ante carreras.
ALTER TABLE asignaciones ADD CONSTRAINT uq_asignacion_kart UNIQUE (codigo_sesion, numero_kart);
ALTER TABLE asignaciones ADD CONSTRAINT uq_asignacion_piloto UNIQUE (codigo_sesion, documento_piloto);
