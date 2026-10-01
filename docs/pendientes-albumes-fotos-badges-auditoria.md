# Pendientes de autorizacion en albumes, fotos y badges

## Autorizacion actual

Albumes, fotos y badges validan acceso solo por propietario de la mascota asociada.

- Badges: el usuario autenticado debe ser propietario de la mascota del badge.
- Albumes: el usuario autenticado debe ser propietario de todas las mascotas activas asociadas al album. Si el album no tiene mascotas activas asociadas, se trata como no encontrado.
- Fotos: el acceso se deriva del album que contiene la foto.

Esta decision es temporal hasta implementar cuidado compartido. Cuando existan perfiles Familia y Cuidador, la validacion debera ampliarse agregando condiciones al metodo centralizado de cada service.

## Restricciones por plan pendientes

La documentacion funcional indica que estas funciones son exclusivas del plan premium, pero no se implementan en este cierre:

- Crear, editar y eliminar albumes.
- Cargar y eliminar fotos.
- Crear y eliminar badges.
- Limite de tres badges activas por mascota.

Estado actual relevado: no hay validaciones de plan premium ni limite de badges en `AlbumService`, `FotoService` ni `BadgeService`. La visualizacion queda disponible para usuarios autenticados con acceso por propietario.

## Consulta de auditoria deshabilitada

Los endpoints de consulta de auditoria estan deshabilitados temporalmente porque no existe un caso de uso de pantalla de auditoria ni un rol global de administrador. El sistema solo tiene perfiles contextuales por mascota, todavia dependientes del modulo de cuidado compartido.

El codigo del controller, service, mapper y DTO se conserva. Para rehabilitar la consulta hay que definir:

- Quien puede consultar logs.
- Con que alcance puede hacerlo.
- Si la consulta sera por mascota, por propietario, por actor o por una regla distinta.

La escritura del log por AOP sigue activa para operaciones anotadas con `@Auditable`.