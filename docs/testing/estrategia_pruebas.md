# Estrategia de pruebas de MisMascotas

## Estado del documento

Documento de trabajo para registrar criterios y decisiones pendientes mientras se diseña la matriz. No constituye evidencia de ejecución ni implica que los casos hayan pasado.

La ubicación de referencia de esta estrategia, del catálogo y del registro de ejecuciones es `docs/testing/` del repositorio activo `D:\AAAIES\MisMascotas-front`. Las copias de preparación de un chat no deben convertirse en registros paralelos de resultados.

## Archivos de la matriz y uso

- `casos_prueba.csv` es el catálogo maestro: un ID estable por caso, módulo y flujo para filtrar por bloques cortos, precondiciones, acción, resultado esperado, prioridad, tipo y fundamento. Las referencias a CU describen el origen del criterio; «Diseñado por QA» identifica cobertura adicional propuesta y «Pendiente» señala una definición aún abierta. Un caso diseñado aún no está ejecutado ni aprobado.
- `ejecuciones_pruebas.csv` registra una fila **por ejecución** de un caso. Por eso permite repetirlo en otra versión o entorno sin modificar el catálogo. Solo se agregan resultados de pruebas reales o estados de una tanda identificada.
- Ambos CSV usan UTF-8 y punto y coma como separador para facilitar su apertura en Excel configurado en español. No son hojas de un mismo archivo: un CSV no admite varias hojas.
- `pruebas_qa.ods` ofrece las vistas **Ejecuciones** y **Casos** con formato en LibreOffice Calc, vinculadas a ambos CSV. Se actualiza al abrir o desde los enlaces externos; los CSV siguen siendo la fuente de edición. La capacidad, portabilidad y los pasos de actualización se documentan en `registro_resultados.md`.
- En cada ejecución anotar ID de caso, fecha y hora con zona, versión exacta del código, entorno, datos o fixtures usados, resultado (`Aprobada`, `Fallida`, `Bloqueada` o `No ejecutada`), observaciones, evidencia, incidencia y ejecutor. Nunca inferir el resultado de una prueba por su documentación.

### Prioridad y alcance de las corridas

- **Crítica:** barreras de autenticación y acceso a datos sensibles, pérdida o corrupción de información relevante, integridad de pagos y flujos esenciales de mascotas, historial y tareas. La corrida smoke selecciona un subconjunto representativo de estas pruebas, no necesariamente todas.
- **Alta:** reglas de negocio, cupos y habilitaciones comerciales, validaciones importantes, variantes de recurrencia, recuperación y funciones habituales con alternativas. Incluye permisos acotados sobre contenido decorativo recuperable. La regresión habitual incluye críticas y altas del alcance afectado.
- **Media:** navegación, presentación, filtros auxiliares, contenido decorativo recuperable y validaciones secundarias. También variantes de menor riesgo acompañadas de controles principales de mayor prioridad. Entra en la regresión completa y en las corridas focalizadas donde corresponda.

La prioridad ordena la ejecución según el riesgo específico del caso, considerando impacto, frecuencia del flujo, recuperación y cobertura relacionada. No es la severidad automática de una incidencia ni depende de que la función esté implementada. No se busca una cantidad similar de casos en cada nivel. Los casos negativos de seguridad no bajan por ser negativos y una regla comercial no pasa a crítica únicamente por pertenecer a Premium.

El 24 de octubre de 2026 se revisaron los 240 casos y se ajustaron 77 prioridades, conservando los demás campos. Los motivos por caso, la distribución y los controles de preservación están en `revision_prioridades_2026-10-24.md`. Los diez casos de `tandas/smoke_api_01.md` conservan prioridad Crítica. El CSV maestro es la fuente vigente; actualizar los vínculos de `pruebas_qa.ods` para reflejar la revisión.

La selección smoke se fija por módulo y versión cuando exista un entorno ejecutable. Los casos de funciones aún no implementadas siguen en el catálogo, pero se registran como bloqueados o no ejecutados hasta contar con una implementación verificable.

### Identificación del entorno y versiones por tanda

El objetivo es poder responder **qué configuración se probó** y repetir la ejecución, no solo anotar la fecha o el nombre de una rama.

Al iniciar cada tanda, registrar una única ficha de entorno junto a sus evidencias, identificada por un ID de corrida. Las filas del CSV deben permitir relacionar cada ejecución con esa tanda. La ficha incluye:

