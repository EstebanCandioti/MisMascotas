BEGIN;
ALTER TABLE public.suscripcion
    ADD COLUMN IF NOT EXISTS reemplazada boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS estado_mercadopago varchar(30),
    ADD COLUMN IF NOT EXISTS actualizada_mercadopago_en timestamptz;
ALTER TABLE public.pago ADD COLUMN IF NOT EXISTS id_factura_mercadopago varchar(150);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pago_factura_mercadopago
    ON public.pago (id_factura_mercadopago);
ALTER TABLE public.notificacion_mercado_pago
    ADD COLUMN IF NOT EXISTS estado_factura_mp text,
    ADD COLUMN IF NOT EXISTS estado_pago_mp text,
    ADD COLUMN IF NOT EXISTS detalle_pago_mp text,
    ADD COLUMN IF NOT EXISTS id_pago_mp text,
    ADD COLUMN IF NOT EXISTS intento_mp integer,
    ADD COLUMN IF NOT EXISTS fecha_debito_mp timestamptz,
    ADD COLUMN IF NOT EXISTS proximo_reintento_mp timestamptz;
CREATE INDEX IF NOT EXISTS idx_notificacion_mp_recurso
    ON public.notificacion_mercado_pago (topico, id_recurso);
COMMIT;
