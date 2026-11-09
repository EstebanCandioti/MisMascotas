-- Exclusivo de QA. El lanzador valida proyecto, servicios, base y volumen.
-- Las credenciales ficticias están documentadas en docs/testing/cuentas_qa.md.
-- No ejecutar contra desarrollo/producción. No modifica la estructura.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL TIME ZONE 'America/Argentina/Buenos_Aires';
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

DO $$
BEGIN
    IF current_setting('mismascotas.entorno', true) IS DISTINCT FROM 'qa-web' THEN
        RAISE EXCEPTION 'Carga rechazada: falta la habilitación del lanzador QA';
    END IF;
    IF NOT pg_try_advisory_xact_lock(740132026) THEN
        RAISE EXCEPTION 'Ya hay otra restauración QA en curso';
    END IF;
END $$;

-- Se conserva el catálogo compartido de 02-seed.sql, pero se exige coincidencia
-- exacta. Un cambio estructural/de catálogo debe revisarse, no esconderse.
DO $$
BEGIN
    IF (SELECT count(*) FROM public.estados) <> 14 OR EXISTS (
        SELECT entidad, nombre FROM public.estados
        EXCEPT SELECT * FROM (VALUES
            ('RECORDATORIO', 'ACTIVO'), ('RECORDATORIO', 'INACTIVO'),
            ('RECORDATORIO', 'COMPLETADO'), ('INVITACION', 'PENDIENTE'),
            ('INVITACION', 'ACEPTADA'), ('INVITACION', 'EXPIRADA'),
            ('INVITACION', 'CANCELADA'), ('PAGO', 'APROBADO'),
            ('PAGO', 'PENDIENTE'), ('PAGO', 'RECHAZADO'),
            ('SUSCRIPCION', 'ACTIVA'), ('SUSCRIPCION', 'CANCELADA'),
            ('SUSCRIPCION', 'VENCIDA'), ('MASCOTA', 'ACTIVA')
        ) AS esperado(entidad, nombre)
    ) OR (SELECT count(DISTINCT (entidad, nombre)) FROM public.estados) <> 14 THEN
        RAISE EXCEPTION 'Catálogo de estados distinto del esperado; revisar 02-seed.sql';
    END IF;
END $$;

-- Lista explícita de las 16 tablas de negocio: sin CASCADE. Una nueva FK
-- externa bloquea el reset hasta revisar conscientemente su alcance.
TRUNCATE TABLE
    public.refresh_token, public.codigo_verificacion, public.log_auditoria,
    public.foto, public.album_mascota, public.album, public.badge,
    public.adjunto, public.evento_clinico, public.recordatorio,
    public.acceso_compartido, public.invitacion, public.pago,
    public.suscripcion, public.mascota, public.usuario
RESTART IDENTITY RESTRICT;

