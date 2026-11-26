# Centralización de Premium

## Objetivo

Definir una única política local de Premium, reutilizable por los servicios de cada módulo. Evitar que cada consumidor interprete por su cuenta el indicador persistido, los pagos o los vencimientos. El cambio de esa política debe poder resolverse en `PremiumService`.

## Decisiones acordadas

- Mantener temporalmente la compatibilidad con cuentas Premium manuales existentes.
- Considerar cobertura pagada vigente a una suscripción con al menos un pago `APROBADO` y vencimiento estrictamente posterior al instante consultado. No alcanza con el estado local `ACTIVA` ni con una fecha futura sin pago.
- Conservar cobertura de suscripciones canceladas o pausadas hasta el vencimiento pagado. Mensual y anual utilizan la misma regla de vigencia.
- Si existe cualquier pago aprobado histórico, un indicador `usuario.esPremium=true` no sustituye una cobertura vencida o sin fecha verificable.
- Si no hay pagos aprobados históricos, se conserva el indicador manual existente. No se agrega un mecanismo nuevo para asignarlo ni se convierte una cuenta vencida en manual automáticamente.
- No consultar Mercado Pago, guardar entidades, renovar períodos ni emitir auditoría durante una validación. Los procesos de pagos y vencimientos conservan sus responsabilidades de escritura.
- Evaluar el plan del propietario del recurso; los permisos del usuario que opera se verifican aparte con el componente de acceso existente.
- Las entidades JPA no invocan servicios. Los consumidores son `MascotaService`, `AlbumService`, etc.

La compatibilidad manual es temporal y explícita: no demuestra un pago ni permite inferir cómo o quién asignó originalmente el indicador. Reemplazarla en el futuro requerirá definir el origen de los beneficios o migrar esas cuentas, no reinterpretar silenciosamente datos históricos.

## Contrato del servicio

```java
boolean esPremium(UUID propietarioId);
void exigirPremium(UUID propietarioId);
EstadoPremium consultar(UUID propietarioId);
```

- `esPremium` devuelve el resultado efectivo de la política.
- `exigirPremium` permite continuar o lanza `PremiumRequeridoException`, traducida a `403 / PREMIUM_REQUERIDO`.
- `consultar` devuelve conjuntamente el resultado efectivo, la cobertura pagada, el mayor vencimiento vigente y si se aplicó la compatibilidad manual. Permite que las consultas reutilicen una evaluación completa.
- Propietario inexistente: `RecursoNoEncontradoException`, con el tratamiento global existente.
- Errores de persistencia: se propagan; no se convierten en un falso resultado Free.
- No se almacena caché de decisiones. El vencimiento se evalúa con un único instante de `Clock` por consulta.

## Subtareas y criterios de cierre

| ID | Entrega | Criterio verificable | Dependencia |
|---|---|---|---|
| P01 | Reglas, compatibilidad manual y contrato | Reglas documentadas, casos límite identificados y excepción manual acordada. | Ninguna |
| P02 | `PremiumService`, excepción y pruebas propias | Consulta solo datos del propietario; respeta pagos/vencimiento/manual; `exigirPremium` produce un 403 uniforme; no realiza escrituras. | P01 |
| P03 | Integrar el límite de mascotas | `MascotaService` usa el resultado central; Free puede tener hasta cinco mascotas y Premium las supera. Se prueban también Premium manual y pago vencido con indicador desactualizado. | P02 |
| P04 | Unificar el perfil consumido por autenticación | `/usuario/me` usa el resultado efectivo después del login y de renovar sesión, conservando su JSON y sin modificar datos durante la consulta. Autenticación y JWT conservan su contrato de tokens sin datos de Premium. | P02 |
| P05 | Unificar consulta de suscripción | `/suscripciones/me` reutiliza la evaluación de Premium y mantiene suscripción e intento pendiente separados; los tres consumidores informan resultados coherentes. | P04 |
| P06 | Compartir el criterio con procesos de escritura | Cobros y vencimientos reutilizan el criterio de cobertura sin perder bloqueos, atomicidad, auditoría ni las excepciones de revisión existentes. | P05 |
| P07 | Integración completa | Pruebas PostgreSQL de vigencia y aislamiento; suite completa; documentación actualizada; PR y verificación en QA. | P03 a P06 |

