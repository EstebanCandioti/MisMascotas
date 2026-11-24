# QA del ciclo de suscripciones

## 1. Entrega y alcance

Implementacion en `feature-pagos`, en la carpeta de desarrollo `PP 2/backend`, sin commit
ni push. Se conserva el trabajo local anterior. No se modificaron credenciales,
`application.properties`, correo ni tunel. No se aplico SQL en Supabase.

El [contrato tecnico](procesador_notificaciones_mercadopago.md) define estados, intervalos,
resultados y recuperacion. Estas son las ocho entregas del plan y su evidencia:

| Entrega | Evidencia de cierre |
|---|---|
| Datos del proveedor | Cliente probado con factura recycling sin next_retry_date, fechas opcionales y detalle del rechazo. |
| Persistencia por factura | Una evaluacion por recurso y tanda, inicio compartido entre notificaciones, unicidad por factura y pago. |
| Clasificacion | Rechazo recycling permanece pendiente, processed/rejected finaliza la cuota, devoluciones e inconsistencias requieren revision. |
| Intervalos | Reloj controlado: cinco minutos, 24 horas, diez dias, consulta final y reanudacion por nueva notificacion. |
| Renovaciones | Primer cobro y dos renovaciones dejan tres pagos y seis eventos, las repeticiones no suman meses. |
| Cambios remotos | Autorizar sin cobro no activa, pausa/reactivacion/cancelacion conservan cobertura y son idempotentes. |
| Vencimiento | Instante exacto, otra cobertura aprobada vigente, pago rechazado que no sostiene cobertura, rollback y carrera con renovacion. |
| QA y documentacion | Contratos HTTP, regresion, PostgreSQL real, migracion sobre esquema anterior y base nueva. |

## 2. Resultados del 18/11/2026

Entorno: Windows, Java 21, PostgreSQL 17.6 en contenedores temporales con puerto local
asignado automaticamente. Los contenedores se eliminaron al finalizar. El gateway MP estuvo
simulado durante todas las pruebas automaticas.

| Grupo | Resultado |
|---|---|
| Controllers, firma, repositorio, servicios y auditoria | 359 pruebas aprobadas. |
| Cliente HTTP MP, ejecutado por separado | 59 pruebas aprobadas. |
| PostgreSQL: bandeja, procesador, primer cobro y ciclo completo | 38 pruebas aprobadas. |
| Total dirigido, sin contar repeticiones de verificacion | 456 pruebas, cero fallos, errores u omisiones. |

Las integraciones incluyen HTTP con MockMvc, firma HMAC real, transaccion y PostgreSQL
reales: `200` con fila confirmada, reenvio sin duplicacion, `401` y rollback del INSERT
antes de responder `503`. Las consultas al proveedor simulado comprueban que no exista
una transaccion de escritura abierta durante HTTP.

Tambien se verifico la migracion dos veces sobre un esquema anterior con pago y
notificacion historicos. Se conservaron montos, factura historica nula, fechas,
REVISION, ultimo motivo y RLS. `DB/01-init.sql` y `DB/02-seed.sql` instalaron la base
nueva que uso toda la integracion. `mismascotas_schema.sql` es una referencia documental,
como indica su encabezado, y no es un instalador ejecutable.

No se ejecuto la suite general con la conexion de desarrollo. Los grupos anteriores
se seleccionaron expresamente. El cliente HTTP se ejecuto separado de MVC para evitar
la interferencia previa del nivel DEBUG con la prueba de captura de logs.

Evidencia local de ejecucion en `backend/target`: `qa-ciclo-unidades.log`,
`qa-ciclo-postgres.log`, `qa-ciclo-esquema.log` y reportes JUnit XML en `surefire-reports`.
Los warnings de unicidad y errores simulados en esos logs corresponden a casos de prueba
que verifican los rechazos y rollback esperados.

## 3. Comandos de pruebas

Ejecutar desde `backend`. No requieren modificar `application.properties` para las
pruebas unitarias y de controllers.