- **Backend y frontend:** identificador de la imagen Docker realmente ejecutada, referencia del código usado para construirla y si el build incluyó cambios locales. El commit actual del repositorio no demuestra por sí solo qué código contiene una imagen construida previamente. Si el origen del build no puede verificarse, indicarlo expresamente; no atribuirle una versión por suposición.
- **Base de datos:** versión del motor e imagen Docker, más la revisión de estructura aplicada. Mientras no haya migraciones versionadas, conservar un volcado de solo estructura como evidencia y su hash, con referencia a los scripts SQL correspondientes. No confundir «PostgreSQL 17.6» con una versión del esquema de MisMascotas.
- **Datos iniciales:** versión y hash del seed, D0, T0 y referencia a la evidencia de restauración. Los datos creados para un caso se identifican además por su fixture o ID.
- **Otros componentes relevantes:** versión de Mailpit y, si se prueba la interfaz, navegador y su versión. Registrar la configuración relevante sin copiar secretos.

Se conservan las columnas actuales: `version_codigo` identifica los builds/código probados mediante commits verificados abreviados, o IDs cortos de imagen si el origen es desconocido; `entorno` identifica el entorno QA y la capa; `id_ejecucion` y `evidencia` identifican la tanda; `datos_utilizados` relaciona seed y fixtures. La ficha común y la evidencia del caso conservan las referencias completas y el detalle de los datos temporales. Las observaciones son breves y específicas. Repetir un caso genera una fila nueva, sin sobrescribir su resultado anterior.

No adjuntar archivos `.env`, inspecciones completas de contenedores con variables sensibles, contraseñas, códigos 2FA ni tokens. Las comprobaciones de preparación del entorno no se convierten automáticamente en casos aprobados de la matriz.

### Primera tanda preparada

`tandas/smoke_api_01.md` selecciona diez casos críticos de autenticación y acceso directo a mascotas ajenas para la capa API, con datos y verificaciones por caso. El registro y la gestión de evidencias se detallan en `registro_resultados.md`. `scripts/qa/Preparar-CorridaQA.ps1` captura versiones reales de imágenes, estructura de la base y referencia del seed, sin ejecutar casos ni modificar datos/resultados. Las fichas se guardan en `docs/testing/evidencias/<id_corrida>/`.

MAS-LIS-001 queda fuera de este smoke: exige mascotas propias y accesos aceptados, pero el seed todavía no tiene cuidado compartido. No se lo reduce a un listado propio para aprobarlo parcialmente. La automatización de la interfaz con Selenium permanece separada. Preparar esta tanda no implica que ya se haya ejecutado.

## Automatización: elección para la interfaz web

La herramienta elegida para automatizar la interfaz web es **Selenium**, por preferencia del responsable del proyecto y su experiencia previa con ella. Los próximos agentes deben mantener esta elección; Playwright fue una alternativa propuesta, no la decisión adoptada.

Selenium cubrirá recorridos visibles en el navegador, como completar formularios, navegar, aplicar filtros y comprobar mensajes. La cobertura de reglas de negocio, API, base de datos y concurrencia se diseñará en las capas correspondientes. Un mismo caso de la matriz puede requerir verificaciones en más de una capa, manteniendo su ID para relacionar las evidencias.

Esta decisión define la herramienta web. La configuración concreta y las demás herramientas propuestas para backend, API y aplicación móvil todavía requieren planificación de implementación.

## Entorno QA: reutilizar web-testing

Se adopta el proyecto Docker Compose existente `mismascotas-web-testing` como entorno para las pruebas manuales y automatizadas. La decisión responde a su propósito original de testing y aprovecha los servicios ya preparados. No se creará otro entorno QA equivalente mientras este cubra las necesidades de la matriz.

La configuración activa está en `D:\AAAIES\MisMascotas-front\docker-compose.web-testing.yml`, con frontend web, backend y PostgreSQL 17.6. Usa la red `mismascotas_web_testing_network` y el volumen `mismascotas_web_testing_db`. Para las pruebas de este entorno, el código de referencia es el de `D:\AAAIES\MisMascotas-front`; la otra copia en `D:\AAAIES\PP 2` no debe confundirse con la que construye sus imágenes. En cada corrida se registra la versión realmente compilada.

Mailpit v1.31.5 está incorporado a este Compose y recibe los correos del backend QA. Su bandeja y API están en `http://localhost:8025`, con SMTP interno `mailpit-web-testing:1025` y sin reenvío externo. El 22 de octubre de 2026 se verificaron registro, recepción de correo, 2FA, consulta de perfil, logout y nuevo login con una cuenta temporal que se retiró al finalizar; el detalle está en `D:\AAAIES\MisMascotas-front\docs\testing\mailpit_qa.md`.

El seed base `qa-base-v1` y el procedimiento explícito de restauración están implementados y se cargaron el 22 de octubre de 2026. `DB/qa/restablecer-seed.sql` restaura los datos de negocio en una transacción; `scripts/qa/Restablecer-QA.ps1` valida el destino, respalda la base anterior y coordina backend y Mailpit. El detalle y las comprobaciones están en `docs/testing/seed_qa.md`. Siguen pendientes los datos específicos de otros módulos y los escenarios de frontera que se preparan por caso.

