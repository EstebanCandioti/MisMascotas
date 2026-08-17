-- ============================================================
-- MISMASCOTAS — Schema PostgreSQL (Supabase)
-- Versión final consolidada
-- ============================================================

-- ============================================================
-- TABLA DE ESTADOS (lookup centralizado)
-- ============================================================

CREATE TABLE estados (
    id_estado   SERIAL PRIMARY KEY,
    entidad     VARCHAR(50)  NOT NULL,
    nombre      VARCHAR(50)  NOT NULL,
    UNIQUE (entidad, nombre)
);

-- Filas iniciales
INSERT INTO estados (entidad, nombre) VALUES
    ('RECORDATORIO', 'ACTIVO'),
    ('RECORDATORIO', 'INACTIVO'),
    ('RECORDATORIO', 'COMPLETADO'),
    ('INVITACION',   'PENDIENTE'),
    ('INVITACION',   'ACEPTADA'),
    ('INVITACION',   'EXPIRADA'),
    ('INVITACION',   'CANCELADA'),
    ('PAGO',         'APROBADO'),
    ('PAGO',         'RECHAZADO'),
    ('PAGO',         'PENDIENTE'),
    ('SUSCRIPCION',  'ACTIVA'),
    ('SUSCRIPCION',  'CANCELADA'),
    ('SUSCRIPCION',  'VENCIDA');

-- ============================================================
-- USUARIO
-- ============================================================

