BEGIN;

CREATE SEQUENCE public.estados_id_estado_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.estados (
    nombre character varying(255) NOT NULL,
    id_estado integer NOT NULL DEFAULT nextval('public.estados_id_estado_seq'::regclass),
    entidad character varying(255) NOT NULL,
    CONSTRAINT estados_pkey PRIMARY KEY (id_estado)
);

ALTER SEQUENCE public.estados_id_estado_seq OWNED BY public.estados.id_estado;

CREATE TABLE public.usuario (
    id_usuario uuid NOT NULL,
    es_premium boolean NOT NULL DEFAULT false,
    activo boolean NOT NULL DEFAULT true,
    email character varying(150) NOT NULL UNIQUE,
    nombre character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT usuario_pkey PRIMARY KEY (id_usuario)
);

CREATE TABLE public.codigo_verificacion (
    id_codigo uuid NOT NULL,
    usuario_id uuid NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    usado boolean NOT NULL DEFAULT false,
    codigo character varying(10) NOT NULL,
    CONSTRAINT codigo_verificacion_pkey PRIMARY KEY (id_codigo),
    CONSTRAINT codigo_verificacion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario)
);

CREATE TABLE public.suscripcion (
    estado_mercadopago varchar(30),
    actualizada_mercadopago_en timestamptz,
    id_suscripcion uuid NOT NULL,
    usuario_id uuid NOT NULL,
    fecha_vencimiento timestamp with time zone,
    proximo_recargo timestamp with time zone,
    fecha_cancelacion timestamp with time zone,
    id_suscripcion_mercadopago character varying(150) UNIQUE,
    tipo_plan character varying(50) NOT NULL,
    estado_id integer NOT NULL,
    fecha_inicio timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT suscripcion_pkey PRIMARY KEY (id_suscripcion),
    CONSTRAINT suscripcion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario),
    CONSTRAINT suscripcion_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);

CREATE INDEX idx_suscripcion_usuario_plan_estado
    ON public.suscripcion (usuario_id, tipo_plan, estado_id);

CREATE TABLE public.pago (
    id_factura_mercadopago varchar(150),
    id_pago uuid NOT NULL,
    suscripcion_id uuid NOT NULL,
    estado_id integer NOT NULL,
    id_transaccion_mercadopago character varying(150) UNIQUE,
    monto numeric(10,2) NOT NULL,
    fecha_pago timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT pago_pkey PRIMARY KEY (id_pago),
    CONSTRAINT pago_suscripcion_id_fkey FOREIGN KEY (suscripcion_id) REFERENCES public.suscripcion(id_suscripcion),
    CONSTRAINT pago_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);

CREATE TABLE public.mascota (
    id_mascota uuid NOT NULL,
    propietario_id uuid NOT NULL,
    fecha_nacimiento date,
    edad_valor integer,
    edad_unidad character varying(20) CHECK (edad_unidad::text = ANY (ARRAY['dias'::character varying::text, 'semanas'::character varying::text, 'meses'::character varying::text, 'anios'::character varying::text])),
    especie character varying(50) NOT NULL,
    nombre character varying(100) NOT NULL,
    peso_actual numeric(6,2),
    raza character varying(100),
    fecha_eliminacion timestamp with time zone,
    notas character varying(255),
    id_cliente uuid UNIQUE,
    fecha_aproximada boolean NOT NULL DEFAULT false,
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    foto_perfil character varying(255),
    CONSTRAINT mascota_pkey PRIMARY KEY (id_mascota),
    CONSTRAINT mascota_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario)
);

CREATE TABLE public.invitacion (
    id_invitacion uuid NOT NULL,
    propietario_id uuid NOT NULL,
    expira_en timestamp with time zone,
    estado_id integer NOT NULL,
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    email_invitado character varying(255) NOT NULL,
    perfil character varying(30) NOT NULL DEFAULT 'Cuidador'::character varying CHECK (perfil::text = ANY (ARRAY['Familia'::character varying::text, 'Cuidador'::character varying::text])),
    CONSTRAINT invitacion_pkey PRIMARY KEY (id_invitacion),
    CONSTRAINT invitacion_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario),
    CONSTRAINT invitacion_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);

