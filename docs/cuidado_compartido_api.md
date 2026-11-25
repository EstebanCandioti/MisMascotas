# Contratos de invitaciones y accesos compartidos

## Alcance C03 a C13

Todas las rutas requieren `Authorization: Bearer <accessToken>`. El actor se obtiene de su sesión; no se acepta un propietario o destinatario arbitrario desde el cuerpo o los parámetros. Los perfiles JSON son `Familia` y `Cuidador`. Las fechas son ISO 8601 UTC y los identificadores UUID.

Esta entrega administra invitaciones y vínculos, autoriza recursos compartidos según la operación y envía correos después del commit. La pantalla de recepción y respuesta corresponde al frontend web.

## Rutas

Prefijo: `/cuidado-compartido`.

| Método y ruta | Datos de entrada | Respuesta exitosa |
|---|---|---|
| POST `/invitaciones` | `{"emailInvitado":"persona@example.test","perfil":"Familia"}` | 201, invitación nueva con vencimiento a 48 horas. |
| GET `/me` | Sin cuerpo | 200, bandejas enviadas/recibidas, accesos otorgados/cuidados recibidos y cupo del actor. |
| GET `/invitaciones/{id}` | UUID de invitación | 200, solo propietario o destinatario. |
| POST `/invitaciones/{id}/aceptar` | Sin cuerpo | 200, `{"invitacion":{...},"acceso":{...}}`. |
| POST `/invitaciones/{id}/rechazar` | Sin cuerpo | 200, invitación rechazada. |
| POST `/invitaciones/{id}/cancelar` | Sin cuerpo | 200, invitación cancelada por el propietario. |
| POST `/invitaciones/{id}/reenviar` | Sin cuerpo; conserva destinatario y perfil originales | 201, invitación nueva con otro UUID. |
| GET `/accesos/{id}` | UUID del vínculo | 200, solo propietario o invitado, incluido su historial inactivo. |
| PATCH `/accesos/{id}/perfil` | `{"perfil":"Cuidador"}` | 200, vínculo actualizado por el propietario. |
| DELETE `/accesos/{id}` | Sin cuerpo | 200, vínculo inactivo por revocación del propietario. |
| POST `/accesos/{id}/salir` | Sin cuerpo | 200, vínculo inactivo por salida del invitado. |

## Formatos de respuesta

Invitación: `idInvitacion`, `propietarioId`, `nombrePropietario`, `emailInvitado`, `perfil`, `estado`, `creadoEn`, `expiraEn`. Los estados son `PENDIENTE`, `ACEPTADA`, `RECHAZADA`, `CANCELADA` y `EXPIRADA`.

Acceso: `idAcceso`, `propietarioId`, `nombrePropietario`, `invitadoId`, `nombreInvitado`, `emailInvitado`, `perfil`, `activo`, `fechaIngreso`.

La bandeja contiene `invitacionesEnviadas`, `invitacionesRecibidas`, `accesosOtorgados`, `cuidadosRecibidos` y `cupo`. Los vínculos listados son activos; las invitaciones conservan su historial. Cupo: `esPremium`, `limite`, `accesosActivos`, `invitacionesPendientes`, `ocupados`, `disponibles`. En Premium `limite` y `disponibles` son null. Free tiene límite tres y disponibles nunca negativos. Los cuidados recibidos no consumen el cupo del invitado.

## Reglas de transición e idempotencia

- Crear valida cuenta registrada/activa, mascota propia activa, auto-invitación, duplicado y plan actual bajo bloqueo del propietario. El perfil es obligatorio. Cada creación reserva un lugar y genera una auditoría.
- Los GET representan como EXPIRADA una pendiente cuyo plazo ya terminó, sin escribir en la base. El instante exacto del vencimiento ya está fuera de vigencia.
- Aceptar solo sustituye una reserva pendiente por un vínculo activo. Con tres lugares ocupados se puede aceptar una reserva existente; si Premium venció y todavía hay más de tres lugares ocupados, se rechaza una nueva aceptación hasta liberar el excedente.
- La aceptación vincula la invitación a su acceso concreto mediante V11. Repetirla devuelve ese mismo acceso, incluso si posteriormente se revocó o se creó otro vínculo. No renueva permisos ni genera otra auditoría. Una aceptación histórica sin vínculo verificable exige revisión y no concede acceso automáticamente.
- Rechazar y cancelar son idempotentes cuando ya tienen el mismo estado terminal. Un estado terminal diferente rechaza la operación. El acceso solo se concede al aceptar una pendiente vigente.
- Reenviar una pendiente cancela la anterior y crea la nueva en la misma transacción. Si falla la creación, se conserva la reserva original. Una vencida, rechazada o cancelada también puede originar una nueva; una aceptada exige usar una nueva invitación tras revocar/salir. Repetir un reenvío mientras ya existe otra pendiente al mismo destinatario devuelve conflicto.
- Cambiar al mismo perfil o repetir revocación/salida no agrega eventos. Cambiar el perfil de un vínculo inactivo se rechaza. Se conserva el historial y se libera el cupo al desactivar.
- Se bloquea/refresca primero al propietario y después la invitación o acceso. Pago, renovación y vencimiento comparten ese bloqueo del propietario. La auditoría se encola en la misma transacción y un fallo revierte todas las escrituras.

