# Plan de recordatorios recurrentes

REC-01 a REC-04 entregan la validación de la programación, el cálculo de fechas y la persistencia de las ocurrencias al crear recordatorios. REC-05 a REC-07 incorporan la renovación, la consulta diaria y la confirmación. REC-08 integra la edición, pausa, reactivación y eliminación de series.

REC-09 reúne la cobertura de QA, el [contrato para la web](recordatorios_api.md) y la verificación de las imágenes en CI. La entrega de este módulo corresponde al backend.

## Cierre de REC-09

REC-01 a REC-09 están completadas para el backend. La verificación se ejecutó localmente con la misma selección Maven que usa GitHub Actions y el verificador de imágenes incluido en el workflow.

| Tipo de prueba | Resultado y alcance |
|---|---|
| Maven con PostgreSQL 17.6 | 826 pruebas, cero fallos, errores u omisiones. Incluye migraciones, fechas límite, permisos, carreras y reversión transaccional. |
| Postman con Newman | 60 solicitudes y 118 aserciones de las carpetas 01, 02 y 03, todas exitosas. La preparación usa login y códigos de Mailpit. |
| Imágenes Docker | Migración del seed V9 a V12, alta con y sin fin, renovación por reinicio, segundo reinicio sin duplicados, confirmación, edición, pausa, reactivación, revocación y borrado lógico. |
| Persistencia | El verificador conserva el seed y los datos agregados al recrear PostgreSQL con su volumen. Las fixtures de recurrencias se retiran por sus UUID antes de limpiar las cuentas sintéticas. |

La carpeta opcional `03 — Permisos Familia y Cuidador` requiere un invitado con vínculo Familia aceptado del propietario de `rec04MascotaId`. Completar `rec09TokenInvitado` en la colección o `accessTokenInvitado` en el entorno. La sesión propietaria sigue usando `accessToken`. La prueba cambia el vínculo a Cuidador y lo restaura a Familia en el paso 15.

El CI ejecuta `ci/verificar_recordatorios_qa.py` desde el verificador de imágenes. Los casos nuevos se ejecutan antes de permitir la publicación en Docker Hub. La [matriz Postman](../postman/MATRIZ_COBERTURA.md) y el [contrato web](recordatorios_api.md) registran los escenarios y el uso de los campos de ocurrencia.

## Contrato y alcance del MVP

El backend ya recibe `fechaFin` como `Instant` y PostgreSQL lo guarda en `fecha_fin`, una columna `timestamp with time zone` que admite `NULL`. Se reutiliza ese campo. Para una serie recurrente, una fecha indica el fin de la programación y `null` indica que continúa por tandas. `modalidad` distingue una serie de un recordatorio único. El esquema ya admite `unica`, `recurrente` y `dias_semana`.

`POST /mascotas/{mascotaId}/recordatorios` conserva su respuesta `201` con los datos del recordatorio. Para una serie con fin, guarda todas las ocurrencias desde el inicio hasta `fechaFin` inclusive, con un máximo de 366. Para una serie sin fin, guarda las primeras 12 ocurrencias desde el inicio de la programación y registra la fecha argentina de la última en `ultimaFechaGenerada`. Para `unica`, solo guarda el recordatorio. El alta del recordatorio y sus ocurrencias se hace en una sola transacción.

El job de REC-05 se ejecuta cuando el backend termina de arrancar y a medianoche de Buenos Aires. Busca las series activas sin fin cuya última fecha generada sea hoy o anterior. Si la tanda llega hasta hoy, genera las siguientes 12 fechas a partir de mañana. Si la tanda ya pasó, retoma desde hoy con la frecuencia original. Las fechas sin generar durante la caída quedan fuera del MVP. Guarda la tanda y su última fecha en una transacción por serie. Un error en una serie permite continuar con las demás.

Las consultas leen las ocurrencias guardadas. La generación es independiente de la confirmación. Se conservan los permisos de cuidado compartido, la confirmación durante el día argentino correspondiente sin restricción horaria y el contrato de recordatorios únicos. Calendario por rango, push y edición individual de una ocurrencia quedan como tareas separadas.

## Tareas y criterios de cierre

