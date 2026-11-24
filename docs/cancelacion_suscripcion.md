# Cancelación de la suscripción propia

## Objetivo

Permitir que el usuario detenga los próximos cobros de una suscripción propia, sin quitar la cobertura ya pagada. La operación verifica el estado remoto y permite recuperarse de una respuesta perdida o de una falla de persistencia local.

## Solicitud

```http
POST /suscripciones/{idSuscripcion}/cancelar
Authorization: Bearer <accessToken>
Accept: application/json
```

`idSuscripcion` es el UUID local obtenido de `GET /suscripciones/me`. No se envía cuerpo, ID de usuario, ID de Mercado Pago ni datos financieros. El principal autenticado determina al propietario. Un token de preautenticación 2FA no sirve para cancelar.

## Flujo

1. Leer la suscripción por UUID y propietario. Una ajena o inexistente devuelve `404` antes de consultar MP.
2. Admitir estados locales `ACTIVA`, `VENCIDA` o `CANCELADA`, siempre que no esté reemplazada. Una reserva `PENDIENTE` continúa usando el flujo de continuación/reemplazo existente; esta operación no crea otra suscripción.
3. Si falta el ID remoto, pedir conciliación sin emitir HTTP.
4. Consultar `GET /preapproval/{id}` y validar ID remoto y UUID local en `external_reference`.
5. Si MP informa `authorized` o `paused`, solicitar la cancelación mediante el gateway existente, que envía `PUT` con `status=cancelled`. Si el PUT falla o termina en timeout, igualmente verificar mediante otro GET: la respuesta perdida no demuestra que la operación haya fallado.
6. Solo una consulta que confirme `cancelled` o `canceled`, con identidad coincidente, habilita el cierre local. Si el primer GET ya informa cancelación, no repetir el PUT.
7. En una transacción corta, bloquear al usuario como hacen el primer cobro, las renovaciones y los vencimientos. Buscar la suscripción propia y recargarla explícitamente desde la base mediante `EntityManager.refresh`, antes de validar propietario, plan, ID remoto, elegibilidad y antigüedad del estado remoto o modificarla.
8. Guardar `CANCELADA`, estado remoto normalizado `cancelled` y fecha de cancelación. Conservar pagos, vencimiento e indicador Premium. Actualizar la última observación remota y encolar auditoría con el actor autenticado y origen `CANCELACION_USUARIO`, en la misma transacción.

Las llamadas a Mercado Pago ocurren fuera de transacciones de base de datos, incluso si el llamador ya tenía una abierta. Una respuesta remota anterior a una actualización ya persistida requiere conciliación y no sobrescribe ese cambio.

### Decisiones

- Cancelación del mandato y cobertura son independientes. No se recorta el vencimiento, tampoco si una renovación lo extendió mientras se consultaba a MP.
- El bloqueo por usuario coordina las escrituras locales, pero no actualiza los objetos ya cargados en JPA. Con Open EntityManager in View, la lectura inicial y la confirmación pueden compartir el mismo contexto durante toda la petición HTTP. Por eso la confirmación usa `refresh` después del bloqueo y antes de cualquier validación o modificación: evita sobrescribir una vigencia más reciente o aceptar una respuesta remota antigua. Las llamadas a MP siguen fuera del bloqueo y de la transacción local.
- La operación no reembolsa pagos ni crea checkout, pagos, renovaciones o períodos adicionales.
- Repetir una cancelación confirmada consulta al proveedor, pero no vuelve a cancelar allí ni duplica auditoría si no cambió la información persistida. Si un webhook ganó la carrera, el resultado puede ser `YA_CANCELADA`.
- Se permite cancelar una suscripción local vencida si MP todavía tiene el mandato autorizado o pausado: vencimiento y autorización de futuros cobros son conceptos distintos.
- Para detener el mandato se verifica identidad, no el precio actual del catálogo: un cambio de precio no debe impedir que el usuario cancele una suscripción anterior.
- `proximoRecargo` conserva la última información obtenida del proveedor; si está cancelada, ese dato no debe mostrarse como promesa de un nuevo cobro.

## Respuesta

Resultados funcionales: `200 OK`, `application/json`. El frontend debe interpretar `resultado`; un HTTP 200 no implica por sí solo una cancelación confirmada.

