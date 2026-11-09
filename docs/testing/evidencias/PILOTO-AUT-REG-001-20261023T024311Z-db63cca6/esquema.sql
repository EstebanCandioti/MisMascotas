--
-- PostgreSQL database dump
--

\restrict 9859WzAjAPVzI7jyNGMLh4Xrj5SInH7zbckM9XKwkV1pfDWpNUffkYYPGojC3TW

-- Dumped from database version 17.6 (Debian 17.6-2.pgdg13+1)
-- Dumped by pg_dump version 17.6 (Debian 17.6-2.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: acceso_compartido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acceso_compartido (
    id_acceso uuid NOT NULL,
    propietario_id uuid NOT NULL,
    invitado_id uuid NOT NULL,
    fecha_ingreso timestamp with time zone DEFAULT now() NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    perfil character varying(30) DEFAULT 'Cuidador'::character varying NOT NULL,
    CONSTRAINT acceso_compartido_perfil_check CHECK (((perfil)::text = ANY (ARRAY[('Familia'::character varying)::text, ('Cuidador'::character varying)::text])))
);


--
-- Name: adjunto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.adjunto (
    id_adjunto uuid NOT NULL,
    evento_id uuid NOT NULL,
    id_cliente uuid,
    formato character varying(20),
    url_archivo character varying(255) NOT NULL,
    fecha_eliminacion timestamp with time zone,
    subida_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: album; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.album (
    id_album uuid NOT NULL,
    creado_por_id uuid NOT NULL,
    descripcion text,
    id_cliente uuid,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    nombre character varying(150) NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    fecha_eliminacion timestamp with time zone
);


--
-- Name: album_mascota; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.album_mascota (
    id_album_mascota uuid NOT NULL,
    id_cliente uuid,
    id_album uuid NOT NULL,
    id_mascota uuid NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    fecha_eliminacion timestamp with time zone
);


--
-- Name: badge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.badge (
    id_badge uuid NOT NULL,
    mascota_id uuid NOT NULL,
    id_cliente uuid,
    emoji character varying(10),
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    fecha_eliminacion timestamp with time zone,
    texto character varying(100) NOT NULL
);


--
-- Name: codigo_verificacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.codigo_verificacion (
    id_codigo uuid NOT NULL,
    usuario_id uuid NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    usado boolean DEFAULT false NOT NULL,
    codigo character varying(10) NOT NULL
);


--
-- Name: estados; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.estados (
    nombre character varying(255) NOT NULL,
    id_estado integer NOT NULL,
    entidad character varying(255) NOT NULL
);


--
-- Name: estados_id_estado_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.estados_id_estado_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: estados_id_estado_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.estados_id_estado_seq OWNED BY public.estados.id_estado;


--
-- Name: evento_clinico; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_clinico (
    id_evento uuid NOT NULL,
    mascota_id uuid NOT NULL,
    registrado_por_id uuid NOT NULL,
    id_cliente uuid,
    diagnostico character varying(255),
    dosis character varying(100),
    motivo character varying(255),
    observaciones character varying(255),
    tipo character varying(50) NOT NULL,
    valor_peso numeric(6,2),
    nombre character varying(255),
    fecha_eliminacion timestamp with time zone,
    fecha timestamp with time zone DEFAULT now() NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: foto; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.foto (
    id_foto uuid NOT NULL,
    album_id uuid NOT NULL,
    url_archivo text NOT NULL,
    subida_por_id uuid,
    id_cliente uuid,
    formato character varying(20),
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    fecha_eliminacion timestamp with time zone
);


--
-- Name: invitacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invitacion (
    id_invitacion uuid NOT NULL,
    propietario_id uuid NOT NULL,
    expira_en timestamp with time zone,
    estado_id integer NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    email_invitado character varying(255) NOT NULL,
    perfil character varying(30) DEFAULT 'Cuidador'::character varying NOT NULL,
    CONSTRAINT invitacion_perfil_check CHECK (((perfil)::text = ANY (ARRAY[('Familia'::character varying)::text, ('Cuidador'::character varying)::text])))
);


--
-- Name: log_auditoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.log_auditoria (
    id_log uuid NOT NULL,
    actor_id uuid,
    valor_anterior text,
    valor_nuevo text,
    id_entidad uuid,
    fecha_hora timestamp with time zone DEFAULT now() NOT NULL,
    entidad_afectada character varying(50) NOT NULL,
    tipo_accion character varying(30) NOT NULL
);


--
-- Name: mascota; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mascota (
    id_mascota uuid NOT NULL,
    propietario_id uuid NOT NULL,
    fecha_nacimiento date,
    edad_valor integer,
    edad_unidad character varying(20),
    especie character varying(50) NOT NULL,
    nombre character varying(100) NOT NULL,
    peso_actual numeric(6,2),
    raza character varying(100),
    fecha_eliminacion timestamp with time zone,
    notas character varying(255),
    id_cliente uuid,
    fecha_aproximada boolean DEFAULT false NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    foto_perfil character varying(255),
    CONSTRAINT mascota_edad_unidad_check CHECK (((edad_unidad)::text = ANY (ARRAY[('dias'::character varying)::text, ('semanas'::character varying)::text, ('meses'::character varying)::text, ('anios'::character varying)::text])))
);


--
-- Name: pago; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pago (
    id_pago uuid NOT NULL,
    suscripcion_id uuid NOT NULL,
    estado_id integer NOT NULL,
    id_transaccion_mercadopago character varying(150),
    monto numeric(10,2) NOT NULL,
    fecha_pago timestamp with time zone DEFAULT now() NOT NULL,
    estado character varying(50) NOT NULL
);


--
-- Name: recordatorio; Type: TABLE; Schema: public; Owner: -
--

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
    id_cliente uuid,
    dia_semana character varying(20),
    intervalo_unidad character varying(20),
    tipo character varying(50) NOT NULL,
    titulo character varying(150) NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    modalidad character varying(50) DEFAULT 'unica'::character varying NOT NULL,
    actualizado_en timestamp with time zone DEFAULT now() NOT NULL,
    fecha_eliminacion timestamp with time zone,
    CONSTRAINT recordatorio_intervalo_unidad_check CHECK (((intervalo_unidad)::text = ANY (ARRAY[('dias'::character varying)::text, ('semanas'::character varying)::text, ('meses'::character varying)::text, ('anios'::character varying)::text]))),
    CONSTRAINT recordatorio_modalidad_check CHECK (((modalidad)::text = ANY (ARRAY[('unica'::character varying)::text, ('recurrente'::character varying)::text, ('dias_semana'::character varying)::text])))
);


--
-- Name: refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_token (
    id_refresh_token uuid NOT NULL,
    usuario_id uuid NOT NULL,
    token_hash character varying(255) NOT NULL,
    expira_en timestamp with time zone NOT NULL,
    revocado_en timestamp with time zone,
    reemplazado_por uuid,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: suscripcion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suscripcion (
    id_suscripcion uuid NOT NULL,
    usuario_id uuid NOT NULL,
    fecha_vencimiento timestamp with time zone,
    proximo_recargo timestamp with time zone,
    fecha_cancelacion timestamp with time zone,
    id_suscripcion_mercadopago character varying(150),
    tipo_plan character varying(50) NOT NULL,
    estado_id integer NOT NULL,
    fecha_inicio timestamp with time zone DEFAULT now() NOT NULL,
    estado character varying(50) NOT NULL
);


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id_usuario uuid NOT NULL,
    es_premium boolean DEFAULT false NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    email character varying(150) NOT NULL,
    nombre character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: estados id_estado; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estados ALTER COLUMN id_estado SET DEFAULT nextval('public.estados_id_estado_seq'::regclass);


--
-- Name: acceso_compartido acceso_compartido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_compartido
    ADD CONSTRAINT acceso_compartido_pkey PRIMARY KEY (id_acceso);


--
-- Name: acceso_compartido acceso_compartido_propietario_invitado_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_compartido
    ADD CONSTRAINT acceso_compartido_propietario_invitado_key UNIQUE (propietario_id, invitado_id);


--
-- Name: adjunto adjunto_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjunto
    ADD CONSTRAINT adjunto_id_cliente_key UNIQUE (id_cliente);


--
-- Name: adjunto adjunto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjunto
    ADD CONSTRAINT adjunto_pkey PRIMARY KEY (id_adjunto);


--
-- Name: album album_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album
    ADD CONSTRAINT album_id_cliente_key UNIQUE (id_cliente);


--
-- Name: album_mascota album_mascota_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_mascota
    ADD CONSTRAINT album_mascota_id_cliente_key UNIQUE (id_cliente);


--
-- Name: album_mascota album_mascota_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_mascota
    ADD CONSTRAINT album_mascota_pkey PRIMARY KEY (id_album_mascota);


--
-- Name: album album_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album
    ADD CONSTRAINT album_pkey PRIMARY KEY (id_album);


--
-- Name: badge badge_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.badge
    ADD CONSTRAINT badge_id_cliente_key UNIQUE (id_cliente);


--
-- Name: badge badge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.badge
    ADD CONSTRAINT badge_pkey PRIMARY KEY (id_badge);


--
-- Name: codigo_verificacion codigo_verificacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codigo_verificacion
    ADD CONSTRAINT codigo_verificacion_pkey PRIMARY KEY (id_codigo);


--
-- Name: estados estados_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.estados
    ADD CONSTRAINT estados_pkey PRIMARY KEY (id_estado);


--
-- Name: evento_clinico evento_clinico_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_clinico
    ADD CONSTRAINT evento_clinico_id_cliente_key UNIQUE (id_cliente);


--
-- Name: evento_clinico evento_clinico_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_clinico
    ADD CONSTRAINT evento_clinico_pkey PRIMARY KEY (id_evento);


--
-- Name: foto foto_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_id_cliente_key UNIQUE (id_cliente);


--
-- Name: foto foto_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_pkey PRIMARY KEY (id_foto);


--
-- Name: invitacion invitacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitacion
    ADD CONSTRAINT invitacion_pkey PRIMARY KEY (id_invitacion);


--
-- Name: log_auditoria log_auditoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.log_auditoria
    ADD CONSTRAINT log_auditoria_pkey PRIMARY KEY (id_log);


--
-- Name: mascota mascota_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mascota
    ADD CONSTRAINT mascota_id_cliente_key UNIQUE (id_cliente);


--
-- Name: mascota mascota_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mascota
    ADD CONSTRAINT mascota_pkey PRIMARY KEY (id_mascota);


--
-- Name: pago pago_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pago
    ADD CONSTRAINT pago_pkey PRIMARY KEY (id_pago);


--
-- Name: recordatorio recordatorio_id_cliente_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordatorio
    ADD CONSTRAINT recordatorio_id_cliente_key UNIQUE (id_cliente);


--
-- Name: recordatorio recordatorio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordatorio
    ADD CONSTRAINT recordatorio_pkey PRIMARY KEY (id_recordatorio);


--
-- Name: refresh_token refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id_refresh_token);


--
-- Name: refresh_token refresh_token_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash);


--
-- Name: suscripcion suscripcion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suscripcion
    ADD CONSTRAINT suscripcion_pkey PRIMARY KEY (id_suscripcion);


--
-- Name: usuario usuario_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_email_key UNIQUE (email);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id_usuario);


