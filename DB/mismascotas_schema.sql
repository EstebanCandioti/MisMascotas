-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.estados (
  id_estado integer NOT NULL DEFAULT nextval('estados_id_estado_seq'::regclass),
  entidad character varying NOT NULL,
  nombre character varying NOT NULL,
  CONSTRAINT estados_pkey PRIMARY KEY (id_estado)
);
CREATE TABLE public.usuario (
  id_usuario uuid NOT NULL,
  nombre character varying NOT NULL,
  email character varying NOT NULL UNIQUE,
  password_hash character varying NOT NULL,
  es_premium boolean NOT NULL DEFAULT false,
  activo boolean NOT NULL DEFAULT true,
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT usuario_pkey PRIMARY KEY (id_usuario)
);
CREATE TABLE public.codigo_verificacion (
  id_codigo uuid NOT NULL,
  usuario_id uuid NOT NULL,
  codigo character varying NOT NULL,
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  expira_en timestamp with time zone NOT NULL,
  usado boolean NOT NULL DEFAULT false,
  CONSTRAINT codigo_verificacion_pkey PRIMARY KEY (id_codigo),
  CONSTRAINT codigo_verificacion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario)
);
CREATE TABLE public.suscripcion (
  id_suscripcion uuid NOT NULL,
  usuario_id uuid NOT NULL,
  tipo_plan character varying NOT NULL,
  fecha_inicio timestamp with time zone NOT NULL DEFAULT now(),
  fecha_vencimiento timestamp with time zone,
  proximo_recargo timestamp with time zone,
  fecha_cancelacion timestamp with time zone,
  id_suscripcion_mercadopago character varying,
  estado_id integer NOT NULL,
  estado character varying NOT NULL,
  CONSTRAINT suscripcion_pkey PRIMARY KEY (id_suscripcion),
  CONSTRAINT suscripcion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario),
  CONSTRAINT suscripcion_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);
CREATE TABLE public.pago (
  id_pago uuid NOT NULL,
  suscripcion_id uuid NOT NULL,
  monto numeric NOT NULL,
  fecha_pago timestamp with time zone NOT NULL DEFAULT now(),
  id_transaccion_mercadopago character varying,
  estado_id integer NOT NULL,
  estado character varying NOT NULL,
  CONSTRAINT pago_pkey PRIMARY KEY (id_pago),
  CONSTRAINT pago_suscripcion_id_fkey FOREIGN KEY (suscripcion_id) REFERENCES public.suscripcion(id_suscripcion),
  CONSTRAINT pago_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);
CREATE TABLE public.mascota (
  id_mascota uuid NOT NULL,
  propietario_id uuid NOT NULL,
  nombre character varying NOT NULL,
  especie character varying NOT NULL,
  raza character varying,
  fecha_nacimiento date,
  fecha_aproximada boolean NOT NULL DEFAULT false,
  edad_valor integer,
  edad_unidad character varying CHECK (edad_unidad::text = ANY (ARRAY['dias'::character varying::text, 'semanas'::character varying::text, 'meses'::character varying::text, 'anios'::character varying::text])),
  foto_perfil character varying,
  peso_actual numeric,
  notas character varying,
  fecha_eliminacion timestamp with time zone,
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  id_cliente uuid UNIQUE,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  estado_id integer NOT NULL,
  CONSTRAINT mascota_pkey PRIMARY KEY (id_mascota),
  CONSTRAINT mascota_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario),
  CONSTRAINT mascota_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);
CREATE TABLE public.invitacion (
  id_invitacion uuid NOT NULL,
  propietario_id uuid NOT NULL,
  email_invitado character varying NOT NULL,
  perfil character varying NOT NULL DEFAULT 'Cuidador'::character varying CHECK (perfil::text = ANY (ARRAY['Familia'::character varying::text, 'Cuidador'::character varying::text])),
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  expira_en timestamp with time zone,
  estado_id integer NOT NULL,
  CONSTRAINT invitacion_pkey PRIMARY KEY (id_invitacion),
  CONSTRAINT invitacion_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario),
  CONSTRAINT invitacion_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);