Las operaciones de limpieza deben apuntar únicamente a los recursos de este entorno; el volumen no se recrea después de cada caso. El reinicio de un contenedor no restaura los datos ni vuelve a aplicar los scripts de inicialización sobre un volumen ya inicializado. La evidencia de restauración registra el commit de trabajo si Git está disponible, pero no certifica el código de las imágenes Docker que ya estaban construidas.

### Cuentas del seed acordadas

Se cargaron seis cuentas QA con emails y contraseñas consultables en `D:\AAAIES\MisMascotas-front\docs\testing\cuentas_qa.md`: Free vacío, Free habitual, Free al límite, Premium y dos propietarios adicionales para comprobar aislamiento. El responsable del proyecto permite reemplazar los datos existentes en la base QA durante la restauración. Se verificó el login con correo 2FA de las seis cuentas y después se restauró el seed para retirar las sesiones, códigos, auditoría y correos de esas comprobaciones. La definición de otros datos continúa por bloques; no se precargan todos los escenarios de la matriz en un único seed.

El catálogo `D:\AAAIES\MisMascotas-front\docs\testing\mascotas_qa.md` completa las 15 mascotas activas de esas cuentas, con edades y especies variadas, pesos consistentes e identidades separadas para homónimos entre propietarios. Están cargados los 20 eventos clínicos iniciales, de los cuales 17 son mediciones de peso, y los 15 recordatorios previstos. Los UUID estables de los fixtures se documentan en `seed_qa.md`.

`eventos_clinicos_qa.md` precisa los IDs y campos de esos 20 eventos. `recordatorios_qa.md` define 15 recordatorios: 11 únicos y cuatro recurrentes, de los cuales uno es inactivo. Los datos temporales de frontera se preparan por caso; guardar una regla recurrente no constituye evidencia de cálculo de ocurrencias ni de envío de avisos.

## Recordatorios: fecha y hora de fin inclusivas

La fecha y hora de fin incluyen una ocurrencia que coincida exactamente con ese instante. Ejemplo: serie diaria de las 08:00 desde D0 + 1, con fin en D0 + 3 a las 08:00: se incluyen tres ocurrencias, en los días + 1, + 2 y + 3, y ninguna posterior. Se compara el instante completo tras convertir las fechas de Argentina a UTC; no se reemplaza la hora de fin por el final del día. Esta regla fue confirmada por el responsable del proyecto y se prueba al implementar el motor de recurrencia.

## Recordatorios: confirmación de ocurrencias

### Brecha técnica actual

El modelo actual de `Recordatorio` guarda una sola pareja `confirmadoPor` / `confirmadoEn` en el recordatorio completo. Tampoco existe una entidad de ocurrencias ni un endpoint que liste las tareas del día.

Por eso, la implementación actual no permite verificar correctamente que confirmar una ocurrencia recurrente afecte solo a esa fecha, que la siguiente quede pendiente, ni que se conserve un historial de confirmaciones por fecha. Los casos de recurrencia e historial de confirmaciones no deben marcarse como aprobados hasta modelar y probar cada ocurrencia y su confirmación.

Referencias del proyecto: `backend/src/main/java/com/MisMascotas/backend/Entity/Recordatorio.java`, `backend/src/main/java/com/MisMascotas/backend/Controller/RecordatorioController.java` y `CU/CU06 Confirmar recordatorio cumplido.docx`.

### Decisión pendiente: desmarcar una confirmación ajena

El caso de uso permite desmarcar una tarea confirmada por error, pero no define si un usuario puede desmarcar una confirmación hecha por otra persona. No asumir una regla de permisos ni cerrar los resultados esperados de esos casos hasta decidirla. La decisión debe contemplar los roles Propietario, Familia y Cuidador, y preservar el registro de auditoría de la confirmación y su reversión.

### Límite de alcance de estas pruebas

El envío de avisos por tareas pendientes y sus reintentos se diseñará en el bloque separado de notificaciones; la confirmación y la lista de tareas del día se prueban como comportamientos distintos.

## Recordatorios: recurrencia mensual en días inexistentes

### Decisión aprobada

Cuando una recurrencia mensual está anclada a un día que no existe en un mes concreto, la ocurrencia de ese mes se programa para su último día. La regla conserva el día original como ancla y vuelve a él cuando el mes lo permite. Ejemplo: 31 de enero → 28 o 29 de febrero → 31 de marzo. El cálculo de marzo debe hacerse desde la regla original, no sumando un mes a la ocurrencia ajustada de febrero.

### Motivo

En recordatorios de cuidado o medicación, omitir por completo un mes puede dejar una tarea necesaria sin aviso. Se adopta el comportamiento de ajuste al último día documentado para Microsoft Planner, en lugar de la omisión de fechas inexistentes propia de las reglas RFC 5545. La interfaz debe informar esta regla al configurar una recurrencia mensual para evitar sorpresas.