--
-- Name: idx_refresh_token_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_usuario ON public.refresh_token USING btree (usuario_id);


--
-- Name: uq_album_mascota_activa; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_album_mascota_activa ON public.album_mascota USING btree (id_album, id_mascota) WHERE (fecha_eliminacion IS NULL);


--
-- Name: acceso_compartido acceso_compartido_invitado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_compartido
    ADD CONSTRAINT acceso_compartido_invitado_id_fkey FOREIGN KEY (invitado_id) REFERENCES public.usuario(id_usuario);


--
-- Name: acceso_compartido acceso_compartido_propietario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acceso_compartido
    ADD CONSTRAINT acceso_compartido_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario);


--
-- Name: adjunto adjunto_evento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.adjunto
    ADD CONSTRAINT adjunto_evento_id_fkey FOREIGN KEY (evento_id) REFERENCES public.evento_clinico(id_evento);


--
-- Name: album album_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album
    ADD CONSTRAINT album_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuario(id_usuario);


--
-- Name: album_mascota album_mascota_id_album_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_mascota
    ADD CONSTRAINT album_mascota_id_album_fkey FOREIGN KEY (id_album) REFERENCES public.album(id_album);


--
-- Name: album_mascota album_mascota_id_mascota_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.album_mascota
    ADD CONSTRAINT album_mascota_id_mascota_fkey FOREIGN KEY (id_mascota) REFERENCES public.mascota(id_mascota);


