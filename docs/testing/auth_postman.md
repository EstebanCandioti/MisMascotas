# Auth: colección Postman y Mailpit

## Objetivo y fuente de verdad

La colección `postman/MisMascotas Auth.postman_collection.json` representa exactamente
los 35 casos de Auth de `docs/testing/casos_prueba.csv`: REG 001–017, LOG 001–006,
2FA 001–006 y SES 001–006. Los IDs y resultados esperados se conservan en cada
solicitud. No se agrega logout al catálogo; se utiliza como limpieza de sesiones.

Los scripts fuente están en `scripts/qa/auth-postman.js`. El generador incorpora
esa biblioteca en los eventos de colección. Las solicitudes adicionales de preparación
y comprobación se envían mediante `pm.sendRequest`; aparecen en la consola local.
Cada solicitud visible representa un caso completo y puede ejecutarse individualmente.

## Preparación y ejecución en Postman

1. Desde el repositorio QA, iniciar los servicios existentes:

   ```powershell
   docker compose --env-file .env.web-testing -f docker-compose.web-testing.yml up -d --no-build --wait db-web-testing mailpit-web-testing backend-web-testing
   ```

2. Importar la colección Auth actualizada y el entorno
   `postman/MisMascotas Auth QA.postman_environment.json`. Si existe una copia vieja
   importada, reemplazarla y comprobar que tenga las cuatro carpetas y 35 solicitudes.
   Usar el cliente de escritorio o Desktop Agent con acceso a localhost.
3. Seleccionar el entorno QA. `baseUrl` y `mailpitUrl` deben coincidir con los puertos
   del entorno (`8080` y `8025` por defecto). Solo se permiten URLs HTTP locales.
4. Ejecutar un caso con **Send** o la colección con **Run**, una iteración y sin
   ejecución paralela ni guardado compartido de respuestas. Usar un tiempo de script
   de al menos 60 segundos para la corrida normal.
5. Consultar **Test Results**: cada verificación lleva el ID del CSV. **Visualize**
   muestra el estado del caso. Una preparación fallida produce una aserción fallida
   `BLOQUEADO` y omite la solicitud dependiente; no se interpreta como aprobación.

La lectura 2FA busca por destinatario exacto, descarta IDs de mensajes anteriores,
comprueba la fecha y extrae seis dígitos del texto del correo. Consulta durante un
máximo de 30 segundos con intervalos de 400 ms. No borra la bandeja ni usa el último
correo global.

## Interpretación de resultados

| Estado | Significado |
|---|---|
| APROBADO_API | Pasaron las comprobaciones automáticas de API del caso. |
| FALLÓ | Una respuesta o comportamiento no coincide con el esperado. |
| BLOQUEADO | No pudo prepararse o falta un contrato requerido. |
| PARCIAL | Pasó lo ejecutado y queda una comprobación pendiente. |
| NO_EJECUTADO / ERROR_EJECUCION | El ejecutor no pudo completar el caso. |

`APROBADO_API` no autoriza a marcar toda la fila del CSV como aprobada cuando
incluye interfaz o efectos internos pendientes. No se escribe automáticamente
`ejecuciones_pruebas.csv` ni se modifica la planilla de resultados.

## Cobertura y límites conocidos

- **REG 001, 008, 009, 016 y LOG 001:** desafío, correo nuevo y bloqueo del recurso
  protegido antes de 2FA. REG 008 reanuda mediante login y completa verificación;
  REG 016 usa exactamente ocho caracteres. REG 009 queda PARCIAL porque la navegación
  directa al desafío pertenece a la interfaz.
- **REG inválidos:** HTTP 400 y código de validación, identificación de todos los
  campos requeridos y ausencia de correo al destinatario válido. Cuando el email
  del intento es válido, un login posterior verifica que no haya una cuenta utilizable.
  Esto no sustituye una consulta SQL que compruebe ausencia total de filas.
- **REG 002 y 017:** exigen HTTP 409 por duplicado. REG 017 conserva espacios y
  mayúsculas del CSV: si la validación previa impide normalizarlo, el caso debe fallar.
- **LOG inválidos:** rechazan sin tokens, identifican campos faltantes y observan
  ausencia de correo adicional por una ventana acotada de 1,2 segundos. No prueba
  que jamás pueda emitirse un correo retrasado.
- **2FA 001 y 002:** completan sesión, identidad propia y rechazo de código consumido.
  **2FA 003:** modifica un dígito del código real, exige rechazo y después demuestra
  que el código correcto sigue funcionando.
