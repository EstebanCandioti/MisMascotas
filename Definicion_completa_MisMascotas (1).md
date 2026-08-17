# MisMascotas — Documento de Definición Completa
*Última actualización: agosto 2026*

---

## 1. Descripción del sistema

MisMascotas es una aplicación mobile-first para la gestión integral de la salud de mascotas. Centraliza el historial clínico, los recordatorios de medicación y vacunas, y la coordinación entre múltiples cuidadores de un mismo hogar.

---

## 2. Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Mobile | React Native + Expo (EAS Build) |
| Web | React Native Web → Vercel |
| Estado global | Zustand |
| Backend | Spring Boot (Java 21) · Hosting: Render |
| Base de datos | PostgreSQL → Supabase |
| Almacenamiento | Supabase Storage |
| Email / 2FA | Gmail SMTP + JavaMailSender |
| Notificaciones push | Expo Push Notification Service |
| Pagos | MercadoPago (suscripciones preconfiguradas + webhook) |
| Auditoría | AOP con @Auditable → AuditAspect (patrón del proyecto MiCursada) |
| Java | 21 LTS (SDKMAN / .sdkmanrc) |
| Node | 22 LTS (nvm / .nvmrc) |

**Nota:** Las notificaciones push remotas requieren development build (EAS) — no funcionan en Expo Go desde SDK 53. Render free tier tiene cold starts (~30s tras inactividad).

---

## 3. Usuarios y perfiles

No existe un rol global en el sistema. Los permisos son contextuales y se definen por invitación mascota a mascota.

| Perfil | Descripción | Permisos |
|--------|-------------|----------|
| **Propietario** | Usuario que registró la mascota | Todo: registrar, editar, eliminar eventos clínicos y recordatorios, invitar, revocar accesos, eliminar mascota, gestionar badges |
| **Familia** | Invitado con perfil de confianza | Ver historial, registrar/editar/eliminar cualquier evento clínico y recordatorio, confirmar checkboxes |
| **Cuidador** | Invitado con perfil limitado | Ver historial, confirmar checkboxes del día |

El acceso compartido aplica sobre **todas** las mascotas del propietario, no sobre mascotas individuales.

---

## 4. Planes

| Función | Gratuito | Premium |
|---------|----------|---------|
| Mascotas registradas | Hasta 5 | Ilimitadas |
| Foto de perfil de mascota | No (bloqueado) | Sí |
| Usuarios con acceso compartido | Hasta 3 | Ilimitados |
| Álbum de fotos por mascota | Solo ver | Crear, editar, eliminar, subir fotos |
| Badges/insignias por mascota | No (bloqueado) | Sí (hasta 3 por mascota) |
| Exportación PDF historial | Sí (todos) | Sí (todos) |

**Modelo de suscripción:** mensual o anual via MercadoPago. Los links de suscripción se crean de antemano en MercadoPago. El backend solo procesa el webhook de pago aprobado — no crea preferencias en tiempo real. El endpoint de webhook es público y se valida por firma x-signature.

---

## 5. Casos de uso

### CUs de usuario (activos)

| ID | Nombre | Actor |
|----|--------|-------|
| CU1 | Registrarse | Usuario |
| CU2 | Iniciar sesión | Usuario |
| CU3 | Registrar mascota | Usuario (propietario) |
| CU4 | Compartir cuidado | Usuario (propietario e invitado) |
| CU6 | Confirmar recordatorio cumplido | Usuario / Cuidador |
| CU7 | Registrar evento clínico | Usuario / Cuidador |
| CU8 | Crear recordatorio | Usuario / Cuidador |
| CU9 | Ver historial clínico y evolución | Usuario / Cuidador |
| CU10 | Ver configuración | Usuario |
| CU10a | Gestionar notificaciones | Usuario |
| CU10b | Cerrar sesión | Usuario |
| CU11 | Ver recordatorios | Usuario / Cuidador |
| CU11a | Editar recordatorio | Usuario (Propietario / Familia) |
| CU11b | Desactivar y reactivar recordatorio | Usuario (Propietario / Familia) |
| CU11c | Eliminar recordatorio | Usuario (Propietario / Familia) |
| CU12 | Exportar historial clínico | Usuario / Cuidador |
| CU13 | Editar información de mascota | Usuario (propietario) |
| CU14 | Eliminar mascota | Usuario (propietario) |
| CU15 | Ver calendario de eventos | Usuario / Cuidador |
| CU16 | Ver álbumes | Usuario / Cuidador |
| CU16a | Crear álbum | Usuario (premium) |
| CU16b | Editar álbum | Usuario (premium) |
| CU16c | Eliminar álbum | Usuario (premium) |
| CU24 | Registrar entrada en log de auditoría | Sistema |
| CU25 | Gestionar cuidado compartido | Usuario |
| CU26 | Gestionar plan premium | Usuario |
| CU27 | Gestionar badges de mascota | Usuario (propietario, premium) |

