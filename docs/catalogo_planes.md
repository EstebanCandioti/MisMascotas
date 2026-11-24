# Catálogo de planes Premium

## Planes definidos

| Código | Importe por cobro | Moneda | Frecuencia |
|---|---:|---|---|
| `PREMIUM_MENSUAL` | 4000 | ARS | Cada 1 mes |
| `PREMIUM_ANUAL` | 40000 | ARS | Cada 12 meses |

El anual cobra el importe completo de 40000 ARS por período de doce meses. No son doce cuotas mensuales. Frente a doce cobros mensuales de 4000 ARS, representa una diferencia de 8000 ARS.

## Fuente de configuración

Los códigos y la validación de las periodicidades pertenecen a `CatalogoPlanesSuscripcion`. Los valores de despliegue se definen en `backend/configuracion/application.properties`, mediante variables de entorno y estos importes por defecto. Docker usa esta plantilla pública y `docker-compose.yml` pasa ambas familias de variables:

- `SUSCRIPCION_PREMIUM_MENSUAL_IMPORTE=4000`
- `SUSCRIPCION_PREMIUM_MENSUAL_MONEDA=ARS`
- `SUSCRIPCION_PREMIUM_MENSUAL_FRECUENCIA=1`
- `SUSCRIPCION_PREMIUM_MENSUAL_FRECUENCIA_TIPO=months`
- `SUSCRIPCION_PREMIUM_MENSUAL_MOTIVO=Plan Premium MisMascotas`
- `SUSCRIPCION_PREMIUM_ANUAL_IMPORTE=40000`
- `SUSCRIPCION_PREMIUM_ANUAL_MONEDA=ARS`
- `SUSCRIPCION_PREMIUM_ANUAL_FRECUENCIA=12`
- `SUSCRIPCION_PREMIUM_ANUAL_FRECUENCIA_TIPO=months`
- `SUSCRIPCION_PREMIUM_ANUAL_MOTIVO=Plan Premium anual MisMascotas`

`.env.example` documenta esos valores. El archivo local ignorado `backend/src/main/resources/application.properties` puede configurarse en desarrollo; nunca se debe copiar una configuración con credenciales locales a la plantilla pública. En este worktree se copia exclusivamente la plantilla pública para compilar y probar.

Se exige importe numérico positivo, ARS, unidad `months`, frecuencia 1 para mensual o 12 para anual, y motivo no vacío. Una configuración inválida no publica un catálogo parcial ni permite crear el alta afectada.

## Consulta del catálogo

```http
GET /suscripciones/planes
Authorization: Bearer <accessToken>
Accept: application/json
```

Sin cuerpo ni parámetros. Requiere sesión completa, como las demás operaciones de suscripciones. Devuelve `401` sin token válido o con token de preautenticación 2FA.

Respuesta `200 OK`, `application/json`: un array, primero mensual y luego anual.

```json
[
  {
    "codigo": "PREMIUM_MENSUAL",
    "motivo": "Plan Premium MisMascotas",
    "importe": 4000,
    "moneda": "ARS",
    "frecuencia": 1,
    "tipoFrecuencia": "months"
  },
  {
    "codigo": "PREMIUM_ANUAL",
    "motivo": "Plan Premium anual MisMascotas",
    "importe": 40000,
    "moneda": "ARS",
    "frecuencia": 12,
    "tipoFrecuencia": "months"
  }
]
```

`importe` es un número JSON serializado desde `BigDecimal`, no un texto formateado. El frontend aplica el formato monetario y muestra los valores recibidos del backend. El catálogo se obtiene de la configuración, sin consultas de negocio a la base ni llamadas a Mercado Pago; la autenticación conserva su mecanismo habitual. No requiere credenciales MP para leer los planes.

## Alta y confirmación

El endpoint de inicio existente admite ahora ambos códigos:

```http
POST /suscripciones/iniciar
Authorization: Bearer <accessToken>
Content-Type: application/json

{"tipoPlan":"PREMIUM_ANUAL"}
```

El servidor obtiene importe, moneda y frecuencia del catálogo; el cliente no elige esos datos financieros. La respuesta de inicio mantiene su contrato existente: `idSuscripcion`, `estado` e `initPoint`.

El primer cobro debe coincidir con el importe y la frecuencia del plan seleccionado. La vigencia anual se calcula con doce meses calendario desde la aprobación; por ejemplo, 29/02/2028 pasa a 28/02/2029, conservando la hora de negocio. Las renovaciones anuales suman doce meses desde la mayor fecha entre el vencimiento anterior y la aprobación del nuevo pago. Los reenvíos no suman otro período.

Consulta propia y cancelación admiten el código anual mediante los mismos contratos. No se agrega una migración de esquema porque `tipo_plan` ya permite ambos códigos. Esta entrega no implementa un cambio automático de plan ni prorrateos entre suscripciones existentes.

## Verificación

- Catálogo: ambos importes y códigos, orden, frecuencia y rechazo de configuración anual inválida.
- HTTP: listado autenticado, rechazo de preautenticación y alta anual con condiciones financieras del servidor.
- Contexto real: consulta de catálogo con los valores por defecto de la plantilla.
- Primer cobro anual: doce meses calendario y año bisiesto.
- PostgreSQL real y MP simulado: alta de cobertura anual, renovación, importes de 40000 ARS y reenvíos sin duplicación de pago o vigencia.

La integración de cobro anual se ejecuta dentro de `SuscripcionCicloPostgresIT`, ya incluido en CI. El catálogo no queda probado con un cobro anual real en Mercado Pago por estas pruebas automáticas.

### Resultado del 24/11/2026

Suite completa: **541 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. La salida está en `backend/target-planes-ci.log`. Se usó PostgreSQL 17.6 descartable y Mercado Pago simulado; el entorno temporal se retiró al terminar. El verificador de configuración empaquetada también pasó. Los cambios siguen locales en `feature-consulta-suscripcion`; no se modificaron Supabase, credenciales ni imágenes QA.
