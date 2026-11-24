-- El estado del pago se obtiene exclusivamente por estado_id.
ALTER TABLE public.pago DROP COLUMN IF EXISTS estado;
