# Consulta de la suscripción propia

## Objetivo y alcance

`GET /suscripciones/me` permite reconstruir la pantalla de configuración después de iniciar sesión, cerrar el navegador o regresar del checkout. Consulta exclusivamente la base local; no confirma cobros ni sincroniza Mercado Pago.

Este documento describe la consulta propia. La cancelación se documenta en [Cancelación de la suscripción propia](cancelacion_suscripcion.md) y los planes mensual/anual en [Catálogo de planes](catalogo_planes.md).

## Solicitud

```http
GET /suscripciones/me
Authorization: Bearer <accessToken>
Accept: application/json
```

No lleva cuerpo ni necesita parámetros de ruta o de consulta. El backend obtiene al usuario del principal autenticado mediante el mecanismo existente. Un parámetro como `usuarioId` no selecciona otra cuenta. El token del desafío 2FA no habilita esta operación.

## Procesamiento y decisiones

1. Resolver el usuario autenticado y comprobar que existe.
2. Leer sus suscripciones y, en una sola consulta adicional, los identificadores de sus suscripciones con al menos un pago `APROBADO`.
3. Capturar un único instante del reloj. Hay cobertura pagada vigente si existe un pago aprobado y el vencimiento es estrictamente posterior a ese instante. En el instante exacto del vencimiento deja de considerarse vigente.
4. Seleccionar como `suscripcion` la que tenga la mayor cobertura vigente. Si ninguna tiene cobertura, seleccionar la más reciente que no esté reemplazada ni pendiente. El criterio de antigüedad es `fechaInicio`, con desempate determinista por UUID.
5. Seleccionar por separado el `intentoPendiente` más reciente, no reemplazado. Esto permite retomar una reserva sin ocultar una cobertura anterior.
6. Mapear un DTO explícito dentro de una transacción de lectura. No guardar entidades, registrar auditoría, emitir HTTP a MP ni extender vigencias.

El mandato y la cobertura son independientes: una suscripción `CANCELADA` o remota `paused` puede conservar cobertura pagada. Una fecha futura sin pago aprobado no demuestra cobertura. La cobertura ya pagada tampoco se elimina simplemente por reemplazar un mandato.

`esPremium` reproduce el indicador persistido que ya devuelve `/usuario/me`, para mantener compatibilidad con la autorización actual y cuentas Premium manuales del seed. `coberturaPagadaVigente` y `fechaFinCobertura` describen la evidencia de suscripciones pagadas; no sustituyen ese indicador ni lo corrigen durante el GET. Puede existir una diferencia transitoria antes de que actúe el procesador de vencimientos. El frontend debe refrescar la consulta tras las operaciones de pago y no activar Premium por su cuenta.

## Respuesta

`200 OK`, `Content-Type: application/json`. La ausencia de suscripción es un resultado normal, no un `404`.

| Campo raíz | Tipo | Significado |
|---|---|---|
| `esPremium` | boolean | Indicador Premium persistido del usuario. |
| `coberturaPagadaVigente` | boolean | Existe una cobertura futura respaldada por un pago aprobado. |
| `fechaFinCobertura` | string ISO 8601 UTC o null | Mayor vencimiento de las coberturas actualmente vigentes. |
| `suscripcion` | objeto o null | Suscripción seleccionada según las reglas anteriores. |
| `intentoPendiente` | objeto o null | Reserva pendiente recuperable al volver a abrir la aplicación. |

Los dos objetos tienen el mismo formato:

| Campo | Tipo | Significado |
|---|---|---|
| `idSuscripcion` | string UUID | ID local para las operaciones de la API de MisMascotas. |
| `tipoPlan` | string | Código persistido: `PREMIUM_MENSUAL` o `PREMIUM_ANUAL`. |
| `estado` | string | Estado local persistido: `PENDIENTE`, `ACTIVA`, `CANCELADA` o `VENCIDA`. |
| `estadoMercadoPago` | string o null | Último estado remoto conocido; no se consulta al proveedor en este GET. |
| `fechaInicio` | string ISO 8601 UTC | Fecha de reserva local; no necesariamente la fecha del cobro. |
| `fechaVencimiento` | string ISO 8601 UTC o null | Fin de cobertura persistido, incluso si ya venció. |
| `proximoRecargo` | string ISO 8601 UTC o null | Próximo cobro remoto conocido; no acredita cobertura. |
| `fechaCancelacion` | string ISO 8601 UTC o null | Cancelación registrada. |
| `tienePagoAprobado` | boolean | Existe al menos un pago aprobado asociado. |
| `coberturaVigente` | boolean | Esa suscripción tiene pago aprobado y vencimiento futuro. |
| `requiereConciliacion` | boolean | Reserva pendiente sin ID remoto, o suscripción pagada sin vencimiento. |

