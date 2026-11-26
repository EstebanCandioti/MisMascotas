# Matriz de cobertura controller ↔ Postman

## Recordatorios recurrentes al cierre de REC-09

La colección `MisMascotas Recordatorios Recurrentes REC-01 a REC-04.postman_collection.json` incorpora tres carpetas de pruebas. Se conserva el nombre del archivo para mantener los enlaces existentes.

| Carpeta | Solicitudes | Aserciones | Cobertura |
|---|---:|---:|---|
| 01 — Pruebas REC-01 a REC-04 | 18 | 36 | Alta única, finita e indefinida, modalidad y límites |
| 02 — Consulta, confirmación y ciclo de series | 26 | 51 | Hoy, confirmación, edición, pausa, reactivación y borrado |
| 03 — Permisos Familia y Cuidador | 16 | 31 | Misma sesión de invitado, gestión Familia y rechazos Cuidador |

Las tres carpetas pasaron con Newman contra el backend REC-08 empaquetado en Docker. Las sesiones se prepararon con login y códigos de Mailpit. Las fechas futuras y el historial se verifican en PostgreSQL. El job se comprueba mediante reinicios reales en `ci/verificar_imagen_qa.py`.

El [contrato actual](../docs/recordatorios_api.md) incluye `GET /recordatorios/hoy` y el selector `idOcurrencia`. El relevamiento siguiente corresponde al estado anterior a este módulo.

## Relevamiento de octubre de 2026

Colección verificada: `MisMascotas Auth Mascotas Auditoria.postman_collection.json`
Contrato relevado desde la rama `backend` el 11 de octubre de 2026.

| Controller | Endpoint | Presente en Postman | Observaciones |
|---|---|---:|---|
| AuthController | POST /auth/register | Sí | Devuelve token de preautenticación. |
| AuthController | POST /auth/login | Sí | Devuelve token de preautenticación. |
| AuthController | POST /auth/verificar-codigo | Sí | Bearer preAuthToken; devuelve accessToken y refreshToken. |
| AuthController | POST /auth/refresh | Sí | Refresh rotativo. |
| AuthController | POST /auth/logout | Sí | Público; revoca el refresh enviado. |
| UsuarioController | GET /usuario/me | Sí | Único endpoint de usuario actual. |
| MascotaController | POST /mascotas | Sí | Protegido. |
| MascotaController | GET /mascotas | Sí | Protegido. |
| MascotaController | GET /mascotas/{id} | Sí | Protegido. |
| MascotaController | PUT /mascotas/{id} | Sí | Protegido. |
| MascotaController | DELETE /mascotas/{id} | Sí | Borrado lógico; ubicado en Limpieza. |
| RecordatorioController | POST /mascotas/{mascotaId}/recordatorios | Sí | Request diasSemana; response diaSemana. |
| RecordatorioController | GET /mascotas/{mascotaId}/recordatorios | Sí | Protegido. |
| RecordatorioController | GET /recordatorios/{id} | Sí | Protegido. |
| RecordatorioController | PUT /recordatorios/{id} | Sí | Protegido. |
| RecordatorioController | PATCH /recordatorios/{id}/estado | Sí | Query nuevoEstado obligatorio. |
| RecordatorioController | PATCH /recordatorios/{id}/confirmacion | Sí | Protegido. |
| RecordatorioController | DELETE /recordatorios/{id}/confirmacion | Sí | Desmarca confirmación; devuelve 200. |
| RecordatorioController | DELETE /recordatorios/{id} | Sí | Borrado lógico. |
| EventoClinicoController | POST /mascotas/{mascotaId}/eventos-clinicos | Sí | Reglas por tipo documentadas. |
| EventoClinicoController | GET /mascotas/{mascotaId}/eventos-clinicos | Sí | Query tipo opcional. |
| EventoClinicoController | GET /eventos-clinicos/{id} | Sí | Protegido. |
| EventoClinicoController | PUT /eventos-clinicos/{id} | Sí | Protegido. |
| EventoClinicoController | DELETE /eventos-clinicos/{id} | Sí | Borrado lógico. |
| AlbumController | POST /mascotas/{mascotaId}/albumes | Sí | mascotaId también obligatorio en body. |
| AlbumController | GET /mascotas/{mascotaId}/albumes | Sí | Protegido. |
| AlbumController | GET /albumes/{id} | Sí | Protegido. |
| AlbumController | PUT /albumes/{id} | Sí | mascotaId obligatorio pero asociación ignorada. |
| AlbumController | DELETE /albumes/{id} | Sí | Borrado lógico en cascada de relaciones/fotos; ubicado en Limpieza. |
| FotoController | POST /albumes/{albumId}/fotos | Sí | Registra URL/metadatos; no multipart. |
| FotoController | GET /albumes/{albumId}/fotos | Sí | Protegido. |
| FotoController | GET /fotos/{id} | Sí | Protegido. |
| FotoController | DELETE /fotos/{id} | Sí | Borrado lógico. |
| BadgeController | POST /mascotas/{mascotaId}/badges | Sí | mascotaId también obligatorio en body. |
| BadgeController | GET /mascotas/{mascotaId}/badges | Sí | Protegido. |
| BadgeController | GET /badges/{id} | Sí | Protegido. |
| BadgeController | DELETE /badges/{id} | Sí | No existe endpoint de edición. |
| LogAuditoriaController | GET /auditoria/{entidad}/{idEntidad} | Sí | Deshabilitado: espera 404. |
| LogAuditoriaController | GET /auditoria/actor/{actorId} | Sí | Deshabilitado: espera 404. |
| LogAuditoriaController | GET /auditoria | Sí | Deshabilitado: espera 404; queries documentadas. |

## Resultado

- Controllers relevados: 9.
- Endpoints expuestos: 40.
- Endpoints representados: 40.
- Omisiones relevantes: ninguna.
- Endpoints no inventados: edición de badges, edición de usuario, cuidado compartido, calendario, pagos, PDF y adjuntos.
