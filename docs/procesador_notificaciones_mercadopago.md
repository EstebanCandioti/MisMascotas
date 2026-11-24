# Procesador de notificaciones de Mercado Pago

## 1. Alcance y arquitectura

El MVP ejecuta una instancia del backend y un procesador secuencial. La misma ejecucion
periodica procesa notificaciones y luego evalua vencimientos Premium. Espera 30 segundos
al terminar la tanda, con una demora inicial de 30 segundos. No usa reservas distribuidas,
SKIP LOCKED ni procesadores paralelos.

Topicos admitidos:

- `subscription_authorized_payment`: una cuota de la suscripcion.
- `subscription_preapproval`: autorizacion, pausa, reactivacion, cancelacion o cambios de condiciones.

Una notificacion dispara la consulta del recurso actual. El cuerpo recibido no acredita
un pago ni decide por si mismo el estado de la suscripcion.

## 2. Identificadores y persistencia

| Dato | Origen y uso |
|---|---|
| ID de notificacion | `id` del webhook. Clave primaria de la bandeja, conserva la deduplicacion. |
| ID de recurso | `data.id`, igual al de la URL firmada. Identifica factura o preapproval segun el topico. |
| ID de factura | Recurso de `/authorized_payments/{id}`. Una cuota puede tener distintos intentos. |
| ID de suscripcion MP | `preapproval_id`. Debe identificar una suscripcion local. |
| Referencia externa | `external_reference`. Debe coincidir con el UUID de la suscripcion local. |
| ID de pago | `payment.id`, distinto del ID de factura. Se verifica en `/v1/payments/{id}`. |

`pago.id_transaccion_mercadopago` e `id_factura_mercadopago` son unicos: un pago o una cuota
no pueden otorgar dos acreditaciones. Los pagos historicos mantienen factura nula hasta
que un webhook verificado permita vincularla, sin extender otra vez la vigencia.

La bandeja conserva `estado_factura_mp`, `estado_pago_mp`, `detalle_pago_mp`, `id_pago_mp`,
`intento_mp`, `fecha_debito_mp` y `proximo_reintento_mp`. Estos ultimos datos son observaciones,
no reglas para calcular reintentos de cobro. La ausencia de `next_retry_date` es valida.

`intentos_pendientes` cuenta comprobaciones locales, no intentos de cobro de MP.
`intentos_temporales` corresponde al episodio tecnico activo.

## 3. Recepcion, agrupacion y commit

1. Validar HMAC antes de interpretar el JSON.
2. Validar topico e identificadores y persistir con `ON CONFLICT DO NOTHING`.
3. Responder `200` despues de confirmar la transaccion. Reenvios del mismo ID conservan la fila.
4. Buscar hasta 20 notificaciones disponibles, ordenadas por proxima fecha, recepcion e ID.
5. Para cada recurso distinto, capturar todos sus IDs pendientes antes de consultar MP.
6. Consultar y evaluar el recurso una sola vez por tanda.
7. Guardar resultado y observacion de los IDs capturados en una transaccion corta.

Las notificaciones que llegan durante la consulta quedan para otra evaluacion. Una nueva
notificacion queda disponible aunque exista una comprobacion diaria futura o una revision
anterior. Una revision historica no se sobrescribe al completar una notificacion nueva.

La primera observacion pendiente se obtiene por topico e ID de recurso entre todas sus
notificaciones. No se reinicia al recibir otra notificacion, cambiar de estado o reiniciar.

HTTP del receptor:

| Codigo | Situacion |
|---|---|
| 200 | Recepcion confirmada, reenvio o topico ajeno al alcance ignorado tras verificar firma. |
| 401 | Firma invalida o ausente. |
| 400 | JSON o identificadores invalidos para un topico admitido. |
| 503 | Secreto ausente o fallo al abrir, escribir o confirmar la transaccion. |

## 4. Clasificacion de cuotas

Antes de actuar, se comprueban factura, preapproval local y referencia externa. Cuando
existe un pago asociado, se consulta su detalle individual.