### CUs absorbidos (ya no existen como documentos separados)

| ID | Absorbido en |
|----|-------------|
| CU5 | Eliminado — integrado en CU3 |
| CU17 | CU1 |
| CU18 | CU2 |
| CU19 | CU3 |
| CU20 | CU7 |
| CU21 | CU8 |
| CU22 | CU12 |
| CU23 | CU14 |

---

## 6. Reglas de negocio

### Usuarios y autenticación
- **RN-005** El email debe ser único en el sistema.
- **RN-006** La contraseña debe tener mínimo 8 caracteres, al menos una mayúscula y un número.
- **RN-007** Las contraseñas se almacenan de forma segura y nunca en texto plano.
- **RN-008** La sesión dura 30 días mediante token JWT. Al vencer, el usuario debe volver a autenticarse incluyendo el 2FA.
- **RN-009** El inicio de sesión requiere verificación en 2 pasos: código de un solo uso enviado al email del usuario. Solo por email en el MVP.

### Mascotas
- **RN-001** Plan gratuito: hasta 5 mascotas. Plan premium: sin límite.
- **RN-002** Campos mínimos obligatorios: nombre, especie y fecha de nacimiento (exacta o aproximada). Fecha aproximada: toggle "¿Sabés la fecha exacta?" → selector de meses/años con límite por especie (Perro/Gato/Ave: 20 años, Conejo: 15 años, Reptil/Otro: 50 años).
- **RN-003** Formatos y tamaño máximo de imagen de perfil: a definir.
- **RN-004** Foto de perfil de mascota: exclusiva del plan premium. Usuarios gratuitos ven la opción bloqueada.
- **RN-019** Eliminación de mascota: soft delete con campo `eliminada` (boolean) + `fecha_eliminacion`. Datos conservados 30 días antes de la purga definitiva.
- **RN-020** Solo el propietario puede eliminar una mascota.
- **RN-021** Al eliminar una mascota, todos los usuarios con acceso compartido son notificados y pierden el acceso de forma inmediata.

### Cuidado compartido
- **RN-010** Al invitar, el propietario elige perfil Familia o Cuidador. El acceso aplica sobre todas las mascotas del propietario.
- **RN-033** Familia: ver historial, registrar/editar/eliminar cualquier evento clínico y recordatorio, confirmar y crear recordatorios. Cuidador: ver historial y confirmar recordatorios del día.
- **RN-034** El propietario puede cambiar el perfil de un invitado o revocarle el acceso en cualquier momento.
- **RN-035** Las invitaciones expiran automáticamente a las 48 horas sin respuesta.
- **RN-036** Plan gratuito: hasta 3 invitados. Plan premium: sin límite.

### Eventos clínicos
- **RN-011** Un evento clínico solo puede registrarse sobre una mascota activa.
- **RN-012** Cada evento clínico queda identificado con el usuario que lo realizó y la fecha y hora exacta.
- **RN-025** La fecha de un evento clínico no puede ser posterior al día actual.
- **RN-040** El registro de peso actualiza automáticamente el valor de peso en el perfil y el gráfico de evolución.
- **RN-043** El historial clínico incluye: vacunas, medicaciones, consultas veterinarias y registros de peso.
- **RN-047** Cualquier usuario con perfil Familia puede editar o eliminar cualquier evento clínico de esa mascota. El propietario también. Toda acción queda en el log de auditoría.

### Recordatorios
- **RN-037** Tipos predefinidos: pipeta/antiparasitario, visita al veterinario, medicación, vacuna, otro.
- **RN-039** Fecha de fin opcional en repetitivos. Sin fecha de fin se repite indefinidamente.
- **RN-042** Un recordatorio confirmado puede desmarcarse si fue un error. Revierte el estado a pendiente (no genera ni revierte evento clínico).
- **RN-048** Desactivar suspende notificaciones sin eliminar. Puede reactivarse en cualquier momento.
- **RN-049** Cualquier usuario con perfil Familia puede editar o eliminar cualquier recordatorio. El propietario también.
- **RN-051** Todos los usuarios con acceso a una mascota ven los recordatorios del día de esa mascota y pueden marcarlos como cumplidos.
- **RN-052** Si el usuario tiene notificaciones desactivadas, la app muestra un aviso descartable invitándolo a activarlas.

