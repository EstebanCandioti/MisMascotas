# Álbumes: gestión Premium y consulta conservada

## Objetivo y alcance

Crear y editar álbumes exige Premium efectivo del propietario de las mascotas asociadas. Free conserva la consulta de contenido propio y compartido y puede eliminar contenido propio, pero no crear álbumes, editarlos ni cargar fotos. Se incluye el alta de fotos porque tiene un endpoint independiente y, sin ese control, un propietario Free podría continuar cargando fotos en un álbum anterior.

El vencimiento conserva los álbumes, las fotos, las asociaciones y la lectura autorizada. La falta de Premium no elimina contenido automáticamente.

## Decisiones de implementación

- `AlbumService` usa `PremiumService.exigirPremium` al crear y después de autorizar todas las mascotas al editar. Eliminar exige los mismos permisos de propiedad, pero no Premium. Se evalúa al propietario real de la mascota, no el campo histórico `creadoPor` del álbum ni un ID recibido en el cuerpo.
- `FotoService.crear` aplica el mismo criterio después de autorizar todas las mascotas del álbum. Compartir lectura no habilita escrituras, aunque el invitado tenga Premium propio.
- Los controles ocurren antes de modificar entidades, guardar asociaciones o iniciar el borrado lógico en cascada. Se mantienen las transacciones y la auditoría existentes; un rechazo no registra una operación exitosa.
- Las consultas no exigen Premium. Un álbum sin asociaciones activas conserva su `404`; el acceso a una mascota ajena conserva el `403 / ACCESO_DENEGADO` antes de evaluar el plan.
- Se conserva la política central de Premium manual, pagos aprobados y cobertura vigente, incluida la cobertura cancelada o pausada. El vencimiento se evalúa con el `Clock` de `PremiumService`, sin consultar Mercado Pago ni confiar directamente en el indicador persistido.

No se modifica `PremiumService`, pagos, JWT, interfaz web, almacenamiento ni esquema. No se introduce un cupo de álbumes. La matriz posterior de fotos permite eliminar fotos individuales y álbumes propios incluso en Free o vencido; se documenta junto con la foto de perfil en [fotos_premium.md](fotos_premium.md). Badges y su cupo se implementan en [badges_premium.md](badges_premium.md).

## Contrato HTTP

| Operación | Ruta | Resultado exitoso | Requisito adicional |
|---|---|---|---|
| Crear álbum | `POST /mascotas/{mascotaId}/albumes` | `201`, `AlbumResponseDTO` | Propietario Premium |
| Editar álbum | `PUT /albumes/{id}` | `200`, `AlbumResponseDTO` | Propietario Premium de todas las mascotas |
| Eliminar álbum | `DELETE /albumes/{id}` | `204`, sin cuerpo | Propietario de todas las mascotas; no exige Premium |
| Cargar foto por URL | `POST /albumes/{albumId}/fotos` | `201`, `FotoResponseDTO` | Propietario Premium de todas las mascotas |
| Consultar álbumes/fotos | GET existentes | `200`, DTO o listado vigente | Acceso propio o vínculo activo; sin exigir Premium |

Los cuerpos y DTO existentes se conservan. `AlbumRequestDTO` recibe `nombre`, `descripcion` opcional y `mascotaId`. En el alta prevalece la mascota de la ruta; la edición no reasigna mascotas mediante el DTO. El alta de foto recibe `urlArchivo` y `formato`.

Sin Premium, crear/editar álbumes y cargar fotos devuelven `403`, con el formato global existente:

```json
{
  "codigo": "PREMIUM_REQUERIDO",
  "mensaje": "…",
  "timestamp": "…"
}
```

Los valores del mensaje y la fecha los genera el manejador global. No se añade información del plan al DTO de álbum.

## Pruebas verificables

`AlbumPremiumPostgresIT` usa HTTP mediante MockMvc y PostgreSQL 17.6 real, con transacciones confirmadas y limpieza de sus propias cuentas. Se ejecuta con `RUN_POSTGRES_PREMIUM_IT=true` y se incorpora al workflow de backend.