No corresponde reemplazar escrituras transaccionales por una consulta HTTP o por una llamada a Mercado Pago. Si P06 requiere un evaluador puro interno al componente, debe conservar una sola definición de la regla de cobertura y permitir evaluar las entidades que ya están bloqueadas en la transacción.

Después de esta centralización se implementa el bloque de restricciones por módulo: álbumes, fotos, foto de perfil y badges. Cada uno tendrá su propia tarea verificable y conservará sus límites. La matriz de operaciones después del vencimiento debe acordarse antes de aplicar esos controles. El límite de invitados se incorpora con cuidado compartido.

Avance posterior: crear/editar álbumes, cargar fotos por URL, asignar/reemplazar foto de perfil y crear badges ya consumen `exigirPremium`, con lectura conservada al vencer el plan. El propietario puede eliminar álbumes/fotos/badges y retirar foto de perfil incluso sin Premium. Badges agrega máximo de tres activas por mascota y bloqueo transaccional contra altas concurrentes. Matrices, contratos y QA: [albumes_premium.md](albumes_premium.md), [fotos_premium.md](fotos_premium.md) y [badges_premium.md](badges_premium.md).

## Casos mínimos de prueba

- Free sin suscripciones.
- Premium manual sin pagos aprobados, incluido el seed QA.
- Activa, cancelada o pausada con cobertura vigente.
- Vencimiento exacto y posterior al instante de consulta.
- Indicador persistido falso con pago aprobado y vigencia futura.
- Indicador persistido verdadero con pagos aprobados vencidos.
- Fecha futura sin pago aprobado; pago aprobado sin vencimiento.
- Varias suscripciones: conserva la cobertura de cualquiera que esté pagada y vigente y obtiene el mayor vencimiento.
- Una cuenta no hereda pagos o beneficios de otra.
- Validación positiva sin excepción y negativa con `403 / PREMIUM_REQUERIDO`.

## Estado de las entregas

Base: `origin/backend`, commit `34403be`, con la PR #2 integrada. Rama de trabajo: `feature-premium-centralizado`.

### P01 y P02: política central

P01 y P02 completadas el 25/11/2026. Se incorporaron `PremiumService`, `PremiumRequeridoException` y el tratamiento global `403 / PREMIUM_REQUERIDO`.

Verificación: 18 pruebas específicas aprobadas; suite completa de CI local con **559 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. Incluye el arranque del contexto y las integraciones existentes sobre PostgreSQL 17.6 descartable. El proveedor MP permaneció simulado; no se accedió a Supabase. Salida en `backend/target-premium-ci.log`. El proyecto de base temporal se elimina al terminar.

### P03: límite de mascotas

`MascotaService` consulta `PremiumService.esPremium` para el propietario autenticado al crear una mascota. Free conserva un máximo de cinco mascotas activas; Premium pagado vigente y Premium manual pueden superarlo. Las mascotas eliminadas lógicamente no consumen cupo. Un vencimiento no elimina mascotas existentes: si ya hay cinco o más, impide agregar otra hasta recuperar cobertura o liberar cupo.

La creación bloquea la fila del propietario durante la validación, el conteo y el guardado. Así, dos solicitudes simultáneas no pueden consumir el mismo último lugar disponible. Se reutiliza el bloqueo existente del usuario, también utilizado por los procesos de suscripción. La validación no consulta Mercado Pago ni modifica el indicador persistido de Premium.

Se conserva el contrato HTTP: alta exitosa `201`; cupo alcanzado `409 / LIMITE_MASCOTAS_ALCANZADO`. Esta operación usa el límite del plan, por lo que no invoca `exigirPremium` ni devuelve `PREMIUM_REQUERIDO` al intentar registrar una sexta mascota.

Verificación dirigida: **41 pruebas aprobadas**, incluidas 14 de HTTP y PostgreSQL real. Cubren cobertura mensual y anual, cancelación y pausa con vigencia, compatibilidad manual, vencimiento exacto, indicador desactualizado, aislamiento entre propietarios, bajas lógicas, conservación de mascotas, auditoría y dos altas concurrentes partiendo de cuatro mascotas. La nueva integración se incorpora al workflow de CI con `RUN_POSTGRES_PREMIUM_IT=true`.

