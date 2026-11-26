# Cuidado compartido del backend

## Objetivo y alcance del MVP

Permitir que un propietario invite a usuarios registrados al cuidado de todas sus mascotas, mediante los perfiles Familia y Cuidador. La entrega se presenta en web. El backend conserva autorización por operación, cupos según el plan actual, trazabilidad y revocación inmediata.

Base: `origin/backend`, commit `46f9803`, con Premium integrado. Rama de trabajo: `feature-cuidado-compartido`. Referencias funcionales: CU4, CU25 y CU26. Este documento incorpora las decisiones posteriores acordadas con el equipo y prevalece sobre las descripciones anteriores que queden desactualizadas.

## Reglas acordadas

- El vínculo es entre propietario e invitado; no se crea un permiso independiente para cada mascota. Comprende las mascotas activas del propietario, incluidas las que registre después. Las eliminadas quedan fuera de consulta mediante la regla existente de recurso no encontrado.
- Solo el propietario invita y administra los accesos que otorga. Un invitado no puede volver a compartir mascotas ajenas. Cada vínculo tiene un perfil; ese perfil no es un rol global del usuario.
- Free tiene tres lugares: accesos activos más invitaciones pendientes no expiradas. Premium no tiene cupo de invitados. Se utiliza `PremiumService` para evaluar al propietario.
- El cupo se comprueba al invitar y al aceptar. Las invitaciones pendientes reservan lugar. Al aceptar, ese lugar se transforma en acceso activo, sin contar dos veces al mismo destinatario.
- Si Premium vence, se conservan los accesos existentes. Mientras el total sea tres o más, no se agregan invitados en Free. Con dos accesos y ninguna pendiente se permite invitar a uno más. No hay una prohibición permanente por haber tenido Premium.
- Las invitaciones vencen a las 48 horas; en el instante exacto del vencimiento ya no pueden aceptarse ni ocupan cupo. Los estados terminales se conservan como historial.
- Se invita por email normalizado a una cuenta registrada y activa. Se rechazan la auto-invitación, un acceso ya activo y una invitación pendiente vigente al mismo destinatario.
- Aceptar o rechazar exige la sesión de la cuenta destinataria. Los correos llevan a la pantalla web; un GET o la apertura de un enlace no concede acceso.
- Reenviar genera una invitación nueva con otro UUID; no revive un identificador vencido, rechazado o cancelado. La revocación o salida tampoco se revierte sin una nueva aceptación.
- Los cambios de perfil, la revocación y la salida se reflejan en la siguiente operación autorizada. No se guarda el perfil compartido en el JWT ni se cachean decisiones.
- Email y bandeja web para el MVP. Push y habilitar a Familia para gestionar contenido multimedia quedan fuera del alcance.
- El actor real se conserva en auditoría. El plan y el propietario del recurso se resuelven por separado del actor autenticado.

Las mascotas nuevas se incluyen por la relación entre cuentas, sin replicar permisos por mascota. Si se elimina la última mascota, el vínculo queda sin recursos visibles; no se revocan otros accesos automáticamente. No se permite una nueva invitación si el propietario no tiene mascotas activas, conforme a la precondición de CU4.

## Matriz de permisos

| Operación | Propietario | Familia | Cuidador |
|---|---|---|---|
| Consultar mascotas e historial clínico | Sí | Sí | Sí |
| Crear, editar o eliminar mascotas propias | Sí, con su cupo de plan | No | No |
| Crear, editar o eliminar eventos clínicos | Sí | Sí | No |
| Crear, editar o eliminar recordatorios | Sí | Sí | No |
| Consultar recordatorios de cualquier fecha | Sí | Sí | Sí |
| Confirmar recordatorios durante su día correspondiente | Sí | Sí | Sí |
| Desmarcar confirmaciones | Regla de edición | Regla de edición | No concedido en este alcance |
| Consultar álbumes, fotos y badges accesibles | Sí | Sí | Sí |
| Crear, modificar o eliminar álbumes, fotos y badges | Solo propietario, sujeto al plan | No | No |
| Invitar, cancelar invitaciones, cambiar perfiles y revocar | Solo sobre sus vínculos | No | No |
| Aceptar/rechazar invitación o salir del cuidado | Como destinatario de su vínculo | Como destinatario de su vínculo | Como destinatario de su vínculo |

