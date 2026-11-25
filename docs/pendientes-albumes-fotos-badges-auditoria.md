# Pendientes de autorizacion en albumes, fotos y badges

## Autorizacion actual

Álbumes, fotos y badges permiten lectura al propietario y a sus invitados activos Familia/Cuidador. Las escrituras siguen siendo exclusivas del propietario.

- Badges: se valida lectura o escritura sobre la mascota del badge según la operación.
- Álbumes: el actor debe tener el permiso de lectura/escritura sobre todas las mascotas activas asociadas, también en listados. Si el álbum no tiene mascotas activas asociadas, se trata como no encontrado. Con varias mascotas, `mascotaId` representa la primera por UUID para mantener el DTO actual.
- Fotos: el acceso se deriva del album que contiene la foto.

La autorización se delega en `AccesoMascotaService`; no se amplía el permiso de escritura por el solo hecho de compartir la lectura.

## Restricciones por plan pendientes

La documentacion funcional indica que estas funciones son exclusivas del plan premium, pero no se implementan en este cierre:

- Crear, editar y eliminar albumes.
- Cargar y eliminar fotos.
- Crear y eliminar badges.
- Limite de tres badges activas por mascota.

Estado actual: no hay validaciones de plan premium ni límite de badges en `AlbumService`, `FotoService` ni `BadgeService`. La visualización está disponible para usuarios autenticados con acceso propio o compartido.

## Consulta de auditoria deshabilitada

Los endpoints de consulta de auditoría están deshabilitados temporalmente porque no existe un caso de uso de pantalla de auditoría ni un rol global de administrador. Los perfiles compartidos son contextuales al vínculo con el propietario; no habilitan la consulta general de logs.

El codigo del controller, service, mapper y DTO se conserva. Para rehabilitar la consulta hay que definir:

- Quien puede consultar logs.
- Con que alcance puede hacerlo.
- Si la consulta sera por mascota, por propietario, por actor o por una regla distinta.

La escritura del log por AOP sigue activa para operaciones anotadas con `@Auditable`.