- **2FA 004:** requiere vencimiento real del código con pre-auth todavía vigente.
  Está BLOQUEADO por defecto. La configuración habitual usa 10 minutos para ambos;
  esperar los 10 minutos dejaría vencer también el pre-auth y no probaría el caso.
  Para ejecutarlo sin alterar la base, configurar explícitamente en un entorno QA
  un pre-auth más largo que el código, indicar el TTL real en `qa_codigo_ttl_ms` y
  activar `qa_esperar_vencimientos=true`. El script espera ese tiempo, exige HTTP 410
  `CODIGO_EXPIRADO`, lee el correo nuevo y completa la recuperación. No se cambia la
  configuración Docker automáticamente. El rechazo y la recuperación visual deben
  comprobarse también en la interfaz si se pretende aprobar todas las capas.
- **2FA 005 y 006:** BLOQUEADOS. El backend inspeccionado solo ofrece registro, login,
  verificar código, refresh y logout. No existe un contrato de reenvío de registro
  ni un estado persistente de cuenta verificada. No se reemplaza reenvío por login:
  login emite códigos también para cuentas que ya verificaron una sesión.
- **SES 001:** identidad, nombre, UUID, plan y ausencia de secretos en el perfil.
  **SES 002:** ausencia de token, texto inválido y firma JWT alterada, los tres con 401.
- **SES 003 y 004:** rotación, perfil válido y rechazo del refresh reutilizado. El
  conteo SQL de filas nuevas y la revocación de otras sesiones requieren observación
  adicional. La reutilización puede revocar otros refresh del mismo usuario: cada
  caso usa una cuenta nueva para no afectar las cuentas del seed.
- **SES 005:** envía dos renovaciones concurrentes mediante `Promise.all`, exige una
  sola emisión y un rechazo 401. Detecta doble emisión HTTP; no demuestra ausencia
  de filas huérfanas en la base. Es una carrera real: una corrida aprobada no prueba
  por sí sola que el backend esté libre de carreras bajo otras intercalaciones.
- **SES 006:** comprueba el access anterior después de renovar. Queda PARCIAL en la
  corrida rápida; para incluir rechazo posterior al vencimiento, activar
  `qa_esperar_vencimientos=true` y un límite de scripts superior a 20 minutos.
  Espera hasta el vencimiento declarado del token original, sin falsificar firmas.

## Datos, aislamiento y seguridad de reportes

Cada caso genera un destinatario `qa.auth.<uuid>@mismascotas.test` y una contraseña
sintética exclusiva de pruebas. No necesita cuentas seed ni credenciales reales.
Los secretos se guardan solo en variables locales de solicitud/corrida y se limpian
al finalizar. No se actualizan variables de colección ni de entorno con tokens.
Las respuestas y el tráfico permanecen visibles en Postman: no exportar ni compartir
la consola, historial o respuestas con códigos y tokens.

La limpieza intenta cerrar cada refresh generado, incluidos los obtenidos por
renovaciones. Las cuentas, códigos, correos y auditoría quedan disponibles para
investigación. No se borra información automáticamente ni se restaura el seed.
La corrida completa agrega cuentas temporales a QA; conservar evidencia y usar luego
el procedimiento autorizado de `seed_qa.md` para restaurar si corresponde.

## Ejecución reproducible con informe saneado

Requiere Node.js y Newman local. Desde la raíz del repositorio:

```powershell
npm.cmd install --prefix .qa/auth-runtime --no-audit --no-fund newman@6.2.3
node scripts/qa/Ejecutar-AuthPostman.cjs
node scripts/qa/Ejecutar-AuthPostman.cjs --caso AUT-SES-005
```

Para esperas reales, después de configurar las vigencias QA necesarias:

```powershell
node scripts/qa/Ejecutar-AuthPostman.cjs --caso AUT-2FA-004 --esperar-vencimientos --codigo-ttl-ms 600000
node scripts/qa/Ejecutar-AuthPostman.cjs --caso AUT-SES-006 --esperar-vencimientos
```

El ejecutor guarda solo ID, estado, cantidades y nombres de verificaciones fallidas
en `docs/testing/evidencias/AUTH-.../resumen_auth.json`. No exporta el reporte completo
de Newman porque incluiría respuestas y secretos. Devuelve código de salida 1 si
hay fallos, bloqueos, parciales o casos no ejecutados. `--salida` permite elegir una
carpeta nueva y `--entorno` un archivo de entorno local.

## Mantenimiento

Actualizar la biblioteca y regenerar la colección desde el CSV:

```powershell
node scripts/qa/Generar-AuthPostman.mjs
```

El generador rechaza IDs duplicados, faltantes o agregados al alcance sin adaptar
los scripts. No modifica el CSV. Después de regenerar, volver a importar la colección.
La colección anterior se conserva en el respaldo de publicación de esta entrega.

Referencias técnicas: [scripts asíncronos de Postman](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-send-request/),
[omisión de solicitudes](https://learning.postman.com/latest-v-12/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution),
[API de Mailpit](https://mailpit.axllent.org/docs/api-v1/).