Todos los perfiles confirman únicamente durante el día correspondiente, sin límite por hora. La consulta admite cualquier fecha. El día se calcula con `Clock` en `America/Argentina/Buenos_Aires`. Las restricciones Premium de álbumes, fotos, foto de perfil y badges están implementadas y documentadas en sus contratos por módulo. El propietario puede eliminar su contenido incluso sin Premium; el acceso compartido sigue sin conceder escritura de esos recursos a invitados.

## Arquitectura y límites de cada entrega

- Conservar `AccesoMascotaService` como entrada de autorización y listado de mascotas visibles. Incorporar operaciones explícitas de lectura y escritura. No ampliar `verificarAcceso` indiscriminadamente: sus consumidores actuales también lo usan para escribir.
- Bloquear y refrescar al propietario antes de comprobar cupo o modificar invitaciones/accesos. Mantener el mismo orden que pagos y mascotas: propietario primero y recurso después. Revalidar bajo bloqueo los datos cargados antes de una llamada o por Open EntityManager in View.
- El cupo y la deduplicación de pendientes son decisiones transaccionales, no un límite en el front. Las consultas reciben un único instante y excluyen expiradas aunque el proceso de materialización todavía no haya cambiado su estado.
- La base impide dos accesos activos para el mismo par de cuentas. Los accesos inactivos conservan su historial y no consumen cupo.
- La auditoría de cambios se encola en la misma transacción. Un fallo debe revertir invitación/acceso/cupo y su auditoría. No se hacen llamadas SMTP dentro del bloqueo.
- Los endpoints devuelven DTO explícitos, nunca entidades JPA. Se documentarán cuerpos, respuestas, códigos y ejemplos antes de implementar cada contrato HTTP.
- Las migraciones publicadas y B9 son inmutables. V10 amplía el esquema; las instalaciones nuevas y las QA existentes pasan por el mismo cambio.

## Backlog verificable

Cada fila es una entrega comprobable por separado. Se actualiza el estado solo con su evidencia; las tareas posteriores no se consideran terminadas por tener entidades o endpoints parciales.

| ID | Entrega concreta | Criterio de cierre | Dependencia | Estado |
|---|---|---|---|---|
| C01 | Reglas, permisos y límites del MVP | Documento de decisiones, matriz y tareas trazables; riesgos de autorización identificados. | Ninguna | Completada |
| C02 | Modelo, repositorios y migración V10 | Entidades separadas, RECHAZADA disponible, consultas de cupo/visibilidad, acceso activo único, migración con datos preservados y pruebas PostgreSQL. | C01 | Completada |
| C03 | Crear invitación con reserva de cupo | POST con email/perfil; valida propietario, cuenta, plan, duplicados y 48 h. Dos solicitudes simultáneas no exceden tres lugares Free. Auditoría atómica; todavía sin SMTP. | C02 | Completada |
| C04 | Consultar bandejas propias | GET de enviadas/recibidas y accesos propios; DTO por principal, estados y expiración efectiva; ningún ID ajeno permite consultar otra cuenta. | C03 | Completada |
| C05 | Aceptar o rechazar invitaciones | Solo destinatario; aceptación atómica con un acceso; rechazar no concede permisos; repetición y carrera con expiración/cambio de plan no duplican ni exceden cupo. | C03 | Completada |
| C06 | Cancelar y volver a invitar | Solo propietario cancela pendientes; el UUID anterior no puede aceptarse; una nueva invitación al destinatario cerrado recibe otro UUID sin consumir dos lugares. | C05 | Completada |
| C07 | Cambiar perfil de un acceso | Solo propietario; cambio efectivo en la siguiente operación y auditado; no modifica otros vínculos ni el propietario de las mascotas. | C05 | Completada |
| C08 | Revocar acceso y salir del cuidado | Propietario revoca; invitado sale de su vínculo; dejan de ver recursos inmediatamente, liberan cupo y las repeticiones no duplican auditoría. | C05 | Completada |
| C09 | Autorización explícita y mascotas visibles | Lectura propia o compartida activa; escrituras de mascota solo del propietario; incluye mascotas futuras y excluye eliminadas; no abre escrituras de otros módulos. | C07, C08 | Completada |
| C10 | Permisos de eventos clínicos | Familia puede consultar/crear/editar/eliminar; Cuidador solo consulta; actor real en auditoría; acceso revocado o recurso ajeno se rechaza. | C09 | Completada |
| C11 | Permisos de recordatorios | Familia gestiona; todos consultan cualquier fecha y confirman solo durante el día correspondiente; reversiones según matriz; misma confirmación visible para todos. | C09 | Completada |
| C12 | Lectura compartida de multimedia | Familia y Cuidador ven álbumes, fotos y badges; todas sus escrituras siguen reservadas al propietario; se prueban accesos indirectos por ID. | C09 | Completada |
| C13 | Email y enlaces para el front web | Correo de invitación y respuesta enlaza la pantalla autenticada; Mailpit verificable; un fallo SMTP no pierde la invitación ni duplica cupo/acceso; bandeja sigue disponible. | C04, C05, C06 | Completada |
| C14 | Cierre integral y entrega QA | Flujo completo por HTTP, permisos negativos, concurrencia, rollback, expiración y plan actual; suite/CI/Docker QA, contratos/Postman y PR con evidencia. | C10, C11, C12, C13 | Pendiente |