### Casos que deben cubrirla

- Mes de 30 días después de un inicio el 31: ocurre el día 30 y vuelve al 31 en el siguiente mes que lo tenga.
- Febrero de año común y bisiesto: ocurre el 28 o el 29, respectivamente, y conserva el ancla original.
- Intervalos de más de un mes: el ajuste se aplica solo a los meses que efectivamente pertenecen a la serie, sin desplazar las ocurrencias siguientes.

Referencia de comportamiento: https://learn.microsoft.com/en-us/graph/planner-task-recurrence-overview

## Recordatorios: fecha de inicio vencida al crear

### Decisión aprobada

Esta regla se aplica únicamente al alta de un recordatorio:

- Si es único y su fecha y hora de inicio ya pasaron, se rechaza el alta.
- Si es recurrente y el inicio ya pasó, se permite el alta solo cuando la regla aún tiene una próxima ocurrencia futura. No se generan tareas ni avisos retroactivos.
- Si es recurrente pero su fecha de fin también pasó y no queda ninguna ocurrencia futura, se rechaza el alta.

### Alcance y motivo

Un recordatorio nuevo debe poder producir al menos un aviso futuro. Esta validación no se extiende automáticamente a la edición de datos de un recordatorio existente ni a su reactivación; esos flujos conservan sus reglas propias. En particular, editar el título de un recordatorio único ya vencido no debe bloquearse solo por su fecha original.

### Casos que deben cubrirla

- Alta de un recordatorio único con inicio pasado: rechazo sin persistencia.
- Alta de una recurrencia con inicio pasado y próxima ocurrencia futura: aceptación; primera tarea generada en esa próxima fecha.
- Alta de una recurrencia con inicio y fin pasados, sin futuras ocurrencias: rechazo sin persistencia.
- Edición de un recordatorio existente y vencido: no aplicar esta validación de alta cuando se cambian otros datos.

## Notificaciones de cuidado compartido y reavisos

### Decisiones aprobadas

- El propietario de la mascota recibe sus notificaciones push por defecto, siempre que las tenga habilitadas en la app y en el dispositivo.
- Los usuarios con perfil Familia o Cuidador pueden optar por recibir push de cada mascota a la que tienen acceso. La preferencia se configura por usuario y por mascota; ver o confirmar una tarea no activa automáticamente sus push.
- La notificación principal se programa para la hora de la ocurrencia. Si la tarea sigue pendiente, los reavisos se programan a los 15, 30 y 60 minutos **desde la hora original**, no como intervalos acumulados entre avisos.
- Los reavisos de una ocurrencia se detienen al confirmarla. No se envía un reaviso de una ocurrencia anterior una vez iniciada la siguiente.

### Motivo

El acceso compartido a una mascota no implica que todos sus cuidadores quieran recibir todos los avisos. La suscripción por mascota limita ruido y respeta las preferencias individuales. Los reavisos acotados y calculados desde una misma hora de referencia son predecibles y evitan una cadena indefinida de notificaciones.

### Casos que deben cubrirla

- Propietario con push habilitadas: recibe el aviso; si las deshabilita, no lo recibe, aunque conserva acceso a la tarea.
- Familia y Cuidador sin suscripción push para esa mascota: ven y pueden confirmar las tareas del día, pero no reciben sus push.
- Familia y Cuidador que activan o desactivan la suscripción de una mascota: reciben o dejan de recibir sus avisos sin afectar las preferencias de otros usuarios ni de otras mascotas.
- Pérdida de acceso a la mascota: se cancelan los avisos futuros de ese usuario para ella.
- Tarea pendiente: se envía el aviso principal y, si corresponde, los reavisos en T+15, T+30 y T+60 minutos.
- Confirmación antes de cualquiera de esos momentos: se suprimen los reavisos restantes para esa ocurrencia.
- Próxima ocurrencia iniciada: no se envían reavisos tardíos de la ocurrencia previa.

Estas reglas no sustituyen la decisión pendiente sobre quién puede desmarcar una confirmación hecha por otra persona.

## Cuidado compartido: cupo de invitaciones Free

### Decisión aprobada

El límite de tres personas del plan Free se calcula por propietario. Ocupan un lugar tanto los accesos aceptados como las invitaciones pendientes. Las invitaciones rechazadas, vencidas o canceladas, y los accesos revocados, liberan ese lugar. Una invitación repetida a la misma persona no debe crear un segundo lugar ni un segundo acceso.

### Motivo

Contar las invitaciones pendientes impide superar el límite enviando varias antes de que sean aceptadas. Liberar el lugar cuando la invitación o el acceso dejan de estar vigentes permite invitar a otra persona sin ampliar el cupo contratado.

### Casos que deben cubrirla

