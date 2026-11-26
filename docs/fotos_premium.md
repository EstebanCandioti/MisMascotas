# Fotos por URL y foto de perfil: política Premium

## Objetivo

Exigir Premium efectivo del propietario para cargar fotos en álbumes y asignar o reemplazar la foto de perfil de una mascota. Una operación general permitida en Free no debe rechazarse por conservar una foto ya almacenada.

## Matriz acordada de álbumes y fotos

| Operación | Propietario Premium | Propietario Free o vencido | Invitado activo |
|---|---|---|---|
| Consultar álbumes/fotos existentes | Sí | Sí | Sí, con acceso a todas las mascotas asociadas |
| Crear álbum / cargar foto por URL | Sí | `403 / PREMIUM_REQUERIDO` | `403 / ACCESO_DENEGADO` |
| Editar álbum | Sí | `403 / PREMIUM_REQUERIDO` | `403 / ACCESO_DENEGADO` |
| Eliminar álbum o foto individual | Sí | Sí | `403 / ACCESO_DENEGADO` |

Esta decisión sustituye la restricción de eliminación del bloque anterior de álbumes. Vencer Premium no borra contenido ni revoca lectura. La eliminación solicitada por el propietario conserva el borrado lógico y la cascada existente del álbum.

No existe una ruta para editar una foto individual. No se agrega una ni se convierte la carga por URL en carga binaria o S3.

## Foto de perfil

La evaluación compara `fotoPerfil` recibida con la almacenada, después de autorizar al propietario y antes de modificar cualquier campo. Asignar una URL o reemplazarla exige `PremiumService.exigirPremium` del propietario real. Conservar la misma URL permite editar los demás datos en Free; la lectura compartida no depende del plan del invitado.

Semántica acordada:

| `fotoPerfil` en la solicitud | Al crear mascota | Al editar mascota |
|---|---|---|
| Ausente | Sin foto; no exige Premium por imagen | Conserva la foto almacenada |
| `null` explícito | Sin foto; no exige Premium por imagen | Retira la foto, también en Free o vencido |
| Misma URL almacenada | No aplica | Conserva la imagen; permite edición general en Free |
| Otra URL de texto | Exige Premium | Exige Premium antes de modificar cualquier campo |

`MascotaRequestDTO` conserva los campos JSON públicos y las validaciones existentes. Un creador JSON distingue ausencia de `null` con `JsonNode`; el indicador interno `fotoPerfilInformada` se ignora en JSON y no se acepta del cliente. El constructor Java previo se conserva: pasar `null` mediante ese constructor significa retiro explícito. No se modifican rutas, campos de respuesta ni tokens.

## Verificación prevista

- HTTP y PostgreSQL real para Free, Premium manual/pagado, vencimiento exacto y posterior e invitados Familia/Cuidador.
- Alta y reemplazo con Premium; conservación de URL con edición general en Free; rechazo de cambios sin modificar nombre, notas, peso, foto ni auditoría.
- Fotos de álbum conservadas y legibles después de vencer; eliminación individual y cascada permitidas al propietario sin Premium.
- Rechazo de lectura o escritura sobre cualquier asociación ajena; el Premium propio del invitado no concede gestión.
- DTO y rutas conservados, sin cambios en pagos, JWT, cupo de mascotas ni almacenamiento.
- Colecciones Postman y preparación QA actualizadas a la matriz final.

## Contrato HTTP y preparación del frontend

Se mantienen `POST /albumes/{albumId}/fotos`, `GET /albumes/{albumId}/fotos`, `GET /fotos/{id}` y `DELETE /fotos/{id}`. El cuerpo del alta conserva `urlArchivo` y `formato`; devuelve `201` con `FotoResponseDTO`. El borrado devuelve `204` sin cuerpo. La consulta devuelve los mismos DTO/listados existentes.

La foto de perfil se opera mediante `POST /mascotas` y `PUT /mascotas/{id}`, con el DTO de mascota existente. En edición:

```json
{"nombre":"Luna","especie":"Perro","fechaAproximada":false}
```

El ejemplo conserva la foto. Agregar `"fotoPerfil": null` la retira; agregar `"fotoPerfil": "https://sitio.test/nueva.jpg"` asigna/reemplaza y exige Premium. Las demás reglas de ficha y cupo se mantienen. El frontend debe omitir el campo si no quiere modificarlo: enviar `null` por defecto tiene ahora significado explícito de retiro. Esta entrega documenta el contrato; no cambia la interfaz web.

