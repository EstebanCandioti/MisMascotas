-- La programación existente y sus confirmaciones quedan en recordatorio.
ALTER TABLE public.recordatorio
    ALTER COLUMN dia_semana TYPE varchar(64),
    ADD COLUMN ultima_fecha_generada date;

CREATE TABLE public.ocurrencia_recordatorio (
    id_ocurrencia uuid PRIMARY KEY,
    recordatorio_id uuid NOT NULL REFERENCES public.recordatorio (id_recordatorio),
    fecha_programada timestamp with time zone NOT NULL,
    confirmado_por_id uuid REFERENCES public.usuario (id_usuario),
    confirmado_en timestamp with time zone,
    CONSTRAINT uq_ocurrencia_recordatorio_fecha UNIQUE (recordatorio_id, fecha_programada),
    CONSTRAINT ck_ocurrencia_confirmacion_completa
        CHECK ((confirmado_por_id IS NULL) = (confirmado_en IS NULL))
);

CREATE INDEX ix_ocurrencia_recordatorio_fecha
    ON public.ocurrencia_recordatorio (fecha_programada);

CREATE INDEX ix_recordatorio_tanda_pendiente
    ON public.recordatorio (ultima_fecha_generada, id_recordatorio)
    WHERE fecha_fin IS NULL AND fecha_eliminacion IS NULL
        AND modalidad IN ('recurrente', 'dias_semana') AND ultima_fecha_generada IS NOT NULL;