## Errores

Formato existente: `{"codigo":"...","mensaje":"...","timestamp":"..."}`.

| HTTP | Código o situación |
|---|---|
| 400 | `ERROR_VALIDACION`: email, perfil, JSON o UUID inválido. |
| 401 | Sesión ausente/inválida o cuenta del actor inactiva. |
| 403 | `ACCESO_DENEGADO`: actor ajeno a la operación. |
| 404 | `RECURSO_NO_ENCONTRADO`: recurso inexistente o destinatario no disponible. |
| 405 | `METODO_NO_PERMITIDO`: por ejemplo, intentar aceptar mediante GET. La respuesta incluye `Allow`. |
| 409 | `LIMITE_INVITADOS_ALCANZADO`, `INVITACION_YA_PENDIENTE`, `ACCESO_YA_EXISTENTE`, `AUTO_INVITACION`, `PROPIETARIO_SIN_MASCOTAS`, `PROPIETARIO_INACTIVO`, `INVITACION_NO_PENDIENTE`, `INVITACION_REQUIERE_REVISION` o `ACCESO_INACTIVO`. |
| 410 | `INVITACION_EXPIRADA`. |

No se interpreta una falla de persistencia o auditoría como éxito, cuenta Free ni permiso concedido. Los contratos no entregan entidades JPA, credenciales ni tokens de invitación reutilizables para autorizar acciones sin sesión.

## Lecturas y permisos de recursos C09 a C12

Los endpoints conservan sus campos previos; las respuestas de recordatorios agregan indicadores calculados. `GET /mascotas` incluye mascotas propias y de propietarios que otorgaron un vínculo activo; `propietarioId` permite distinguirlas. Las compartidas no consumen el cupo de mascotas propias. La bandeja `/cuidado-compartido/me` informa los perfiles por propietario; no se agregan roles al JWT.

- Mascotas: ambos perfiles consultan; solo el propietario edita/elimina.
- Eventos clínicos: Familia consulta/crea/edita/elimina; Cuidador consulta. La autoría y auditoría guardan al actor real.
- Recordatorios: todos los perfiles con acceso consultan cualquier fecha, incluidos inactivos. Todos confirman únicamente durante el día correspondiente de Argentina, sin límite por hora. Familia y propietario gestionan y desmarcan; Cuidador consulta y confirma. Los eliminados quedan fuera de consulta.
- Álbumes, fotos y badges: ambos perfiles consultan; solo el propietario escribe. Se exige acceso a todas las mascotas activas del álbum, incluso en los listados; no basta conocer el ID de una foto.

`GET /recordatorios/hoy` no recibe cuerpo ni fecha. Devuelve 200 con un arreglo JSON, vacío cuando no hay registros. Reúne mascotas propias y compartidas; ordena por `fechaHoraInicio` y UUID, y permite agrupar por `mascotaId` en el front. Excluye mascotas/recordatorios eliminados y estado INACTIVO; conserva COMPLETADO para mostrar las confirmaciones.

Cada elemento contiene: `idRecordatorio`, `mascotaId`, `titulo`, `tipo`, `fechaHoraInicio`, `modalidad`, `intervaloValor`, `intervaloUnidad`, `diaSemana`, `fechaFin`, `creadoPorId`, `creadoEn`, `confirmadoPorId`, `confirmadoEn`, `estado`, `situacionTemporal`, `puedeConfirmar`. Los campos opcionales se devuelven como null. Las fechas siguen siendo ISO 8601 UTC.