--
-- Name: badge badge_mascota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.badge
    ADD CONSTRAINT badge_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota);


--
-- Name: codigo_verificacion codigo_verificacion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codigo_verificacion
    ADD CONSTRAINT codigo_verificacion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario);


--
-- Name: evento_clinico evento_clinico_mascota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_clinico
    ADD CONSTRAINT evento_clinico_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota);


--
-- Name: evento_clinico evento_clinico_registrado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_clinico
    ADD CONSTRAINT evento_clinico_registrado_por_id_fkey FOREIGN KEY (registrado_por_id) REFERENCES public.usuario(id_usuario);


--
-- Name: foto foto_album_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_album_id_fkey FOREIGN KEY (album_id) REFERENCES public.album(id_album);


--
-- Name: foto foto_subida_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.foto
    ADD CONSTRAINT foto_subida_por_id_fkey FOREIGN KEY (subida_por_id) REFERENCES public.usuario(id_usuario);


--
-- Name: invitacion invitacion_estado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitacion
    ADD CONSTRAINT invitacion_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado);


--
-- Name: invitacion invitacion_propietario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invitacion
    ADD CONSTRAINT invitacion_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario);


--
-- Name: log_auditoria log_auditoria_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.log_auditoria
    ADD CONSTRAINT log_auditoria_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.usuario(id_usuario);