**Importante:** Confirmar un checkbox (CU6) NO genera un evento en el historial clínico. Solo actualiza el estado del recordatorio. El registro formal de eventos clínicos es exclusivo de CU7.

### Notificaciones
- **RN-013** Los recordatorios se persisten en base de datos y sobreviven reinicios del servidor.
- **RN-014** Margen máximo entre hora programada y envío: 5 minutos.
- **RN-015** Recordatorio automático por protocolo veterinario: **pendiente de consulta con veterinarios**.
- **RN-032** Todos los usuarios pueden configurar múltiples avisos previos por recordatorio, independientemente del plan.

### Historial y evolución
- **RN-044** Contenido adicional de la pestaña evolución más allá del peso: **pendiente de consulta con veterinarios**.

### Exportación
- **RN-016** Exportación del historial en PDF: gratuita para todos los usuarios.
- **RN-045** El PDF se genera al momento y se descarga directamente al dispositivo. El sistema no guarda copia.
- **RN-046** El PDF incluye: nombre y foto de la mascota (si existe), datos del propietario, fecha de generación e historial clínico completo.

### Archivos y documentos
- **RN-026** Adjuntos a eventos clínicos: JPG, PNG, PDF, HEIC. Máximo 10 MB.
- **RN-028** Los archivos se almacenan de forma segura.
- **RN-029** Álbum de fotos por mascota: exclusivo del plan premium. Usuarios gratuitos pueden ver pero no crear ni subir. Solo fotos en el MVP (videos en alcance futuro). Google Photos fuera del alcance inicial.

### Calendario
- **RN-030** El calendario muestra todos los eventos de todas las mascotas del usuario en una vista unificada.
- **RN-031** Los eventos se diferencian visualmente por tipo.
- **RN-050** Filtro por mascota opcional. Por defecto muestra todas.

### Log de auditoría
- **RN-022** El historial de acciones no puede ser modificado ni eliminado por ningún usuario.
- **RN-023** El sistema registra acciones de usuarios y procesos automáticos.
- **RN-024** Las entradas del log se eliminan únicamente con la purga definitiva de la mascota.

El log se implementa con AOP (@Auditable en Services). Actor = null cuando la acción es del sistema. Los CUs que disparan log: CU3, CU4, CU6, CU7, CU8, CU11a/b/c, CU25, CU27 (a confirmar con el equipo los demás).

### Badges
- **RN-053** Cada mascota puede tener hasta 3 badges activas. Son puramente decorativas.
- **RN-054** Las badges son exclusivas del plan premium. Usuarios gratuitos ven la sección bloqueada.

---

## 7. Modelo de datos

### Tabla de estados (lookup centralizado)

```sql
CREATE TABLE estados (
  id SERIAL PRIMARY KEY,
  entidad VARCHAR(50) NOT NULL,
  nombre VARCHAR(50) NOT NULL,
  UNIQUE (entidad, nombre)
);

INSERT INTO estados (entidad, nombre) VALUES
  ('RECORDATORIO', 'ACTIVO'),
  ('RECORDATORIO', 'INACTIVO'),
  ('RECORDATORIO', 'COMPLETADO'),
  ('INVITACION', 'PENDIENTE'),
  ('INVITACION', 'ACEPTADA'),
  ('INVITACION', 'RECHAZADA'),
  ('INVITACION', 'EXPIRADA'),
  ('INVITACION', 'CANCELADA'),
  ('PAGO', 'PENDIENTE'),
  ('PAGO', 'ACEPTADO'),
  ('PAGO', 'RECHAZADO'),
  ('SUSCRIPCION', 'ACTIVA'),
  ('SUSCRIPCION', 'VENCIDA'),
  ('SUSCRIPCION', 'CANCELADA');
```

### Entidades principales