| Factura y pago verificados | Estado de bandeja / motivo | Accion |
|---|---|---|
| `recycling` y `rejected` | PENDIENTE / ESPERANDO_REINTENTO_MP | Comprobacion diaria. No modifica cobertura. |
| `waiting for gateway` y pago pendiente soportado o ausente | PENDIENTE / ESPERANDO_RESOLUCION_MP | Comprobacion en cinco minutos. |
| `scheduled` sin pago | PENDIENTE / COBRO_SIN_COBRO_APROBADO | Comprobacion en cinco minutos. |
| `processed` y `approved`, validaciones correctas | COMPLETADA / ACTIVADA o RENOVADA | Registrar una sola vez el cobro y la cobertura. |
| Cobro ya registrado coherentemente | COMPLETADA / YA_CONFIRMADA | Conservar pago, auditoria y vencimiento. |
| `processed` y `rejected` | COMPLETADA / CUOTA_SIN_COBRO | Finalizar la cuota sin extender cobertura ni cancelar la suscripcion. |
| Pago `refunded`, `charged_back`, `cancelled` o `canceled` | REVISION / PAGO_REQUIERE_REVISION | Tratamiento manual para el MVP. |
| Identidad, ID de pago o combinacion incompatibles | REVISION / motivo de inconsistencia | No acreditar. |

Los estados pendientes de pago soportados son `pending`, `in_process`, `authorized` e
`in_mediation`. Una combinacion no contemplada requiere revision.

`processed` con pago rechazado puede corresponder a intentos agotados o expiracion de
cuota. No se inventa esa causa a partir del contador. La expiracion de la cuota de MP
es distinta del vencimiento de cobertura Premium local.