| ID | Entrega | Cómo se verifica | Depende de |
|---|---|---|---|
| REC-01 | **Cerrar la matriz de validación y el contrato.** Revisar los clientes y los datos existentes. Definir campos obligatorios e incompatibles por modalidad, formato de `diasSemana`, selector de ocurrencia y límites por serie. | Hay ejemplos JSON válidos e inválidos para los tres modos y un resultado esperado por caso. El alta con modalidad omitida conserva `unica`. Las incompatibilidades devuelven `400` con un motivo claro. Queda documentado cómo se tratan los registros previos y la unidad `anios` que hoy admite PostgreSQL. | — |
| REC-02 | **Calcular fechas en un componente puro.** Avanzar por días, semanas, meses o días semanales desde la fecha inicial, manteniendo la hora local. Calcular tanto una cantidad fija como las ocurrencias hasta una fecha o desde un día de corte. | Pruebas unitarias con fechas exactas. Cada 10 días desde el 20 de julio da 20 y 30 de julio y 9 de agosto. Lunes y miércoles con inicio un martes arranca el miércoles. Cubrir meses cortos, bisiestos, medianoche y fin según las reglas de REC-01. | REC-01 |
| REC-03 | **Persistir ocurrencias y progreso de la tanda.** Agregar una migración Flyway, identidad única de ocurrencia por serie y fecha programada, índice para buscar series pendientes y espacio para autor y fecha de confirmación. | Una base vacía y una actualizada arrancan. Los recordatorios únicos y sus confirmaciones conservan sus datos. PostgreSQL impide duplicados de serie y fecha. El plan de la consulta puede usar el índice. | REC-01 |
| REC-04 | **Validar y generar al crear.** Aplicar la matriz antes de escribir. Con fin, guardar todas las fechas válidas dentro del límite. Sin fin, guardar la primera tanda y registrar su última fecha. | Prueba HTTP/PostgreSQL que comprueba las fechas y la cantidad exactas de ambas variantes. Un intervalo inválido o una serie por encima del límite deja la base y la auditoría sin cambios. Una falla de inserción revierte el alta completa. | REC-02, REC-03 |
| REC-05 | **Renovar tandas con el job.** Ejecutar al arrancar y una vez al día. Seleccionar únicamente series activas sin fin cuya última fecha generada sea `<= hoy`. Agregar la tanda y avanzar la fecha en la misma transacción. | Con reloj fijo, probar el día anterior y el mismo día de vencimiento de la tanda, una fecha posterior a la caída y un reintento concurrente. El día anterior no agrega filas. El mismo día se agrega una sola tanda aunque no se confirme la última ocurrencia. Tras una caída, la próxima tanda se calcula desde hoy, anclada al inicio original. Las series con fin o inactivas quedan excluidas. | REC-04 |
| REC-06 | **Mostrar las ocurrencias de hoy.** Integrar las ocurrencias guardadas a `GET /recordatorios/hoy` junto con los únicos y sus indicadores temporales. | HTTP/PostgreSQL comprueba que la respuesta incluye solo registros del día argentino y de mascotas accesibles, con orden estable. Incluye una repetición aunque la serie haya empezado antes. Consultar dos veces conserva el estado de la base y la revocación surte efecto con la misma sesión. | REC-03, REC-04 |
| REC-07 | **Confirmar y desmarcar una ocurrencia.** Resolver el selector acordado y aplicar las reglas actuales de acceso y fecha. Conservar el autor y la hora por ocurrencia. Cubrir también la transición a `COMPLETADO` de la ruta de estado para evitar una confirmación global de la serie. | Confirmar una fecha deja intactas las demás. Propietario, Familia y Cuidador pueden confirmar en el día argentino. Solo Propietario y Familia pueden desmarcar. Se rechazan confirmaciones pasadas, futuras, inactivas y duplicadas. Dos confirmaciones simultáneas dejan un único autor y una confirmación efectiva. | REC-03, REC-06 |
| REC-08 | **Editar, desactivar, reactivar y eliminar series.** Aplicar la regla acordada para editar futuras y preservar historial. Mantener la generación coordinada con el job. Retomar desde hoy con la frecuencia original cuando se reactiva. | HTTP/PostgreSQL conserva las confirmaciones tras cambiar la programación, excluye series desactivadas o eliminadas y evita que el job regenere una tanda a partir de datos anteriores a la edición. Al reactivar se verifica que las fechas de la pausa no vuelvan como tareas pendientes, incluso si ya estaban guardadas. Se comprueba el comportamiento acordado cuando ya no quedan fechas antes del fin. | REC-05, REC-07 |
| REC-09 | **Cerrar QA y contrato web.** Actualizar Postman y la documentación de la API. Ejecutar regresiones, migración y recorrido de punta a punta. | En Docker con PostgreSQL se prueban alta con y sin fin, renovación de tanda, «hoy», confirmación y reactivación. La colección incluye casos negativos y roles compartidos. Pasan la suite anterior y las nuevas integraciones de CI. | REC-01 a REC-08 |