## Entrega C01 y C02

En la primera etapa se cerraron C01 y C02. En ese cierre todavía no se exponían endpoints de invitación ni se ampliaban permisos de lectura/escritura. El flujo HTTP se incorpora en la entrega C03 a C08 descrita al final.

Las tareas de este documento corresponden al backend. La pantalla web se implementa por el equipo de frontend utilizando los contratos de C03 a C08 y los enlaces de C13.

### Decisiones de persistencia de C02

- `PerfilCompartido` conserva los valores persistidos `Familia` y `Cuidador`; `AccesoCompartido` pasa a ser una entidad pública separada.
- V10 agrega `INVITACION/RECHAZADA`, normaliza emails existentes y completa las expiraciones faltantes con creación más 48 horas. No modifica expiraciones ya registradas.
- Invitaciones con email vacío, plazos inválidos, accesos a uno mismo o accesos activos duplicados requieren corregir los datos antes de migrar. No se eliminan ni fusionan silenciosamente.
- No se crea un índice temporal con `now()` ni un estado booleano paralelo para pendientes. La unicidad de pendientes vigentes se controla bajo bloqueo del propietario en C03; la unicidad de accesos activos sí queda protegida por PostgreSQL desde C02.
- La unicidad global anterior del par propietario/invitado se reemplaza por unicidad solo entre accesos activos. Así pueden coexistir vínculos inactivos históricos con una nueva aceptación, sin conceder dos accesos activos.

### Evidencia de cierre de C01 y C02

Completadas el 25/11/2026 en `feature-cuidado-compartido`.

- 23 pruebas dirigidas aprobadas: 15 de migraciones y 8 de persistencia del cuidado compartido. Se prueban aislamiento de consultas, perfiles, normalización, vencimiento exacto, historial inactivo y unicidad activa ante dos inserciones simultáneas.
- Suite completa local de CI: **632 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. Se incorpora `CuidadoCompartidoBasePostgresIT` a CI con `RUN_POSTGRES_CUIDADO_IT=true`.
- Imágenes locales construidas con los Dockerfile del repositorio. La comprobación Docker adopta B9 y aplica V10, conserva los conteos del seed y la persistencia al recrear la base, y mantiene aprobados los nueve escenarios Premium existentes.
- Las migraciones verifican actualización desde bases anteriores, conservación de vínculos, rechazo de datos incompatibles sin borrado, reanudación tras corregir duplicados y restauración del seed sobre V10. La actualización futura simulada pasa a V11, exclusivamente en recursos de prueba.
- `git diff --check` y verificador de configuración empaquetada aprobados. Las bases, cuentas y credenciales son ficticias; no se usa Supabase ni se envían correos o cobros reales. Los recursos Docker temporales se retiran al finalizar.

Salidas locales de esa entrega: `backend/target-cuidado-c02.log`, `backend/target-cuidado-c02-ci.log` y `backend/target-cuidado-c02-qa.log`. Las consultas y restricciones de persistencia verificadas forman la base del flujo HTTP posterior.

## Entrega C03 a C08

Completada el 25/11/2026 en `feature-cuidado-compartido`. El [contrato HTTP](cuidado_compartido_api.md) define las once rutas, solicitudes, DTO y errores. Las cuentas se resuelven por sesión, los perfiles son contextuales y la auditoría conserva al actor real.

`CupoCuidadoService` centraliza el límite de tres y consulta `PremiumService` para el propietario. Los escritores bloquean/refrescan primero a ese propietario y luego la invitación o acceso. La aceptación sustituye una reserva; la creación necesita un lugar nuevo. La expiración efectiva se consulta sin escrituras ni proceso programado obligatorio. El correo pertenece a C13 y no se ejecuta en estas transacciones.

