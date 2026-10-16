# Decisiones - Acceso a mascotas

- La verificacion de acceso a mascotas queda centralizada en `AccesoMascotaService`.
- La regla vigente es propietario unico: el usuario autenticado debe coincidir con `mascota.propietario.idUsuario`.
- Una mascota inexistente o eliminada se trata como recurso no encontrado (`404`). Una mascota activa de otro propietario devuelve `403 ACCESO_DENEGADO`.
- Los services de mascotas, recordatorios, eventos clinicos, albumes, fotos y badges deben delegar en este componente en lugar de repetir la comparacion de propietario.
- La resolucion propia de cada modulo se mantiene en su service. Por ejemplo, albumes y fotos siguen resolviendo sus relaciones con `AlbumMascota`, pero cada mascota asociada se valida con `AccesoMascotaService`.
- El listado de mascotas visibles para un usuario debe salir de `AccesoMascotaService.obtenerMascotasAccesibles(...)`.
- Cuando se implementen los perfiles Familia y Cuidador, la ampliacion de la regla de acceso se realizara en este componente y no en cada service consumidor.
