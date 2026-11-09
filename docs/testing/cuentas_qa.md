# Cuentas y credenciales del entorno QA

## Estado y alcance

Catálogo implementado y cargado en `mismascotas-web-testing` el **2026-10-22**, con el seed `qa-base-v1`. Las seis cuentas pudieron iniciar sesión con contraseña y código 2FA recibido en Mailpit. Esta comprobación de preparación no equivale a ejecutar toda la matriz de pruebas.

El repositorio que construye el entorno es `D:\AAAIES\MisMascotas-front`. La copia de este documento en `docs/testing/cuentas_qa.md` de ese repositorio será la referencia para las pruebas manuales y automatizadas.

Se autoriza reemplazar el usuario que ya existe en la base QA al restaurarla. Esta decisión se limita a la base y al volumen del entorno `mismascotas-web-testing`; no alcanza otras bases ni volúmenes.

## Credenciales definidas

Las contraseñas se documentan expresamente para facilitar las pruebas manuales. Son datos de prueba exclusivos de QA. Los correos de estas direcciones ficticias se capturan localmente en Mailpit.

| Cuenta | Email | Contraseña | Plan | Mascotas previstas |
|---|---|---|---|---:|
| QA-USR-001 — Free vacío | `qa.free.vacio@mismascotas.test` | `QaVacio2026!` | Free | 0 |
| QA-USR-002 — Free habitual | `qa.free.habitual@mismascotas.test` | `QaHabitual2026!` | Free | 2 |
| QA-USR-003 — Free al límite | `qa.free.limite@mismascotas.test` | `QaLimite2026!` | Free | 5 |
| QA-USR-004 — Premium | `qa.premium@mismascotas.test` | `QaPremium2026!` | Premium | 6 |
| QA-USR-005 — Propietario A | `qa.propietario.a@mismascotas.test` | `QaPropietarioA2026!` | Free | 1 |
| QA-USR-006 — Propietario B | `qa.propietario.b@mismascotas.test` | `QaPropietarioB2026!` | Free | 1 |

Las cantidades describen el estado restaurable ya cargado. Las 15 mascotas activas y sus condiciones iniciales se detallan en `mascotas_qa.md`, distribuidas entre las cuentas de esta tabla. Una prueba puede modificar esos datos; el procedimiento de `seed_qa.md` permite recuperarlos.

## Propósito de cada cuenta

- **QA-USR-001:** listado vacío y alta inicial de mascota.
- **QA-USR-002:** flujos habituales de consulta, edición y eliminación con datos existentes.
- **QA-USR-003:** rechazo de la sexta mascota Free y liberación del cupo después de una eliminación permitida.
- **QA-USR-004:** altas por encima del límite Free y escenarios Premium implementados. Su presencia no demuestra que todas las restricciones Premium estén implementadas.
- **QA-USR-005 y QA-USR-006:** aislamiento de datos entre propietarios. Permiten comprobar que cambiar de cuenta no conserva recursos ni permisos de otro dueño y que conocer un ID ajeno no concede acceso.

## Reglas del seed

- Las seis cuentas comienzan activas. En el modelo actual esto significa `activo=true`, y `es_premium` se asigna según la tabla. La representación de suscripciones y vencimientos se completará al preparar las pruebas de ese módulo.
- Los nombres visibles seguirán los alias descriptivos de la tabla, de forma que pueda identificarse la cuenta activa desde la interfaz.
- Las contraseñas se guardarán en la base como hashes compatibles con el `PasswordEncoder` de la aplicación; el texto de esta tabla es la credencial para iniciar sesión, no el valor de `password_hash`.
- Los tokens y códigos 2FA se generan durante la ejecución del flujo de autenticación. No se precargan códigos estáticos ni sesiones permanentes.
- Los casos de registro exitoso usan emails nuevos por ejecución. Los casos de email duplicado pueden usar una cuenta de esta tabla, sin depender de que otro caso la haya registrado antes.
- Los escenarios que modifican cuentas o sus mascotas deben restaurar su condición inicial o usar datos propios del caso. Las cantidades de esta tabla describen el punto de partida tras restaurar el seed.
- Los roles Familia y Cuidador se prepararán como relaciones de acceso cuando se defina y pruebe cuidado compartido. Las seis cuentas son identidades de usuario, no roles globales.

## Estado de preparación

1. Mailpit está incorporado y recibe los correos del backend QA. Además del circuito temporal documentado en `mailpit_qa.md`, se verificó el login con 2FA de las seis cuentas del seed.
2. Están cargados los catálogos definidos: 15 mascotas en `mascotas_qa.md`, 20 eventos en `eventos_clinicos_qa.md` y 15 recordatorios en `recordatorios_qa.md`. Los datos de otros módulos se completarán por separado.
3. `DB/qa/restablecer-seed.sql` y `scripts/qa/Restablecer-QA.ps1` implementan la restauración transaccional, con respaldo y validaciones del destino. El procedimiento y los UUID están en `seed_qa.md`.
4. Se comprobaron cantidades, distribución, consistencia de pesos y consultas autenticadas por API. Después de la comprobación se restaura nuevamente para no conservar códigos, sesiones, auditoría ni correos generados por ella.