- Con dos accesos aceptados y una invitación pendiente, un cuarto envío se rechaza.
- Con tres invitaciones pendientes, un cuarto envío se rechaza.
- Si una invitación pendiente se rechaza, vence o cancela, se puede enviar otra.
- Si un acceso se revoca, se puede invitar a otra persona.
- Los envíos simultáneos no pueden superar el límite aunque cada petición lea inicialmente un cupo disponible.

## Cuidado compartido: respuesta y vencimiento de invitaciones

### Decisiones aprobadas

- Una invitación pendiente puede aceptarse antes de que transcurran 48 horas desde su creación. En el instante exacto en que se cumplen las 48 horas ya está vencida y no puede aceptarse.
- La aceptación concede un único acceso con el perfil invitado (Familia o Cuidador) a todas las mascotas del propietario. No se concede acceso mientras la invitación siga pendiente.
- El rechazo no concede acceso y libera el cupo Free; una invitación rechazada o vencida puede reenviarse como una nueva invitación pendiente con su propio plazo de 48 horas.
- Aceptación, rechazo y vencimiento deben conservar un estado coherente aun ante respuestas repetidas o simultáneas.

### Casos acordados para la matriz

- Aceptación válida como Familia y como Cuidador: estado aceptado, acceso correcto y aviso al propietario.
- Rechazo válido: sin acceso, cupo liberado y aviso al propietario.
- Intento de respuesta de una persona distinta de la invitada: rechazo sin cambios.
- Aceptación de una invitación vencida o cancelada: rechazo sin acceso.
- Dos aceptaciones, incluso simultáneas: un solo acceso, sin duplicados.
- Reenvío tras rechazo o vencimiento: nueva invitación pendiente con nuevo plazo de 48 horas.
- Vencimiento automático: estado vencido, cupo liberado e imposibilidad de aceptación posterior.
- Prueba del límite temporal: aceptación permitida inmediatamente antes de las 48 horas y denegada al alcanzarlas.

## Cuidado compartido: listados

### Decisión aprobada

El listado principal muestra únicamente invitaciones pendientes y accesos aceptados. No mezcla en ese listado relaciones rechazadas, vencidas, canceladas, revocadas ni abandonadas. La pantalla distingue «Personas que invité» de «Me invitaron a su cuidado»; cada relación visible debe mostrar su perfil y estado correctos sin exponer datos de otros usuarios.

### Motivo

La vista principal sirve para conocer qué invitaciones requieren respuesta y quiénes tienen acceso vigente. Los estados terminados no deben aparentar permisos activos. Su trazabilidad permanece en auditoría; una vista de historial, si se necesitara, sería una decisión posterior y separada.

### Casos acordados para la matriz

- Listado de invitaciones enviadas pendientes y accesos aceptados con perfil y estado correctos.
- Listado de invitaciones recibidas pendientes y cuidados aceptados, identificando al propietario y el perfil concedido.
- Estado vacío cuando no hay relaciones pendientes ni aceptadas.
- Un cuidador asociado con varios propietarios: cada relación aparece una sola vez y sin mezclar datos.
- Los cambios de estado se reflejan en ambos listados; los estados terminados dejan de aparecer en la vista principal.
- Un usuario no puede consultar relaciones o datos privados de otro usuario.

## Cuidado compartido: gestión de relaciones

### Casos acordados para la matriz

- El propietario cambia un acceso aceptado de Cuidador a Familia: los nuevos permisos se aplican de inmediato a todas sus mascotas.
- El propietario cambia un acceso aceptado de Familia a Cuidador: los permisos retirados dejan de funcionar de inmediato, incluso con una sesión ya abierta.
- El propietario revoca un acceso aceptado: cesan el acceso a todas sus mascotas y los avisos futuros para esa persona.
- El propietario cancela una invitación pendiente: ya no puede aceptarse y se libera el cupo Free.
- La persona invitada abandona un cuidado aceptado: pierde acceso y deja de aparecer como cuidador activo.
- Cancelar un cuadro de confirmación no modifica la relación ni sus permisos.
- Una persona distinta del propietario no puede cambiar perfiles, revocar accesos ni cancelar invitaciones enviadas por él.
- La repetición de una operación no genera estados inconsistentes ni efectos duplicados.
- Tras una revocación o un abandono se exige una nueva invitación; no se reactiva la relación anterior.
- Cambio de perfil, revocación y abandono registran autor y momento para auditoría.

### Criterio transversal

La autorización se verifica en el servidor en cada operación. Cambiar o revocar un perfil no puede requerir que el usuario afectado cierre sesión o renueve su token para que se apliquen los nuevos límites.

## Cuidado compartido: permisos por perfil

### Decisiones aprobadas