## Contrato de programación

| Modalidad | Intervalo | Días seleccionados |
|---|---|---|
| `unica` o modalidad omitida | Ausente o `null` | Ausente o `null` |
| `recurrente` | Entero JSON positivo y unidad `dias`, `semanas`, `meses` o `anios` | Ausente o `null` |
| `dias_semana` | Ausente o `null` | Uno o más días distintos en un string separado por comas |

El formulario web actual usa `unica` y envía campos de repetición nulos. Se conserva también `anios` porque el esquema y el seed de QA ya lo usan. Los nombres ingresados de modalidades y unidades se guardan en minúsculas. `diasSemana` acepta nombres completos en español, con o sin tilde y sin distinguir mayúsculas. La respuesta mantiene la clave `diaSemana` y devuelve los días en mayúsculas, sin tildes y ordenados de lunes a domingo. V12 amplía la columna para los siete días.

`fechaFin` es un instante inclusivo y debe ser igual o posterior al inicio. Si la web permite elegir solo una fecha de fin, debe convertirla al final de ese día en Argentina. En `unica`, se admite el campo por compatibilidad y solo hay una fecha. En series con fin se rechaza una programación que daría cero o más de 366 ocurrencias. Para meses cortos se usa el último día disponible y se recupera el día original en el siguiente mes que lo tenga. Las series semanales por día empiezan en la primera fecha seleccionada a partir del inicio.

Ejemplos de alta válidos, con respuesta `201`:

```json
{"titulo":"Control","tipo":"OTRO","fechaHoraInicio":"2027-01-20T21:00:00Z"}
```

```json
{"titulo":"Medicación","tipo":"MEDICACION","fechaHoraInicio":"2027-01-20T21:00:00Z","modalidad":"recurrente","intervaloValor":10,"intervaloUnidad":"dias","fechaFin":"2027-02-09T21:00:00Z"}
```

```json
{"titulo":"Medicación","tipo":"MEDICACION","fechaHoraInicio":"2027-01-19T21:00:00Z","modalidad":"dias_semana","diasSemana":"LUNES,MIERCOLES"}
```

Ejemplos de datos para combinar con un título, un tipo y un inicio válidos:

| Datos incompatibles | Respuesta |
|---|---|
| `{"modalidad":"unica","intervaloValor":1}` | `400 / ERROR_VALIDACION`, campo `intervaloValor` |
| `{"modalidad":"recurrente","intervaloValor":1.5,"intervaloUnidad":"dias"}` | `400 / ERROR_VALIDACION`, campo `intervaloValor` |
| `{"modalidad":"recurrente","intervaloValor":2,"intervaloUnidad":"semanas","diasSemana":"LUNES"}` | `400 / ERROR_VALIDACION`, campo `diasSemana` |
| `{"modalidad":"dias_semana","diasSemana":"LUNES,LUNES"}` | `400 / ERROR_VALIDACION`, campo `diasSemana` |

## Persistencia y próximas integraciones

V12 conserva todas las filas y confirmaciones previas e instala `ocurrencia_recordatorio`. El índice de tandas usa la fecha local de la última ocurrencia generada. El job consulta los candidatos en lotes y descarta series finitas, inactivas, eliminadas y sin una primera tanda registrada.

Los registros previos conservan su programación y confirmación. V12 deja el progreso en `NULL` y no infiere ocurrencias ni autores de fechas previas. Un cambio de programación en una serie activa genera sus fechas futuras. Una reactivación puede generar ocurrencias desde hoy.

