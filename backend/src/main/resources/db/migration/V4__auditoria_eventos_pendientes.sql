CREATE TABLE IF NOT EXISTS public.evento_auditoria_pendiente (
    id_evento uuid NOT NULL,
    actor_id uuid,
    entidad_afectada character varying(50) NOT NULL,
    tipo_accion character varying(30) NOT NULL,
    id_entidad uuid,
    valor_anterior text,
    valor_nuevo text,
    fecha_evento timestamp with time zone NOT NULL DEFAULT now(),
    procesado_en timestamp with time zone,
    intentos_procesamiento integer NOT NULL DEFAULT 0,
    ultimo_intento_en timestamp with time zone,
    ultimo_error text,
    CONSTRAINT evento_auditoria_pendiente_pkey PRIMARY KEY (id_evento),
    CONSTRAINT evento_auditoria_pendiente_actor_id_fkey
        FOREIGN KEY (actor_id) REFERENCES public.usuario(id_usuario)
);

CREATE INDEX IF NOT EXISTS idx_evento_auditoria_pendiente_sin_procesar
    ON public.evento_auditoria_pendiente (fecha_evento, id_evento)
    WHERE procesado_en IS NULL;

ALTER TABLE public.log_auditoria
    ADD COLUMN IF NOT EXISTS evento_pendiente_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS uq_log_auditoria_evento_pendiente
    ON public.log_auditoria (evento_pendiente_id)
    WHERE evento_pendiente_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'log_auditoria_evento_pendiente_id_fkey'
          AND conrelid = 'public.log_auditoria'::regclass
    ) THEN
        ALTER TABLE public.log_auditoria
            ADD CONSTRAINT log_auditoria_evento_pendiente_id_fkey
            FOREIGN KEY (evento_pendiente_id)
            REFERENCES public.evento_auditoria_pendiente(id_evento);
    END IF;
END $$;
