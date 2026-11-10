# Recordatorios del seed QA

## Estado y alcance

Catálogo de **15 recordatorios** implementado y cargado en `mismascotas-web-testing` el **2026-10-22**, mediante `qa-base-v1`. Complementa `cuentas_qa.md`, `mascotas_qa.md` y `eventos_clinicos_qa.md`. La restauración y los UUID están en `seed_qa.md`.

Se cubren los cinco tipos actuales y se conservan mascotas sin recordatorios para probar el estado vacío. La existencia de reglas recurrentes guardadas no demuestra que el backend calcule sus ocurrencias, disponga de tareas del día o envíe avisos: esas capacidades todavía requieren implementación y verificación.

## Fechas y campos comunes

- **D0:** fecha local de restauración en Argentina. **T0:** instante de restauración.
- Las horas de las tablas son de Argentina y se convierten a UTC al persistir. Las altas futuras empiezan desde D0 + 1 o después para que no venzan durante la preparación del entorno.
- El creador de cada recordatorio es el propietario de su mascota. No se precargan accesos compartidos ni se atribuyen actuaciones a usuarios ajenos.
- Todos comienzan sin eliminación lógica. El estado es `ACTIVO` salvo QA-REC-003, que es `INACTIVO`, y QA-REC-006, que es `COMPLETADO`.
- `confirmadoPor` y `confirmadoEn` son nulos salvo en QA-REC-006. Los recordatorios únicos confirmados usan el estado `COMPLETADO`; las series recurrentes conservan su estado global.
- Los títulos y tipos se usan exactamente como aparecen en el catálogo.
- La fecha y hora de fin son inclusivas, según decisión del responsable del proyecto: una ocurrencia exactamente igual a ese instante es la última permitida. No se extiende el límite automáticamente hasta el final del día si la hora guardada es otra.

## Catálogo: únicos

| ID de fixture | Mascota | Título | Tipo | Fecha y hora local | Confirmación |
|---|---|---|---|---|---|
| QA-REC-001 | QA-MAS-001 — Toby habitual | Control QA programado | VISITA_VETERINARIO | D0 + 2 días, 10:00 | Pendiente |
| QA-REC-004 | QA-MAS-001 — Toby habitual | Visita QA anterior sin confirmar | VISITA_VETERINARIO | D0 − 2 días, 10:00 | Pendiente |
| QA-REC-005 | QA-MAS-003 — Luna | Vacuna QA programada | VACUNA | D0 + 7 días, 11:00 | Pendiente |
| QA-REC-006 | QA-MAS-006 — Mora | Control QA confirmado | VISITA_VETERINARIO | D0 − 1 día, 09:00 | Propietario, D0 − 1 día a las 09:10 |
| QA-REC-007 | QA-MAS-007 — Coco | Revisión general QA | OTRO | D0 + 3 días, 16:00 | Pendiente |
| QA-REC-010 | QA-MAS-010 — Pipa | Aviso de medicación QA | MEDICACION | D0 + 1 día, 08:00 | Pendiente |
| QA-REC-011 | QA-MAS-011 — Simón | Vacuna QA programada | VACUNA | D0 + 7 días, 11:00 | Pendiente |
| QA-REC-012 | QA-MAS-012 — Lola | Cuidado QA de madrugada | OTRO | D0 + 2 días, 00:05 | Pendiente |
| QA-REC-013 | QA-MAS-013 — León | Control QA nocturno | VISITA_VETERINARIO | D0 + 2 días, 23:55 | Pendiente |
| QA-REC-014 | QA-MAS-014 — Toby propietario A | Control QA programado | VISITA_VETERINARIO | D0 + 2 días, 10:00 | Pendiente |
| QA-REC-015 | QA-MAS-015 — Nala propietario B | Vacuna QA programada | VACUNA | D0 + 7 días, 11:00 | Pendiente |

Todos los únicos usan `modalidad=unica`, con intervalo, unidad, días de semana y fecha de fin nulos. QA-REC-004 y QA-REC-006 representan recordatorios históricos existentes: su `creado_en` será el día anterior a su inicio, a las 12:00 de Argentina. No deben crearse mediante una petición de alta con fecha pasada ni usarse para aprobar esa petición.

QA-REC-006 mantiene `estado=COMPLETADO` y los datos de confirmación indicados. Esta transición se limita a recordatorios únicos; no se usa el estado global `COMPLETADO` para representar todas las fechas de una serie.

Los homónimos QA-REC-001 y QA-REC-014 coinciden intencionalmente en título, nombre de mascota y hora, pero pertenecen a distintos propietarios. Las vacunas de Luna, Simón y Nala propietario B también coinciden en título y horario. Los IDs y permisos deben mantenerlos separados.

## Catálogo: recurrentes

| ID de fixture | Mascota | Título | Tipo | Inicio local | Intervalo | Fin local | Estado |
|---|---|---|---|---|---|---|---|
| QA-REC-002 | QA-MAS-001 — Toby habitual | Aviso de medicación QA | MEDICACION | D0 + 1 día, 08:00 | Cada 1 día | D0 + 8 días, 23:59 | ACTIVO |
| QA-REC-003 | QA-MAS-001 — Toby habitual | Pipeta QA pausada | PIPETA_ANTIPARASITARIO | D0 + 5 días, 18:00 | Cada 1 mes | Sin fin | INACTIVO |
| QA-REC-008 | QA-MAS-008 — Bruno | Control periódico QA | VISITA_VETERINARIO | D0 + 1 día, 10:00 | Cada 2 semanas | D0 + 45 días, 23:59 | ACTIVO |
| QA-REC-009 | QA-MAS-009 — Ámbar | Revisión anual QA | OTRO | D0 + 30 días, 18:00 | Cada 1 año | Sin fin | ACTIVO |