## Consulta diaria y confirmación

`GET /recordatorios/hoy` devuelve los recordatorios únicos y las ocurrencias de series activas programadas para el día de Buenos Aires. Aplica la visibilidad actual de cada mascota y ordena por fecha programada y, si coincide, por `idRecordatorio`.

Cada ocurrencia incluye `idOcurrencia` y `fechaProgramada`, junto con `idRecordatorio` y `fechaHoraInicio` de la serie. Para mostrar el horario de la fila se usa `fechaProgramada` cuando está disponible y `fechaHoraInicio` para los únicos. En las ocurrencias, `estado` representa a la serie. `situacionTemporal`, `puedeConfirmar`, `confirmadoPorId` y `confirmadoEn` corresponden a la fecha particular. Las rutas de detalle y listado por mascota devuelven el recordatorio o la serie.

| Operación sobre una ocurrencia | Solicitud | Perfiles con permiso |
|---|---|---|
| Confirmar | `PATCH /recordatorios/{id}/confirmacion?idOcurrencia={uuid}` | Propietario, Familia y Cuidador |
| Desmarcar | `DELETE /recordatorios/{id}/confirmacion?idOcurrencia={uuid}` | Propietario y Familia |
| Confirmar desde la ruta de estado | `PATCH /recordatorios/{id}/estado?nuevoEstado=COMPLETADO&idOcurrencia={uuid}` | Propietario y Familia |

`{id}` identifica el recordatorio y `{uuid}` la ocurrencia. La confirmación se permite durante el día argentino que corresponde a la ocurrencia, a cualquier hora. Para las series, omitir el selector devuelve `400` y una ocurrencia inexistente o de otra serie devuelve `404`. El registro único usa solo su ID. Las ocurrencias conservadas al cambiar una serie a única siguen usando su UUID. Las series sin ocurrencias guardadas quedan fuera de la consulta diaria de ocurrencias.

## Ciclo de vida de las series

Propietario y Familia tienen permisos para editar, pausar, reactivar y eliminar una serie. Todas estas operaciones usan la zona de Buenos Aires y coordinan sus cambios con el job mediante el bloqueo del recordatorio.

| Acción | Ruta | Qué sucede con las fechas |
|---|---|---|
| Editar la programación | `PUT /recordatorios/{id}` | Mantiene las ocurrencias hasta hoy inclusive y las confirmaciones. Una serie activa calcula su nueva programación desde mañana. Las fechas futuras que coinciden conservan su UUID. |
| Pausar | `PATCH /recordatorios/{id}/estado?nuevoEstado=INACTIVO` | Retira las ocurrencias sin confirmar de hoy y posteriores, y limpia el progreso de la tanda. Conserva las anteriores y confirmadas. |
| Reactivar | `PATCH /recordatorios/{id}/estado?nuevoEstado=ACTIVO` | Retoma el patrón original desde hoy inclusive. Si es finita, genera hasta el fin. Si es indefinida, genera una tanda de 12 y fija su última fecha. |
| Eliminar | `DELETE /recordatorios/{id}` | Registra el borrado lógico. Conserva los datos de las ocurrencias, excluye el recordatorio de la consulta y detiene su generación. |

Editar título o tipo conserva las ocurrencias. Editar la programación de una serie inactiva actualiza la configuración para su próxima reactivación. Si no queda una fecha elegible dentro del fin de la serie, la reactivación devuelve `400 / ERROR_NEGOCIO` y conserva la serie inactiva. Puede corregirse el fin antes de reintentar. Los días ya transcurridos de la pausa quedan fuera de la regeneración. Una fecha que ya tiene una confirmación se conserva al reactivar, incluso si cambió el horario.

Al pasar de único a serie se conserva una fecha anterior a hoy, una fecha confirmada o la fecha de un único activo que corresponde a hoy. La nueva programación se aplica desde mañana. Al pasar de serie a único se requiere una fecha desde mañana y se conservan las ocurrencias históricas y la de hoy. Para operar sobre una fila de la consulta diaria que incluya `idOcurrencia`, se debe enviar ese UUID aunque su modalidad haya cambiado a `unica`.

