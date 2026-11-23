BEGIN;

INSERT INTO public.estados (entidad, nombre)
SELECT 'SUSCRIPCION', 'PENDIENTE'
WHERE NOT EXISTS (
    SELECT 1
    FROM public.estados
    WHERE entidad = 'SUSCRIPCION'
      AND nombre = 'PENDIENTE'
);

-- La entidad JPA usa estado_id. Esta columna heredada impide insertar si conserva NOT NULL.
ALTER TABLE public.suscripcion DROP COLUMN IF EXISTS estado;

CREATE INDEX IF NOT EXISTS idx_suscripcion_usuario_plan_estado
    ON public.suscripcion (usuario_id, tipo_plan, estado_id);

COMMIT;
