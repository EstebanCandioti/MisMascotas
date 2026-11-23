-- Aplicacion manual e independiente en Supabase; no requiere seed ni otras tablas.

CREATE TABLE IF NOT EXISTS public.notificacion_mercado_pago (
    id_notificacion text NOT NULL,
    topico text NOT NULL,
    id_recurso text NOT NULL,
    recibida_en timestamp with time zone NOT NULL DEFAULT now(),
    procesada_en timestamp with time zone,
    CONSTRAINT notificacion_mercado_pago_pkey PRIMARY KEY (id_notificacion)
);

-- El recurso puede repetirse: la deduplicacion corresponde solo a id_notificacion.
CREATE INDEX IF NOT EXISTS idx_notificacion_mercado_pago_pendiente
    ON public.notificacion_mercado_pago (recibida_en, id_notificacion)
    WHERE procesada_en IS NULL;

-- Bandeja interna: los clientes de la Data API no gestionan notificaciones.
ALTER TABLE public.notificacion_mercado_pago ENABLE ROW LEVEL SECURITY;

-- PostgreSQL de testing puede no tener los roles de la Data API de Supabase.
DO $permisos$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL PRIVILEGES ON TABLE public.notificacion_mercado_pago FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL PRIVILEGES ON TABLE public.notificacion_mercado_pago FROM authenticated;
    END IF;
END;
$permisos$;