CREATE TABLE usuario (
    id_usuario      SERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    es_premium      BOOLEAN      NOT NULL DEFAULT FALSE,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- CÓDIGO DE VERIFICACIÓN (2FA propio)
-- ============================================================

CREATE TABLE codigo_verificacion (
    id_codigo   SERIAL PRIMARY KEY,
    usuario_id  INTEGER      NOT NULL REFERENCES usuario(id_usuario),
    codigo      VARCHAR(10)  NOT NULL,
    creado_en   TIMESTAMP    NOT NULL DEFAULT NOW(),
    expira_en   TIMESTAMP    NOT NULL,
    usado       BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ============================================================
-- SUSCRIPCIÓN
-- ============================================================

CREATE TABLE suscripcion (
    id_suscripcion              SERIAL PRIMARY KEY,
    usuario_id                  INTEGER      NOT NULL REFERENCES usuario(id_usuario),
    tipo_plan                   VARCHAR(20)  NOT NULL, -- 'mensual' | 'anual'
    estado_id                   INTEGER      NOT NULL REFERENCES estados(id_estado),
    fecha_inicio                TIMESTAMP    NOT NULL,
    fecha_vencimiento_actual    TIMESTAMP    NOT NULL,
    proximo_recargo             TIMESTAMP,
    fecha_cancelacion           TIMESTAMP,
    id_suscripcion_mercadopago  VARCHAR(100)
);

-- ============================================================
-- PAGO
-- ============================================================

CREATE TABLE pago (
    id_pago                     SERIAL PRIMARY KEY,
    suscripcion_id              INTEGER        NOT NULL REFERENCES suscripcion(id_suscripcion),
    monto                       NUMERIC(10,2)  NOT NULL,
    fecha_pago                  TIMESTAMP      NOT NULL,
    estado_id                   INTEGER        NOT NULL REFERENCES estados(id_estado),
    id_transaccion_mercadopago  VARCHAR(100)
);

-- ============================================================
-- MASCOTA
-- ============================================================

CREATE TABLE mascota (
    id_mascota          SERIAL PRIMARY KEY,
    id_cliente          UUID         NOT NULL UNIQUE, -- generado en cliente (offline-first)
    propietario_id      INTEGER      NOT NULL REFERENCES usuario(id_usuario),
    nombre              VARCHAR(100) NOT NULL,
    especie             VARCHAR(20)  NOT NULL, -- 'Perro'|'Gato'|'Ave'|'Conejo'|'Reptil'|'Roedor'|'Otro'
    raza                VARCHAR(100),
    fecha_nacimiento    DATE,
    fecha_aproximada    BOOLEAN      NOT NULL DEFAULT FALSE,
    edad_valor          INTEGER,               -- si fecha_aproximada = true
    edad_unidad         VARCHAR(10),           -- 'meses' | 'años'
    foto_perfil         TEXT,                  -- path en Supabase Storage (premium)
    peso_actual         NUMERIC(6,2),          -- cache, se actualiza desde evento tipo 'peso'
    notas               TEXT,
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_eliminacion   TIMESTAMP,             -- soft delete (deleted_at)
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT NOW(),
    creado_en           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INVITACIÓN
-- ============================================================

CREATE TABLE invitacion (
    id_invitacion   SERIAL PRIMARY KEY,
    propietario_id  INTEGER      NOT NULL REFERENCES usuario(id_usuario),
    email_invitado  VARCHAR(150) NOT NULL,
    perfil          VARCHAR(20)  NOT NULL, -- 'Familia' | 'Cuidador'
    estado_id       INTEGER      NOT NULL REFERENCES estados(id_estado),
    creado_en       TIMESTAMP    NOT NULL DEFAULT NOW(),
    expira_en       TIMESTAMP    NOT NULL  -- creado_en + 48hs
);

-- ============================================================
-- ACCESO COMPARTIDO
-- Relación reflexiva Usuario↔Usuario (propietario↔invitado)
-- Aplica sobre TODAS las mascotas del propietario
-- ============================================================

CREATE TABLE acceso_compartido (
    id_acceso       SERIAL PRIMARY KEY,
    propietario_id  INTEGER     NOT NULL REFERENCES usuario(id_usuario),
    invitado_id     INTEGER     NOT NULL REFERENCES usuario(id_usuario),
    perfil          VARCHAR(20) NOT NULL, -- 'Familia' | 'Cuidador'
    fecha_ingreso   TIMESTAMP   NOT NULL DEFAULT NOW(),
    activo          BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (propietario_id, invitado_id)
);

-- ============================================================
-- EVENTO CLÍNICO
-- Campos opcionales según tipo:
--   vacuna     → nombre, observaciones
--   medicacion → nombre, dosis, observaciones
--   consulta   → motivo, diagnostico, observaciones
--   peso       → valor_peso (kg)
-- ============================================================

CREATE TABLE evento_clinico (
    id_evento           SERIAL PRIMARY KEY,
    id_cliente          UUID         NOT NULL UNIQUE,
    mascota_id          INTEGER      NOT NULL REFERENCES mascota(id_mascota),
    registrado_por_id   INTEGER      NOT NULL REFERENCES usuario(id_usuario),
    tipo                VARCHAR(20)  NOT NULL, -- 'vacuna'|'medicacion'|'consulta'|'peso'
    fecha               DATE         NOT NULL,
    nombre              VARCHAR(150),          -- vacuna o medicamento
    dosis               VARCHAR(100),          -- solo medicacion
    motivo              TEXT,                  -- solo consulta
    diagnostico         TEXT,                  -- solo consulta, opcional
    valor_peso          NUMERIC(6,2),          -- solo peso, en kg
    observaciones       TEXT,                  -- opcional, aplica a vacuna/medicacion/consulta
    fecha_eliminacion   TIMESTAMP,
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT NOW(),
    creado_en           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ADJUNTO
-- Comprobantes clínicos (distinto de Foto/Álbum)
-- Formatos: JPG, PNG, PDF, HEIC — max 10MB
-- ============================================================

CREATE TABLE adjunto (
    id_adjunto          SERIAL PRIMARY KEY,
    id_cliente          UUID        NOT NULL UNIQUE,
    evento_id           INTEGER     NOT NULL REFERENCES evento_clinico(id_evento),
    url_archivo         TEXT        NOT NULL, -- path en Supabase Storage
    formato             VARCHAR(10) NOT NULL, -- 'JPG'|'PNG'|'PDF'|'HEIC'
    fecha_eliminacion   TIMESTAMP,
    actualizado_en      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ============================================================
-- RECORDATORIO
-- ============================================================

CREATE TABLE recordatorio (
    id_recordatorio     SERIAL PRIMARY KEY,
    id_cliente          UUID         NOT NULL UNIQUE,
    mascota_id          INTEGER      NOT NULL REFERENCES mascota(id_mascota),
    creado_por_id       INTEGER      NOT NULL REFERENCES usuario(id_usuario),
    estado_id           INTEGER      NOT NULL REFERENCES estados(id_estado),
    titulo              VARCHAR(150) NOT NULL,
    tipo                VARCHAR(50)  NOT NULL, -- 'pipeta'|'veterinario'|'medicacion'|'vacuna'|'otro'
    fecha_hora_inicio   TIMESTAMP    NOT NULL,
    modalidad           VARCHAR(20)  NOT NULL, -- 'unico'|'intervalo'|'dias_semana'
    intervalo_valor     INTEGER,               -- si modalidad = 'intervalo'
    intervalo_unidad    VARCHAR(10),           -- 'dias'|'semanas'|'meses'
    dias_semana         VARCHAR(20),           -- si modalidad = 'dias_semana', ej: 'LU,MI,VI'
    fecha_fin           TIMESTAMP,             -- opcional en repetitivos
    confirmado_por_id   INTEGER      REFERENCES usuario(id_usuario),
    confirmado_en       TIMESTAMP,             -- timestamp del tap (desempate first-write-wins)
    fecha_eliminacion   TIMESTAMP,
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT NOW(),
    creado_en           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- BADGE (Insignia decorativa)
-- Máximo 3 activas por mascota — validar en aplicación
-- Solo propietario puede gestionar
-- ============================================================

CREATE TABLE badge (
    id_badge            SERIAL PRIMARY KEY,
    id_cliente          UUID         NOT NULL UNIQUE,
    mascota_id          INTEGER      NOT NULL REFERENCES mascota(id_mascota),
    texto               VARCHAR(100) NOT NULL,
    emoji               VARCHAR(10),
    fecha_eliminacion   TIMESTAMP,
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ÁLBUM
-- Premium — creado por el propietario
-- Un álbum puede tener múltiples mascotas (N:M via album_mascota)
-- ============================================================

CREATE TABLE album (
    id_album            SERIAL PRIMARY KEY,
    id_cliente          UUID         NOT NULL UNIQUE,
    creado_por_id       INTEGER      NOT NULL REFERENCES usuario(id_usuario),
    nombre              VARCHAR(150) NOT NULL,
    descripcion         TEXT,
    fecha_eliminacion   TIMESTAMP,
    actualizado_en      TIMESTAMP    NOT NULL DEFAULT NOW(),
    creado_en           TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ÁLBUM_MASCOTA (tabla intermedia N:M)
-- ============================================================

CREATE TABLE album_mascota (
    album_id            INTEGER   NOT NULL REFERENCES album(id_album)   ON DELETE CASCADE,
    mascota_id          INTEGER   NOT NULL REFERENCES mascota(id_mascota),
    fecha_eliminacion   TIMESTAMP,
    actualizado_en      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (album_id, mascota_id)
);

-- ============================================================
-- FOTO
-- Solo JPG/PNG/HEIC (sin PDF — a diferencia de adjunto clínico)
-- ============================================================

CREATE TABLE foto (
    id_foto             SERIAL PRIMARY KEY,
    id_cliente          UUID        NOT NULL UNIQUE,
    album_id            INTEGER     NOT NULL REFERENCES album(id_album) ON DELETE CASCADE,
    url_archivo         TEXT        NOT NULL, -- path en Supabase Storage
    formato             VARCHAR(10) NOT NULL, -- 'JPG'|'PNG'|'HEIC'
    subida_por_id       INTEGER     NOT NULL REFERENCES usuario(id_usuario),
    fecha_eliminacion   TIMESTAMP,
    actualizado_en      TIMESTAMP   NOT NULL DEFAULT NOW(),
    subida_en           TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ============================================================
-- LOG DE AUDITORÍA
-- Inmutable — sin UPDATE ni DELETE (salvo purga definitiva de mascota)
-- actor_id nullable cuando el actor es el Sistema
-- ============================================================

CREATE TABLE log_auditoria (
    id_log              SERIAL PRIMARY KEY,
    actor_id            INTEGER      REFERENCES usuario(id_usuario), -- NULL = Sistema
    tipo_accion         VARCHAR(30)  NOT NULL, -- 'creacion'|'modificacion'|'eliminacion'|'cambio_acceso'
    entidad_afectada    VARCHAR(50)  NOT NULL,
    id_entidad          INTEGER      NOT NULL,
    valor_anterior      TEXT,
    valor_nuevo         TEXT,
    fecha_hora          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ÍNDICES RECOMENDADOS
-- ============================================================

-- Consultas frecuentes por propietario
CREATE INDEX idx_mascota_propietario     ON mascota(propietario_id);
CREATE INDEX idx_mascota_activo          ON mascota(activo);

-- Historial clínico por mascota
CREATE INDEX idx_evento_mascota          ON evento_clinico(mascota_id);
CREATE INDEX idx_evento_fecha            ON evento_clinico(fecha);

-- Recordatorios por mascota y estado
CREATE INDEX idx_recordatorio_mascota    ON recordatorio(mascota_id);
CREATE INDEX idx_recordatorio_estado     ON recordatorio(estado_id);

-- Acceso compartido — consultas por propietario o invitado
CREATE INDEX idx_acceso_propietario      ON acceso_compartido(propietario_id);
CREATE INDEX idx_acceso_invitado         ON acceso_compartido(invitado_id);

-- Invitaciones pendientes
CREATE INDEX idx_invitacion_estado       ON invitacion(estado_id);
CREATE INDEX idx_invitacion_propietario  ON invitacion(propietario_id);

-- Sync offline — consultas por updated_at
CREATE INDEX idx_mascota_actualizado     ON mascota(actualizado_en);
CREATE INDEX idx_evento_actualizado      ON evento_clinico(actualizado_en);
CREATE INDEX idx_recordatorio_actualizado ON recordatorio(actualizado_en);

-- Auditoría
CREATE INDEX idx_log_entidad             ON log_auditoria(entidad_afectada, id_entidad);
CREATE INDEX idx_log_actor               ON log_auditoria(actor_id);

-- Código 2FA — búsqueda por usuario
CREATE INDEX idx_codigo_usuario          ON codigo_verificacion(usuario_id);

-- Álbum por creador
CREATE INDEX idx_album_creador           ON album(creado_por_id);
CREATE INDEX idx_album_mascota_mascota   ON album_mascota(mascota_id);

-- Fotos por álbum
CREATE INDEX idx_foto_album              ON foto(album_id);

-- Badges por mascota
CREATE INDEX idx_badge_mascota           ON badge(mascota_id);

-- Pagos por suscripción
CREATE INDEX idx_pago_suscripcion        ON pago(suscripcion_id);

-- Suscripción por usuario
CREATE INDEX idx_suscripcion_usuario     ON suscripcion(usuario_id);
