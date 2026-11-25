# Decisiones - Acceso a mascotas

- La verificacion de acceso a mascotas queda centralizada en `AccesoMascotaService`.
- La lectura admite al propietario y a invitados con vínculo activo y ambas cuentas activas. `verificarLectura` aplica esta regla; el listado incluye mascotas propias y compartidas, incluidas las futuras.
- `verificarAcceso` conserva el permiso exclusivo del propietario para modificar mascotas y multimedia. `verificarGestionSalud` admite además Familia para eventos clínicos y recordatorios. Cada consumidor elige explícitamente la operación.
- Una mascota inexistente o eliminada se trata como recurso no encontrado (`404`). Una mascota activa de otro propietario devuelve `403 ACCESO_DENEGADO`.
- Los services de mascotas, recordatorios, eventos clinicos, albumes, fotos y badges deben delegar en este componente en lugar de repetir la comparacion de propietario.
- La resolucion propia de cada modulo se mantiene en su service. Por ejemplo, albumes y fotos siguen resolviendo sus relaciones con `AlbumMascota`, pero cada mascota asociada se valida con `AccesoMascotaService`.
- El listado de mascotas visibles para un usuario debe salir de `AccesoMascotaService.obtenerMascotasAccesibles(...)`.
- Familia y Cuidador se resuelven por vínculo con el propietario, nunca como roles globales. La revocación y el cambio de perfil se consultan en cada petición. El servicio de recordatorios limita la confirmación al día correspondiente para todos los perfiles, sin limitar la lectura por fecha. Matriz completa en `cuidado_compartido.md`.
