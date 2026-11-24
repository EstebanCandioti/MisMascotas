-- Revisar y aplicar manualmente. No cambia ni elimina pagos/suscripciones existentes.

-- Impide que aparezcan duplicados entre el diagnostico y la creacion de indices.
LOCK TABLE public.suscripcion, public.pago IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.suscripcion
        WHERE id_suscripcion_mercadopago IS NOT NULL
        GROUP BY id_suscripcion_mercadopago HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Hay IDs remotos duplicados en suscripcion. Revisarlos sin modificar automaticamente los datos.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.pago
        WHERE id_transaccion_mercadopago IS NOT NULL
        GROUP BY id_transaccion_mercadopago HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Hay IDs remotos duplicados en pago. Revisarlos sin modificar automaticamente los datos.';
    END IF;
END $$;

-- UNIQUE permite varias reservas con NULL; nunca reescribe sus referencias.
CREATE UNIQUE INDEX IF NOT EXISTS uq_suscripcion_id_mercadopago
    ON public.suscripcion (id_suscripcion_mercadopago);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pago_id_transaccion_mercadopago
    ON public.pago (id_transaccion_mercadopago);

-- JPA usa estado_id. Conserva el dato textual historico y permite nuevos INSERT.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'pago' AND column_name = 'estado'
    ) THEN
        ALTER TABLE public.pago ALTER COLUMN estado DROP NOT NULL;
    END IF;
END $$;