P03 completada el 25/11/2026. Verificación completa de CI local: **580 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. Salida en `backend/target-premium-p03-ci.log`. También pasaron `git diff --check` y el verificador de configuración empaquetada. Las integraciones utilizaron PostgreSQL 17.6 descartable con Mercado Pago simulado.

Al cierre de P03 seguían pendientes P04 a P07. No se agregan restricciones de álbumes, fotos o badges en P03.

### P04: perfil consumido por autenticación

La revisión de `AuthService`, `AuthResponseDTO` y `JwtService` confirma que autenticación no devuelve ni incorpora Premium al JWT. El dato se expone mediante `UsuarioResponseDTO` en `/usuario/me`. Por eso, P04 conecta `UsuarioService` con `PremiumService` y verifica ese perfil con sesiones reales emitidas y renovadas por autenticación. No se agregan campos a los tokens ni consultas de Premium a la emisión de sesiones: duplicar allí el dato produciría otra copia que puede quedar vencida durante la sesión.

`UsuarioService.obtenerPerfilAutenticado` consulta ahora `PremiumService.esPremium` dentro de una transacción de lectura. Se conserva el JSON de perfil (`idUsuario`, `nombre`, `email`, `esPremium`). Cada consulta evalúa la vigencia del propietario autenticado en ese momento, incluso con el mismo token de acceso, sin escribir el indicador, los pagos, las suscripciones o la auditoría. Se mantiene la compatibilidad manual. La consulta de suscripción corresponde a P05.

Verificación dirigida: **21 pruebas aprobadas** (6 unitarias de perfil, 12 de integración con PostgreSQL y 3 existentes del controlador de autenticación). Se recorren login con código y renovación de refresh token reales, sin enviar correo ni contactar Mercado Pago. Se comprueban Free, Premium manual, mensual y anual con indicador falso, cancelación o pausa con cobertura, pago vencido con indicador verdadero, vencimiento exacto y pago rechazado. También se verifican el aislamiento entre cuentas, el rechazo de consultas sin sesión, la ausencia de escrituras durante el GET y el vencimiento sin cambiar el token de acceso. El workflow incorpora `UsuarioPremiumPostgresIT` con el mismo indicador `RUN_POSTGRES_PREMIUM_IT=true`.

P04 completada el 25/11/2026. Verificación completa de CI local: **598 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. Salidas en `backend/target-premium-p04.log` y `backend/target-premium-p04-ci.log`. También pasaron `git diff --check` y el verificador de configuración empaquetada. El proyecto PostgreSQL temporal se retira al finalizar; no se utilizó la base QA ni se enviaron pagos o correos reales.

Al cierre de P04 seguían pendientes P05 a P07.

### P05: consulta de suscripción

`SuscripcionConsultaService` consume una evaluación completa de `PremiumService`, sin volver a interpretar fechas, pagos o el indicador persistido. Para construir los detalles, el servicio central ofrece `consultarSuscripciones`, una consulta interna con el estado efectivo, las suscripciones del propietario y los identificadores pagados y vigentes evaluados con un solo instante del reloj. La consulta HTTP reutiliza esos resultados y conserva las reglas de selección de suscripción e intento pendiente, los desempates y los campos de conciliación.

La evaluación interna no se expone como DTO HTTP: las entidades se mapean dentro de la transacción de lectura existente. La consulta básica de Premium mantiene la optimización de no leer suscripciones cuando no hay pagos aprobados. No se modifica el contrato JSON ni los procesos de cobro y vencimiento de P06. Tampoco se agregan escrituras, auditoría, caché o llamadas al proveedor. La coherencia entre consultas separadas se entiende para los mismos datos e instante; una cobertura puede vencer entre dos solicitudes.

