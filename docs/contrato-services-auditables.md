# Contrato para servicios auditables

Este documento define los dos mecanismos de auditoría que conviven durante la migración:

- eventos pendientes transaccionales, para flujos nuevos o migrados;
- el aspecto histórico `@Auditable`, que se conserva sin cambios para las operaciones todavía no migradas.

`MascotaService.editar` es el primer flujo que usa eventos pendientes. El resto de las operaciones auditadas continúa usando el aspecto actual.

## Eventos pendientes transaccionales

### Encolar desde un flujo de negocio

El método de negocio debe tener una transacción activa y llamar a `EventoAuditoriaPendienteService.encolar(...)` con datos explícitos:

```java
@Transactional
public Respuesta editar(UUID id, Solicitud solicitud, UUID actorId) {
    Respuesta valorAnterior = obtenerSnapshot(id);
    Respuesta valorNuevo = guardarCambios(solicitud);

    eventoAuditoriaPendienteService.encolar(
            actorId,
            "entidad",
            TipoAccionAuditoria.UPDATE,
            id,
            valorAnterior,
            valorNuevo);
    return valorNuevo;
}
```

El servicio usa `Propagation.MANDATORY`: si el llamador no tiene una transacción activa, la llamada falla. El actor se recibe como parámetro y queda persistido en el evento; el procesamiento posterior no depende de `SecurityContextHolder`.

Los snapshots deben contener los campos funcionalmente necesarios para explicar el cambio y no deben incluir secretos. La serialización y la inserción del evento forman parte de la transacción de negocio. Por eso, un fallo al encolar revierte tanto el cambio como el evento.

### Procesamiento y recuperación

`ProcesadorEventosAuditoriaProgramado` busca periódicamente eventos no procesados. Cada evento se procesa en una transacción independiente:

1. toma la fila con `FOR UPDATE SKIP LOCKED`;
2. crea la fila definitiva en `log_auditoria`;
3. marca el evento con `procesado_en`;
4. confirma ambas escrituras de forma atómica.

`log_auditoria.evento_pendiente_id` es nullable para conservar compatibilidad con filas históricas y único para que un evento no genere dos logs. Si la aplicación se detiene antes de procesarlo, el evento permanece en PostgreSQL y se recupera en un ciclo posterior.

Si falla la escritura del log, esa transacción independiente se revierte y la operación de negocio ya confirmada se conserva. El procesador registra `intentos_procesamiento`, `ultimo_intento_en` y `ultimo_error`, y vuelve a intentar en ciclos posteriores. A partir del tercer fallo también emite un error técnico para revisión.

El tamaño de lote y los intervalos tienen valores seguros por defecto y pueden configurarse externamente con:

- `auditoria.pendientes.tamano-lote`;
- `auditoria.pendientes.intervalo-ms`;
- `auditoria.pendientes.demora-inicial-ms`;
- `auditoria.pendientes.procesamiento-habilitado`.

Este comportamiento implementa CU24, paso 4: no existe log de éxito si la acción principal revierte; un fallo posterior del log no revierte una acción ya confirmada y queda recuperable.

### Base de datos y prueba de integración

La migración `DB/migraciones/20261112_auditoria_eventos_pendientes.sql` crea la cola, el índice de recuperación y la referencia idempotente del log. No debe aplicarse automáticamente sobre Supabase; primero se valida en PostgreSQL dedicado de QA.

Con la migración aplicada y las variables de conexión de Spring configuradas, la prueba real se ejecuta desde `backend` con:

```bash
RUN_POSTGRES_AUDITORIA_IT=true ./mvnw -Dtest=AuditoriaPendientePostgresIT test
```

La prueba verifica la confirmación conjunta de edición/evento, rollback conjunto, rechazo por permisos, exclusión entre dos procesadores y recuperación después de un fallo forzado al insertar el log.

## Aspecto histórico `@Auditable`

## Uso de la anotacion

La anotacion se aplica sobre metodos de service:

```java
@Auditable(entidad = "mascota", accion = TipoAccionAuditoria.CREATE)
```

El atributo `entidad` identifica la entidad afectada. El atributo `accion` indica el tipo de accion registrada en `log_auditoria`.

## Operaciones de modificacion y eliminacion

Para acciones sobre registros existentes, el identificador de la entidad afectada debe ser el primer argumento del metodo.

Ejemplos esperados:

```java
public MascotaResponseDTO editar(UUID idMascota, MascotaRequestDTO request)
public void eliminar(UUID idMascota)
public RecordatorioResponseDTO confirmar(UUID idRecordatorio, UUID usuarioId)
```

El aspecto lee ese primer argumento para completar `id_entidad` y para intentar capturar el estado anterior.

## Captura del estado anterior

Para acciones que modifican registros existentes, el service debe exponer un metodo publico con esta firma:

```java
public Object obtenerPorId(UUID id)
```

El tipo de retorno puede ser una entidad o un DTO. El aspecto invoca ese metodo antes de ejecutar la operacion auditada y serializa su resultado como JSON en `valor_anterior`.

Acciones que intentan capturar estado anterior:

- `UPDATE`
- `DELETE`
- `RESTORE`
- `ACTIVATE`
- `DEACTIVATE`
- `SHARE`
- `UNSHARE`
- `CONFIRM`

## Operaciones de creacion

Para acciones `CREATE`, el aspecto toma el identificador desde el objeto devuelto por el metodo.

El objeto de respuesta debe exponer un identificador publico cuyo nombre empiece con `getId` o `id`, cuyo retorno sea `UUID` y que no reciba parametros. Esto cubre getters JavaBean y accessors de records.

Ejemplos compatibles:

```java
getIdMascota()
getIdAlbum()
idMascota()
idAlbum()
```

Si el metodo devuelve `void`, `null` o un objeto sin identificador compatible, no se puede completar `id_entidad`.


## Actor de la accion

El actor se resuelve desde el contexto de seguridad de Spring.

- Si existe un usuario autenticado, se busca por el email disponible en `Authentication.getName()`.
- Si no hay usuario autenticado, el actor queda en `null`.

Esto permite registrar tanto acciones de usuarios como acciones automaticas del sistema, de acuerdo con RN-023.

## Fallas silenciosas del aspecto histórico

El aspecto no debe interrumpir la operacion principal si falla la auditoria. Si no puede extraer el ID, serializar valores, obtener estado anterior o guardar el log, registra el problema en logs tecnicos y la accion principal continua.

Por este motivo, incumplir este contrato produce una falla silenciosa de auditoria, no un error visible para el usuario.

## Transaccion de escritura del aspecto histórico

La escritura del log usa una transaccion independiente para cumplir CU24 E.1: si falla el registro de auditoria, la accion principal no debe revertirse.

Trade-off histórico: si la accion principal falla despues de que el advice registro el log, puede quedar una entrada de auditoria de una accion que finalmente no se completo. Este comportamiento no se cambia globalmente en este lote; los flujos deben migrarse de forma explícita a eventos pendientes.

## Estado de migración

- `MascotaService.editar`: evento pendiente transaccional; no usa `@Auditable`.
- `MascotaService.crear` y `MascotaService.eliminar`: aspecto histórico.
- Auth, recordatorios, eventos clínicos, álbumes, fotos y badges: conservan el mecanismo previo según sus anotaciones o llamadas directas.

## Casos de uso que deben disparar auditoria

Segun la documentacion vigente, deben auditarse acciones asociadas a:

- CU3
- CU4
- CU6
- CU7
- CU8
- CU11a
- CU11b
- CU11c
- CU25
- CU27

La anotacion de estos services se realizara junto con la implementacion de cada CRUD o flujo funcional.
