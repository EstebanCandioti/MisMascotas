# API de recordatorios recurrentes

## Alcance y autenticación

Contrato del backend al cierre de REC-09. Cada ruta exige un access token JWT en `Authorization: Bearer <token>`. La identidad del actor se obtiene de la sesión y los permisos de la mascota se evalúan en cada solicitud.

| Operación | Propietario | Familia | Cuidador |
|---|---|---|---|
| Consultar | Sí | Sí | Sí |
| Crear, editar, pausar, reactivar y eliminar | Sí | Sí | |
| Confirmar hoy | Sí | Sí | Sí |
| Desmarcar una confirmación | Sí | Sí | |

## Rutas y respuestas

| Método y ruta | Respuesta exitosa |
|---|---|
| `POST /mascotas/{mascotaId}/recordatorios` | `201`, recordatorio creado |
| `GET /mascotas/{mascotaId}/recordatorios` | `200`, arreglo de recordatorios y series |
| `GET /recordatorios/{id}` | `200`, recordatorio o serie |
| `GET /recordatorios/hoy` | `200`, arreglo de únicos y ocurrencias de hoy |
| `PUT /recordatorios/{id}` | `200`, recordatorio editado |
| `PATCH /recordatorios/{id}/estado?nuevoEstado=INACTIVO` | `200`, serie pausada |
| `PATCH /recordatorios/{id}/estado?nuevoEstado=ACTIVO` | `200`, serie reactivada |
| `PATCH /recordatorios/{id}/confirmacion?idOcurrencia={uuid}` | `200`, ocurrencia confirmada |
| `DELETE /recordatorios/{id}/confirmacion?idOcurrencia={uuid}` | `200`, ocurrencia desmarcada |
| `DELETE /recordatorios/{id}` | `204`, sin cuerpo |

`{id}` es el ID del recordatorio o serie. `{uuid}` es el ID de la ocurrencia concreta. En únicos se omite `idOcurrencia`. Si una fila conserva una ocurrencia de antes de cambiar su modalidad a única, se envía su UUID.

La ruta de estado permite confirmar con `nuevoEstado=COMPLETADO&idOcurrencia={uuid}`, con permisos de gestión. El estado persistido de una serie confirmada sigue siendo `ACTIVO`. El estado de esa fecha se informa en `situacionTemporal`.

## Alta y edición

POST y PUT reciben JSON con título, tipo e inicio obligatorios. PUT envía la programación completa.

```json
{
  "titulo": "Medicación",
  "tipo": "MEDICACION",
  "fechaHoraInicio": "2030-01-01T21:00:00Z",
  "modalidad": "recurrente",
  "intervaloValor": 2,
  "intervaloUnidad": "dias",
  "diasSemana": null,
  "fechaFin": null
}
```

| Campo | Formato y regla |
|---|---|
| `titulo` | Texto obligatorio |
| `tipo` | `PIPETA_ANTIPARASITARIO`, `VISITA_VETERINARIO`, `MEDICACION`, `VACUNA` u `OTRO` |
| `fechaHoraInicio` | Instante ISO 8601 con zona u offset |
| `modalidad` | `unica`, `recurrente` o `dias_semana`. Omitirla equivale a `unica`. |
| `intervaloValor` | Entero JSON positivo, obligatorio para `recurrente` |
| `intervaloUnidad` | `dias`, `semanas`, `meses` o `anios`, obligatorio para `recurrente` |
| `diasSemana` | String de nombres completos en español separados por coma, obligatorio para `dias_semana`. Por ejemplo `LUNES,MIERCOLES`. |
| `fechaFin` | Instante inclusivo igual o posterior al inicio. `null` en una serie significa continuidad por tandas. |

Los campos de intervalo se envían ausentes o nulos para las modalidades que no los usan. Lo mismo aplica a `diasSemana`. Se rechazan combinaciones incompatibles, intervalos decimales y días repetidos. Los nombres de días admiten tildes y mayúsculas o minúsculas. La respuesta devuelve el string normalizado en `diaSemana`.

