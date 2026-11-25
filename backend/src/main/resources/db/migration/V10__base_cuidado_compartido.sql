-- Base de cuidado compartido. No elimina ni fusiona vínculos anteriores.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.estados GROUP BY entidad, nombre HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Catalogo de estados ambiguo; revisar antes de migrar cuidado compartido';
    END IF;
    IF EXISTS (SELECT 1 FROM public.acceso_compartido WHERE propietario_id = invitado_id) THEN
        RAISE EXCEPTION 'Hay accesos compartidos a uno mismo; revisar antes de migrar';
    END IF;
    IF EXISTS (SELECT 1 FROM public.acceso_compartido WHERE activo
               GROUP BY propietario_id, invitado_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Hay accesos activos duplicados; revisar antes de migrar';
    END IF;
    IF EXISTS (SELECT 1 FROM public.invitacion WHERE btrim(email_invitado, E' \t\r\n') = ''
               OR (expira_en IS NOT NULL AND expira_en <= creado_en)) THEN
        RAISE EXCEPTION 'Hay invitaciones con email vacio o plazo invalido; revisar antes de migrar';
    END IF;
END $$;

INSERT INTO public.estados (entidad, nombre)
SELECT 'INVITACION', 'RECHAZADA'
WHERE NOT EXISTS (SELECT 1 FROM public.estados WHERE entidad = 'INVITACION' AND nombre = 'RECHAZADA');

UPDATE public.invitacion SET email_invitado = lower(btrim(email_invitado, E' \t\r\n'));
UPDATE public.invitacion SET expira_en = creado_en + INTERVAL '48 hours' WHERE expira_en IS NULL;
ALTER TABLE public.invitacion ALTER COLUMN expira_en SET NOT NULL;
ALTER TABLE public.invitacion ADD CONSTRAINT ck_invitacion_email_normalizado
    CHECK (email_invitado <> '' AND email_invitado = lower(btrim(email_invitado, E' \t\r\n')));
ALTER TABLE public.invitacion ADD CONSTRAINT ck_invitacion_plazo CHECK (expira_en > creado_en);
ALTER TABLE public.acceso_compartido ADD CONSTRAINT ck_acceso_compartido_otro_usuario
    CHECK (propietario_id <> invitado_id);

-- La unicidad anterior también bloqueaba un nuevo vínculo tras revocar el previo.
ALTER TABLE public.acceso_compartido DROP CONSTRAINT IF EXISTS acceso_compartido_propietario_invitado_key;
CREATE UNIQUE INDEX uq_acceso_compartido_activo ON public.acceso_compartido (propietario_id, invitado_id) WHERE activo;
CREATE INDEX ix_acceso_compartido_invitado_activo ON public.acceso_compartido (invitado_id, propietario_id) WHERE activo;
CREATE INDEX ix_invitacion_cupo ON public.invitacion (propietario_id, estado_id, expira_en);
CREATE INDEX ix_invitacion_destinatario ON public.invitacion (email_invitado, creado_en DESC);