```
USUARIOS
  id, nombre, email, passwordHash, plan, planVencimiento,
  fechaRegistro, activo

MASCOTAS
  id, nombre, especie, fechaNacimiento, fechaAproximada (boolean),
  edadAproximadaValor, edadAproximadaUnidad, raza, urlFoto, notas,
  eliminada (boolean), fechaEliminacion, propietarioId

USUARIO_MASCOTA (acceso compartido)
  id, usuarioId, mascotaId, perfil (PROPIETARIO/FAMILIA/CUIDADOR), activo

INVITACIONES
  id, propietarioId, emailInvitado, perfil, estadoId (FK→estados),
  creadoEn, expiraEn

EVENTOS_CLINICOS
  id, mascotaId, tipo (VACUNA/MEDICACION/CONSULTA/PESO),
  fecha, nombre, dosis, valorNumerico, unidad (kg),
  motivo, diagnostico, observaciones, urlAdjunto, registradoPorId

RECORDATORIOS
  id, mascotaId, creadoPorId, titulo, tipo, fechaInicio,
  modalidad (UNICO/INTERVALO/DIAS_SEMANA), intervaloValor,
  intervaloUnidad, diasSemana, fechaFin, estadoId (FK→estados)

ALBUMS
  id, nombre, descripcion, mascotaId, creadoPorId, creadoEn

FOTOS
  id, albumId, urlArchivo, subidaPorId, subidaEn

BADGES
  id, mascotaId, texto, emoji, creadoPorId

SUSCRIPCIONES
  id, usuarioId, plan, estadoId (FK→estados),
  fechaInicio, fechaFin, proximoRecargo, fechaCancelacion

PAGOS
  id, suscripcionId, monto, estadoId (FK→estados),
  fechaPago, idTransaccionMp

CODIGO_VERIFICACION (2FA)
  id, usuarioId, codigo, creadoEn, expiraEn, usado

LOG_AUDITORIA
  id, actorId (nullable), tipoAccion, entidad,
  idEntidad, valorAnterior (JSON), valorNuevo (JSON), timestampUTC
```

---

## 8. Arquitectura

```
┌─────────────────────────────────┐
│            Frontend             │
│  App Mobile          App Web    │
│  RN + Expo (EAS)  RN Web/Vercel │
└────────────┬────────────────────┘
             │ HTTPS/REST
┌────────────▼────────────────────┐
│            Backend              │
│  API REST (Spring Boot Java 21) │
│  Hosting: Render                │
│  ┌──────────┐ ┌───────────────┐ │
│  │ Webhook  │ │  API REST     │ │
│  │ MP(firma)│ │  JWT + AOP    │ │
│  └──────────┘ └───────────────┘ │
└──────┬────────────────┬─────────┘
       │                │
┌──────▼──────┐  ┌──────▼──────────┐
│ PostgreSQL  │  │ Servicios ext.  │
│ Supabase    │  │ Gmail SMTP      │
│ + Storage   │  │ Expo Push       │
└─────────────┘  │ MercadoPago     │
                 └─────────────────┘
```

**Flujo de pagos MercadoPago:**
1. App redirige directo al link de suscripción preconfigurado en MP
2. Usuario paga en interfaz de MP
3. MP envía webhook al backend (endpoint público, validado por x-signature)
4. Backend valida firma → consulta detalles a API MP → actualiza plan en DB

---

## 9. Orden de sprints

| Sprint | Épica | CUs |
|--------|-------|-----|
| 1 | Base de datos | Crear todas las tablas + tabla ESTADOS con datos iniciales |
| 2 | Autenticación y seguridad | CU1, CU2, Spring Security, JWT, 2FA, CU24 (AOP) |
| 3 | Gestión de mascotas | CU3, CU13, CU14 (soft delete + purga automática) |
| 4 | Eventos clínicos e historial | CU7, CU9, CU12 |
| 5 | Recordatorios y notificaciones | CU8, CU11, CU11a/b/c, CU6, Expo Push |
| 6 | Cuidado compartido | CU4, CU25 |
| 7 | Álbumes y badges | CU16, CU16a/b/c, CU27, Supabase Storage |
| 8 | Calendario y pantalla de inicio | CU15, pantalla inicio con actividad reciente |
| 9 | Configuración y pagos | CU10, CU10a/b, CU26, webhook MercadoPago |

---

## 10. Pendientes de definición

| Pendiente | Estado |
|-----------|--------|
| Contenido adicional pestaña Evolución (RN-044) | Pendiente veterinario |
| Calendarios de vacunación automáticos (RN-015) | Pendiente veterinario |
| Formatos y tamaño foto de perfil (RN-003) | Pendiente equipo |
| Videos en álbumes | Fuera del MVP, alcance futuro |
| Recuperación de contraseña | Fuera del MVP |
| CUs que disparan log de auditoría (lista completa) | Pendiente confirmar con equipo |
| Modelo de suscripción lifetime | Pendiente equipo |

---

*Este documento es la fuente de verdad del proyecto. Ante cualquier contradicción con documentos anteriores, prevalece este.*