Referencia: [logica de reintentos de MP](https://www.mercadopago.com.ar/developers/es/docs/subscriptions/integration-configuration/subscription-no-associated-plan/authorized-payments).

## 5. Intervalos y limites

Los intervalos empiezan al terminar la evaluacion anterior. La ejecucion efectiva ocurre
en una tanda posterior, segun la carga.

| Resultado | Siguiente consulta | Limite |
|---|---|---|
| Recycling | 24 horas | Diez dias desde la primera observacion pendiente de esa factura. |
| Esperando resolucion o sin pago | Cinco minutos | El mismo limite de diez dias. |
| Fallo tecnico | 1, 5, 15, 60 minutos y luego cada hora | 24 horas desde el inicio del episodio tecnico activo. |
| HTTP 429 | `Retry-After` valido o intervalo tecnico de respaldo | Conserva los limites aplicables. |

En el limite de negocio se realiza una ultima consulta. Si confirma un resultado
final, se aplica. Si sigue pendiente, guarda `REVISION / PLAZO_COBRO_PENDIENTE_AGOTADO`.
No declara un rechazo por calendario y no bloquea nuevos webhooks.

Una consulta valida que vuelve a encontrar una cuota pendiente cierra el episodio tecnico:
reinicia su contador y fecha tecnica, pero conserva la primera observacion de la factura.
Los fallos tecnicos sucesivos mantienen su episodio. Al vencerlo, se realiza la consulta
y se pasa a revision si sigue fallando. Se aplica el limite que venza primero.

`Retry-After` admite segundos no negativos o fecha HTTP RFC 1123. Un valor ausente o
invalido usa el intervalo tecnico. Cero o fecha pasada dejan el trabajo disponible para
la siguiente tanda. Un valor que alcanza o excede el limite produce
`REVISION / RETRY_AFTER_FUERA_DE_PLAZO`.

## 6. Primer cobro, renovaciones y atomicidad

Las consultas HTTP ocurren fuera de las transacciones de escritura. Se verifican IDs,
propietario, referencia, estado aprobado, fecha de aprobacion, importe, moneda y frecuencia
contra el catalogo del backend. Importe y moneda de la factura tambien deben coincidir.

La escritura bloquea primero al usuario, igual que alta y reemplazo, y relee la suscripcion.
Un intento reemplazado pasa a revision. Las cancelaciones historicas sin evidencia que
permita distinguir su origen tampoco se reactivan automaticamente.

- Primer cobro: `date_approved` mas un mes calendario en Buenos Aires.
- Renovacion: un mes calendario desde la mayor fecha entre el vencimiento vigente y
  `date_approved`. No se suman 30 dias fijos.
- `fecha_inicio` conserva la reserva y `fecha_pago` conserva la aprobacion real.
- `proximo_recargo` proviene de `next_payment_date`, puede ser nulo y no es la vigencia Premium.

Pago, vigencia, bandera Premium y dos eventos de auditoria se confirman juntos. Los
procesos automaticos usan actor nulo y un origen explicito en los snapshots. Las
renovaciones auditan el pago creado y la actualizacion de suscripcion.

Una confirmacion repetida retorna YA_CONFIRMADA sin sumar meses ni eventos. La unicidad
por factura y pago protege tambien ante solicitudes concurrentes o asociacion a otra cuenta.
La confirmacion manual del primer cobro conserva su endpoint y su DTO.

No se agrega una excepcion de vigencia para cobros confirmados meses despues ni un rechazo
por antiguedad. El vencimiento automatico se basa en las fechas almacenadas.

## 7. Cambios de suscripcion y vencimiento

Se consulta `/preapproval/{id}` y se verifica identidad y condiciones. Una modificacion
inesperada de condiciones queda en revision. `estado_mercadopago` y su fecha de actualizacion
se guardan separados del estado local por catalogo, que sigue usando `estado_id`.

- Autorizar el mandato no activa Premium sin un pago confirmado.
- Pausar y reactivar no conceden meses adicionales.
- Cancelar registra CANCELADA y conserva la cobertura pagada. No finaliza por si solo el
  seguimiento de facturas inconclusas.
- La respuesta remota con `last_modified` anterior al persistido no revierte ese estado.
- Un pago aprobado valido puede reconocerse estando el mandato cancelado, sin reactivar
  el mandato. Los intentos reemplazados mantienen su proteccion.

Al terminar cada tanda se buscan hasta 20 usuarios con cobertura paga vencida. Por usuario,
una transaccion toma el mismo bloqueo que las renovaciones, relee todas sus suscripciones
y conserva Premium si cualquiera tiene cobertura paga vigente. Las canceladas tambien
pueden conservar cobertura hasta su vencimiento.

Cuando corresponde, ACTIVA pasa a VENCIDA y se deshabilita Premium. Se preserva CANCELADA.
Se auditan los cambios reales en la misma transaccion. Repetirlo no duplica eventos.
Cuentas Premium manuales sin suscripciones pagas no se deshabilitan por este barrido.
Una suscripcion paga sin vencimiento exige revision y revierte esa evaluacion.

## 8. Errores y recuperacion

| Situacion | Tratamiento |
|---|---|
| Timeout, conexion, HTTP 5xx | Pendiente tecnico, motivo TEMPORAL o HTTP_codigo. |
| 429 | Pendiente tecnico, conserva Retry-After. |
| 401/403 o credencial ausente | Revision por credenciales/permisos. |
| Otros 4xx, JSON o fechas invalidas | Revision. |
| Importe, moneda, identidad, condiciones o asociacion de pago incompatibles | Revision. |
| Fallo imprevisto durante la evaluacion | Reintento tecnico con FALLO_TEMPORAL y log de clase de excepcion. |
| Fallo al guardar pago o auditoria | Rollback conjunto. La bandeja puede reintentar. |
| Fallo al guardar resultado/observacion de bandeja | Rollback del grupo capturado, conserva estado anterior. |
| Fallo al vencer | Rollback por usuario, el resto de usuarios puede continuar. |

Si el pago ya se confirmo y falla el guardado posterior de la bandeja, el siguiente intento
encuentra el cobro existente y termina sin duplicarlo. Los logs no incluyen credenciales,
cuerpo completo de MP ni cabeceras de firma.

## 9. Configuracion y despliegue

Se conservan las propiedades `mercadopago.notificaciones.scheduler-habilitado` (true),
`intervalo-ms` (30000), `demora-inicial-ms` (30000) y `tamano-lote` (20).
Deshabilitar el scheduler tambien detiene su barrido de vencimientos.

Para la base de desarrollo que ya tiene las migraciones anteriores, aplicar manualmente
`DB/migraciones/20261118_ciclo_suscripciones.sql` antes de iniciar el backend. Agrega columnas
e indices, conserva datos y RLS y admite repeticion. No reabre historicos completados o en revision.
`DB/01-init.sql` contiene la instalacion limpia completa de Docker. `mismascotas_schema.sql`
se mantiene como referencia documental y no debe ejecutarse como instalador.

## 10. QA

La validacion ejecutada, los comandos y los casos Postman/SQL estan en
[QA del ciclo de suscripciones](qa_ciclo_suscripciones.md). Las pruebas de tiempo usan reloj
controlado. PostgreSQL se ejecuta en un contenedor temporal separado de Supabase y el gateway
de MP esta simulado. No se generan cobros reales en las pruebas automaticas.
