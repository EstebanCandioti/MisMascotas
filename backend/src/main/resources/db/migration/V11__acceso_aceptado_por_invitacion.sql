-- Cada aceptación conserva el vínculo exacto que creó. No se infieren vínculos históricos.
ALTER TABLE public.invitacion ADD COLUMN acceso_id uuid;
ALTER TABLE public.invitacion ADD CONSTRAINT fk_invitacion_acceso
    FOREIGN KEY (acceso_id) REFERENCES public.acceso_compartido(id_acceso);
ALTER TABLE public.invitacion ADD CONSTRAINT uq_invitacion_acceso UNIQUE (acceso_id);
