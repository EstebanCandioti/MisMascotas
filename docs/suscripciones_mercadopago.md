# Suscripciones con Mercado Pago

Los planes mensual de 4000 ARS y anual de 40000 ARS, la configuración y `GET /suscripciones/planes` se documentan en [Catálogo de planes](catalogo_planes.md).

La consulta autenticada `GET /suscripciones/me` y su contrato para el frontend se documentan en [Consulta de la suscripción propia](consulta_suscripcion_propia.md).

La cancelación desde la aplicación, `POST /suscripciones/{idSuscripcion}/cancelar`, se documenta en [Cancelación de la suscripción propia](cancelacion_suscripcion.md).

Este bloque implementa alta, continuación, reemplazo, primer cobro, renovaciones, cambios de suscripción y vencimiento de cobertura. El procesamiento de webhooks es secuencial. Ver el [contrato actual del procesador](procesador_notificaciones_mercadopago.md) y su [QA](qa_ciclo_suscripciones.md).

## Confirmación del primer cobro

`SuscripcionService.confirmarPrimerCobro(usuarioId, idSuscripcion)` identifica primero la suscripción por UUID y propietario. Una reserva ajena/inexistente produce recurso no encontrado antes de consultar al proveedor. Si no tiene ID remoto, queda `PENDIENTE_CONCILIACION` sin escrituras ni consultas HTTP. Una confirmación previa devuelve el pago y vencimiento persistidos sin consultar otra vez al proveedor.

La verificación remota ocurre fuera de transacciones de escritura (también suspende una eventual transacción del llamador):

1. Consulta `GET /preapproval/{id}` y verifica ID remoto, UUID local en `external_reference`, importe, moneda, frecuencia y unidad contra el catálogo del servidor.
2. Exige `authorized` antes de buscar cuotas. `pending` no acredita un cobro.
3. Recorre `GET /authorized_payments/search?preapproval_id=...&offset=...` hasta completar la paginación con el tamaño de página predeterminado por Mercado Pago. Rechaza páginas incompletas, cambios del total y cuotas de otra suscripción. El límite defensivo de 10.000 cuotas deja el caso sin verificar, en lugar de elegir sobre una lista parcial.
4. Deduplica por `payment.id`, que no es el ID de la cuota, y consulta `GET /v1/payments/{id}` por cada candidato.
5. Exige el mismo ID, `approved`, importe, moneda y `date_approved`. Compara importes con `BigDecimal.compareTo`, admitiendo el decimal numérico o textual del proveedor.
6. Elige el pago aprobado más temprano por fecha de aprobación. Un empate entre IDs distintos, datos inconsistentes o evidencia insuficiente dejan el caso sin activar. Una cuota con pago aún incierto impide elegir otra como primer cobro.