V11 agrega la relación única entre invitación y acceso aceptado. Una repetición devuelve el acceso original, incluso inactivo; no reanima un vínculo revocado ni devuelve otro creado posteriormente. El reenvío genera otro UUID y, si la nueva reserva falla, revierte también la cancelación de la anterior. No se infieren asociaciones históricas sin evidencia. La actualización futura simulada de las pruebas pasa a V12.

### Evidencia de cierre

- **78 pruebas dirigidas aprobadas**: 15 de migraciones, 8 de persistencia base y 55 del flujo HTTP C03 a C08 sobre PostgreSQL real.
- Se verificaron cupos activos más pendientes, cambio de Premium a Free, cobertura pagada con indicador falso, vencimiento exacto, normalización de email, validación de perfiles, actores ajenos y bandejas aisladas.
- Las carreras incluyen último lugar disponible, mismo destinatario, doble aceptación, aceptación contra cancelación, revocación contra reaceptación y dos propietarios aceptando entre sí. No duplican reservas, accesos ni auditorías.
- Los fallos de auditoría revierten creación, aceptación, ambos eventos encolados, cambios de perfil y desactivación. Los reintentos válidos conservan idempotencia. Los GET y los errores no conceden accesos ni agregan eventos.
- Suite completa local de CI: **687 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. `CuidadoCompartidoPostgresIT` se incorpora al workflow con `RUN_POSTGRES_CUIDADO_IT=true`.
- Imagen backend construida y flujo por HTTP verificado contra el JAR Docker: invitación, aceptación idempotente, perfil, revocación sin reactivación, rechazo, reenvío, cancelación, salida y cupo Free. Se mantienen los nueve escenarios Premium, la adopción de B9 y actualización a V11, los datos del seed y la persistencia al recrear la base.
- Pasaron `git diff --check`, el verificador de configuración empaquetada y la compilación de los verificadores Python. Los datos son sintéticos, sin SMTP ni MP real. Los entornos temporales se retiran al finalizar.

Salidas: `backend/target-cuidado-c08.log`, `backend/target-cuidado-c08-ci.log` y `backend/target-cuidado-c08-qa.log`. Los cambios permanecen locales; no se ha publicado una nueva entrega QA.

La entrega C09 a C13 conecta esos vínculos a los permisos de recursos y agrega emails. C14 conserva el cierre integral, colección Postman y PR.

## Alcance de implementación C09 a C13

Antes de ampliar permisos se mantienen las comprobaciones de propietario de escritura existentes. Se agregan verificaciones explícitas de lectura y de gestión de salud para Familia; cada consumidor selecciona la operación correcta. La visibilidad usa vínculos activos, sin replicar permisos por mascota ni guardar perfiles en JWT. La revocación y el cambio de perfil se evalúan en las consultas siguientes.

Propietario, Familia y Cuidador consultan cualquier fecha y confirman solo registros cuyo día de `fechaHoraInicio` corresponde al día actual de Argentina. La hora no limita: una consulta programada a las 17 puede confirmarse a las 21. Se conserva el registro de confirmación existente y no se materializan ocurrencias de recurrencias: ese motor tiene su backlog separado. GET `/recordatorios/hoy` es un filtro diario para cualquier perfil. Familia y propietario conservan la gestión y reversión de confirmaciones.

### Corrección funcional acordada — confirmación por día

Se retira la interpretación anterior que limitaba la lectura de Cuidador. La regla diaria pertenece a la operación de confirmar, para todos los perfiles. Se valida en el servidor, también cuando propietario/Familia solicitan estado COMPLETADO por `/estado`.

El DTO agrega `situacionTemporal` (`PROXIMO`, `PENDIENTE`, `VENCIDO`, `COMPLETADO`, `INACTIVO`) y `puedeConfirmar`. Son valores calculados al consultar, sin modificar el catálogo de estados ni necesitar un proceso nocturno. Un registro confirmado conserva COMPLETADO aunque su fecha ya haya pasado. Un inactivo no puede confirmarse. Dos confirmaciones simultáneas se serializan con bloqueo del recordatorio, para conservar una sola autoría; repetir sobre uno confirmado devuelve error de negocio sin sobrescribirlo.

La recurrencia no se expande en este cambio: la regla se evalúa sobre la fecha almacenada del registro actual. La confirmación por ocurrencia sigue pendiente del motor de recurrencias.