--
-- Name: mascota mascota_propietario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mascota
    ADD CONSTRAINT mascota_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuario(id_usuario);


--
-- Name: pago pago_estado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pago
    ADD CONSTRAINT pago_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado);


--
-- Name: pago pago_suscripcion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pago
    ADD CONSTRAINT pago_suscripcion_id_fkey FOREIGN KEY (suscripcion_id) REFERENCES public.suscripcion(id_suscripcion);


--
-- Name: recordatorio recordatorio_confirmado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordatorio
    ADD CONSTRAINT recordatorio_confirmado_por_id_fkey FOREIGN KEY (confirmado_por_id) REFERENCES public.usuario(id_usuario);


--
-- Name: recordatorio recordatorio_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordatorio
    ADD CONSTRAINT recordatorio_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuario(id_usuario);


--
-- Name: recordatorio recordatorio_estado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordatorio
    ADD CONSTRAINT recordatorio_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado);


--
-- Name: recordatorio recordatorio_mascota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordatorio
    ADD CONSTRAINT recordatorio_mascota_id_fkey FOREIGN KEY (mascota_id) REFERENCES public.mascota(id_mascota);


--
-- Name: refresh_token refresh_token_reemplazado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_reemplazado_por_fkey FOREIGN KEY (reemplazado_por) REFERENCES public.refresh_token(id_refresh_token);


--
-- Name: refresh_token refresh_token_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario);


--
-- Name: suscripcion suscripcion_estado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suscripcion
    ADD CONSTRAINT suscripcion_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.estados(id_estado);


--
-- Name: suscripcion suscripcion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suscripcion
    ADD CONSTRAINT suscripcion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id_usuario);


--
-- PostgreSQL database dump complete
--

\unrestrict 9859WzAjAPVzI7jyNGMLh4Xrj5SInH7zbckM9XKwkV1pfDWpNUffkYYPGojC3TW