Una serie finita genera todas sus fechas con un máximo de 366. Una serie indefinida empieza con 12. Para meses cortos se usa el último día disponible y se recupera el día original en el mes que lo tenga. El job renueva al arrancar y a medianoche argentina. El cálculo mantiene el ritmo anclado al inicio original.

## Campos de respuesta y uso en la web

La respuesta conserva `idRecordatorio`, `mascotaId`, título, tipo, programación, autoría de creación, confirmación y estado. Las filas de ocurrencias agregan `idOcurrencia` y `fechaProgramada`. Los campos de ocurrencia se omiten en las respuestas de la serie y los únicos.

La web debe usar `idOcurrencia` para identificar una fila recurrente y `idRecordatorio` para gestionar la serie. Para mostrar el horario de una fila se usa `fechaProgramada` cuando está presente y `fechaHoraInicio` en los únicos. Las fechas se entregan en ISO 8601 UTC.

`GET /recordatorios/hoy` reúne las mascotas accesibles al actor y devuelve fechas desde las 00:00 de Buenos Aires inclusive hasta las 00:00 siguientes exclusive. Ordena por fecha efectiva e ID del recordatorio. Una lectura conserva el estado de la base.

| Campo | Interpretación |
|---|---|
| `estado` | Estado persistido del recordatorio o serie |
| `situacionTemporal` | `PENDIENTE`, `COMPLETADO`, `VENCIDO`, `PROXIMO` o `INACTIVO` según la fila y la fecha |
| `puedeConfirmar` | Activo, pendiente y en el día argentino correspondiente |
| `confirmadoPorId`, `confirmadoEn` | Autor e instante de confirmación de esa ocurrencia, o del único |

La confirmación se permite a cualquier hora del día correspondiente. Las fechas pasadas y futuras se rechazan. Repetir una confirmación conserva al primer autor.

## Edición, pausa y reactivación

Editar la programación conserva pasado, hoy y confirmaciones, y aplica las nuevas fechas desde mañana. Cambiar título o tipo conserva las ocurrencias.

Pausar retira las pendientes de hoy y posteriores. Reactivar retoma desde hoy con la frecuencia original y conserva las confirmaciones. Las fechas transcurridas de la pausa quedan fuera de la regeneración. Si la serie tiene fin y ya no queda una fecha válida, la reactivación falla y sigue inactiva.

El borrado es lógico. Las ocurrencias guardadas conservan su historial en la base. Una serie borrada deja de aparecer en la API y de renovar sus tandas.

## Errores

Los errores devuelven `{"codigo":"...","mensaje":"...","timestamp":"..."}`.

| HTTP | Código y causa |
|---|---|
| `400` | `ERROR_VALIDACION` para programación incompatible o selector omitido en una serie |
| `400` | `ERROR_NEGOCIO` para confirmación fuera del día, duplicada o inactiva, o reactivación sin fechas elegibles |
| `401` | Sesión ausente o inválida |
| `403` | `ACCESO_DENEGADO` para permisos insuficientes |
| `404` | `RECURSO_NO_ENCONTRADO` para recordatorio eliminado, ocurrencia inexistente o de otra serie |

## Datos anteriores y preparación QA

Flyway V12 conserva recordatorios y confirmaciones anteriores. Las series anteriores sin ocurrencias mantienen su detalle. Un cambio de programación o una reactivación permite preparar sus siguientes fechas.

Usar [la colección de recordatorios](../postman/MisMascotas%20Recordatorios%20Recurrentes%20REC-01%20a%20REC-04.postman_collection.json) y el entorno **MisMascotas Local**. Las carpetas 01 y 02 requieren una cuenta propietaria y una mascota QA propia. La carpeta opcional 03 requiere además un token de invitado con acceso Familia aceptado. Esta última cambia el perfil a Cuidador y lo restaura a Familia al terminar.

La renovación por reinicio, la migración, los permisos y la conservación de fechas se verifican en CI mediante `ci/verificar_imagen_qa.py` y `ci/verificar_recordatorios_qa.py`. Las carreras y los rollbacks se cubren con PostgreSQL en `CuidadoPermisosPostgresIT`.

El [plan del módulo](plan_recordatorios_recurrentes.md) registra criterios y resultados de cierre.
