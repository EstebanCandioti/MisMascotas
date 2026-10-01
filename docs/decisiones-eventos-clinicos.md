# Decisiones y pendientes - Eventos clinicos

## Decisiones implementadas

- Autorizacion: todas las operaciones de eventos clinicos quedan limitadas al propietario de la mascota. Cuando exista cuidado compartido, las condiciones de Familia y Cuidador deben agregarse en los metodos privados de autorizacion de `EventoClinicoService`.
- Borrado: la eliminacion de eventos clinicos es logica mediante `fechaEliminacion`. Las consultas, ediciones y eliminaciones posteriores excluyen esos registros y los tratan como no encontrados.
- Fecha del evento: `fecha` usa `Instant`, alineado con la columna `timestamptz`. En la API viaja como ISO-8601 con zona/offset, por ejemplo `2026-10-01T15:30:00Z`.
- Tipos soportados: se aceptan como texto y se normalizan a `VACUNA`, `MEDICACION`, `CONSULTA_VETERINARIA` y `PESO`. La normalizacion tolera acentos, espacios y guiones.
- Validaciones por tipo: quedan en el service porque dependen de la combinacion de campos del request. Los errores se devuelven con `ERROR_VALIDACION` y mensajes por campo.
- Peso razonable: para eventos de tipo `PESO`, el valor debe estar entre `0.01` y `300.00` kg. El rango cubre mascotas pequenas y especies grandes sin aceptar valores nulos, cero o negativos.
- Peso actual de mascota: se recalcula a partir del evento activo de tipo `PESO` mas reciente por fecha del evento. Si se carga un peso antiguo, no pisa el perfil si existe otro peso posterior. Si se edita o elimina el peso mas reciente, se vuelve a calcular con el siguiente evento activo; si no queda ninguno, el perfil queda sin peso actual.
- Auditoria: registrar, editar y eliminar eventos clinicos quedan anotados con `@Auditable` usando la entidad `evento_clinico`.
- Nombre del valor numerico: se unifico como `valorPeso` en DTOs y entidad para que el contrato del cliente coincida con el dato real del modulo.

## Pendientes

- Adjuntos: fuera del alcance por ahora. No forman parte del contrato de request/response de eventos clinicos hasta resolver el almacenamiento de archivos, persistencia en `adjunto` y exposicion segura de URLs. Segun RN-026, los formatos aceptados serian JPG, PNG, PDF y HEIC, con un tamano maximo de 10 MB.
- Permisos ampliados: falta integrar Familia y Cuidador cuando exista el modulo de cuidado compartido.
- Grafico de evolucion: no se implementa aca; se alimentara consultando los eventos clinicos de tipo `PESO`.