Se usa `modalidad=recurrente`, `intervaloValor` según la tabla y `intervaloUnidad` con los literales `dias`, `meses`, `semanas` y `anios`, respectivamente. `diasSemana` es nulo en las cuatro reglas. Las futuras ocurrencias no se insertan como eventos clínicos ni como confirmaciones globales.

Los textos de medicación y pipeta son etiquetas ficticias para pruebas de programación; no contienen indicaciones de tratamiento.

## Distribución y resultados iniciales

| Cuenta | Recordatorios | Únicos | Recurrentes |
|---|---:|---:|---:|
| QA-USR-001 — Free vacío | 0 | 0 | 0 |
| QA-USR-002 — Free habitual | 4 | 2 | 2 |
| QA-USR-003 — Free al límite | 3 | 3 | 0 |
| QA-USR-004 — Premium | 6 | 4 | 2 |
| QA-USR-005 — Propietario A | 1 | 1 | 0 |
| QA-USR-006 — Propietario B | 1 | 1 | 0 |
| Total | 15 | 11 | 4 |

- Hay 13 registros activos, uno inactivo y un único completado. Solo QA-REC-006 tiene datos de confirmación.
- Nala habitual QA-MAS-002, Milo QA-MAS-004 y Chispa QA-MAS-005 no tienen recordatorios.
- Los tipos se distribuyen en seis visitas, tres vacunas, dos medicaciones, una pipeta y tres de tipo otro.
- QA-REC-012 y QA-REC-013 permiten comprobar que la conversión a UTC no desplaza el día mostrado al usuario: las 23:55 locales pertenecen al día siguiente en UTC, pero deben conservar su fecha local en la interfaz.
- Un recordatorio de vacuna o visita programada no es evidencia de que haya ocurrido un evento clínico. La confirmación no debe inventar un evento clínico sin una regla de producto expresamente definida.

## Escenarios temporales aislados

Se preparan al ejecutar los casos correspondientes, sin ampliar permanentemente el seed base.

| Escenario | Preparación | Verificación y dependencia |
|---|---|---|
| Mensual anclado al 31 | Reloj controlado al 30 de enero y serie mensual desde el 31, a las 09:00; repetir en 2027 y 2028 | Febrero se ajusta al 28 o 29 y marzo vuelve al 31; requiere motor de recurrencia y reloj controlable |
| Días de semana | Serie para lunes, miércoles y viernes, a las 09:00 | Solo ocurren los días elegidos; su serialización queda pendiente del contrato del motor, no se inventa un formato |
| Avisos próximos | Ocurrencia en T0 + 5 minutos con destinatario de prueba habilitado | Aviso y reavisos a los 15, 30 y 60 minutos desde la hora original; confirmación suprime los restantes; requiere despachador y tiempo controlable |
| Inicio recurrente pasado | Regla diaria iniciada en D0 − 2 días, con próximas fechas futuras | Alta permitida según la decisión acordada y sin generar tareas retroactivas |
| Alta sin ocurrencia futura | Único pasado o serie ya terminada | La petición de alta se rechaza sin persistencia |
| Confirmar fechas separadas | Dos ocurrencias de la misma serie | Confirmar una no modifica la otra; bloqueado hasta disponer de ocurrencias independientes |
| Desactivar o eliminar con pasado | Serie con ocurrencias pasadas confirmadas y no confirmadas | Se conserva pasado y desaparece futuro, sin dar por implementado el historial de ocurrencias |
| Instante exacto de fin | Serie diaria desde D0 + 1 a las 08:00, con fin en D0 + 3 a las 08:00 | Se incluyen las ocurrencias de D0 + 1, + 2 y + 3 a las 08:00; no se genera la de D0 + 4. El límite es inclusivo por decisión aprobada |

## Brechas actuales y restricciones del cargador

- `Recordatorio` conserva una sola pareja `confirmadoPor/confirmadoEn`. El seed no puede suplir con datos ficticios la entidad de ocurrencias ni la lista de tareas del día que aún faltan.
- La web permite altas únicas y no ofrece edición de recurrencias. Los recurrentes del catálogo sirven para inspección y pruebas del contrato existente; su ejecución temporal se verifica cuando exista el motor.
- El request usa `diasSemana` y la respuesta `diaSemana`. El almacenamiento actual es texto de hasta 20 caracteres. El formato de tres días no debe adivinarse ni truncarse al preparar el escenario semanal.
- Los ejemplos actuales de Postman usan modalidades `semanal`/`mensual` y unidades en mayúsculas, pero el SQL define modalidades `unica`, `recurrente`, `dias_semana` y unidades en minúsculas. El catálogo usa los valores del esquema; esos ejemplos deberán revisarse antes de utilizarlos para cargar recurrencias.
- Quién puede desmarcar una confirmación ajena sigue pendiente. Este catálogo solo atribuye confirmaciones al propietario, sin resolver esa decisión por medio del seed.
- Las reglas recurrentes no equivalen a notificaciones enviadas ni a pruebas aprobadas. La carga inicial no prueba el endpoint de alta ni la auditoría automática.
