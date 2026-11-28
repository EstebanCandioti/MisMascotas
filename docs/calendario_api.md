# Calendario MVP

## Alcance y decisiones

El calendario muestra recordatorios de hoy y futuros de las mascotas propias y compartidas. Cada fila representa una aparición. Los eventos clínicos siguen en su módulo.

Las fechas `desde` y `hasta` son inclusivas y se interpretan en Buenos Aires (`America/Argentina/Buenos_Aires`). El rango admite hasta 366 días, contando ambos extremos. Un rango que empieza antes de hoy devuelve `400`. La web debe enviar hoy como inicio cuando el mes visible incluye días anteriores.

El listado es plano. La web agrupa las filas por día de Buenos Aires para mostrar el calendario y usa las mismas filas como listado del día. Debe convertir `fechaProgramada` a esa zona antes de agrupar, porque la respuesta usa UTC. El detalle de una fila se consulta con el ID del recordatorio y su `fechaProgramada`.

## Consultar un rango o un día

`GET /calendario?desde=2030-01-01&hasta=2030-01-31`

Ambos parámetros son obligatorios, con formato `AAAA-MM-DD`. Para un solo día se envía la misma fecha en ambos. Requiere un JWT de acceso vigente.

Devuelve `200` y un array JSON. Sin resultados devuelve `[]`. Ordena por `fechaProgramada` y luego por `idRecordatorio`.

```json
[
  {
    "idRecordatorio": "7d3aab78-2392-4da8-b602-b13b01ed8e53",
    "idOcurrencia": null,
    "fechaProgramada": "2030-01-31T21:00:00Z",
    "esPersistida": false,
    "mascotaId": "9d634221-72b1-48de-aedc-659173e7c774",
    "nombreMascota": "Luna",
    "propietarioId": "a65166c6-d0c7-48dc-b94e-c1573752ec28",
    "titulo": "Medicación",
    "tipo": "MEDICACION",
    "fechaHoraInicio": "2030-01-01T21:00:00Z",
    "modalidad": "recurrente",
    "intervaloValor": 1,
    "intervaloUnidad": "dias",
    "diaSemana": null,
    "fechaFin": null,
    "creadoPorId": "a65166c6-d0c7-48dc-b94e-c1573752ec28",
    "creadoEn": "2030-01-01T15:00:00Z",
    "confirmadoPorId": null,
    "confirmadoEn": null,
    "estado": "ACTIVO",
    "situacionTemporal": "PROXIMO",
    "puedeConfirmar": false
  }
]
```

| Campo | Uso |
|---|---|
| `idRecordatorio` | Identifica el recordatorio único o la serie. |
| `fechaProgramada` | Fecha y hora de esta aparición en ISO 8601 UTC. Se usa para mostrarla y pedir su detalle. |
| `idOcurrencia` | UUID de la ocurrencia guardada. Es `null` para un único o una fecha calculada. |
| `esPersistida` | `true` si la aparición ya está guardada. Un único también devuelve `true`. |
| `mascotaId`, `nombreMascota`, `propietarioId` | Identifican la mascota y a su propietario. |
| `titulo`, `tipo` | Describen el recordatorio. Tipos según el contrato de recordatorios. |
| `fechaHoraInicio` | Inicio original de la programación. Puede ser distinto de la fecha de esta aparición. |
| `modalidad`, `intervaloValor`, `intervaloUnidad`, `diaSemana`, `fechaFin` | Programación actual, con los mismos nombres y valores de la respuesta de recordatorios. |
| `creadoPorId`, `creadoEn` | Autor y fecha de creación. |
| `confirmadoPorId`, `confirmadoEn` | Confirmación de la aparición seleccionada, si existe. |
| `estado` | Estado del recordatorio o de la serie. |
| `situacionTemporal` | `PENDIENTE` para hoy sin confirmar, `COMPLETADO` si se confirmó, `PROXIMO` para una fecha futura. |
| `puedeConfirmar` | Solo es `true` para una aparición guardada de hoy que todavía puede confirmarse. |

La clave de una fila del calendario es la pareja `(idRecordatorio, fechaProgramada)`. Los campos nulos se incluyen en este DTO. El calendario permite ver futuras apariciones aunque todavía no tengan `idOcurrencia`.

## Consultar el detalle

`GET /calendario/{idRecordatorio}?fechaProgramada=2030-01-31T21%3A00%3A00Z`

`fechaProgramada` es obligatoria. Se envía exactamente el instante de la fila seleccionada, codificado como parámetro de URL. También admite el mismo instante expresado con offset, por ejemplo `2030-01-31T18:00:00-03:00`.

Devuelve `200` y un objeto con los mismos campos de una fila. El backend vuelve a comprobar que el usuario tenga acceso a la mascota y que la aparición siga disponible en esa fecha. Una edición puede cambiar una fecha calculada. Una pausa, un borrado o una revocación de acceso también pueden invalidar un detalle que la web tenía abierto.

## Cálculo de fechas y conservación de datos

El listado combina únicos y ocurrencias guardadas con fechas calculadas de las series indefinidas activas. Usa `CalculadorOcurrencias` y conserva el inicio original como referencia para días, semanas, meses, años y días de la semana.