- El propietario conserva la gestión de invitaciones y accesos, además de la edición de la ficha básica de sus mascotas (por ejemplo, nombre, especie y fecha de nacimiento).
- Familia puede consultar mascotas e historial clínico, crear/editar/eliminar registros clínicos y recordatorios, y ver/confirmar tareas del día. No puede editar la ficha básica de la mascota ni administrar accesos.
- Cuidador puede consultar mascotas e historial clínico y ver/confirmar tareas del día. No puede crear/editar/eliminar registros clínicos o recordatorios, editar la ficha básica ni administrar accesos.
- Los permisos compartidos se aplican a todas las mascotas del propietario que concedió el acceso, incluidas las que agregue después. No se extienden a mascotas de otros propietarios con los que la misma persona no tenga relación.

### Criterio de prueba

Cada permiso denegado debe comprobarse con una petición directa al servidor; ocultar una acción en la interfaz no es suficiente. También se prueban sesiones ya abiertas después de un cambio de perfil o una revocación, y el aislamiento entre propietarios.

### Historial tras la pérdida de acceso

Revocar un acceso o abandonar un cuidado impide acciones y consultas futuras, pero no borra los registros clínicos ni las confirmaciones de tareas que esa persona realizó mientras estaba autorizada. Esos datos permanecen vinculados a la mascota y conservan la identidad de quien los registró. Se prueba que el historial siga visible para quienes aún tienen permiso y que la persona desvinculada ya no pueda consultarlo ni modificarlo.

## Prioridad y ciclos de ejecución

- **Crítica:** si falla, compromete autorización, integridad de datos o un recorrido indispensable para usar el producto.
- **Alta:** función importante que debe cubrirse en la regresión habitual, pero cuyo fallo tiene un alcance más acotado que una crítica.
- **Media:** comportamiento no bloqueante, normalmente de presentación, claridad o consistencia de la experiencia, sin pérdida de datos ni acceso indebido.

El smoke usa una selección representativa de casos críticos; no implica ejecutar necesariamente todos los críticos. La regresión habitual cubre críticos y altos. Una ejecución completa incorpora también los medios. No existe una cuota por nivel: la prioridad se asigna por impacto, y se revisa al consolidar la matriz para evitar que todo quede artificialmente en Crítica.

## Calendario: fuente de los eventos

### Decisión aprobada

El calendario presenta las ocurrencias programadas de los recordatorios. Por ejemplo, una visita al veterinario figura como recordatorio de esa visita, no como una consulta clínica ya realizada. Cada ocurrencia refleja su tipo, fecha, mascota y estado pendiente o confirmado. Confirmar un recordatorio actualiza su estado en el calendario, pero no crea automáticamente un evento clínico. Los eventos clínicos que documentan hechos ya ocurridos permanecen en el historial clínico y no se mezclan con los programados en esta vista.

### Motivo

Separar lo planificado de lo efectivamente realizado evita interpretar una vacuna o consulta programada como si ya hubiese ocurrido. Permite además mostrar de manera consistente las ocurrencias de recordatorios recurrentes y su confirmación individual.

### Interacción para crear un recordatorio

Al tocar un día del calendario, un usuario con permiso para crear recordatorios puede elegir «Crear recordatorio». La aplicación abre el formulario de alta con ese día ya seleccionado en la fecha de inicio; la elección del día no crea ni guarda nada por sí sola. La hora y los demás datos obligatorios se completan y validan en el formulario. El estado vacío del calendario debe orientar a crear un recordatorio, no a registrar un evento clínico que no aparecería en esta vista. Este comportamiento es una decisión de producto posterior al caso de uso presentado; no requiere modificarlo.

La opción de alta se muestra a propietario y Familia. Cuidador conserva acceso de lectura al calendario, pero no puede crear recordatorios: tampoco debe lograrlo abriendo directamente el formulario o enviando una petición al servidor.

Si el calendario está filtrado por una mascota al iniciar el alta, el formulario precarga también esa mascota, pero permite cambiarla por otra a la que el usuario tenga acceso. Sin filtro de mascota, el usuario debe elegirla. La fecha seleccionada permanece precargada aunque se cambie la mascota.

### Casos de alta desde el calendario acordados para la matriz

- Tocar un día con eventos o vacío permite consultarlo y ofrece crear un recordatorio al usuario autorizado.
- Elegir crear abre el formulario con la fecha local correcta, sin guardar todavía ningún recordatorio.
- Salir del formulario sin guardar no crea una tarea ni una ocurrencia.
- Tras guardar un recordatorio válido, su ocurrencia aparece en el día elegido.
- Cuidador no recibe la opción de alta y tampoco puede crear por acceso directo o petición al servidor.
- Con filtro por mascota, el formulario precarga esa mascota; sin filtro, exige seleccionarla. El usuario autorizado puede cambiar la mascota precargada.

## Calendario: vistas y filtros

### Decisiones aprobadas