Verificación dirigida: **120 pruebas aprobadas** (16 de `PremiumService`, 19 de consulta, 57 del controlador de suscripción y 28 de integración PostgreSQL). Las integraciones existentes se amplían para comparar ambos GET después del login y refresh, confirmar que el mismo token refleja el vencimiento, verificar ausencia de escrituras y cotejar los resultados con el límite real de mascotas. Se mantienen la cobertura de un mandato reemplazado, el intento pendiente separado y el desempate determinista cuando dos coberturas tienen la misma fecha. La consulta interna se verifica con un reloj que cambia entre lecturas: raíz y detalles consumen un único instante y cada repositorio se consulta una sola vez.

P05 completada el 25/11/2026. Suite completa de CI local: **603 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. Salidas en `backend/target-premium-p05.log` y `backend/target-premium-p05-ci.log`. También pasaron `git diff --check` y el verificador de configuración empaquetada. Se utilizó exclusivamente PostgreSQL 17.6 descartable y se retiró el proyecto temporal al finalizar, sin acceder a QA, Supabase, correo o Mercado Pago reales.

Al cierre de P05 seguían pendientes P06 y P07.

### P06: criterio compartido con los escritores

Se extrae dentro de `PremiumService` una evaluación pura de las entidades del propietario, los identificadores con pago aprobado y un instante explícito. Las lecturas y los procesos de primer cobro, renovación y vencimiento reutilizan esa regla sin volver a consultar al usuario bloqueado. Los escritores conservan las transacciones, el orden de bloqueo por usuario, la idempotencia y el encolado de auditoría actuales. Se evalúa la cobertura después de guardar el pago y el vencimiento, dentro de la misma transacción.

Un primer cobro confirmado tarde no activa Premium si su período ya venció; una cobertura vigente de otra suscripción sí conserva el beneficio. Autorizar un mandato no acredita un pago, incluso si existe una fecha futura desactualizada. El proceso de vencimiento conserva la excepción de revisión para pagos aprobados sin fecha: aborta y revierte, en lugar de quitar acceso silenciosamente. La consulta de beneficios sigue sin inventar cobertura en ese caso. Los ciclos mensual y anual mantienen sus cálculos de meses calendario.

P06 completada el 25/11/2026. **82 pruebas dirigidas aprobadas**, incluidas las integraciones PostgreSQL de webhook y primer cobro. Se verificaron confirmaciones simultáneas, renovación concurrente con vencimiento, idempotencia, rollback de pago/usuario/suscripción/auditoría, cobros históricos, vencimiento exacto y revisión por fecha faltante. Salida: `backend/target-premium-p06.log`.

### P07: alcance de integración

Se ejecutan la suite completa con PostgreSQL temporal y los casos de concurrencia y rollback del primer cobro, webhook y vencimiento. Se construyen las imágenes y se verifica el backend empaquetado con el esquema y seed QA en un entorno Docker aislado. Esa validación utiliza exclusivamente datos sintéticos y no confirma cobros reales ni cambia los volúmenes QA existentes. Se prepara una PR hacia `backend`; publicar la PR no implica fusionarla ni promover las imágenes distribuidas.

Suite completa de CI local ampliada: **619 pruebas aprobadas, cero fallos, errores u omisiones**, `BUILD SUCCESS` y JAR generado. Incluye ahora `SuscripcionPrimerCobroPostgresIT` mediante `RUN_POSTGRES_PRIMER_COBRO_IT=true`, además de las integraciones de P03 a P05. Salida: `backend/target-premium-p07-ci.log`. Las imágenes `mismascotas-backend:premium-validacion` y `mismascotas-db-qa:premium-validacion` se construyeron localmente con los Dockerfile del repositorio.

Verificación Docker QA aprobada el 25/11/2026: nueve escenarios por HTTP del JAR real (Free, manual, mensual, anual, cancelación, pausa, vencimiento, pago sin fecha y fecha futura sin pago aprobado), aislamiento entre cuentas y alta de sexta mascota según plan. Se conservaron los conteos del seed (6 usuarios, 15 mascotas, 20 eventos clínicos, 15 recordatorios), la adopción de V9 sin reaplicar SQL y los datos al recrear la base. El verificador retiró sus fixtures, contenedores, red y volumen etiquetados. Salida: `backend/target-premium-p07-qa.log`. Se añade esta verificación al paso existente de imágenes QA en CI.
