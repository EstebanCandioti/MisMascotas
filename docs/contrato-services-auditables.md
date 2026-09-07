# Contrato para services auditables

Este documento define las convenciones que debe cumplir cualquier metodo de service anotado con `@Auditable` para que el aspecto de auditoria registre correctamente la accion.

## Uso de la anotacion

La anotacion se aplica sobre metodos de service:

```java
@Auditable(entidad = "Mascota", accion = TipoAccionAuditoria.CREATE)
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

El objeto de respuesta debe exponer un getter publico cuyo nombre empiece con `getId` y cuyo retorno sea `UUID`, por ejemplo:

```java
getIdMascota()
getIdAlbum()
getIdRecordatorio()
```

Si el metodo devuelve `void`, `null` o un objeto sin getter de ID compatible, no se puede completar `id_entidad`.

## Actor de la accion

El actor se resuelve desde el contexto de seguridad de Spring.

- Si existe un usuario autenticado, se busca por el email disponible en `Authentication.getName()`.
- Si no hay usuario autenticado, el actor queda en `null`.

Esto permite registrar tanto acciones de usuarios como acciones automaticas del sistema, de acuerdo con RN-023.

## Fallas silenciosas

El aspecto no debe interrumpir la operacion principal si falla la auditoria. Si no puede extraer el ID, serializar valores, obtener estado anterior o guardar el log, registra el problema en logs tecnicos y la accion principal continua.

Por este motivo, incumplir este contrato produce una falla silenciosa de auditoria, no un error visible para el usuario.

## Transaccion de escritura del log

La escritura del log usa una transaccion independiente para cumplir CU24 E.1: si falla el registro de auditoria, la accion principal no debe revertirse.

Trade-off aceptado: si la accion principal falla despues de que el advice registro el log, puede quedar una entrada de auditoria de una accion que finalmente no se completo.

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