CREATE TABLE public.acceso_compartido (
  id_acceso uuid NOT NULL,
  propietario_id uuid NOT NULL,
  invitado_id uuid NOT NULL,
  perfil character varying NOT NULL DEFAULT 'Cuidador'::character varying CHECK (perfil::text = ANY (ARRAY['Familia'::character varying::text, 'Cuidador'::character varying::text])),
  fecha_ingreso timestamp with time zone NOT NULL DEFAULT now(),
  activo boolean NOT NULL DEFAULT true,
  CONSTRAINT acceso_compartido_pkey PRIMARY KEY (id_acceso),
  CONSTRAINT acceso_compartido_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario),
  CONSTRAINT acceso_compartido_invitado_id_fkey FOREIGN KEY (invitado_id) REFERENCES public.usuario(id_usuario)
);
CREATE TABLE public.evento_clinico (
  id_evento uuid NOT NULL,
  mascota_id uuid NOT NULL,
  registrado_por_id uuid NOT NULL,
  tipo character varying NOT NULL,
  nombre character varying,
  fecha timestamp with time zone NOT NULL DEFAULT now(),
  dosis character varying,
  motivo character varying,
  diagnostico character varying,
  valor_peso numeric,
  observaciones character varying,
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  id_cliente uuid UNIQUE,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  fecha_eliminacion timestamp without time zone,
  CONSTRAINT evento_clinico_pkey PRIMARY KEY (id_evento),
  CONSTRAINT evento_clinico_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota),
  CONSTRAINT evento_clinico_registrado_por_id_fkey FOREIGN KEY (registrado_por_id) REFERENCES public.usuario(id_usuario)
);
CREATE TABLE public.adjunto (
  id_adjunto uuid NOT NULL,
  evento_id uuid NOT NULL,
  url_archivo character varying NOT NULL,
  formato character varying,
  subida_en timestamp with time zone NOT NULL DEFAULT now(),
  id_cliente uuid UNIQUE,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  fecha_eliminacion timestamp without time zone,
  CONSTRAINT adjunto_pkey PRIMARY KEY (id_adjunto),
  CONSTRAINT adjunto_evento_id_fkey FOREIGN KEY (evento_id) REFERENCES public.evento_clinico(id_evento)
);
CREATE TABLE public.recordatorio (
  id_recordatorio uuid NOT NULL,
  mascota_id uuid NOT NULL,
  creado_por_id uuid NOT NULL,
  titulo character varying NOT NULL,
  tipo character varying NOT NULL,
  fecha_hora_inicio timestamp with time zone NOT NULL,
  modalidad character varying NOT NULL DEFAULT 'unica'::character varying CHECK (modalidad::text = ANY (ARRAY['unica'::character varying::text, 'recurrente'::character varying::text, 'dias_semana'::character varying::text])),
  intervalo_valor integer,
  intervalo_unidad character varying CHECK (intervalo_unidad::text = ANY (ARRAY['dias'::character varying::text, 'semanas'::character varying::text, 'meses'::character varying::text, 'anios'::character varying::text])),
  dia_semana character varying,
  fecha_fin timestamp with time zone,
  confirmado_por_id uuid,
  confirmado_en timestamp with time zone,
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  estado_id integer NOT NULL,
  id_cliente uuid UNIQUE,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  fecha_eliminacion timestamp without time zone,
  CONSTRAINT recordatorio_pkey PRIMARY KEY (id_recordatorio),
  CONSTRAINT recordatorio_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota),
  CONSTRAINT recordatorio_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuario(id_usuario),
  CONSTRAINT recordatorio_confirmado_por_id_fkey FOREIGN KEY (confirmado_por_id) REFERENCES public.usuario(id_usuario),
  CONSTRAINT recordatorio_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado)
);
CREATE TABLE public.badge (
  id_badge uuid NOT NULL,
  mascota_id uuid NOT NULL,
  texto character varying NOT NULL,
  emoji character varying,
  id_cliente uuid UNIQUE,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  fecha_eliminacion timestamp without time zone,
  CONSTRAINT badge_pkey PRIMARY KEY (id_badge),
  CONSTRAINT badge_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota)
);
CREATE TABLE public.album (
  id_album uuid NOT NULL,
  creado_por_id uuid NOT NULL,
  nombre character varying NOT NULL,
  descripcion text,
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  id_cliente uuid UNIQUE,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  fecha_eliminacion timestamp without time zone,
  CONSTRAINT album_pkey PRIMARY KEY (id_album),
  CONSTRAINT album_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuario(id_usuario)
);
CREATE TABLE public.album_mascota (
  id_album uuid NOT NULL,
  id_mascota uuid NOT NULL,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  fecha_eliminacion timestamp without time zone,
  CONSTRAINT album_mascota_pkey PRIMARY KEY (id_album, id_mascota),
  CONSTRAINT album_mascota_id_album_fkey FOREIGN KEY (id_album) REFERENCES public.album(id_album),
  CONSTRAINT album_mascota_id_mascota_fkey FOREIGN KEY (id_mascota) REFERENCES public.mascota(id_mascota)
);
CREATE TABLE public.foto (
  id_foto uuid NOT NULL,
  album_id uuid NOT NULL,
  url_archivo text NOT NULL,
  formato character varying,
  subida_por_id uuid,
  creado_en timestamp with time zone NOT NULL DEFAULT now(),
  id_cliente uuid UNIQUE,
  actualizado_en timestamp without time zone NOT NULL DEFAULT now(),
  fecha_eliminacion timestamp without time zone,
  CONSTRAINT foto_pkey PRIMARY KEY (id_foto),
  CONSTRAINT foto_album_id_fkey FOREIGN KEY (album_id) REFERENCES public.album(id_album),
  CONSTRAINT foto_subida_por_id_fkey FOREIGN KEY (subida_por_id) REFERENCES public.usuario(id_usuario)
);
CREATE TABLE public.log_auditoria (
  id_log uuid NOT NULL,
  actor_id uuid,
  tipo_accion character varying NOT NULL,
  entidad_afectada character varying NOT NULL,
  id_entidad uuid,
  valor_anterior character varying,
  valor_nuevo character varying,
  fecha_hora timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT log_auditoria_pkey PRIMARY KEY (id_log),
  CONSTRAINT log_auditoria_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.usuario(id_usuario)
);