Las fechas calculadas empiezan después de `ultimaFechaGenerada` y siempre después de hoy. No se completan huecos dentro de una tanda, lo que conserva el comportamiento de las pausas y las ediciones. Las series finitas usan sus ocurrencias guardadas. Las series anteriores a REC sin `ultimaFechaGenerada` conservan la lectura de sus datos guardados y requieren edición o reactivación para preparar tandas, según el contrato existente.

Consultar el listado o un detalle no guarda ocurrencias, no avanza `ultimaFechaGenerada` y no genera auditorías de alta. Se usa una transacción de solo lectura con una vista consistente de PostgreSQL para combinar los datos. El job sigue generando tandas de 12. Las confirmaciones conservan sus endpoints y reglas actuales.

Las apariciones guardadas prevalecen sobre las calculadas del mismo día y serie. Se conserva el historial al editar, pausar o borrar, aunque el calendario no ofrece fechas anteriores a hoy. Los recordatorios y mascotas eliminados, y los recordatorios inactivos, quedan fuera de los resultados.

No se agregó una migración. El esquema continúa en Flyway V12.

## Permisos y errores

Propietario, Familia y Cuidador pueden consultar sus mascotas accesibles con las mismas reglas actuales. El plan del invitado no restringe la lectura. El calendario no agrega permisos de escritura. Revocar un vínculo quita la lectura en la siguiente consulta, incluso con el mismo JWT.

| HTTP | Código y causa |
|---|---|
| `400` | `ERROR_VALIDACION`: falta un parámetro, formato inválido, fecha anterior a hoy, extremos invertidos, más de 366 días o fecha posterior al año 9999. |
| `401` | Sesión ausente o inválida. |
| `403` | `ACCESO_DENEGADO`: el actor ya no puede consultar la mascota del detalle. |
| `404` | `RECURSO_NO_ENCONTRADO`: recordatorio o mascota eliminados, ID inexistente o aparición no disponible en la fecha pedida. |

Los errores mantienen el formato `{"codigo":"...","mensaje":"...","timestamp":"..."}`. Un listado sin acceso a cierta mascota omite sus filas.

## Pruebas y Postman

`CuidadoPermisosPostgresIT` prueba los endpoints con PostgreSQL real: rango inclusivo, límite de 366 días, validaciones, intervalos, días de semana, meses cortos, combinación sin duplicados, lectura sin escrituras, detalle, confirmaciones y permisos revocados. Estas pruebas forman parte del CI existente.

En [la colección de recordatorios](../postman/MisMascotas%20Recordatorios%20Recurrentes%20REC-01%20a%20REC-04.postman_collection.json), ejecutar la carpeta **04 — Calendario MVP** con el entorno **MisMascotas Local**. Primero iniciar sesión con la carpeta 00 y completar `rec04MascotaId` con una mascota propia de QA. La carpeta 04 crea sus recordatorios, comprueba los casos y los elimina lógicamente al terminar. No requiere cambiar email, contraseña o token en sus solicitudes.

La prueba de la imagen Docker en `ci/verificar_recordatorios_qa.py` comprueba también el calendario, su detalle y la ausencia de escrituras con el backend empaquetado.

### Corridas que cruzan medianoche

Las variables `calHoy`, `calFechaHoy` y las respuestas guardadas pertenecen a una misma corrida. La solicitud 05 compara la aparición del día consultado con la que guardó 02. Cambiar solamente `calHoy` después de medianoche mezcla apariciones de días distintos.

Si cambia el día de Buenos Aires, ejecutar 17 para eliminar la serie anterior y volver a correr la carpeta desde 01. La colección registra `calDiaCorrida` y detiene las solicitudes que dependen de fechas si cambió el día o se modificaron las fechas por separado. Las solicitudes 17 y 18 siguen disponibles para limpiar la corrida anterior. Una variable de entorno con el mismo nombre tiene prioridad sobre la de colección y debe quitarse si impide recalcular las fechas.

La prueba 05 sigue comparando todos los campos y ahora indica cuál difiere. Estos cambios afectan únicamente a Postman y no requieren reconstruir Docker.

### Resultados de validación — 27/11/2026

| Ejecución | Resultado |
|---|---|
| Suite completa seleccionada por `.github/workflows/backend-qa.yml` | 844 pruebas, sin fallos, errores ni omitidas. |
| Casos específicos `CuidadoPermisosPostgresIT#calendario*` | 20 pruebas HTTP/PostgreSQL, sin fallos. Incluyen dos casos adicionales de mascota eliminada y tanda vencida. |
| Postman, carpeta 04 | 18 solicitudes y 32 verificaciones, sin fallos. |
| Postman, carpetas anteriores 01, 02 y 03 | 60 solicitudes y 118 verificaciones, sin fallos. Inicio de sesión probado con y sin entorno. |
| Imagen Docker y PostgreSQL temporal | Calendario, detalle, permisos, ausencia de escrituras y regresión de Premium, cuidado compartido y recurrencias correctos. |
| QA local `mismascotas-qa` | Backend saludable con `mismascotas-backend:calendario-validacion`, listado HTTP 200, Flyway V12 y volumen `mismascotas-qa_datos_qa` conservado. Respaldo previo verificado. |

La prueba Docker espera a que termine la renovación del job tras el reinicio antes de comprobar su resultado. La disponibilidad HTTP puede adelantarse a la finalización de `ApplicationReadyEvent`. La cantidad esperada de ocurrencias se sigue comprobando exactamente.
