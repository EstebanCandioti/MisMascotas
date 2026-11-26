# Pendientes de autorizacion en albumes, fotos y badges

## Autorizacion actual

Álbumes, fotos y badges permiten lectura al propietario y a sus invitados activos Familia/Cuidador. Las escrituras siguen siendo exclusivas del propietario.

- Badges: se valida lectura o escritura sobre la mascota del badge según la operación.
- Álbumes: el actor debe tener el permiso de lectura/escritura sobre todas las mascotas activas asociadas, también en listados. Si el álbum no tiene mascotas activas asociadas, se trata como no encontrado. Con varias mascotas, `mascotaId` representa la primera por UUID para mantener el DTO actual.
- Fotos: el acceso se deriva del album que contiene la foto.

La autorización se delega en `AccesoMascotaService`; no se amplía el permiso de escritura por el solo hecho de compartir la lectura.

## Restricciones por plan implementadas

Implementado (contratos y pruebas en `albumes_premium.md`, `fotos_premium.md` y `badges_premium.md`): crear/editar álbumes, cargar fotos por URL, asignar/reemplazar `fotoPerfil` y crear badges requieren `PremiumService.exigirPremium` del propietario real. Se conserva la lectura propia y compartida al vencer el plan. El propietario puede eliminar álbumes, fotos y badges y retirar `fotoPerfil` sin Premium.

`BadgeService` aplica un máximo de tres badges activas por mascota bajo bloqueo transaccional y rechaza altas con `409 / LIMITE_BADGES_ALCANZADO`. Los históricos excedidos se conservan; las bajas lógicas liberan cupo. No se agregó edición de badges. La visualización está disponible para usuarios autenticados con acceso propio o compartido.

## Consulta de auditoria deshabilitada

Los endpoints de consulta de auditoría están deshabilitados temporalmente porque no existe un caso de uso de pantalla de auditoría ni un rol global de administrador. Los perfiles compartidos son contextuales al vínculo con el propietario; no habilitan la consulta general de logs.

El codigo del controller, service, mapper y DTO se conserva. Para rehabilitar la consulta hay que definir:

- Quien puede consultar logs.
- Con que alcance puede hacerlo.
- Si la consulta sera por mascota, por propietario, por actor o por una regla distinta.

La escritura del log por AOP sigue activa para operaciones anotadas con `@Auditable`.
