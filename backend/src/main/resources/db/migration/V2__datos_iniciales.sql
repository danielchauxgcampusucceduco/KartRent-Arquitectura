-- Datos de ejemplo del kartódromo (valores a validar con el cliente).

INSERT INTO parametros (clave, valor) VALUES
    ('umbral_horas_preventivo', '20'),
    ('tiempo_minimo_vuelta_s', '25'),
    ('retencion_reserva_min', '10'),
    ('longitud_pista_m', '850'),
    ('nit_emisor', '900123456-7');

INSERT INTO tarifas (tipo_kart, valor_por_cupo, vigente_desde) VALUES
    ('ADULTO', 45000, DATE '2026-01-01'),
    ('INFANTIL', 30000, DATE '2026-01-01');

INSERT INTO transponders (codigo, nivel_bateria, activo) VALUES
    ('TR-101', 95, TRUE), ('TR-102', 90, TRUE), ('TR-103', 88, TRUE), ('TR-104', 76, TRUE),
    ('TR-105', 99, TRUE), ('TR-106', 81, TRUE), ('TR-107', 64, TRUE), ('TR-108', 92, TRUE),
    ('TR-109', 87, TRUE), ('TR-110', 93, TRUE), ('TR-111', 70, TRUE), ('TR-112', 85, TRUE);

INSERT INTO karts (numero, tipo, estado, kilometraje, horas_motor, horas_desde_servicio, codigo_transponder) VALUES
    (1, 'ADULTO', 'DISPONIBLE', 1250.4, 310.5, 4.2, 'TR-101'),
    (2, 'ADULTO', 'DISPONIBLE', 1180.0, 298.0, 11.7, 'TR-102'),
    (3, 'ADULTO', 'DISPONIBLE', 990.7, 250.2, 19.9, 'TR-103'),
    (4, 'ADULTO', 'DISPONIBLE', 1402.3, 344.8, 2.5, 'TR-104'),
    (5, 'ADULTO', 'DISPONIBLE', 875.1, 221.0, 8.0, 'TR-105'),
    (6, 'ADULTO', 'DISPONIBLE', 640.9, 160.4, 15.3, 'TR-106'),
    (7, 'ADULTO', 'EN_MANTENIMIENTO', 1530.2, 380.0, 20.1, 'TR-107'),
    (8, 'ADULTO', 'DISPONIBLE', 455.5, 112.6, 6.6, 'TR-108'),
    (9, 'INFANTIL', 'DISPONIBLE', 320.2, 90.3, 3.1, 'TR-109'),
    (10, 'INFANTIL', 'DISPONIBLE', 298.8, 85.0, 9.4, 'TR-110'),
    (11, 'INFANTIL', 'DISPONIBLE', 410.0, 101.7, 12.2, 'TR-111'),
    (12, 'INFANTIL', 'DISPONIBLE', 150.6, 40.2, 1.0, 'TR-112');

INSERT INTO mecanicos (codigo_empleado, documento, nombre, especialidad) VALUES
    ('M-01', '98000001', 'Carlos Rosero', 'GENERAL'),
    ('M-02', '98000002', 'Luisa Bastidas', 'PREVENTIVO');

INSERT INTO ordenes_mantenimiento (numero, numero_kart, tipo, estado, fecha_apertura, horas_motor_al_abrir)
VALUES ('OM-0001', 7, 'PREVENTIVO', 'ABIERTA', TIMESTAMP '2026-11-20 19:10:00', 380.0);

ALTER SEQUENCE seq_orden RESTART WITH 2;

INSERT INTO clientes (documento, nombre, correo, telefono, fecha_nacimiento, exoneracion_firmada, fecha_exoneracion) VALUES
    ('1085000001', 'Daniel Chaux', 'daniel@example.com', '3000000001', DATE '2002-04-15', TRUE, DATE '2026-11-01'),
    ('1085000002', 'Ana Ordóñez', 'ana@example.com', '3000000002', DATE '1998-09-30', TRUE, DATE '2026-11-01'),
    ('1085000003', 'Felipe Muñoz', 'felipe@example.com', '3000000003', DATE '2001-01-20', TRUE, DATE '2026-11-02'),
    ('1085000004', 'Sofía Erazo', 'sofia@example.com', '3000000004', DATE '2013-06-12', TRUE, DATE '2026-11-02');