- El calendario abre en vista mensual y, sin filtros, reúne las ocurrencias de todas las mascotas a las que el usuario tiene acceso.
- Se puede filtrar por mascota, por tipo de recordatorio o por ambos criterios a la vez. Un resultado vacío por filtros se distingue de un calendario sin eventos y permite limpiar los filtros.
- Al cambiar entre vistas mensual, semanal y listado se conservan la fecha consultada y los filtros activos. Cambiar de vista no debe perder, duplicar ni desplazar ocurrencias.
- Las fechas se presentan según la hora local de Argentina; la navegación entre meses y años mantiene cada ocurrencia en su día local correcto.

### Casos acordados para la matriz

- Filtro por mascota accesible: solo sus ocurrencias.
- Filtro por tipo: solo las ocurrencias del tipo elegido.
- Combinación de ambos filtros: intersección correcta, sin eventos ajenos.
- Filtros sin resultados: mensaje específico y opción de limpiar filtros.
- Intento de consultar eventos de una mascota sin acceso, incluso mediante petición directa: denegado.
- Cambio de mes a semana o listado, y regreso: mismos datos, fecha consultada y filtros conservados.
- Navegación entre meses y años, incluidas fechas próximas a medianoche: día local correcto.

## Calendario: detalle de ocurrencias

### Decisión aprobada

El detalle abierto desde el calendario es de consulta. La confirmación de una tarea se realiza desde las tareas del día; no se agrega una segunda acción de confirmación en el detalle del calendario. El detalle corresponde a una ocurrencia concreta, aunque el recordatorio sea recurrente.

### Motivo

Mantener una sola vía de confirmación evita ampliar el alcance y evita ambigüedades al abrir ocurrencias futuras o pasadas. El calendario refleja el estado de las confirmaciones, pero no es su punto de entrada.

### Casos acordados para la matriz

- Abrir una ocurrencia muestra recordatorio, mascota, tipo, fecha y hora, y estado correctos.
- Si hay varias ocurrencias el mismo día, se abre exactamente la elegida.
- En una recurrencia, el detalle presenta el estado de esa fecha y no el estado global de toda la serie.
- La pérdida de acceso impide abrir el detalle aunque el calendario todavía estuviera visible en pantalla.
- Si el evento fue eliminado desde otro dispositivo, se informa que ya no está disponible y se retira al actualizar.
- Cerrar el detalle devuelve a la misma fecha, vista y filtros.

## Calendario: sincronización e historial

### Decisión aprobada

Al desactivar o eliminar un recordatorio, dejan de aparecer sus ocurrencias futuras, pero permanecen visibles para los usuarios autorizados todas las ocurrencias pasadas. Las confirmadas conservan su fecha, estado y autoría; las que no se confirmaron se identifican como «no confirmadas», sin presentarlas como cumplidas. El calendario refleja así el historial de tareas sin confundirlo con eventos clínicos efectivamente registrados.

### Casos acordados para la matriz

- Crear o modificar la fecha y hora de un recordatorio agrega o mueve sus ocurrencias al día correcto, sin duplicados en la fecha anterior.
- Confirmar una tarea desde las tareas del día actualiza solo la ocurrencia correspondiente en el calendario.
- Desactivar, reactivar o eliminar un recordatorio actualiza las ocurrencias futuras según su estado, sin borrar las pasadas confirmadas ni las pasadas no confirmadas.
- Una ocurrencia pasada sin confirmación se identifica como «no confirmada» y nunca como cumplida.
- Una recurrencia sin fecha de fin carga solo las ocurrencias del período consultado, sin intentar materializar una serie infinita.
- Los tipos se distinguen visualmente con color y texto o ícono; el color no es el único indicador.

## Módulos restantes incluidos en la matriz

La matriz también cubre exportación del historial clínico (CU12), álbumes y fotos (CU16, CU16a, CU16b, CU16c), configuración y notificaciones (CU10 y CU10a), cierre de sesión (CU10b), plan Premium y pagos (CU26), badges (CU27) y auditoría transversal (CU24). Se diseñan pruebas contra el comportamiento objetivo, aunque algunas funciones todavía no estén implementadas. Ningún caso debe registrarse como ejecutado o aprobado por el solo hecho de estar diseñado.

### Reglas comprobadas en los casos de uso