## Verificación de REC-08

Pasaron las 81 pruebas dirigidas y las 826 de la selección completa de Maven del CI, sobre PostgreSQL 17.6 y sin omisiones. Las dirigidas cubrieron la conservación del historial, los días omitidos durante la pausa, el fin sin fechas elegibles, la carrera entre edición y renovación, y la reversión transaccional ante una falla de inserción. En Docker se verificó por HTTP el recorrido de alta, confirmación, edición, pausa, reactivación y borrado lógico sobre la misma serie.

### Ciclo de vida en Postman

1. Importá la [colección de recordatorios](../postman/MisMascotas%20Recordatorios%20Recurrentes%20REC-01%20a%20REC-04.postman_collection.json) y seleccioná el entorno **MisMascotas Local**. `baseUrl` debe apuntar al backend con REC-08 y Flyway V12.
2. En **Environments → MisMascotas Local**, completá `email` y `password` con una cuenta QA. Enviá `00-01`, copiá el código de Mailpit (`http://localhost:8025` en QA) y pegalo en `rec04Codigo` del mismo entorno. Enviá `00-02`. La respuesta `200` guarda `accessToken` automáticamente y limpia el código.
3. Ejecutá **00-03 — Listar mis mascotas** y guardá un UUID propio en `rec04MascotaId`, en **Variables** de la colección. Corré solo **02 — Consulta, confirmación y ciclo de series** en Runner, con una iteración y el mismo entorno. Esta carpeta prepara y elimina lógicamente sus dos series de prueba. Usá el mismo día argentino durante toda la corrida.

Los casos cubren la vista de hoy, la confirmación y el desmarcado, el reemplazo de una ocurrencia pendiente tras pausar, la conservación de una confirmación tras editar y reactivar, el borrado lógico y el rechazo de reactivar una serie finita vencida. La consulta de hoy expone la fecha actual. El calendario futuro, la integridad del historial y la ejecución del job se verifican con las pruebas de PostgreSQL y Docker.

La carpeta `02` pasó dos corridas consecutivas con Newman sobre Docker, con 26 solicitudes y 51 aserciones por corrida. PostgreSQL confirmó el borrado lógico de las series de prueba y la conservación de la ocurrencia confirmada. También pasó la carpeta `01` con 18 solicitudes y 36 aserciones.

## Verificación de REC-05 a REC-07

Pasaron las 73 pruebas dirigidas y las 818 de la selección completa de Maven del CI, con PostgreSQL 17.6 y sin omisiones.

- PostgreSQL con reloj controlado. Se verificaron la renovación en el último día, la recuperación después de una pausa, dos renovaciones simultáneas y el rollback de una tanda al fallar su tercera inserción.
- HTTP con PostgreSQL. Se verificaron la consulta diaria junto con los recordatorios únicos, el límite de medianoche en Buenos Aires, la revocación de acceso con el mismo JWT, la confirmación por los tres perfiles y el desmarcado por Propietario y Familia. Dos solicitudes paralelas sobre la misma ocurrencia conservaron al primer autor.
- Backend empaquetado en Docker. Al reiniciar con una primera tanda que vencía ese día, el job pasó de 12 a 24 ocurrencias. Después se consultó, confirmó y desmarcó la ocurrencia de hoy por HTTP.

## Verificación de REC-01 a REC-03

La selección completa del CI local pasó con 803 pruebas, incluidas validaciones HTTP, cálculo de fechas, selección de tandas y migración sobre PostgreSQL 17.6. Se verificaron la conservación de filas de V11, el rechazo de duplicados y el uso del índice con datos de prueba. En Docker, el backend empaquetado migró una base B9 a V12, conservó los 15 recordatorios del seed y verificó la persistencia al recrear la base.

## Verificación de REC-04

Pasaron 63 pruebas dirigidas de servicio, cálculo de fechas, migración y alta por HTTP sobre PostgreSQL 17.6 en Docker. Se comprobaron las fechas de una serie mensual, una por días de semana y una sin fin. El límite admite 366 ocurrencias y rechaza 367. Un intervalo inválido o un intento de alta por un Cuidador deja la base y la auditoría sin cambios. Un rechazo de PostgreSQL en la tercera inserción de la tanda también revierte el alta completa y permite reintentar.

