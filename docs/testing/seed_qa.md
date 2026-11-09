# Seed restaurable del entorno QA

## Estado y alcance

**Versión:** `qa-base-v1`. Implementación y comprobación: **2026-10-22**.

La base del entorno `mismascotas-web-testing` utiliza el volumen Docker existente
`mismascotas_web_testing_db`. No se crea otro volumen, no se comparte con desarrollo
y no se modifica la estructura ni el backend para cargar estos datos.

| Datos base | Cantidad |
|---|---:|
| Cuentas activas | 6: cinco Free y una Premium |
| Mascotas activas | 15 |
| Eventos clínicos | 20: 17 pesos y tres de otros tipos |
| Recordatorios | 15: 11 únicos y cuatro recurrentes |
| Estados del catálogo | 14, conservados y validados |

Las credenciales ficticias están en [cuentas_qa.md](cuentas_qa.md). El detalle de
datos está en [mascotas_qa.md](mascotas_qa.md),
[eventos_clinicos_qa.md](eventos_clinicos_qa.md) y
[recordatorios_qa.md](recordatorios_qa.md).

Este seed no pretende cubrir toda la matriz: aún no precarga relaciones de cuidado
compartido, invitaciones, suscripciones/pagos, álbumes, fotos, badges ni adjuntos.
Premium se representa mediante `usuario.es_premium`, según el contrato actual.
Esos escenarios y los datos inválidos/de frontera se preparan por caso o en una
ampliación explícita del catálogo. No se inventan reglas de producto pendientes.

## Archivos y decisión de aislamiento

- `DB/qa/restablecer-seed.sql`: transacción completa de limpieza, inserciones y comprobaciones.
- `scripts/qa/Restablecer-QA.ps1`: valida el destino, guarda respaldo y coordina la carga.
- `DB/01-init.sql`: estructura existente, sin cambios.
- `DB/02-seed.sql`: catálogo de estados compartido, sin cambios.

El SQL QA no se monta como inicializador de Docker ni se añade al Compose de
desarrollo. La carga es explícita, incluso después de crear un volumen nuevo.
Así las cuentas con contraseñas conocidas no aparecen silenciosamente en otros
entornos. Compartir o versionar el seed no implica versionar el volumen Docker.

## Cómo cargar o restablecer

Requisitos: Docker Desktop y PowerShell. No se necesita instalar Java, PostgreSQL
ni herramientas de hashing para ejecutar el seed.

1. Iniciar el stack siguiendo `docker-web.md`. La base y Mailpit deben estar saludables.
2. Desde la raíz del repositorio, validar sin modificar datos:

   ```powershell
   .\scripts\qa\Restablecer-QA.ps1
   ```

3. Para **reemplazar todos los datos de negocio de QA** por el catálogo base:

   ```powershell
   .\scripts\qa\Restablecer-QA.ps1 -ConfirmarRestablecimiento
   ```

4. Empezar las pruebas con una sesión nueva del navegador. El reset no borra
   almacenamiento del navegador ni impide por sí solo reutilizar un access token
   JWT ya emitido y aún vigente. No ejecutar pruebas ni usar la app durante la carga.

El procedimiento:

- Exige proyecto y servicios Compose esperados, directorio del repositorio activo,
  volumen nominal con etiquetas QA y coincidencia entre la base del backend y PostgreSQL.
- Comprueba que SMTP apunta a Mailpit y que su bandeja solo se publica en localhost.
- Impide dos restauraciones simultáneas desde el lanzador y usa un bloqueo transaccional SQL.
- Detiene únicamente el backend QA si estaba encendido.
- Guarda un respaldo PostgreSQL de la base anterior **antes de modificar datos**.
- Ejecuta un `TRUNCATE` explícito de las 16 tablas de negocio, sin `CASCADE`, y carga
  los datos en la misma transacción. Conserva el catálogo de estados; exige que sus
  14 pares entidad/nombre coincidan con el catálogo previsto. Una FK nueva no incluida
  o un cambio de catálogo hacen fallar la carga para exigir una revisión.
- Valida cantidades, distribución por propietario, peso actual contra última medición,
  autoría, estados, recurrencias, confirmaciones y tablas que deben empezar vacías.
- Confirma solamente si todo resulta válido; después vacía Mailpit. Su limpieza no
  es parte de la transacción SQL: si falla, informa que la base ya fue cargada.
- Reinicia exactamente el backend validado y comprueba su salud. Si estaba apagado,
  lo deja apagado. No recrea otros contenedores.