```powershell
$qaClases = @(
  'SuscripcionControllerTests', 'MercadoPagoWebhookControllerTests',
  'MercadoPagoFirmaWebhookTests', 'NotificacionMercadoPagoRepositoryTests',
  'SuscripcionServiceTests', 'SuscripcionReservaServiceTests',
  'SuscripcionPrimerCobroServiceTests', 'SuscripcionCobroTransaccionalServiceTests',
  'SuscripcionSinCobroServiceTests', 'CatalogoPlanesSuscripcionTests',
  'MascotaServiceTests', 'EventoAuditoriaPendienteServiceTests',
  'ProcesadorEventoAuditoriaServiceTests', 'ProcesadorEventosAuditoriaProgramadoTests',
  'ProcesadorNotificacionMercadoPagoServiceTests', 'ClasificacionNotificacionMercadoPagoTests',
  'PlanificadorNotificacionMercadoPagoTests', 'ProcesadorNotificacionesMercadoPagoProgramadoTests',
  'SuscripcionCicloServiceTests', 'SuscripcionCicloTransaccionalServiceTests',
  'SuscripcionVencimientoServiceTests'
)
.\mvnw.cmd '-DargLine=-Duser.timezone=UTC' (('-Dtest=') + ($qaClases -join ',')) test
.\mvnw.cmd '-DargLine=-Duser.timezone=UTC' '-Dtest=MercadoPagoClientTests,MercadoPagoCicloClientTests' test
```

Para las integraciones, usar exclusivamente una base local descartable llamada
`qa_notificaciones_mercado_pago`, inicializada con `DB/01-init.sql` y `DB/02-seed.sql`.
Las siguientes variables se definen solamente en el proceso de PowerShell de QA, sin
crear un `.env` ni cambiar la conexion del backend de desarrollo:

- `QA_COBRO_DB_URL`: `jdbc:postgresql://127.0.0.1:PUERTO_QA/qa_notificaciones_mercado_pago`.
- `QA_COBRO_DB_USERNAME` y `QA_COBRO_DB_PASSWORD`: credenciales de ese contenedor temporal.
- `QA_MP_NOTIFICACIONES_DB_URL`, `QA_MP_NOTIFICACIONES_DB_USERNAME` y
  `QA_MP_NOTIFICACIONES_DB_PASSWORD`: los mismos valores de QA.

```powershell
$env:RUN_POSTGRES_CICLO_IT = 'true'
$env:RUN_POSTGRES_NOTIFICACIONES_IT = 'true'
$env:RUN_POSTGRES_PRIMER_COBRO_IT = 'true'
$env:RUN_POSTGRES_PROCESADOR_NOTIFICACION_IT = 'true'
.\mvnw.cmd '-DargLine=-Duser.timezone=UTC' '-Dtest=SuscripcionCicloPostgresIT,NotificacionMercadoPagoPostgresIT,SuscripcionPrimerCobroPostgresIT,ProcesadorNotificacionMercadoPagoPostgresIT' test
```

UTC en la JVM evita el alias de zona de Windows que PostgreSQL no reconoce. Los calculos
de meses del negocio usan explicitamente `America/Argentina/Buenos_Aires`.

## 4. Aplicacion en Supabase

Con las migraciones anteriores ya aplicadas, ejecutar manualmente en SQL Editor:

`DB/migraciones/20261118_ciclo_suscripciones.sql`

Aplicarla antes de iniciar el backend actualizado, porque Hibernate usa `ddl-auto=validate`.
La migracion agrega datos remotos en suscripcion, indicador de intento reemplazado,
ID de factura unico en pago, observaciones de la bandeja e indice por recurso. No borra
pagos ni modifica resultados historicos o permisos RLS.

Los historicos ya COMPLETADA o REVISION no se reabren automaticamente. Una nueva
notificacion valida de la misma factura puede disparar otra consulta del recurso.

Verificacion de solo lectura:

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'notificacion_mercado_pago'
  AND column_name IN ('estado_factura_mp', 'estado_pago_mp', 'id_pago_mp',
                      'intento_mp', 'fecha_debito_mp', 'proximo_reintento_mp');

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'pago'
  AND indexdef LIKE '%id_factura_mercadopago%';

