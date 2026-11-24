BEGIN;

ALTER TABLE public.notificacion_mercado_pago
    ADD COLUMN IF NOT EXISTS intentos_temporales integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS primer_fallo_temporal_en timestamp with time zone,
    ADD COLUMN IF NOT EXISTS intentos_pendientes integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS primera_comprobacion_pendiente_en timestamp with time zone,
    ADD COLUMN IF NOT EXISTS proximo_procesamiento_en timestamp with time zone DEFAULT now(),
    ADD COLUMN IF NOT EXISTS ultimo_motivo text,
    ADD COLUMN IF NOT EXISTS estado_procesamiento text NOT NULL DEFAULT 'PENDIENTE';

-- Permite aplicar esta version sobre una base con la propuesta anterior.
ALTER TABLE public.notificacion_mercado_pago
    ALTER COLUMN proximo_procesamiento_en DROP NOT NULL,
    DROP COLUMN IF EXISTS reservado_hasta,
    DROP COLUMN IF EXISTS id_reserva;

UPDATE public.notificacion_mercado_pago
SET estado_procesamiento = 'COMPLETADA', proximo_procesamiento_en = NULL
WHERE procesada_en IS NOT NULL;

-- Una nueva ejecucion de la migracion conserva los reintentos ya programados.
UPDATE public.notificacion_mercado_pago
SET proximo_procesamiento_en = COALESCE(recibida_en, now())
WHERE procesada_en IS NULL
  AND estado_procesamiento = 'PENDIENTE'
  AND proximo_procesamiento_en IS NULL;

CREATE INDEX IF NOT EXISTS idx_notificacion_mercado_pago_planificada
    ON public.notificacion_mercado_pago (proximo_procesamiento_en, recibida_en)
    WHERE procesada_en IS NULL AND estado_procesamiento = 'PENDIENTE';

COMMIT;