`estado` conserva el catálogo persistido. `situacionTemporal` se calcula al consultar: `PROXIMO` para fechas futuras sin confirmar, `PENDIENTE` para hoy, `VENCIDO` para fechas pasadas sin confirmar, `COMPLETADO` si ya tiene confirmación o ese estado y `INACTIVO` si está desactivado. `puedeConfirmar` es true solo si está activo, sin confirmar y en su día. Ambos campos también se entregan en los GET generales y respuestas de escritura. El cambio de día no requiere una actualización en base.

`PATCH /recordatorios/{id}/confirmacion` rechaza con 400 `ERROR_NEGOCIO` un pasado, futuro, inactivo o ya confirmado, para cualquier perfil. No sobrescribe autor ni fecha. Cambiar a `COMPLETADO` mediante `/estado?nuevoEstado=COMPLETADO` aplica las mismas validaciones y registra al actor; ese endpoint sigue siendo exclusivo de propietario/Familia. La hora no restringe: antes o después del horario programado, durante ese mismo día, se puede confirmar. La lectura por ID de otras fechas devuelve 200 si el actor tiene acceso.

El día comprende desde las 00:00 de Argentina inclusive hasta las 00:00 siguientes exclusive, calculado con `Clock`. Este endpoint selecciona por `fechaHoraInicio`; no expande recordatorios repetitivos ni crea confirmaciones por ocurrencia. El motor de recurrencias mantiene su backlog propio.

## Correo y contrato con frontend C13

Se envía un correo al destinatario al crear/reenviar, y al propietario cuando se acepta/rechaza. Las respuestas idempotentes no repiten correos; cancelar, cambiar perfil, revocar o salir no generan estos avisos.

Configurar `CUIDADO_COMPARTIDO_URL_INVITACIONES` con la URL absoluta de la pantalla web, sin query, fragmento ni credenciales. Valor local: `http://localhost:5173/cuidado-compartido`. El enlace queda así:

```text
http://localhost:5173/cuidado-compartido?invitacionId=<UUID>
```

El frontend conserva ese ID durante el login y consulta `GET /cuidado-compartido/invitaciones/{id}` con la sesión. Muestra los botones que correspondan y llama al POST de aceptar/rechazar. El UUID identifica el recurso: no es una credencial ni permite responder con otra cuenta. La apertura del enlace nunca modifica permisos.

El transporte usa las variables SMTP existentes `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH` y opciones STARTTLS. Para Mailpit: host del servicio, puerto 1025, sin autenticación ni STARTTLS. Su API web usa 8025.

La respuesta HTTP confirma persistencia, no entrega SMTP. El correo se intenta de forma asíncrona después del commit, con datos capturados durante la transacción. Un fallo se registra sin revertir invitación, cupo o acceso; la bandeja sigue disponible. No hay cola persistente ni reintento automático de correo en este alcance. El propietario puede reenviar una invitación no aceptada, generando otro UUID y una sola reserva.

## Verificación de la entrega

C03 a C08 verificadas el 25/11/2026: 55 pruebas HTTP con PostgreSQL real, incluidas concurrencia, reintentos, cupos y rollback. Suite completa local con 687 pruebas aprobadas y JAR generado. La imagen Docker también verifica el ciclo de gestión por HTTP en recursos aislados, sin SMTP ni MP reales.

C09 a C13 verificadas el 25/11/2026: 29 pruebas nuevas de permisos/correos y suite completa con **716 aprobadas**. Docker verifica además el transporte SMTP real hacia Mailpit y los enlaces de invitación/respuesta, sin servicios externos. El workflow incluye estas integraciones; todavía no se publicó una nueva entrega QA.

Corrección posterior del mismo día: se elimina el límite de lectura por fecha de Cuidador y se aplica la confirmación diaria a los tres perfiles, con `situacionTemporal` y `puedeConfirmar`. Suite completa actual: **727 pruebas aprobadas** y verificación Docker aprobada. El backend de QA local se actualizó preservando datos; el paquete distribuido todavía no se publicó. Reimportar la colección Postman actualizada para obtener los nuevos resultados esperados.

Los datos para creación son únicamente `emailInvitado` y `perfil`; el propietario siempre proviene de la sesión. Las operaciones de respuesta, cancelación, reenvío, revocación y salida no llevan cuerpo. Solo el cambio de perfil recibe un nuevo `perfil`. La autenticación existente proporciona el token de acceso de cada actor.