```json
{
  "idSuscripcion": "1fd8b69e-ffc4-4798-b306-74b6d89dc43c",
  "resultado": "CANCELADA",
  "estado": "CANCELADA",
  "fechaCancelacion": "2026-11-24T20:30:00Z",
  "fechaVencimiento": "2026-12-24T17:54:59Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `idSuscripcion` | UUID como string | Identificador local solicitado. |
| `resultado` | string enumerado | Resultado de la operación, según tabla siguiente. |
| `estado` | string o null | `CANCELADA` solo si quedó confirmada localmente. |
| `fechaCancelacion` | string ISO 8601 UTC o null | Fecha de cancelación persistida. |
| `fechaVencimiento` | string ISO 8601 UTC o null | Vencimiento conservado de esa suscripción. Para cobertura global consultar `/suscripciones/me`. |

| Resultado | Significado y acción del frontend |
|---|---|
| `CANCELADA` | Confirmada en MP y guardada localmente. Refrescar `/suscripciones/me` y `/usuario/me`. |
| `YA_CANCELADA` | Ya estaba cancelada localmente y se verificó el estado remoto. Refrescar la consulta. |
| `NO_CANCELABLE` | Reserva pendiente, reemplazada o estado local no admitido. Refrescar y ofrecer el flujo correspondiente. |
| `CANCELACION_NO_CONFIRMADA` | Fallo al consultar MP o la consulta posterior aún no confirma cancelación. Permitir reintentar, sin anunciar éxito. |
| `REQUIERE_CONCILIACION` | Falta de ID, identidad/estado remoto inconsistente, cambio concurrente incompatible, respuesta antigua o cancelación remota confirmada con falla local. No anunciar éxito; refrescar y revisar/reintentar según el caso. |

En los últimos tres resultados, `estado`, `fechaCancelacion` y `fechaVencimiento` son null para no presentar una instantánea previa como confirmación de éxito.

## Errores HTTP

- `400`: UUID inválido, mediante el tratamiento existente del controller.
- `401`: falta de sesión válida o token de preautenticación.
- `404`: usuario o suscripción no encontrados en el alcance autenticado.
- Otros errores internos previos a la cancelación remota conservan el manejo global.

Errores con el formato global `codigo`, `mensaje` y `timestamp`. No se imprimen cuerpos del proveedor, tokens o credenciales en logs.

## Verificación

- Unidad: activa/pausada, cancelación remota previa, timeout del PUT, error del GET, estado sin confirmar, identidad ajena, falta de ID, estado no cancelable y falla de cierre local.
- HTTP: cuerpo no requerido, sesión obligatoria, UUID inválido y rechazo del token 2FA.
- `SuscripcionCancelacionPostgresIT`: pago/vigencia/Premium conservados, auditoría con actor, repetición sin duplicados, concurrencia, rollback y reintento, rechazo de datos remotos viejos, aislamiento entre usuarios y vigencia extendida durante HTTP.
- Dos regresiones HTTP mantienen Open EntityManager in View habilitado y confirman cambios desde otra conexión/hilo durante la consulta remota simulada: una extensión de vigencia debe conservarse en respuesta, base y auditoría; una actualización remota local más reciente debe provocar `REQUIERE_CONCILIACION`, sin cancelar ni auditar localmente.
- La integración usa PostgreSQL descartable del perfil `ci`, `RUN_POSTGRES_CONSULTA_IT=true` y Mercado Pago simulado. Se incorpora al conjunto de CI. No implica una nueva cancelación real con el proveedor.

Esta entrega continúa en `feature-consulta-suscripcion`; no actualiza por sí sola las imágenes distribuidas de QA.

### Resultado local del 24/11/2026

La suite completa finalizó con **524 pruebas aprobadas, cero fallos, errores u omisiones**, y `verify` generó el JAR correctamente. Incluye las siete pruebas de cancelación sobre PostgreSQL 17.6 real y proveedor simulado. La salida queda en `backend/target-cancelacion-ci-final.log` y los reportes en `backend/target/surefire-reports`. El proyecto PostgreSQL descartable se elimina al finalizar; QA y las credenciales de desarrollo se conservan.

### Corrección de concurrencia del 24/11/2026

Las dos nuevas regresiones HTTP reprodujeron el problema antes de la corrección: se devolvía la vigencia anterior a una renovación confirmada y se aceptaba una respuesta remota anterior a la actualización persistida. Tras agregar `EntityManager.refresh` bajo el bloqueo por usuario, ambas pasan. No se mantiene la transacción abierta durante las llamadas a MP.

La verificación final ejecutó el mismo conjunto de CI sobre PostgreSQL 17.6 temporal, con perfil `ci`, los indicadores `RUN_POSTGRES_CONSULTA_IT`, `RUN_POSTGRES_CICLO_IT` y `RUN_POSTGRES_MIGRACIONES_IT` habilitados, y las conexiones dirigidas exclusivamente a esa base:

```powershell
.\mvnw.cmd -B -ntp -DreuseForks=false '-Dtest=*Tests,SuscripcionCicloPostgresIT,MigracionesPostgresIT,SuscripcionConsultaPostgresIT,SuscripcionCancelacionPostgresIT' verify
```

Resultado: **526 pruebas aprobadas, cero fallos, errores u omisiones; BUILD SUCCESS y JAR generado**. Incluye nueve pruebas de cancelación sobre PostgreSQL real, con Mercado Pago simulado. Los reportes están en `backend/target/surefire-reports`. Las nuevas regresiones pertenecen a la clase de integración que ya ejecuta CI; no requieren modificar la pipeline. El entorno temporal se eliminó al terminar. No se modificaron Supabase, credenciales, configuración local ni imágenes QA; no se realizó commit ni push.