### Pruebas manuales con Postman

1. Importá [MisMascotas — Recordatorios recurrentes](../postman/MisMascotas%20Recordatorios%20Recurrentes%20REC-01%20a%20REC-04.postman_collection.json). Para esta carpeta, usá un backend que incluya REC-04 y PostgreSQL migrado a V12.
2. En **Variables**, ajustá `baseUrl` y completá `rec04MascotaId` con el ID de una mascota QA propia. Podés guardar un token vigente en `rec04Token` dentro de esta colección o en `accessToken` del entorno activo. Si usaste otra colección para el login, copiá el `accessToken` de su respuesta. Para iniciar sesión desde esta colección, ejecutá `00-01`, copiá el código recibido en `rec04Codigo` y ejecutá `00-02`. Una respuesta `200` guarda el token. Confirmá el acceso con `00-03 — Listar mis mascotas`.
3. En **Runner**, ejecutá solo `01 — Pruebas REC-01 a REC-04`, con una iteración. Elegí el mismo entorno que usaste para `00-03` o **No Environment** si la URL y el token están guardados en las variables de la colección. También podés enviar las 18 solicitudes en orden. Hay seis altas válidas, diez solicitudes que deben devolver `400` y dos lecturas. Cada ejecución deja seis recordatorios de prueba identificados por `rec04Prefijo`.

La carpeta de pruebas pasó con Newman con 18 solicitudes y 36 aserciones. Las tres solicitudes de preparación también pasaron usando el código recibido en Mailpit. Se verificó la consulta siguiente en PostgreSQL 17.6 con la migración V12 aplicada.

La API permite comprobar la programación devuelta, los rechazos y cuáles altas quedan visibles. Las filas con las fechas generadas y el progreso de la tanda se pueden inspeccionar con la consulta de solo lectura de abajo. Si corrés la carpeta con Runner, activá **Keep variable values** para copiar `rec04Prefijo` después. Reemplazá `PREFIJO_DE_LA_CORRIDA` y `UUID_DE_LA_MASCOTA` por los valores de la colección.

```sql
SELECT r.titulo,
       count(o.id_ocurrencia) AS ocurrencias,
       r.ultima_fecha_generada,
       min(o.fecha_programada AT TIME ZONE 'America/Argentina/Buenos_Aires') AS primera_fecha_local,
       max(o.fecha_programada AT TIME ZONE 'America/Argentina/Buenos_Aires') AS ultima_fecha_local,
       CASE WHEN count(o.id_ocurrencia) BETWEEN 1 AND 12 THEN
           array_agg(o.fecha_programada AT TIME ZONE 'America/Argentina/Buenos_Aires'
                     ORDER BY o.fecha_programada)
       END AS fechas_locales
FROM recordatorio r
LEFT JOIN ocurrencia_recordatorio o ON o.recordatorio_id = r.id_recordatorio
WHERE r.mascota_id = 'UUID_DE_LA_MASCOTA'::uuid
  AND r.titulo LIKE 'PREFIJO_DE_LA_CORRIDA | %'
GROUP BY r.id_recordatorio
ORDER BY r.titulo;
```

| Alta | Ocurrencias | Fechas locales esperadas |
|---|---:|---|
| Unico | 0 | |
| Sin fin | 12 | Cada dos días del 01/01/2030 al 23/01/2030 a las 23:30. La última fecha generada es `2030-01-23`. |
| Semanal | 3 | 01/01, 15/01 y 29/01 de 2030 a las 18:00. |
| Mensual | 3 | 31/01, 28/02 y 31/03 de 2030 a las 18:00. |
| Dias de semana | 3 | 02/01, 07/01 y 09/01 de 2030 a las 18:00. |
| Limite 366 | 366 | Del 01/01/2030 al 01/01/2031 a las 18:00, ambas fechas incluidas. |

Referencias del repositorio: `RecordatorioRequestDTO`, `RecordatorioResponseDTO`, `RecordatorioService`, `RecordatorioRepository`, `RecordatorioController`, `B9__esquema_actual.sql` y [contrato de cuidado compartido](cuidado_compartido_api.md).