Las notificaciones de invitación y de aceptación/rechazo usan eventos de transacción y se envían en forma asíncrona después del commit. El evento contiene datos planos, sin entidades JPA ni lecturas tardías. Un fallo SMTP se registra y no revierte ni duplica la invitación o el acceso; la bandeja permite recuperarla y el reenvío existente genera un nuevo enlace. El envío es de mejor esfuerzo, sin cola de correo persistida ni reintento automático en esta etapa.

El email lleva a la pantalla web autenticada mediante `CUIDADO_COMPARTIDO_URL_INVITACIONES` y el parámetro `invitacionId`. Abrir el enlace no acepta ni rechaza; ambas acciones siguen siendo POST autenticados. La pantalla pertenece al frontend. La configuración predeterminada es `http://localhost:5173/cuidado-compartido` y se debe ajustar al despliegue web real.

## Entrega C09 a C13 — 25/11/2026

- C09: listado de mascotas propias/compartidas, futuras y activas; no hay acceso transitivo. Las compartidas no consumen el cupo de mascotas propias. Revocar o cambiar perfil se aplica con el mismo JWT en la petición siguiente; cuentas inactivas no otorgan cuidado.
- C10: Familia gestiona eventos clínicos conservando al actor real en autoría y auditoría. Cuidador solo consulta. No se permite mover un evento a otra mascota manipulando el cuerpo.
- C11: permisos de gestión, confirmación y reversión según la matriz; GET del día y límites exactos de medianoche argentina. Todos leen los mismos campos de confirmación. La recurrencia mantiene su alcance previo, sin expansión de ocurrencias.
- C12: lectura compartida y escrituras exclusivas del propietario en álbumes/fotos/badges, incluidos IDs indirectos. El listado de álbumes también valida todas sus mascotas. Se corrigió la elección de `mascotaId` en álbumes con varias mascotas: UUID menor, sin consulta singular que falle por múltiples relaciones.
- C13: invitación/reenvío y respuesta al propietario después del commit; destinatarios y enlaces web verificados. No se envía ante rollback ni se duplica ante reintentos de aceptar/rechazar. SMTP fallido conserva el estado confirmado y la bandeja.

### Evidencia

- **29 pruebas nuevas aprobadas**: 15 de matriz HTTP con PostgreSQL real, 8 de correo transaccional con listener real y SMTP simulado, 6 de configuración/ausencia de transporte.
- Suite completa de la selección CI: **716 pruebas, cero fallos, errores u omisiones**; `BUILD SUCCESS` y JAR generado. Se incorporan `CuidadoPermisosPostgresIT` y `CuidadoCorreoPostgresIT` al workflow.
- Imagen Docker reconstruida y verificada con PostgreSQL y Mailpit `v1.31.5` aislados. Se probaron correos de invitación, aceptación, rechazo y reenvío, enlaces sin concesión automática, visibilidad por aceptación y pérdida de lectura tras revocar. Se mantienen gestión, cupo Free, nueve escenarios Premium, migración B9→V11, seed y persistencia al recrear DB.
- `git diff --check`, control de configuración pública y sintaxis Python aprobados. Solo cuentas ficticias; sin proveedor de correo ni pagos externos. Los recursos temporales se retiran al terminar.

Salidas locales: `backend/target-cuidado-c13-ci.log`, `build-cuidado-c13.log` y `verificar-cuidado-c13-docker.log`. Cambios locales en `feature-cuidado-compartido`, sin publicación QA ni PR nueva.

C14 conserva la revisión final de entrega, colección Postman y PR. La implementación de la pantalla web y el motor de recurrencias pertenecen a sus respectivos backlogs; no quedan incluidos en este cierre backend.

## Verificación de la corrección de recordatorios — 25/11/2026

- Consulta de cualquier fecha para los tres perfiles. Confirmación limitada al día argentino, tanto por `/confirmacion` como por cambio a estado COMPLETADO. Se permite confirmar antes o después del horario programado dentro del mismo día.
- Se calculan `situacionTemporal` y `puedeConfirmar`; confirmados anteriores siguen completos, sin convertirlos en vencidos. No se requiere migración ni proceso nocturno.
- Se rechazan confirmaciones de pasados, futuros, inactivos y ya confirmados, sin sobrescribir autor/fecha. La carrera de dos confirmaciones conserva un solo autor.
- **31 pruebas dirigidas aprobadas**: 24 de permisos HTTP con PostgreSQL real y 7 unitarias. Suite completa de CI: **727 pruebas aprobadas, cero fallos, errores u omisiones**, JAR generado.
- Docker con PostgreSQL y Mailpit aislados aprobado: confirma la regla para propietario, Familia y Cuidador y los indicadores temporales sobre el JAR empaquetado. Se preservan seed, migraciones, Premium, gestión y correos.
- Colección Postman actualizada: 89 solicitudes ordenadas, incluidos los rechazos de confirmación por fecha para los tres perfiles y las lecturas ahora permitidas. JSON, variables y sintaxis de los scripts verificados.