Sin Premium, una asignación/reemplazo devuelve `403` con `codigo=PREMIUM_REQUERIDO`, `mensaje` y `timestamp`, sin aplicar otros cambios del mismo cuerpo. Los permisos se validan antes del plan; un invitado obtiene `403 / ACCESO_DENEGADO` incluso para retirar la foto.

## Postman y QA

Importar `postman/MisMascotas Fotos Premium.postman_collection.json` y ejecutar sus cuatro carpetas en orden. Preparar cuentas y mascotas exclusivas:

- Free: cuenta sin beneficios, con una mascota y foto previa.
- Vencido: cuenta con pago aprobado y cobertura vencida, con una mascota y foto previa. Un indicador manual sin pagos aprobados no representa un vencimiento.
- Premium: cuenta con beneficio efectivo y mascota propia con foto previa.
- Invitado: vínculo Familia activo hacia Free, con Premium propio para verificar que ese plan no concede gestión compartida.

Completar `baseUrl`, `tokenFree`, `tokenVencido`, `tokenPremium`, `tokenInvitado`, `mascotaFreeId`, `mascotaVencidoId`, `mascotaPremiumId` y `fotoPerfilPrevia`. Esta última tiene como ejemplo `https://example.test/perfil-previa.jpg`; debe coincidir exactamente con las tres fotos almacenadas. Usar propietarios Free/Vencido con espacio para dos mascotas adicionales dentro del límite vigente. No guardar credenciales reales en la colección ni usar variables de entorno homónimas para IDs generados.

La colección tiene 34 solicitudes: lectura compartida; rechazos por permisos y Premium; conservación por ausencia/URL igual; retiro con `null`; alta general Free; alta/reemplazo Premium e intento de manipular el indicador interno. Crea dos mascotas por cuenta Free/Vencido y una Premium; elimina lógicamente solo la mascota Premium recién creada. La limpieza de las otras cuentas/mascotas de prueba corresponde al entorno que prepara los fixtures.

La colección de álbumes se actualiza a 41 solicitudes. Su carpeta final elimina lógicamente las fotos y álbumes previos de Free/Vencido, después de verificar que seguían consultables. Usar recursos exclusivos de QA. No exige Premium para eliminar.

## Evidencia de validación

QA habitual actualizado y verificado el 26/11/2026: [qa_premium_multimedia.md](qa_premium_multimedia.md). Los resultados siguientes corresponden al cierre previo de implementación en recursos descartables.

Pruebas nuevas: `MascotaRequestDTOTests` verifica presencia y exclusión del indicador interno; `FotoPerfilPremiumPostgresIT` verifica HTTP, PostgreSQL real, permisos y atomicidad. `AlbumPremiumPostgresIT` incorpora eliminación Free/vencido y conserva los controles de alta/edición. Se ejecutan junto con la suite completa de CI; `FotoPerfilPremiumPostgresIT` usa `RUN_POSTGRES_PREMIUM_IT=true`.

Verificación completada el 25/11/2026:

- Dirigidas: **83 pruebas aprobadas**, incluidas 20 de foto de perfil, 23 de álbumes, 2 del contrato JSON y las regresiones de servicio/controlador/cuidado compartido.
- Suite completa de CI: **772 pruebas aprobadas**, sin fallos, errores ni omisiones; `BUILD SUCCESS` y JAR generado.
- Newman contra la imagen Docker `mismascotas-backend:fotos-validacion`: **34 solicitudes / 69 aserciones** de perfil y **41 solicitudes / 69 aserciones** de álbumes, todas aprobadas (75 solicitudes y 138 aserciones en total).
- PostgreSQL confirmó la conservación del contenido previo, el retiro explícito de perfil, las eliminaciones Free/vencido y el borrado lógico en cascada. Las pruebas de integración verificaron rechazo sin escrituras ni auditoría parciales.
- La imagen pasó también arranque, migraciones B9 a V11, persistencia al recrear, nueve escenarios Premium y el ciclo de cuidado compartido con Mailpit. No se utilizaron pagos ni correo externos.
- `git diff --check` y el verificador de configuración empaquetada aprobados.

Salidas locales: `backend/target-fotos-dirigidas.log`, `backend/target-fotos-ci.log` y, fuera del repositorio, `artifacts/testing/fotos-premium/newman-fotos.json`, `newman-albumes.json` y `artifacts/testing/fotos-premium-docker.log`. Los recursos usados fueron descartables. La actualización del contenedor QA habitual con la imagen nueva queda como paso de despliegue independiente.