`requiereConciliacion` detecta esas dos inconsistencias locales. No afirma que la suscripción esté libre de otros motivos de revisión en la bandeja de webhooks. No se devuelven secretos, tokens, datos de tarjeta, enlaces de checkout ni identificadores privados del proveedor. Para obtener un enlace vigente se utiliza el endpoint de continuación existente.

### Usuario Free sin suscripciones

```json
{
  "esPremium": false,
  "coberturaPagadaVigente": false,
  "fechaFinCobertura": null,
  "suscripcion": null,
  "intentoPendiente": null
}
```

### Suscripción cancelada con cobertura vigente

Ejemplo consultado antes del 24 de diciembre de 2026:

```json
{
  "esPremium": true,
  "coberturaPagadaVigente": true,
  "fechaFinCobertura": "2026-12-24T17:54:59Z",
  "suscripcion": {
    "idSuscripcion": "1fd8b69e-ffc4-4798-b306-74b6d89dc43c",
    "tipoPlan": "PREMIUM_MENSUAL",
    "estado": "CANCELADA",
    "estadoMercadoPago": "cancelled",
    "fechaInicio": "2026-11-24T17:50:00Z",
    "fechaVencimiento": "2026-12-24T17:54:59Z",
    "proximoRecargo": null,
    "fechaCancelacion": "2026-11-24T18:31:43Z",
    "tienePagoAprobado": true,
    "coberturaVigente": true,
    "requiereConciliacion": false
  },
  "intentoPendiente": null
}
```

### Reserva pendiente

`suscripcion` puede ser null mientras `intentoPendiente` contiene el ID local y `estado: "PENDIENTE"`. La reserva no concede cobertura ni Premium por iniciarse. Si falta su ID remoto, `requiereConciliacion` es true: no se debe iniciar automáticamente otro checkout ni inventar un enlace.

## Errores

- `401`: token ausente, inválido, vencido o de preautenticación, según el mecanismo de seguridad existente.
- `404`: el principal autenticado no corresponde a un usuario existente.
- Errores internos de persistencia: se conserva el tratamiento global existente; no se responde una cuenta Free ficticia ante un fallo.

Los errores siguen el formato global `codigo`, `mensaje` y `timestamp`. No se incluyen detalles de conexión ni del proveedor.

## Verificación

- Pruebas unitarias: Free, Premium manual, activa/cancelada, vencimiento exacto, fecha futura sin pago, reserva con/sin ID remoto, reemplazo, coberturas múltiples y pago sin vencimiento.
- Contrato HTTP: sesión requerida, rechazo del token 2FA y selección por principal aunque se envíe un ID ajeno.
- `SuscripcionConsultaPostgresIT`: consulta HTTP sobre PostgreSQL real, aislamiento entre dos usuarios, pago aprobado y cancelación con cobertura. Usa exclusivamente el perfil `ci`, con datos transaccionales que se revierten. Se incorpora a la suite de CI.

Para ejecutarla localmente, levantar `ci/compose.yaml` en un proyecto temporal, activar `SPRING_PROFILES_ACTIVE=ci` y `RUN_POSTGRES_CONSULTA_IT=true`, y ejecutar desde `backend`:

```powershell
.\mvnw.cmd '-DargLine=-Duser.timezone=UTC' '-Dtest=SuscripcionConsultaServiceTests,SuscripcionControllerTests,SuscripcionConsultaPostgresIT' test
```

### Resultado local del 24/11/2026

Suite completa con el mismo conjunto de clases de CI, agregando la nueva integración: **499 pruebas, cero fallos, errores u omisiones**. `verify` terminó con `BUILD SUCCESS` y generó el JAR. PostgreSQL 17.6 fue local y descartable; no se consultaron Supabase ni Mercado Pago. Los reportes JUnit quedan en `backend/target/surefire-reports` y la salida final en `backend/target-consulta-ci-final.log`.

La implementación está en la rama `feature-consulta-suscripcion`, basada en `origin/backend` (`21c28c7`). Esta verificación no actualiza las imágenes Docker distribuidas ni despliega el endpoint en el entorno QA existente.