CREATE TEMP TABLE qa_contexto ON COMMIT DROP AS
SELECT 'qa-base-v1'::text AS version_seed, transaction_timestamp() AS t0,
       (transaction_timestamp() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date AS d0;

-- Prefijos: 1=usuario, 2=mascota, 3=evento clínico, 4=recordatorio.
-- El sufijo decimal coincide con QA-USR/MAS/ECL/REC-NNN.
INSERT INTO public.usuario
    (id_usuario, nombre, email, password_hash, es_premium, activo, creado_en)
SELECT ('10000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       nombre, email, hash, premium, true, c.t0
FROM (VALUES
    (1, 'QA Free vacío', 'qa.free.vacio@mismascotas.test', '$2a$10$eUeop71HVrJnNJyGqfz6SeFmlli2Cvo7OUTf.sl77fMVxLk9kL.BK', false),
    (2, 'QA Free habitual', 'qa.free.habitual@mismascotas.test', '$2a$10$Lbei/rVsSTlRHdYY5gvL4u4zouz9dzaBwKdSyR8NZ6DxhzWBlz/tO', false),
    (3, 'QA Free al límite', 'qa.free.limite@mismascotas.test', '$2a$10$xdUZ8Tfl7whP/W20//H.I.0tn/FBSfQCzw6SBTCJ2qP0BvLBtDkoq', false),
    (4, 'QA Premium', 'qa.premium@mismascotas.test', '$2a$10$.9Zya503HTeHhvg3.TxtheqrRBWngt3QG3ilN.1ELKLF4OZCwa1rG', true),
    (5, 'QA Propietario A', 'qa.propietario.a@mismascotas.test', '$2a$10$MxbYH1imdZYCUky9aKZCMe1rALZhQDhxSIwz2lPhKuea4McFoIVbW', false),
    (6, 'QA Propietario B', 'qa.propietario.b@mismascotas.test', '$2a$10$ED2ZQZFzNee2Qboa5T6BOu2oBNK9J6vwYRmWL0x9iDFd180yOQbhW', false)
) AS datos(n, nombre, email, hash, premium)
CROSS JOIN qa_contexto c;

INSERT INTO public.mascota
    (id_mascota, propietario_id, nombre, especie, raza, peso_actual,
     fecha_nacimiento, fecha_aproximada, edad_valor, edad_unidad, notas,
     creado_en, actualizado_en)
SELECT ('20000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       ('10000000-0000-4000-8000-' || lpad(dueno::text, 12, '0'))::uuid,
       nombre, especie, raza, peso,
       CASE WHEN meses_nacimiento IS NOT NULL
            THEN (c.d0 - make_interval(months => meses_nacimiento))::date END,
       aproximada, edad, unidad, notas, c.t0, c.t0
FROM (VALUES
    (1, 2, 'Toby', 'Perro', 'Mestizo', 18.50, 48, false, NULL::int, NULL::text, NULL::text),
    (2, 2, 'Nala', 'Gato', NULL, 3.20, NULL, true, 8, 'meses', NULL),
    (3, 3, 'Luna', 'Perro', 'Mestizo', 12.40, 24, false, NULL, NULL, NULL),
    (4, 3, 'Milo', 'Gato', NULL, 4.60, NULL, false, NULL, NULL, NULL),
    (5, 3, 'Chispa', 'Perro', NULL, 2.10, NULL, true, 10, 'semanas', 'Edad estimada en semanas. Ficha de prueba QA.'),
    (6, 3, 'Mora', 'Gato', NULL, 4.20, 144, false, NULL, NULL, NULL),
    (7, 3, 'Coco', 'Conejo', NULL, 1.80, 14, true, NULL, NULL, 'Fecha de nacimiento aproximada. Ficha de prueba QA.'),
    (8, 4, 'Bruno', 'Perro', 'Labrador', 32.75, 96, false, NULL, NULL, NULL),
    (9, 4, 'Ámbar', 'Gato', NULL, 4.35, NULL, true, 4, 'anios', 'Observación QA: nombre con tilde, signos (¿?) y número 123.'),
    (10, 4, 'Pipa', 'Hámster', NULL, 0.08, NULL, true, 8, 'semanas', NULL),
    (11, 4, 'Simón', 'Gato', NULL, 5.10, 18, false, NULL, NULL, NULL),
    (12, 4, 'Lola', 'Perro', 'Mestizo', 0.65, NULL, true, 21, 'dias', NULL),
    (13, 4, 'León', 'Perro', NULL, 8.90, NULL, true, 14, 'anios', 'Ficha senior QA. Mantener la autoría de sus registros.'),
    (14, 5, 'Toby', 'Perro', NULL, 25.20, 84, false, NULL, NULL, NULL),
    (15, 6, 'Nala', 'Gato', NULL, 4.90, NULL, true, 2, 'anios', NULL)
) AS datos(n, dueno, nombre, especie, raza, peso, meses_nacimiento, aproximada, edad, unidad, notas)
CROSS JOIN qa_contexto c;

-- Los siete eventos de Toby y Nala habituales conservan sus IDs de catálogo.
INSERT INTO public.evento_clinico
    (id_evento, mascota_id, registrado_por_id, tipo, fecha,
     valor_peso, nombre, dosis, motivo, diagnostico, observaciones, creado_en, actualizado_en)
SELECT ('30000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       m.id_mascota, m.propietario_id, tipo,
       ((c.d0 + dias) + time '12:00') AT TIME ZONE 'America/Argentina/Buenos_Aires',
       peso, nombre_evento, dosis, motivo, diagnostico, observaciones, c.t0, c.t0
FROM (VALUES
    (1, 1, -90, 'PESO', 19.00, NULL::text, NULL::text, NULL::text, NULL::text, NULL::text),
    (2, 1, -60, 'VACUNA', NULL, 'Vacuna de prueba QA', NULL, NULL, NULL, 'Aplicación ficticia para pruebas.'),
    (3, 1, -45, 'MEDICACION', NULL, 'Medicación de prueba QA', 'Dosis ficticia QA', NULL, NULL, 'Sin uso clínico.'),
    (4, 1, -30, 'PESO', 19.80, NULL, NULL, NULL, NULL, NULL),
    (5, 1, -14, 'CONSULTA_VETERINARIA', NULL, NULL, NULL, 'Control de prueba QA', 'Resultado ficticio QA', 'Registro de consulta para filtrado y exportación.'),
    (6, 1, -7, 'PESO', 18.50, NULL, NULL, NULL, NULL, NULL),
    (7, 2, -7, 'PESO', 3.20, NULL, NULL, NULL, NULL, NULL)
) AS datos(n, mascota, dias, tipo, peso, nombre_evento, dosis, motivo, diagnostico, observaciones)
JOIN public.mascota m ON m.id_mascota =
    ('20000000-0000-4000-8000-' || lpad(datos.mascota::text, 12, '0'))::uuid
CROSS JOIN qa_contexto c;

-- Eventos QA-ECL-008..020: un peso inicial para QA-MAS-003..015.
INSERT INTO public.evento_clinico
    (id_evento, mascota_id, registrado_por_id, tipo, fecha, valor_peso, creado_en, actualizado_en)
SELECT ('30000000-0000-4000-8000-' || lpad((n + 5)::text, 12, '0'))::uuid,
       m.id_mascota, m.propietario_id, 'PESO',
       ((c.d0 - 7) + time '12:00') AT TIME ZONE 'America/Argentina/Buenos_Aires',
       m.peso_actual, c.t0, c.t0
FROM generate_series(3, 15) AS serie(n)
JOIN public.mascota m ON m.id_mascota =
    ('20000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid
CROSS JOIN qa_contexto c;

INSERT INTO public.recordatorio
    (id_recordatorio, mascota_id, creado_por_id, titulo, tipo, fecha_hora_inicio,
     modalidad, intervalo_valor, intervalo_unidad, fecha_fin, estado_id,
     confirmado_por_id, confirmado_en, creado_en, actualizado_en)
SELECT ('40000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
       m.id_mascota, m.propietario_id, titulo, tipo,
       ((c.d0 + dias) + hora) AT TIME ZONE 'America/Argentina/Buenos_Aires',
       CASE WHEN intervalo IS NULL THEN 'unica' ELSE 'recurrente' END,
       intervalo, unidad,
       CASE WHEN dias_fin IS NOT NULL THEN ((c.d0 + dias_fin) + time '23:59')
           AT TIME ZONE 'America/Argentina/Buenos_Aires' END,
       e.id_estado,
       CASE WHEN n = 6 THEN m.propietario_id END,
       CASE WHEN n = 6 THEN ((c.d0 - 1) + time '09:10')
           AT TIME ZONE 'America/Argentina/Buenos_Aires' END,
       CASE WHEN n IN (4, 6) THEN ((c.d0 + dias - 1) + time '12:00')
           AT TIME ZONE 'America/Argentina/Buenos_Aires' ELSE c.t0 END,
       c.t0
FROM (VALUES
    (1, 1, 'Control QA programado', 'VISITA_VETERINARIO', 2, time '10:00', NULL::int, NULL::text, NULL::int),
    (2, 1, 'Aviso de medicación QA', 'MEDICACION', 1, time '08:00', 1, 'dias', 8),
    (3, 1, 'Pipeta QA pausada', 'PIPETA_ANTIPARASITARIO', 5, time '18:00', 1, 'meses', NULL),
    (4, 1, 'Visita QA anterior sin confirmar', 'VISITA_VETERINARIO', -2, time '10:00', NULL, NULL, NULL),
    (5, 3, 'Vacuna QA programada', 'VACUNA', 7, time '11:00', NULL, NULL, NULL),
    (6, 6, 'Control QA confirmado', 'VISITA_VETERINARIO', -1, time '09:00', NULL, NULL, NULL),
    (7, 7, 'Revisión general QA', 'OTRO', 3, time '16:00', NULL, NULL, NULL),
    (8, 8, 'Control periódico QA', 'VISITA_VETERINARIO', 1, time '10:00', 2, 'semanas', 45),
    (9, 9, 'Revisión anual QA', 'OTRO', 30, time '18:00', 1, 'anios', NULL),
    (10, 10, 'Aviso de medicación QA', 'MEDICACION', 1, time '08:00', NULL, NULL, NULL),
    (11, 11, 'Vacuna QA programada', 'VACUNA', 7, time '11:00', NULL, NULL, NULL),
    (12, 12, 'Cuidado QA de madrugada', 'OTRO', 2, time '00:05', NULL, NULL, NULL),
    (13, 13, 'Control QA nocturno', 'VISITA_VETERINARIO', 2, time '23:55', NULL, NULL, NULL),
    (14, 14, 'Control QA programado', 'VISITA_VETERINARIO', 2, time '10:00', NULL, NULL, NULL),
    (15, 15, 'Vacuna QA programada', 'VACUNA', 7, time '11:00', NULL, NULL, NULL)
) AS datos(n, mascota, titulo, tipo, dias, hora, intervalo, unidad, dias_fin)
JOIN public.mascota m ON m.id_mascota =
    ('20000000-0000-4000-8000-' || lpad(datos.mascota::text, 12, '0'))::uuid
JOIN public.estados e ON e.entidad = 'RECORDATORIO'
    AND e.nombre = CASE WHEN n = 3 THEN 'INACTIVO' ELSE 'ACTIVO' END
CROSS JOIN qa_contexto c;

-- Cualquier discrepancia impide COMMIT y revierte también el TRUNCATE.
DO $$
DECLARE
    distribucion jsonb;
    tabla text;
    cantidad bigint;
BEGIN
    IF (SELECT count(*) FROM public.usuario) <> 6
       OR (SELECT count(*) FROM public.usuario WHERE activo AND NOT es_premium) <> 5
       OR (SELECT count(*) FROM public.usuario WHERE activo AND es_premium) <> 1
       OR (SELECT count(*) FROM public.mascota) <> 15
       OR (SELECT count(*) FROM public.evento_clinico) <> 20
       OR (SELECT count(*) FROM public.evento_clinico WHERE tipo = 'PESO') <> 17
       OR (SELECT count(*) FROM public.recordatorio) <> 15 THEN
        RAISE EXCEPTION 'Cantidad de datos QA incorrecta';
    END IF;

    SELECT jsonb_agg(jsonb_build_array(
        (SELECT count(*) FROM public.mascota m WHERE m.propietario_id = u.id_usuario),
        (SELECT count(*) FROM public.evento_clinico ec WHERE ec.registrado_por_id = u.id_usuario),
        (SELECT count(*) FROM public.recordatorio r WHERE r.creado_por_id = u.id_usuario)
    ) ORDER BY u.id_usuario) INTO distribucion FROM public.usuario u;
    IF distribucion <> '[[0,0,0],[2,7,4],[5,5,3],[6,6,6],[1,1,1],[1,1,1]]'::jsonb THEN
        RAISE EXCEPTION 'Distribución por propietario incorrecta: %', distribucion;
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.mascota m LEFT JOIN LATERAL (
            SELECT ec.valor_peso FROM public.evento_clinico ec
            WHERE ec.mascota_id = m.id_mascota AND ec.tipo = 'PESO'
                AND ec.fecha_eliminacion IS NULL
            ORDER BY ec.fecha DESC, ec.id_evento DESC LIMIT 1
        ) ultimo ON true
        WHERE m.peso_actual IS DISTINCT FROM ultimo.valor_peso
            OR m.fecha_eliminacion IS NOT NULL OR m.foto_perfil IS NOT NULL
            OR (m.fecha_nacimiento IS NOT NULL AND m.edad_valor IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'Peso actual, edad o datos opcionales inconsistentes';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.evento_clinico ec JOIN public.mascota m ON m.id_mascota = ec.mascota_id
        WHERE ec.registrado_por_id <> m.propietario_id OR ec.fecha_eliminacion IS NOT NULL
    ) OR EXISTS (
        SELECT 1 FROM public.recordatorio r JOIN public.mascota m ON m.id_mascota = r.mascota_id
        WHERE r.creado_por_id <> m.propietario_id OR r.fecha_eliminacion IS NOT NULL
            OR (r.confirmado_por_id IS NOT NULL AND r.confirmado_por_id <> m.propietario_id)
    ) THEN
        RAISE EXCEPTION 'Autoría o eliminación lógica incorrecta';
    END IF;

    IF (SELECT count(*) FROM public.recordatorio WHERE modalidad = 'unica') <> 11
       OR (SELECT count(*) FROM public.recordatorio WHERE modalidad = 'recurrente') <> 4
       OR (SELECT count(*) FROM public.recordatorio r JOIN public.estados e ON e.id_estado = r.estado_id
           WHERE e.entidad = 'RECORDATORIO' AND e.nombre = 'ACTIVO') <> 14
       OR (SELECT count(*) FROM public.recordatorio WHERE confirmado_por_id IS NOT NULL AND confirmado_en IS NOT NULL) <> 1
       OR EXISTS (SELECT 1 FROM public.recordatorio WHERE
           (confirmado_por_id IS NULL) <> (confirmado_en IS NULL)
           OR (modalidad = 'unica' AND (intervalo_valor IS NOT NULL OR intervalo_unidad IS NOT NULL OR fecha_fin IS NOT NULL))
           OR dia_semana IS NOT NULL) THEN
        RAISE EXCEPTION 'Modalidades, estados o confirmaciones incorrectos';
    END IF;

    FOREACH tabla IN ARRAY ARRAY['codigo_verificacion','refresh_token','invitacion',
        'acceso_compartido','suscripcion','pago','adjunto','badge','album',
        'album_mascota','foto','log_auditoria'] LOOP
        EXECUTE format('SELECT count(*) FROM public.%I', tabla) INTO cantidad;
        IF cantidad <> 0 THEN RAISE EXCEPTION 'La tabla % debe empezar vacía', tabla; END IF;
    END LOOP;
END $$;

SELECT jsonb_build_object('version_seed', c.version_seed, 't0', c.t0, 'd0', c.d0,
    'zona_horaria', 'America/Argentina/Buenos_Aires', 'base', current_database(),
    'usuarios', (SELECT count(*) FROM public.usuario),
    'mascotas', (SELECT count(*) FROM public.mascota),
    'eventos_clinicos', (SELECT count(*) FROM public.evento_clinico),
    'recordatorios', (SELECT count(*) FROM public.recordatorio))
FROM qa_contexto c;
COMMIT;
