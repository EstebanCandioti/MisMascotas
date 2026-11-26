# Badges: Premium y máximo de tres activas por mascota

## Objetivo y matriz acordada

| Operación | Propietario Premium | Propietario Free o vencido | Invitado activo |
|---|---|---|---|
| Consultar badges accesibles | Sí | Sí | Sí, Familia o Cuidador |
| Crear con menos de tres activas | Sí | `403 / PREMIUM_REQUERIDO` | `403 / ACCESO_DENEGADO` |
| Crear con tres o más activas | `409 / LIMITE_BADGES_ALCANZADO` | `403 / PREMIUM_REQUERIDO` | `403 / ACCESO_DENEGADO` |
| Eliminar una badge propia | Sí | Sí | `403 / ACCESO_DENEGADO` |

El vencimiento no elimina contenido ni revoca la lectura compartida. El propietario puede borrar voluntariamente las badges existentes sin Premium. Las eliminadas lógicamente dejan de contar para el cupo; las de otra mascota no lo consumen. Si hay más de tres badges históricas activas, se conservan todas y se bloquean altas hasta que queden menos de tres. Un listado puede devolver más de tres por ese histórico; no debe recortarse. Texto y emoji repetidos están permitidos; no se incorpora edición de badges.

## Atomicidad y orden de bloqueos

`BadgeService` autoriza primero al actor. Para crear y eliminar toma el bloqueo existente del propietario, lo refresca y luego bloquea/refresca la mascota. Se revalida la autorización tras los bloqueos. Se mantiene el orden propietario → recurso utilizado por pagos, mascotas y cuidado compartido.

En el alta, `PremiumService.exigirPremium`, conteo de activas e inserción se ejecutan en la misma transacción y bajo esos bloqueos. Dos altas para una mascota no pueden consumir el mismo último lugar. La eliminación utiliza el mismo orden, para coordinar la liberación del cupo con las altas; no exige Premium. Se recargan los datos bajo bloqueo para evitar decisiones basadas en entidades precargadas por Open EntityManager in View.

El bloqueo del propietario también serializa brevemente operaciones sobre sus distintas mascotas; el conteo sigue siendo por mascota. Se reutiliza esta coordinación con cambios locales de suscripción y no se hacen llamadas externas dentro del bloqueo. No se cambia `PremiumService`, la política manual/pagada, pagos, JWT ni el esquema.

## Contrato HTTP

- `POST /mascotas/{mascotaId}/badges`: conserva `BadgeRequestDTO` y devuelve `201` con `idBadge`, `mascotaId`, `texto` y `emoji`. El texto es obligatorio; el emoji es opcional. Prevalece la mascota de la ruta, como antes.
- `GET /mascotas/{mascotaId}/badges` y `GET /badges/{id}`: conservan sus DTO, permisos y respuesta `200`. Las eliminadas no se consultan.
- `DELETE /badges/{id}`: conserva `204` sin cuerpo y borrado lógico. Requiere propiedad, no Premium.
- Validaciones y recursos no encontrados conservan sus errores. Primero se evalúan permisos, después Premium y finalmente cupo.

Nuevo error uniforme de cupo:

```json
{
  "codigo": "LIMITE_BADGES_ALCANZADO",
  "mensaje": "Alcanzaste el limite de 3 badges activas por mascota.",
  "timestamp": "2026-11-25T15:00:00Z"
}
```

El estado HTTP es `409`; la fecha del ejemplo se genera al responder. Los rechazos no insertan badges ni registran auditoría de éxito.

## Verificación

Se verifican sobre HTTP y PostgreSQL real: Free; Premium manual, pagado y vencido; tercera y cuarta badge; cupo independiente por mascota; bajas lógicas; histórico excedido; texto/emoji repetidos; lectura y escrituras compartidas; recursos eliminados y rechazo sin modificar filas/auditoría.

La carrera real parte de dos activas. Dos solicitudes HTTP concurrentes esperan sobre el bloqueo de PostgreSQL antes de liberar la transacción de control; una debe responder `201`, la otra `409`, y deben quedar exactamente tres activas y una auditoría de creación. También se verifica revalidación del plan después de esperar un bloqueo.