CREATE TABLE public.acceso_compartido (
    id_acceso uuid NOT NULL,
    propietario_id uuid NOT NULL,
    invitado_id uuid NOT NULL,
    fecha_ingreso timestamp with time zone NOT NULL DEFAULT now(),
    activo boolean NOT NULL DEFAULT true,
    perfil character varying(30) NOT NULL DEFAULT 'Cuidador'::character varying CHECK (perfil::text = ANY (ARRAY['Familia'::character varying::text, 'Cuidador'::character varying::text])),
    CONSTRAINT acceso_compartido_pkey PRIMARY KEY (id_acceso),
    CONSTRAINT acceso_compartido_propietario_invitado_key UNIQUE (propietario_id, invitado_id),
    CONSTRAINT acceso_compartido_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario),
    CONSTRAINT acceso_compartido_invitado_id_fkey FOREIGN KEY (invitado_id) REFERENCES public.usuario(id_usuario)
);

CREATE TABLE public.evento_clinico (
    id_evento uuid NOT NULL,
    mascota_id uuid NOT NULL,
    registrado_por_id uuid NOT NULL,
    id_cliente uuid UNIQUE,
    diagnostico character varying(255),
    dosis character varying(100),
    motivo character varying(255),
    observaciones character varying(255),
    tipo character varying(50) NOT NULL,
    valor_peso numeric(6,2),
    nombre character varying(255),
    fecha_eliminacion timestamp with time zone,
    fecha timestamp with time zone NOT NULL DEFAULT now(),
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT evento_clinico_pkey PRIMARY KEY (id_evento),
    CONSTRAINT evento_clinico_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota),
    CONSTRAINT evento_clinico_registrado_por_id_fkey FOREIGN KEY (registrado_por_id) REFERENCES public.usuario(id_usuario)
);

CREATE TABLE public.adjunto (
    id_adjunto uuid NOT NULL,
    evento_id uuid NOT NULL,
    id_cliente uuid UNIQUE,
    formato character varying(20),
    url_archivo character varying(255) NOT NULL,
    fecha_eliminacion timestamp with time zone,
    subida_en timestamp with time zone NOT NULL DEFAULT now(),
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT adjunto_pkey PRIMARY KEY (id_adjunto),
    CONSTRAINT adjunto_evento_id_fkey FOREIGN KEY (evento_id) REFERENCES public.evento_clinico(id_evento)
);

CREATE TABLE public.recordatorio (
    id_recordatorio uuid NOT NULL,
    mascota_id uuid NOT NULL,
    creado_por_id uuid NOT NULL,
    fecha_hora_inicio timestamp with time zone NOT NULL,
    intervalo_valor integer,
    fecha_fin timestamp with time zone,
    confirmado_por_id uuid,
    confirmado_en timestamp with time zone,
    estado_id integer NOT NULL,
    id_cliente uuid UNIQUE,
    dia_semana character varying(20),
    intervalo_unidad character varying(20) CHECK (intervalo_unidad::text = ANY (ARRAY['dias'::character varying::text, 'semanas'::character varying::text, 'meses'::character varying::text, 'anios'::character varying::text])),
    tipo character varying(50) NOT NULL,
    titulo character varying(150) NOT NULL,
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    modalidad character varying(50) NOT NULL DEFAULT 'unica'::character varying CHECK (modalidad::text = ANY (ARRAY['unica'::character varying::text, 'recurrente'::character varying::text, 'dias_semana'::character varying::text])),
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    fecha_eliminacion timestamp with time zone,
    CONSTRAINT recordatorio_pkey PRIMARY KEY (id_recordatorio),
    CONSTRAINT recordatorio_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota),
    CONSTRAINT recordatorio_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuario(id_usuario),
    CONSTRAINT recordatorio_confirmado_por_id_fkey FOREIGN KEY (confirmado_por_id) REFERENCES public.usuario(id_usuario),
    CONSTRAINT recordatorio_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);

CREATE TABLE public.badge (
    id_badge uuid NOT NULL,
    mascota_id uuid NOT NULL,
    id_cliente uuid UNIQUE,
    emoji character varying(10),
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    fecha_eliminacion timestamp with time zone,
    texto character varying(100) NOT NULL,
    CONSTRAINT badge_pkey PRIMARY KEY (id_badge),
    CONSTRAINT badge_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota)
);

