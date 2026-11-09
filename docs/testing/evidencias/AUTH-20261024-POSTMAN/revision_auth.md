# Revisión Auth Postman — 24 de octubre de 2026

## Entorno y alcance

Repositorio: `D:\AAAIES\MisMascotas-front`. Docker Compose:
`mismascotas-web-testing`; backend, PostgreSQL y Mailpit saludables.
No se reconstruyeron imágenes ni se alteró el código de la aplicación.
La revisión del código fuente no demuestra por sí sola qué revisión contienen las imágenes.

Se ejecutaron con Newman 6.2.3 las 35 solicitudes de la colección generada desde
`docs/testing/casos_prueba.csv`, sin credenciales seed. La corrida final está en
`resumen_auth.json`: **28 APROBADO_API, 2 FALLÓ, 3 BLOQUEADO y 2 PARCIAL**.
No hubo errores de red o de scripts en esa corrida. Una corrida previa observó
los mismos dos fallos. No se actualizaron resultados del CSV ni de la planilla.

## Fallos observados

### AUT-REG-017 — Email duplicado normalizado

Después de registrar una cuenta temporal, se repitió su email con mayúsculas y
espacios externos. El esperado fue HTTP 409 por duplicado; se recibió HTTP 400.
El código fuente tiene validación `@Email` sobre el texto antes de que AuthService
ejecute `trim().toLowerCase()`. Esa secuencia es una explicación probable del fallo;
no se modifica el esperado para aceptar la respuesta actual.

### AUT-SES-005 — Dos renovaciones simultáneas

Dos solicitudes concurrentes con el mismo refresh devolvieron HTTP 200 y emitieron
sesión. El esperado permite como máximo una. La prueba queda FALLÓ. El servicio
inspeccionado lee el token y lo rota sin que se observe un bloqueo de concurrencia
en esa operación. Debe investigarse una protección transaccional efectiva antes
de corregirlo; esta entrega no cambia el backend.

## Bloqueos y comprobaciones pendientes

- **AUT-2FA-004:** la corrida rápida no espera expiraciones. Además, la configuración
  habitual da 10 minutos tanto al código como al pre-auth: no permite comprobar el
  rechazo por código expirado conservando válido el token. La guía explica cómo
  preparar vigencias QA distintas y ejecutar la comprobación real.
- **AUT-2FA-005 y AUT-2FA-006:** no existe endpoint ni contrato de reenvío de registro
  en el backend inspeccionado. No se los aprueba sustituyendo esa operación por login.
- **AUT-REG-009:** verificado el desafío en API; falta observar navegación en UI.
- **AUT-SES-006:** verificado access anterior tras refresh; falta la parte posterior
  al vencimiento natural. Puede ejecutarse con `--esperar-vencimientos`.

## Evidencia y datos

El informe conserva únicamente IDs y nombres de comprobaciones. No contiene
contraseñas, códigos 2FA, tokens ni respuestas completas. Los efectos internos de
base de datos no se certifican por las respuestas HTTP: consultar los límites
de cada caso en `docs/testing/auth_postman.md` antes de aprobar todas sus capas.

Las cuentas sintéticas `qa.auth.*@mismascotas.test`, correos y auditoría permanecen
en QA para inspección. Se intentó cerrar los refresh generados. No se restauró
el seed ni se borraron cuentas o mensajes.