Salidas: `backend/target-recordatorios-dia-dirigidas.log`, `backend/target-recordatorios-dia-ci.log`, `build-recordatorios-dia.log` y `verificar-recordatorios-dia-docker.log`. La recurrencia por ocurrencia conserva su backlog independiente.

QA local actualizado con `mismascotas-backend:qa-cuidado-local`: backend saludable en `http://localhost:8080` y Mailpit en `http://localhost:8025`. Se realizó un respaldo previo y se conservaron las 8 cuentas, 16 mascotas, 19 recordatorios, 1 pago, 1 suscripción, 1 invitación y 1 acceso existentes al actualizar. El esquema sigue en V11; este cambio no requirió migración. La publicación del paquete distribuible y la PR siguen pendientes.

## Colección completa ejecutada contra QA — 25/11/2026

Se ejecutaron las **89 solicitudes de la colección** con Newman 6.2.3 contra el QA local actualizado: **89 respuestas esperadas, 106 aserciones aprobadas, cero fallos**. Se usaron dos cuentas nuevas y se automatizó únicamente la lectura de códigos de Mailpit, manteniendo los cuerpos, permisos y resultados esperados de la colección. Se ejercitaron ambas alternativas de sesión —registro y después login— y la renovación de los dos usuarios.

El recorrido cubrió preparación de recursos, invitación/aceptación, idempotencia, Familia, multimedia, cambio a Cuidador con la misma sesión, consulta general, confirmación diaria, revocación, rechazo, reenvío, cancelación y refresh. Los rechazos previstos por permisos o reglas de negocio son casos aprobados.

Se comprobaron nueve correos de las cuentas temporales: cuatro códigos 2FA, tres invitaciones/reenvíos, una aceptación y un rechazo, con enlaces web válidos. Al finalizar se retiraron exclusivamente las cuentas y datos creados para esta ejecución; las cantidades de los datos previos quedaron iguales. Los correos de prueba permanecen en Mailpit como evidencia.

Informe sin contraseñas, códigos ni tokens: `artifacts/testing/cuidado-compartido/20261125T183116399Z-e428fac8/resultado.json`, dentro del workspace principal. El resultado valida la API y el correo; la integración de la pantalla web y el cierre de la PR siguen pendientes.

### Corrección de variables en la ejecución manual 07-02

Un fallo manual `expected [...] to include ''` evidenció que `recordatorioId` podía estar vacío en las variables de colección. Las aserciones leían `pm.collectionVariables.get` mientras las URLs y cuerpos resuelven también el entorno y variables locales; ambos podían usar valores distintos. La ejecución anterior completa tenía sus IDs preparados, por eso pasó; no demostraba que otra sesión manual tuviera esa preparación.

Las aserciones ahora usan `pm.variables.get`, con la misma prioridad que las solicitudes. 07-02 exige IDs UUID de hoy/ayer/mañana y da una indicación explícita cuando falta la preparación 03-03 a 03-05. También comprueba directamente que todos los resultados pertenezcan al día argentino actual y que no incluya inactivos. No se convierte una preparación faltante en una prueba aprobada.

Verificación local con Newman: IDs vacíos producen el fallo de preparación esperado; IDs definidos solo en entorno o en colección aprueban; una respuesta que incluye un futuro falla en fecha e inclusión. Se repitió la colección real contra QA: **89 solicitudes y 109 aserciones aprobadas, cero fallos**, nueve correos verificados y datos temporales retirados. El informe final registra cada aserción, los UUID obtenidos de las respuestas de creación y la respuesta de 07-02: `artifacts/testing/cuidado-compartido/20261125T183939385Z-d4b8b1c9/resultado.json`. Se verificó explícitamente que el ID de hoy esté presente y los de ayer/mañana ausentes. La reproducción de variables está en `artifacts/testing/cuidado-compartido/verificacion-variables-07-02.json` del workspace principal. La lista inicial de variables del SDK de Newman no refleja los valores del sandbox durante la ejecución y no se utiliza como evidencia de preparación.