Si SQL falla antes de confirmar, PostgreSQL revierte también el `TRUNCATE`. Si se
interrumpe el proceso después de `COMMIT`, revisar evidencia y cantidades: no se
puede asumir que un error posterior implica que no hubo cambios. Si la interrupción
es forzada, comprobar además el estado del backend.

No se eliminan archivos subidos a servicios externos. Este catálogo no incluye esos
archivos; al incorporar fotos/adjuntos hará falta definir también su limpieza aislada.

## Fechas, contraseñas e identidades

- **T0:** instante de inicio de la transacción de carga.
- **D0:** fecha de T0 en `America/Argentina/Buenos_Aires`.
- Las fechas de nacimiento y eventos se calculan desde D0. Los horarios de recordatorios
  se interpretan en Argentina y se guardan como instantes con zona horaria. Una restauración
  otro día conserva los escenarios relativos, no las fechas absolutas de la ejecución anterior.
- Los seis hashes BCrypt se generaron con `BCryptPasswordEncoder`, coste 10. Se verificó
  su compatibilidad mediante el login real; no se guarda la contraseña en texto en PostgreSQL.
- No se precargan códigos 2FA, refresh tokens, sesiones ni auditoría ficticia. Los correos
  y registros creados al comprobar el entorno se quitan con una restauración final.

Los UUID son estables entre restauraciones. Su sufijo es el número del fixture,
expresado en decimal y completado con ceros hasta 12 posiciones:

| Fixture | Patrón de UUID | Ejemplo |
|---|---|---|
| QA-USR-NNN | `10000000-0000-4000-8000-SSSSSSSSSSSS` | QA-USR-002 → `10000000-0000-4000-8000-000000000002` |
| QA-MAS-NNN | `20000000-0000-4000-8000-SSSSSSSSSSSS` | QA-MAS-014 → `20000000-0000-4000-8000-000000000014` |
| QA-ECL-NNN | `30000000-0000-4000-8000-SSSSSSSSSSSS` | QA-ECL-020 → `30000000-0000-4000-8000-000000000020` |
| QA-REC-NNN | `40000000-0000-4000-8000-SSSSSSSSSSSS` | QA-REC-013 → `40000000-0000-4000-8000-000000000013` |

Se permiten UUID conocidos únicamente como identificadores de fixtures. No son tokens
ni secretos y no reemplazan los controles de autorización del backend.

## Evidencia y recuperación

Cada carga confirmada genera en `.qa/restauraciones/`:

- Un `.dump` con la base anterior, recuperable con herramientas PostgreSQL y siempre
  sobre el destino QA validado. No se restaura automáticamente ante un fallo posterior.
- Un `.json` con versión/hash SHA-256 del seed, D0, T0, zona horaria, base, volumen,
  cantidades, ruta del respaldo y resultado de limpieza de Mailpit. Si Git está disponible,
  registra el commit actual y si hay cambios locales; no identifica por sí solo el binario
  que está corriendo en Docker.

La carpeta está ignorada por Git: no publicar respaldos ni confundirlos con casos o
resultados de la matriz. La primera carga conserva un respaldo de los datos anteriores;
las siguientes conservan el estado anterior a cada ejecución. El backup temporal usado
para copiar el `.dump` también puede permanecer en `/tmp` del contenedor hasta recrearlo.

## Comprobaciones realizadas

El **2026-10-22** se verificó:

1. En una base temporal creada dentro del contenedor QA: rechazo sin habilitación,
   carga inicial, fallo intencional después del `TRUNCATE` y las inserciones con rollback
   completo, y segunda carga que recupera el mismo contenido sin duplicados. La base
   temporal se eliminó al terminar; no se eliminó el volumen QA.
2. En la base QA real: login, correo nuevo en Mailpit, verificación 2FA, perfil y logout
   de las seis cuentas; listados por propietario con distribución exacta y peso actual
   consistente con el historial devuelto por API; acceso ajeno denegado para los dos
   propietarios adicionales.
3. Restauración final para dejar las cantidades base y retirar códigos, tokens, auditoría
   y correos generados durante las comprobaciones.

Estas son comprobaciones de preparación y consistencia, **no una ejecución completa
de la matriz ni una aprobación de funcionalidades pendientes**. No se verificó la
interfaz con Selenium en esta tarea. Las recurrencias precargadas no implementan
ocurrencias independientes, tareas del día ni envío de notificaciones.