SELECT relrowsecurity AS rls_activo
FROM pg_class WHERE oid = 'public.notificacion_mercado_pago'::regclass;
```

El indice de factura debe ser UNIQUE y `rls_activo` debe ser true.

## 5. Casos Postman

Usar el entorno local ya configurado. Las rutas `/suscripciones/**` y `/usuario/me`
usan `Authorization: Bearer {{accessToken}}`. El webhook usa No Auth y firma HMAC.

| Caso | Peticion o accion | Resultado que verificar |
|---|---|---|
| Alta | POST `{{baseUrl}}/suscripciones/iniciar`, JSON `{"tipoPlan":"PREMIUM_MENSUAL"}` | Checkout o intento existente, sin conceder Premium por iniciar. |
| Primer pago | Completar el checkout de prueba y esperar el webhook | Un pago aprobado, una activacion, dos eventos y Premium. |
| Repeticion manual | POST `{{baseUrl}}/suscripciones/{{idSuscripcion}}/confirmar-primer-cobro`, Body none | YA_CONFIRMADA, mismos datos y vencimiento. |
| Reenvio del webhook | Repetir la misma solicitud firmada e ID de notificacion | 200 y una sola fila. |
| Actualizacion de una factura | Nueva notificacion firmada con nuevo ID y mismo data.id | Consulta actual, sin duplicar un pago ya registrado. |
| Recycling | MP informa recycling y pago rechazado | Pendiente y proxima consulta en 24 horas. |
| Waiting for gateway | MP informa ese estado y pago pendiente | Pendiente y proxima consulta en cinco minutos. |
| Cambio remoto | Pausar/cancelar/reactivar desde MP y recibir subscription_preapproval | Estado remoto actualizado, misma cobertura pagada. |
| Firma invalida | Cambiar x-signature | 401 sin persistir. |
| Estado desconocido o recurso inexistente del simulador | Notificacion firmada y consulta sin recurso verificable | Revision, sin acreditar. |
| Perfil | GET `{{baseUrl}}/usuario/me` | esPremium coherente con la cobertura. |

Formato del webhook, con data.id igual al parametro de la URL:

```json
{
  "id": "ID_UNICO_DE_NOTIFICACION",
  "type": "subscription_authorized_payment",
  "data": { "id": "ID_FACTURA_MP" }
}
```

Para un cambio remoto, usar `type=subscription_preapproval` y el ID del preapproval en
`data.id`. En ambos casos, `POST {{baseUrl}}/webhooks/mercado-pago?data.id=ID_RECURSO`.
Headers: Content-Type application/json, x-request-id y x-signature validos para ese recurso
y el secreto del entorno. Reutilizar el mecanismo de firma de QA existente. El ejemplo
con secreto ficticio esta en [suscripciones](suscripciones_mercadopago.md).

Cambiar un estado en el cuerpo del webhook no simula el estado de MP: el backend vuelve
a consultar la API. Los ciclos, plazos y errores que no podamos provocar de forma
controlada en MP se prueban con gateway simulado y reloj controlado en las IT.
No modificar fechas de usuarios reales para acelerar el vencimiento.

## 6. Consultas de seguimiento

Todas son de solo lectura. Reemplazar el ID de notificacion o UUID antes de ejecutar.

```sql
SELECT id_notificacion, topico, id_recurso,
       estado_procesamiento, ultimo_motivo, estado_factura_mp,
       estado_pago_mp, detalle_pago_mp, id_pago_mp, intento_mp,
       primera_comprobacion_pendiente_en, proximo_procesamiento_en,
       fecha_debito_mp, proximo_reintento_mp, procesada_en
FROM public.notificacion_mercado_pago
WHERE id_notificacion = 'ID_NOTIFICACION';

SELECT s.id_suscripcion, e.nombre AS estado_local,
       s.estado_mercadopago, s.fecha_vencimiento, s.proximo_recargo,
       u.es_premium,
       (SELECT count(*) FROM public.pago p WHERE p.suscripcion_id = s.id_suscripcion) AS pagos
FROM public.suscripcion s
JOIN public.estados e ON e.id_estado = s.estado_id
JOIN public.usuario u ON u.id_usuario = s.usuario_id
WHERE s.id_suscripcion = 'UUID_SUSCRIPCION'::uuid;

SELECT id_pago, id_transaccion_mercadopago, id_factura_mercadopago, monto, fecha_pago
FROM public.pago
WHERE suscripcion_id = 'UUID_SUSCRIPCION'::uuid
ORDER BY fecha_pago, id_pago;

SELECT id_notificacion, topico, id_recurso, ultimo_motivo, recibida_en
FROM public.notificacion_mercado_pago
WHERE estado_procesamiento = 'REVISION'
ORDER BY recibida_en DESC;
```

La auditoria automatica usa actor_id nulo y un origen en los snapshots. Para contarla,
filtrar por id_entidad de la suscripcion, usuario o pagos correspondientes, no por actor_id.
No se agrego una pantalla o endpoint administrativo de revision.