CREATE TABLE public.album (
    id_album uuid NOT NULL,
    creado_por_id uuid NOT NULL,
    descripcion text,
    id_cliente uuid UNIQUE,
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    nombre character varying(150) NOT NULL,
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    fecha_eliminacion timestamp with time zone,
    CONSTRAINT album_pkey PRIMARY KEY (id_album),
    CONSTRAINT album_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuario(id_usuario)
);

CREATE TABLE public.album_mascota (
    id_album_mascota uuid NOT NULL,
    id_cliente uuid UNIQUE,
    id_album uuid NOT NULL,
    id_mascota uuid NOT NULL,
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    fecha_eliminacion timestamp with time zone,
    CONSTRAINT album_mascota_pkey PRIMARY KEY (id_album_mascota),
    CONSTRAINT album_mascota_id_album_fkey FOREIGN KEY (id_album) REFERENCES public.album(id_album),
    CONSTRAINT album_mascota_id_mascota_fkey FOREIGN KEY (id_mascota) REFERENCES public.mascota(id_mascota)
);

CREATE TABLE public.foto (
    id_foto uuid NOT NULL,
    album_id uuid NOT NULL,
    url_archivo text NOT NULL,
    subida_por_id uuid,
    id_cliente uuid UNIQUE,
    formato character varying(20),
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    actualizado_en timestamp with time zone NOT NULL DEFAULT now(),
    fecha_eliminacion timestamp with time zone,
    CONSTRAINT foto_pkey PRIMARY KEY (id_foto),
    CONSTRAINT foto_album_id_fkey FOREIGN KEY (album_id) REFERENCES public.album(id_album),
    CONSTRAINT foto_subida_por_id_fkey FOREIGN KEY (subida_por_id) REFERENCES public.usuario(id_usuario)
);

CREATE TABLE public.evento_auditoria_pendiente (
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
    CONSTRAINT evento_auditoria_pendiente_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.usuario(id_usuario)
);

CREATE INDEX idx_evento_auditoria_pendiente_sin_procesar
    ON public.evento_auditoria_pendiente (fecha_evento, id_evento)
    WHERE procesado_en IS NULL;

CREATE TABLE public.log_auditoria (
    id_log uuid NOT NULL,
    actor_id uuid,
    valor_anterior text,
    valor_nuevo text,
    id_entidad uuid,
    fecha_hora timestamp with time zone NOT NULL DEFAULT now(),
    entidad_afectada character varying(50) NOT NULL,
    tipo_accion character varying(30) NOT NULL,
    evento_pendiente_id uuid UNIQUE,
    CONSTRAINT log_auditoria_pkey PRIMARY KEY (id_log),
    CONSTRAINT log_auditoria_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.usuario(id_usuario),
    CONSTRAINT log_auditoria_evento_pendiente_id_fkey FOREIGN KEY (evento_pendiente_id) REFERENCES public.evento_auditoria_pendiente(id_evento)
);

CREATE TABLE public.refresh_token (
    id_refresh_token uuid NOT NULL,
    usuario_id uuid NOT NULL,
    token_hash character varying(255) NOT NULL UNIQUE,
    expira_en timestamp with time zone NOT NULL,
    revocado_en timestamp with time zone,
    reemplazado_por uuid,
    creado_en timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT refresh_token_pkey PRIMARY KEY (id_refresh_token),
    CONSTRAINT refresh_token_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario),
    CONSTRAINT refresh_token_reemplazado_por_fkey FOREIGN KEY (reemplazado_por) REFERENCES public.refresh_token(id_refresh_token)
);

CREATE INDEX idx_refresh_token_usuario ON public.refresh_token USING btree (usuario_id);

CREATE UNIQUE INDEX uq_album_mascota_activa
    ON public.album_mascota USING btree (id_album, id_mascota)
    WHERE fecha_eliminacion IS NULL;

COMMIT;

-- Aplicacion manual e independiente en Supabase; no requiere seed ni otras tablas.
BEGIN;

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

COMMIT;

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
