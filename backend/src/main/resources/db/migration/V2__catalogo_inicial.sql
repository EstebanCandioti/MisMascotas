-- No corregir automáticamente un catálogo ambiguo.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.estados GROUP BY entidad, nombre HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'El catálogo contiene estados duplicados; revisar antes de migrar';
    END IF;
END $$;
INSERT INTO public.estados (entidad, nombre)
SELECT entidad, nombre FROM (VALUES
    ('RECORDATORIO', 'ACTIVO'),
    ('RECORDATORIO', 'INACTIVO'),
    ('RECORDATORIO', 'COMPLETADO'),
    ('INVITACION', 'PENDIENTE'),
    ('INVITACION', 'ACEPTADA'),
    ('INVITACION', 'EXPIRADA'),
    ('INVITACION', 'CANCELADA'),
    ('PAGO', 'APROBADO'),
    ('PAGO', 'PENDIENTE'),
    ('PAGO', 'RECHAZADO'),
    ('SUSCRIPCION', 'ACTIVA'),
    ('SUSCRIPCION', 'CANCELADA'),
    ('SUSCRIPCION', 'VENCIDA'),
    ('MASCOTA', 'ACTIVA')
) AS catalogo(entidad, nombre)
WHERE NOT EXISTS (SELECT 1 FROM public.estados e
    WHERE e.entidad = catalogo.entidad AND e.nombre = catalogo.nombre);