- La exportación PDF es gratuita e ilimitada para quien tenga acceso a la mascota; incluye sus datos, los del propietario, fecha de generación e historial clínico completo. Se descarga al dispositivo y el servidor no conserva una copia.
- Los álbumes y las fotos se pueden ver con plan Free; su creación y modificación están reservadas a Premium. Los archivos admitidos son JPG, PNG y HEIC, con máximo de 10 MB por foto. Los videos no integran el MVP.
- Un álbum se elimina de forma irreversible junto con sus fotos, previa confirmación que indica cuántas se perderán. Las fallas de almacenamiento no deben dejar una eliminación parcial presentada como exitosa.
- Cada mascota admite hasta tres badges activas. Solo el propietario Premium las administra; Familia y Cuidador pueden verlas. El texto es obligatorio y el emoji opcional.
- Premium se activa solo tras confirmación válida del pago por MercadoPago. Los fallos y pagos aún pendientes no cambian el plan. La exportación PDF no depende del plan.
- La configuración muestra perfil, plan y estado de notificaciones. El toggle de push debe reflejar el estado real y volver al anterior si falla su actualización. El bloqueo de notificaciones en el sistema operativo se informa sin fingir que llegan avisos.
- El log de auditoría se escribe para acciones completadas, identifica actor, acción, entidad y fecha, y no es editable por usuarios. Una falla al escribirlo no revierte la operación principal y requiere reintento y registro interno del error.

### Definiciones abiertas que afectan resultados esperados

- Renovación automática y cancelación de suscripciones: el CU26 no fija su comportamiento y no se asumen para el MVP.
- Consulta del log por propietario prevista en CU24 frente a los endpoints de consulta actualmente deshabilitados. Recomendación QA: mantener la escritura del log dentro del alcance y no exponer lectura a usuarios hasta definir una necesidad, una interfaz y reglas precisas de privacidad; esta diferencia con el CU debe quedar explicada.
- El CU10b admite que, si falla la revocación remota al cerrar sesión, se borren datos locales aunque el token aún pueda funcionar. Es una brecha de seguridad que requiere decisión antes de cerrar el resultado esperado de recuperación de error.
- El CU16 fija 10 MB por foto pero no especifica si se cuentan 10 000 000 o 10 485 760 bytes. La frontera exacta queda pendiente de contrato técnico; se prueban ambos lados una vez definida.
- Los CU clínicos cubren registrar y consultar, pero no fijan qué roles pueden corregir o eliminar un evento ya guardado. Los casos de esas operaciones están identificados, pero deben concretar permisos y semántica de borrado antes de ejecutarse.
- Deshabilitar una cuenta se consideró como evolución de perfil, pero faltan reglas de recuperación, acceso compartido y retención de datos. No se le asignan resultados esperados ficticios en esta matriz.
- Alcance de la purga de una mascota compartida por un álbum con otras mascotas activas: debe conservarse el álbum de las demás, pero falta concretar la desvinculación y la propiedad de las fotos.

### Decisiones aprobadas sobre álbumes y plan

- Un álbum puede estar asociado a varias mascotas. Los casos de prueba usan esa relación N:M como comportamiento objetivo, aunque los CU16a/CU16b describan una sola mascota y el contrato actual aún use `mascotaId` singular. Esta diferencia no se corrige editando los CU presentados.
- Las asociaciones de mascotas se fijan al crear el álbum. No se editan después: si cambia el conjunto de mascotas, se crea otro álbum. Esto limita el alcance de edición a nombre y descripción.
- Solo el propietario Premium gestiona álbumes y fotos de sus mascotas; Familia y Cuidador pueden verlos si mantienen acceso, con independencia de su propio plan. Un álbum de varias mascotas debe pertenecer a un único propietario y cada mascota asociada debe ser suya.
- Al vencer Premium no se eliminan mascotas, accesos compartidos, álbumes, fotos, badges ni fotos de perfil existentes. Permanecen visibles según los permisos ordinarios. Se bloquean nuevas acciones exclusivas Premium y nuevas altas que excedan los límites Free; una futura reactivación del plan debe permitir continuar con los datos conservados.
- Una renovación Premium anticipada agrega el período comprado al vencimiento vigente; no recorta los días restantes. Esta regla se aplica a los pagos confirmados y evita extender por pagos pendientes, fallidos o notificaciones duplicadas.
- El perfil permite editar nombre y avatar para Free. Premium puede usar foto de usuario. El email se mantiene sin edición en esta entrega. Esta ampliación posterior al CU10 se documenta aquí sin modificar el CU presentado.

### Brechas técnicas conocidas que no deben aprobarse por anticipado

El plan backend del 11 de octubre indica que exportación PDF y pagos aún no existen. La colección Postman registra álbumes con `mascotaId` singular, fotos como URL y metadatos sin carga binaria, eliminación lógica de álbumes/fotos, badges sin endpoint de edición y consultas de auditoría deshabilitadas. También están pendientes las restricciones Premium de álbumes, fotos y badges y el límite de tres badges. Los casos de la matriz describirán el producto objetivo; estas brechas se registran como no ejecutadas o fallidas según la verificación real, nunca como aprobadas por documentación.

Con álbumes N:M, la purga de una mascota no puede borrar un álbum que siga asociado a otra mascota activa. La política de desvinculación y de fotos compartidas debe definirse antes de cerrar los casos de purga.