1. Free, pago vencido, vencimiento exacto, pago rechazado o sin fecha: crear/editar álbum y cargar foto devuelven `403 / PREMIUM_REQUERIDO`. El propietario puede eliminar álbumes y fotos, conservando borrado lógico y cascada.
2. Premium manual, mensual, anual, cancelado y pausado con vigencia: gestión exitosa con los DTO actuales y borrado lógico de álbum, asociaciones y fotos.
3. Propietario e invitados conservan lectura después del vencimiento. Cada consulta reevalúa el instante sin necesitar renovar la sesión.
4. Familia, Cuidador y ajenos no consiguen escrituras por tener Premium. Una mascota ajena en un álbum con varias asociaciones rechaza toda la operación.
5. Los rechazos conservan las filas completas de álbum, asociaciones, fotos, propietario y pagos, y no agregan auditoría de éxito ni eventos pendientes.
6. Se conserva la validación de datos, los `404` y la imposibilidad de reasignar mascotas mediante edición.

`CuidadoPermisosPostgresIT` prepara ahora al propietario como Premium manual para crear multimedia; las cuentas invitadas y sus permisos mantienen la matriz anterior.

## Postman y preparación QA

Importar `postman/MisMascotas Albumes Premium.postman_collection.json`. Ejecutar sus carpetas en orden usando exclusivamente datos de prueba:

- Free y Vencido: cada uno necesita una mascota, un álbum previo y una foto previa. Vencido debe tener un pago aprobado con cobertura ya vencida; asignarle solamente `esPremium=true` lo convertiría en Premium manual si no tiene pagos aprobados.
- Premium: una mascota propia y cobertura efectiva o la excepción manual vigente.
- Invitado: cuenta con Premium propio y vínculo Familia activo hacia el propietario Free, para demostrar que su plan no habilita escrituras sobre recursos compartidos.
- Completar `tokenFree`, `tokenVencido`, `tokenPremium`, `tokenInvitado`, `mascotaFreeId`, `mascotaVencidoId`, `mascotaPremiumId`, `albumFreeId`, `albumVencidoId`, `fotoFreeId` y `fotoVencidoId`. Los IDs de las altas nuevas se guardan automáticamente. Evitar variables de entorno homónimas para los IDs generados que oculten las de colección.

La colección incluye aserciones de estado, código de error, conservación de lectura y contratos. La carpeta final elimina lógicamente las fotos y álbumes previos de las cuentas Free/Vencido usadas como fixtures; deben ser exclusivos de esta prueba. Ningún token o contraseña queda guardado en el archivo versionado.

La colección anterior de cuidado compartido requiere ahora un propietario Premium antes de crear el álbum y la foto en 03-06/03-07. Una cuenta recién registrada es Free; esa preparación debe hacerse con datos exclusivos de QA, sin agregar endpoints de cambio de plan.

## Evidencia de validación

QA habitual actualizado y verificado el 26/11/2026: [qa_premium_multimedia.md](qa_premium_multimedia.md). Los resultados siguientes corresponden a verificaciones previas en recursos descartables.

Matriz final con eliminación permitida sin Premium: 23 pruebas de álbumes aprobadas, suite completa de 772 pruebas y colección Newman de 41 solicitudes / 69 aserciones aprobadas. Evidencia del cierre actual en [fotos_premium.md](fotos_premium.md).

Verificación del bloque anterior, antes de permitir eliminar sin Premium (25/11/2026):

- Pruebas dirigidas: **58 aprobadas**, incluidas **18 nuevas** de álbumes y la matriz de cuidado compartido.
- Suite completa de CI: **745 aprobadas**, sin fallos, errores ni omisiones; `BUILD SUCCESS` y JAR generado.
- Imagen `mismascotas-backend:albumes-validacion`: arranque, migraciones B9 a V11, persistencia al recrear, nueve casos de Premium y ciclo de cuidado compartido con Mailpit aprobados.
- Colección real ejecutada con Newman: **31 solicitudes y 57 aserciones aprobadas**. Se verificaron además en PostgreSQL la conservación de filas y auditoría ante rechazos, y el borrado lógico en cascada autorizado.
- `git diff --check` y el verificador de configuración empaquetada aprobados.

Las salidas locales se registran en `backend/target-albumes-dirigidas.log` y `backend/target-albumes-ci.log`. Los resultados de Newman se guardan fuera del repositorio en `artifacts/testing/albumes-premium/newman.json`; la verificación Docker, en `artifacts/testing/albumes-premium-docker.log`. Se utilizaron recursos descartables; el contenedor y los datos de QA habituales conservan su versión anterior hasta su actualización explícita.