`BadgePremiumPostgresIT` se incorpora a la suite de CI con `RUN_POSTGRES_PREMIUM_IT=true`. Las 19 pruebas nuevas y las regresiones de cuidado compartido y Premium sumaron 59 pruebas dirigidas aprobadas.

## Postman y preparación QA

Importar `postman/MisMascotas Badges Premium.postman_collection.json` y ejecutar las cinco carpetas en orden con datos exclusivos de QA. Tiene 37 solicitudes; las eliminaciones son lógicas y afectan los fixtures preparados, no solo las altas de la colección.

- Free y Vencido: mascota propia con una badge previa. Vencido debe tener pago aprobado y cobertura vencida; el indicador manual por sí solo no simula un vencimiento.
- Premium: mascota propia con dos badges activas; otra con tres; y mascota histórica con cuatro. El histórico se prepara como fixture de base, ya que la API nueva no permite crear una cuarta.
- Invitado: vínculo Familia activo hacia Free, con Premium propio para demostrar que no habilita escrituras compartidas.

Completar `baseUrl`, `tokenFree`, `tokenVencido`, `tokenPremium`, `tokenInvitado`, `mascotaFreeId`, `mascotaVencidoId`, `mascotaPremiumId`, `mascotaOtraId`, `mascotaHistoricoId`, `badgeFreeId`, `badgeVencidoId`, `badgeHistorico1Id` y `badgeHistorico2Id`. Los IDs de la tercera badge y su reemplazo se guardan automáticamente. Evitar variables de entorno homónimas para los IDs generados; no guardar credenciales reales en el archivo.

Ejemplo de cuerpo de alta, conservando el DTO actual:

```json
{"mascotaId":"UUID-de-la-mascota","texto":"Sociable","emoji":"★"}
```

La colección verifica lectura compartida, rechazo de invitados, Free/vencido sin altas, borrado voluntario, tercera/cuarta badge, liberación de cupo, texto/emoji repetidos, independencia por mascota e histórico excedido. Es secuencial; la carrera real se verifica en PostgreSQL mediante la integración de CI.

La preparación 03-08 de la colección de cuidado compartido también necesita propietario Premium para crear su badge. Se conservan todos sus casos de permisos.

El contenedor QA habitual se actualizó y verificó posteriormente el 26/11/2026: ver [qa_premium_multimedia.md](qa_premium_multimedia.md).

## Evidencia del cierre

Verificado el 25/11/2026:

- **59 pruebas dirigidas aprobadas**, incluidas 19 de badges. La carrera observó dos esperas reales en PostgreSQL y obtuvo una `201`, una `409`, tres activas y una sola auditoría de creación.
- Se verificaron además la revalidación de Premium tras esperar un bloqueo y dos borrados simultáneos de la misma badge (`204` y `404`, una sola auditoría).
- Suite completa de CI: **791 pruebas aprobadas**, sin fallos, errores ni omisiones; `BUILD SUCCESS` y JAR generado.
- Newman sobre `mismascotas-backend:badges-validacion`: **37 solicitudes y 62 aserciones de badges aprobadas**. Incluyendo regresiones de álbumes/fotos: **112 solicitudes y 200 aserciones**, todas aprobadas.
- La base confirmó cupo por mascota, conservación del histórico sin recortes y borrado lógico permitido en Free/vencido. Se conservaron migraciones B9 a V11, persistencia al recrear y el ciclo de cuidado compartido con Mailpit. No se hicieron pagos ni envíos de correo externos.
- `git diff --check` y verificación de configuración empaquetada aprobados.

Salidas locales: `backend/target-badges-dirigidas.log`, `backend/target-badges-ci.log` y, fuera del repositorio, `artifacts/testing/badges-premium/newman-badges.json`, sus regresiones y `artifacts/testing/badges-premium-docker.log`. Los contenedores, cuentas y volumen de verificación fueron descartables; no se actualizó el entorno QA habitual.