El detalle del pago individual y la separación entre creación/aprobación siguen la [referencia oficial de Obtener pago](https://www.mercadopago.com.ar/developers/es/reference/online-payments/subscriptions/get-payment/get). La [búsqueda de cuotas](https://www.mercadopago.com.ar/developers/es/reference/online-payments/subscriptions/authorized-payment-search/get) documenta `preapproval_id`, `payment.id` y la paginación. La colección QA específica de Mercado Pago no está presente en este árbol de trabajo; los contratos simulados se basan en esas referencias y deben contrastarse con esa colección en QA.

Después de terminar las consultas, `SuscripcionCobroTransaccionalService` abre una transacción, bloquea primero al usuario como alta/reemplazo y relee la reserva. Comprueba nuevamente propietario, estado `PENDIENTE`, tipo de plan e ID remoto. Si ganó un reemplazo o cambió la identidad verificada, no activa. Si otra confirmación ya terminó, devuelve el resultado persistido.

La transacción guarda `Pago` en `PAGO / APROBADO`, pasa la suscripción a `ACTIVA`, habilita Premium y encola dos eventos mediante `EventoAuditoriaPendienteService`, uno por el pago (`CREATE`) y otro por la activación de la suscripción (`ACTIVATE`). Si la transacción falla, se revierten el pago, la activación, el cambio de Premium y ambos eventos.

Los resultados son:

- `ACTIVADA`: pago, activación, Premium y eventos confirmados.
- `YA_CONFIRMADA`: devuelve el pago y vencimiento de una suscripción ya activada.
- `SIN_COBRO_APROBADO`: reserva remota pendiente, ausencia de cuotas con pago o únicamente pagos rechazados/cancelados; permite repetir la verificación.
- `NO_SE_PUDO_VERIFICAR`: error de transporte/HTTP, respuesta incompleta, paginación incierta o pago todavía no definitivo; permite reintentar sin escrituras.
- `PENDIENTE_CONCILIACION`: identidad, condiciones, pago asociado a otra suscripción, empate de candidatos, falta de ID remoto o reserva que dejó de ser elegible; requiere revisar la evidencia.

### Fechas de cobro y vigencia

Para el MVP, `fecha_inicio` conserva la fecha de reserva y `Pago.fechaPago` toma `date_approved`. El vencimiento del primer cobro se calcula sumando la frecuencia de meses calendario a la aprobación en `America/Argentina/Buenos_Aires`. El próximo recargo se obtiene de `next_payment_date` de Mercado Pago y puede ser nulo. Las renovaciones suman un mes desde la mayor fecha entre el vencimiento vigente y la aprobación del nuevo pago. No se suman 30 días fijos ni se parte de la fecha en que el usuario solicita la confirmación. Ejemplo: 31 de enero de 2026 a las 23:30 de Argentina vence el 28 de febrero a las 23:30. La repetición devuelve ese vencimiento original. Una unidad distinta de `months` queda pendiente de conciliación hasta definir su regla.

### SQL para revisión y aplicación manual

`DB/migraciones/20261112_confirmacion_primer_cobro.sql` detecta IDs externos duplicados y aborta sin cambiarlos. Si no hay duplicados, crea índices únicos para `suscripcion.id_suscripcion_mercadopago` y `pago.id_transaccion_mercadopago`. PostgreSQL permite múltiples `NULL`, por lo que las reservas inciertas siguen siendo válidas. El bloqueo de tablas mantiene consistente la comprobación durante la creación de índices.

La migración también permite `NULL` en la columna textual heredada `pago.estado`, si existe; conserva sus valores históricos y permite los nuevos inserts JPA mediante `estado_id`. La inicialización QA ya omite esa columna heredada. Revisar y aplicar el SQL manualmente en Supabase: esta tarea no ejecuta migraciones allí.

### Pruebas del primer cobro

Las pruebas de servicio usan el gateway simulado y las del cliente usan `MockRestServiceServer`; no se realizan cobros ni consultas reales. La IT opt-in `SuscripcionPrimerCobroPostgresIT` cubre dos confirmaciones concurrentes, repetición, rollback posterior a ambos encolados, reemplazo que gana durante la verificación, pago asociado a otra cuenta y unicidad del ID remoto de suscripción.

Para ejecutarla sobre PostgreSQL dedicado de Docker con el SQL de QA actualizado y el seed aplicado, configurar exclusivamente `QA_COBRO_DB_URL`, `QA_COBRO_DB_USERNAME` y `QA_COBRO_DB_PASSWORD`. La prueba sobrescribe la conexión de Spring con esas variables para no usar la configuración de desarrollo. Desde `backend`:

```bash
RUN_POSTGRES_PRIMER_COBRO_IT=true ./mvnw -Dtest=SuscripcionPrimerCobroPostgresIT test
```

En esta VM no están disponibles Docker ni `psql`; la IT queda pendiente, no constituye evidencia de bloqueo/rollback en PostgreSQL real. Sus datos son ficticios y la limpieza se limita a las cuentas UUID creadas por cada prueba. El procesador periódico de auditoría se deshabilita en esa IT para contar de forma determinista los eventos pendientes.

Validación del 12/11/2026, ejecutada desde `backend`:

```bash
./mvnw \
  -DargLine=-javaagent:/home/esteban/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar \
  -Dtest=MercadoPagoClientTests,CatalogoPlanesSuscripcionTests,SuscripcionReservaServiceTests,SuscripcionServiceTests,SuscripcionPrimerCobroServiceTests,SuscripcionCobroTransaccionalServiceTests,SuscripcionPrimerCobroPostgresIT,EventoAuditoriaPendienteServiceTests,ProcesadorEventoAuditoriaServiceTests,ProcesadorEventosAuditoriaProgramadoTests,MascotaServiceTests \
  test
```

Resultado: `BUILD SUCCESS`, 99 casos contabilizados, 93 ejecutados sin fallos/errores y 6 de PostgreSQL omitidos por la condición opt-in. Incluye las 46 pruebas previas de alta/continuación/reemplazo. El agente Java resuelve la limitación de auto-attach de Mockito en esta VM, sin cambios de dependencias. Los tests simulados cubren identidad, importes, moneda, estados, paginación, ambigüedad, snapshots, repetición y relectura luego de un reemplazo; la exclusión concurrente y el rollback real de los cuatro cambios deben confirmarse mediante la IT.

Archivos de este bloque:

- Contrato/HTTP: `MercadoPagoGateway`, `MercadoPagoClient` y `MercadoPagoClientTests`.
- Persistencia: entidades `Pago`/`Suscripcion`, nuevo `PagoRepository`, `DB/01-init.sql`, `DB/mismascotas_schema.sql` y la migración del primer cobro.
- Servicios: `SuscripcionService`, nuevos `SuscripcionPrimerCobroService` y `SuscripcionCobroTransaccionalService`.
- Pruebas: nuevas `SuscripcionPrimerCobroServiceTests`, `SuscripcionCobroTransaccionalServiceTests` y `SuscripcionPrimerCobroPostgresIT`; adaptación del constructor en `SuscripcionServiceTests` y del gateway simulado en `SuscripcionConcurrenciaPostgresIT`.
- Documentación: este archivo.

## Flujo

1. El backend valida el plan configurado y las credenciales requeridas.
2. Una transacción bloquea la fila del usuario y busca una suscripción `PENDIENTE` para ese usuario y plan.
3. Si no existe, guarda el intento local. Su UUID será el `external_reference`.
4. Fuera de esa transacción llama a `POST /preapproval` con estado `pending`.
5. Guarda el identificador remoto antes de devolver el `init_point`.

Si ya existe una reserva, no se vuelve a llamar al proveedor. Una reserva sin identificador remoto queda `PENDIENTE_CONCILIACION`; una que ya tiene identificador queda `INTENTO_EN_CURSO`. Una respuesta incierta del proveedor o un fallo al guardar su identificador conserva el UUID local para conciliación.

El bloqueo sobre el usuario serializa solamente las altas del mismo usuario. Usuarios distintos pueden reservar en paralelo. La llamada HTTP no mantiene bloqueos de base de datos.

## Continuar una suscripción pendiente

`SuscripcionService.continuarAlta(usuarioId, idSuscripcion)` recupera una reserva solamente por su UUID y propietario. La lectura transaccional termina antes de consultar `GET /preapproval/{id}` y la operación no modifica la reserva ni crea otro `preapproval`.

El enlace se devuelve únicamente cuando Mercado Pago informa el mismo ID remoto, el UUID local como `external_reference`, estado `pending` y las mismas condiciones vigentes de importe, moneda, frecuencia y unidad de frecuencia. El importe se compara numéricamente, sin exigir la misma escala decimal.

Los resultados posibles son:

- `CHECKOUT_DISPONIBLE`: el intento sigue pendiente y compatible; incluye el `init_point` existente.
- `PENDIENTE_CONCILIACION`: la reserva local aún no tiene ID remoto; no se consulta al proveedor.
- `REQUIERE_VERIFICAR_PRIMER_COBRO`: Mercado Pago informa `authorized`; este bloque no confirma el cobro ni activa Premium.
- `NO_CONTINUABLE`: la reserva local no está pendiente, Mercado Pago informa `cancelled` o cambiaron las condiciones del plan.
- `NO_SE_PUDO_CONFIRMAR`: timeout, error HTTP, estado inesperado, respuesta incompleta o identidad/referencia inconsistente.

Una reserva ajena o inexistente se responde como recurso no encontrado y nunca dispara una llamada al proveedor. La continuación tampoco ejecuta `POST`, cancelación, reemplazo, webhook ni conciliación por búsqueda.

## Empezar de nuevo

`SuscripcionService.reemplazarAlta(usuarioId, idSuscripcion)` reemplaza únicamente una reserva propia que todavía está `PENDIENTE` y conserva un ID remoto. Una reserva sin ID queda `PENDIENTE_CONCILIACION` y no llama a Mercado Pago.

El flujo se divide para que ninguna llamada HTTP mantenga bloqueos de base de datos:

1. Consulta `GET /preapproval/{id}` y valida el ID remoto y el `external_reference`.
2. Si el estado es `pending`, solicita la cancelación con `PUT /preapproval/{id}` y siempre vuelve a consultar con `GET`.
3. Si el primer `GET` ya informa `cancelled`, omite el `PUT`.
4. Solo después de confirmar `cancelled`, una transacción bloquea la fila del usuario, relee el intento, lo marca `SUSCRIPCION / CANCELADA`, registra `fecha_cancelacion` y crea una única reserva `PENDIENTE` para el mismo plan.
5. La transacción confirma antes del nuevo `POST /preapproval`. Únicamente quien creó la reserva nueva puede ejecutar ese `POST` y guardar su ID remoto.

La integración verificada con la cuenta de prueba aceptó `{"status":"cancelled"}` y rechazó `canceled`, aunque la guía de gestión utiliza esta última variante. El cliente usa deliberadamente `cancelled` y confirma el resultado mediante un `GET` posterior.

Los resultados del reemplazo son:

- `CHECKOUT_DISPONIBLE`: cancelación confirmada, nueva reserva persistida e ID remoto guardado; incluye el enlace nuevo.
- `PENDIENTE_CONCILIACION`: falta el ID anterior, falló la transacción local o quedó incierto el `POST`/guardado del reemplazo.
- `REQUIERE_VERIFICAR_PRIMER_COBRO`: una consulta informó `authorized`; no se crea reemplazo.
- `CANCELACION_NO_CONFIRMADA`: el estado posterior no confirma `cancelled`, hubo un error de consulta o la identidad remota fue inconsistente. Puede reintentarse la consulta sin cambiar la reserva local.
- `INTENTO_EN_CURSO`: la transacción encontró otra reserva pendiente para el mismo usuario y plan.
- `NO_REEMPLAZABLE`: el intento anterior ya no era elegible o una solicitud previa ya lo procesó.

Si se pierde la respuesta del `PUT`, el `GET` posterior decide el flujo. Si se pierde la respuesta del nuevo `POST`, la nueva reserva queda pendiente sin ID; repetir la operación sobre el intento anterior no emite otro `POST`.

Queda pendiente para QA reproducir la carrera en la que el usuario completa el checkout anterior mientras se procesa la cancelación. Si cualquiera de las consultas informa `authorized`, el reemplazo debe detenerse para que `confirmarPrimerCobro` verifique el cobro antes de modificar estados locales.

## Configuración

### Procesamiento individual de una notificación

### Ejecución periódica, renovaciones y cambios de suscripción

El procesador admite `subscription_authorized_payment` y `subscription_preapproval`.
Consulta el recurso actual y procesa una sola vez por tanda cada recurso, agrupando
las notificaciones pendientes capturadas antes del HTTP. Nuevas llegadas quedan para
otra evaluación. Resultado y observación se guardan en una transacción corta posterior.

La factura `recycling` con pago rechazado sigue pendiente con consulta cada 24 horas.
`waiting for gateway` se consulta cada cinco minutos. El límite propio es de diez días
por factura desde la primera observación pendiente, con una última consulta antes de
pasar a revisión. Un nuevo webhook permite evaluar sin esperar la fecha programada.
`processed` con pago rechazado finaliza la cuota sin cobrar, sin cancelar la suscripción.

Los fallos técnicos usan 1, 5, 15 y 60 minutos y luego cada hora hasta 24 horas del
episodio. Una consulta válida que encuentra una cuota pendiente cierra ese episodio,
conservando el plazo de negocio. HTTP 429 respeta Retry-After. Credenciales, datos
inconsistentes y estados desconocidos requieren revisión.

Las renovaciones se identifican por factura y pago, ambos únicos. Registran pago,
vigencia y auditoría atómicamente bajo bloqueo del usuario. Una repetición no suma
meses. Pausa/cancelación conservan cobertura pagada y reactivación no concede un mes.
El barrido de vencimientos usa la misma ejecución periódica y el mismo bloqueo.

Aplicar `DB/migraciones/20261118_ciclo_suscripciones.sql` después de las migraciones
anteriores y antes de iniciar el backend. La inicialización Docker ya incluye el esquema.

Consultar la [especificación técnica](procesador_notificaciones_mercadopago.md) para
estados, motivos, intervalos y errores, y el [QA](qa_ciclo_suscripciones.md) para comandos,
resultados y consultas. El endpoint manual de primer cobro mantiene su contrato HTTP.

### Recepción del webhook de suscripciones

`POST /webhooks/mercado-pago?data.id=<id de factura>` autentica la notificación mediante
`x-request-id` y `x-signature`, no mediante JWT. Solo este POST de webhooks es público;
las rutas de suscripciones siguen exigiendo la sesión. La excepción antigua
`/pagos/webhook` se reemplazó por esta ruta exacta.

En `backend/config/application.properties` (archivo local ignorado por Git), cargar:

```properties
# Solo para la prueba reproducible que sigue; no usar este secreto en un entorno real.
mercadopago.webhook.secret=secreto-ficticio-mismascotas
```

En el entorno de desarrollo real, usar la clave secreta de Webhooks de la aplicación
de Mercado Pago, no el Access Token. Reiniciar el backend después de cargarla.
No versionar ese archivo ni copiar credenciales reales a colecciones o logs.
La ausencia de la propiedad, o un valor vacío, permite iniciar la aplicación pero
devuelve `503` al recibir el webhook.

La firma se verifica antes de interpretar el JSON. Para `type=subscription_authorized_payment`,
el `id` del nivel superior identifica la **notificación**, mientras que `data.id`
identifica la **factura** y debe coincidir con la URL. Para `subscription_preapproval`,
`data.id` identifica la suscripción remota y conserva la misma validación de firma e IDs. Ambos IDs del cuerpo pueden
ser texto no vacío o enteros, nunca decimales, booleanos ni objetos.
El receptor delega la deduplicación al repositorio existente. Una transacción
independiente confirma el INSERT (o su conflicto por reenvío) **antes** de responder `200`.
La fila queda con `procesada_en` nulo; no se consulta a Mercado Pago ni se activa Premium.

Resultados HTTP:

- `200`: recepción persistida, reenvío ya registrado o tópico distinto ignorado.
- `401`: firma inválida o datos de firma ausentes; no guarda.
- `400`: JSON firmado inválido, tópico ausente, IDs incompletos o factura inconsistente; no guarda.
- `503`: falta el secreto o falla la apertura, escritura o confirmación de la transacción; no acusa recepción.

Un tópico distinto debe tener firma válida y un `type` textual no vacío, pero no
se aplican las validaciones específicas de factura ni se accede a la bandeja.
No se agrega todavía una regla de antigüedad del timestamp.
El formato sigue la [documentación oficial de Webhooks](https://www.mercadopago.com.ar/developers/es/docs/subscriptions/additional-content/your-integrations/notifications/webhooks).

#### Petición reproducible en Postman (solo secreto ficticio)

Método: `POST`. Autorización: **No Auth**. URL:

```text
http://localhost:8080/webhooks/mercado-pago?data.id=7034059187
```

Headers:

```text
Content-Type: application/json
x-request-id: qa-webhook-001
x-signature: ts=1704908010,v1=de029c13ba6f5a3eb2f103e5a5562903fa77c957ca2f9181bb48ca90974cf1aa
```

Body (raw / JSON):

```json
{
  "id": "notificacion-qa-webhook-001",
  "type": "subscription_authorized_payment",
  "data": { "id": 7034059187 }
}
```

Con la tabla ya creada y el secreto ficticio configurado, debe responder `200`.
Repetir la petición debe conservar una sola fila. Verificar con una consulta de solo lectura:

```sql
SELECT id_notificacion, topico, id_recurso, recibida_en, procesada_en
FROM public.notificacion_mercado_pago
WHERE id_notificacion = 'notificacion-qa-webhook-001';
```

El timestamp fijo solo sirve para esta prueba: el validador actual no aplica caducidad.
No reemplazar la configuración real de un entorno compartido por el secreto ficticio.

Pruebas dirigidas de recepción y regresión:

```bash
./mvnw -Dtest=MercadoPagoWebhookControllerTests,SuscripcionControllerTests,SuscripcionServiceTests,MercadoPagoFirmaWebhookTests,NotificacionMercadoPagoRepositoryTests test
```

El endpoint se prueba con la configuración real de seguridad, el validador real y
persistencia/transacciones simuladas (incluidos fallos de INSERT y de commit).
La prueba de secreto ausente usa MVC aislado para no modificar la configuración compartida.
La corrida HTTP con PostgreSQL está cubierta además por `SuscripcionCicloPostgresIT`: firma, recepción confirmada, reenvío y rollback tras un INSERT real. No se ejecuta contra Supabase.

Corrida de recepción del 16/11/2026 en Ubuntu, desde `backend`, rama `feature-pagos`:

```bash
./mvnw \
  -DargLine=-javaagent:/home/esteban/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar \
  -Dtest=MercadoPagoWebhookControllerTests,SuscripcionControllerTests,SuscripcionServiceTests,MercadoPagoFirmaWebhookTests,NotificacionMercadoPagoRepositoryTests,SuscripcionPrimerCobroServiceTests,SuscripcionCobroTransaccionalServiceTests,SuscripcionSinCobroServiceTests \
  test
```

Resultado: **BUILD SUCCESS**, 210 pruebas, cero fallos, errores u omisiones. Incluye
40 de recepción, 46 del controller de suscripciones, 52 de firma, 12 del repositorio
y 60 de los servicios de suscripciones/primer cobro. El agente Java se usa para
Mockito en esta VM; su ruta debe adaptarse o quitarse en otros entornos.
No hay Docker ni herramientas PostgreSQL disponibles en esta VM. La aplicación
manual de la migración y la deduplicación concurrente en PostgreSQL informadas por
el usuario son antecedentes; esta corrida no volvió a ejecutarlas.

Las credenciales y los datos del plan se leen del entorno:

- `MERCADOPAGO_ACCESS_TOKEN`
- `MERCADOPAGO_BACK_URL`
- `MERCADOPAGO_BASE_URL` (opcional; por defecto, la API oficial)
- `SUSCRIPCION_PREMIUM_MENSUAL_IMPORTE`
- `SUSCRIPCION_PREMIUM_MENSUAL_MONEDA` (por defecto `ARS`)
- `SUSCRIPCION_PREMIUM_MENSUAL_FRECUENCIA` (por defecto `1`)
- `SUSCRIPCION_PREMIUM_MENSUAL_FRECUENCIA_TIPO` (por defecto `months`)
- `SUSCRIPCION_PREMIUM_MENSUAL_MOTIVO`

No se deben versionar ni registrar en logs los tokens reales.

## Supabase y PostgreSQL existentes

Ejecutar `DB/migraciones/20261112_suscripciones_pendientes.sql`. La migración es idempotente para el estado e índice, y elimina la columna textual heredada `suscripcion.estado`: el modelo canónico usa `estado_id`. El script de inicialización local y el esquema de referencia ya reflejan ese cambio.

## Verificación pendiente de integración

La prueba automatizada del servicio usa una respuesta controlada y no consume Mercado Pago real. La concurrencia debe repetirse con PostgreSQL del Compose antes de aprobar el bloque: dos solicitudes del mismo usuario y plan deben dejar una sola fila local y observarse un solo `POST` en el proveedor simulado.

Con PostgreSQL levantado y las variables `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` configuradas:

```bash
RUN_POSTGRES_SUSCRIPCION_IT=true ./mvnw -Dtest=SuscripcionConcurrenciaPostgresIT test
```

La prueba crea una cuenta ficticia, verifica el conteo y la referencia externa, y elimina sus datos al terminar. El gateway de prueba no usa red ni credenciales de Mercado Pago.
