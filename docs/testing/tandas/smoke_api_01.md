# Primera tanda: smoke de API

## Estado, objetivo y alcance

**Plan:** `SMK-API-01`. **Estado:** preparado, no ejecutado.

Comprobar una base mínima de autenticación y autorización antes de ampliar las
pruebas. Se seleccionan **10 casos de prioridad Crítica** del catálogo maestro,
sin cambiar sus IDs ni resultados esperados. No es toda la regresión de auth.

Esta tanda verifica la **capa API**, mediante Postman o solicitudes HTTP
equivalentes. No prueba la interfaz, navegación, mensajes visuales ni integración
Selenium. Un resultado aprobado se informa con ese alcance, no como aprobación
de todas las capas de la aplicación.

## Preparación y criterios de entrada

1. Usar el worktree `D:\AAAIES\MisMascotas-front`, rama de trabajo correspondiente
   y Compose `mismascotas-web-testing`. No usar la otra copia del proyecto.
2. Verificar los cuatro servicios saludables. No compilar, actualizar ni cambiar
   imágenes/configuración mientras se ejecuta una tanda.
3. Antes de **iniciar efectivamente** la ejecución, restaurar QA con el procedimiento
   de `../seed_qa.md` si no se puede garantizar su estado base. No se restaura ahora
   por preparar este plan ni entre cada paso de un caso. Nunca borrar el volumen.
4. Tener las seis cuentas y el catálogo base disponibles: 15 mascotas, 20 eventos y
   15 recordatorios. Empezar sin códigos, refresh tokens, auditoría ni correos de
   comprobaciones anteriores. Los conteos solos no certifican la integridad completa.
5. Capturar la ficha **después de la restauración**, antes del primer caso:

   ```powershell
   .\scripts\qa\Preparar-CorridaQA.ps1
   ```

   El script solo consulta y guarda la ficha y la estructura de la base; no ejecuta
   casos, no restaura datos, no borra correos ni escribe resultados. Un ID ya usado
   se rechaza para no sobrescribir evidencia. Para elegir un ID explícito:

   ```powershell
   .\scripts\qa\Preparar-CorridaQA.ps1 -IdCorrida SMK-API-01-20261022-01
   ```

6. Confirmar URLs en los puertos de la ficha: backend habitualmente
   `http://localhost:8080` y Mailpit `http://localhost:8025`. No usar SMTP externo.
7. Usar un entorno Postman local y sin sincronizar credenciales/tokens. Desactivar
   autenticación heredada cuando el caso requiera ausencia de token. No exportar
   una consola, colección o entorno que conserve contraseñas, códigos o tokens.

La ficha inicial puede prepararse para revisión sin iniciar la tanda; si cambian
imágenes, estructura, configuración o datos iniciales, debe capturarse otra antes
de ejecutar. No inferir el código de una imagen a partir del commit actual del
worktree: registrar el origen del build como no verificado si no se puede demostrar.

## Datos y aislamiento

- Credenciales de las cuentas seed: `../cuentas_qa.md`. No duplicarlas en evidencias.
- UUID: `../seed_qa.md`. QA-USR-NNN usa prefijo `10000000-0000-4000-8000-` y
  QA-MAS-NNN `20000000-0000-4000-8000-`, con el número decimal en 12 posiciones.
- Cada caso inicia su propio desafío/sesión; no depende del token generado por
  otro caso. No ejecutar en paralelo sobre las mismas cuentas.
- Los registros nuevos usan `qa.smk.<id_corrida_en_minusculas>.<caso>.<intento>@mismascotas.test`,
  con `caso=reg001` o `2fa001`, y nombre `QA Smoke <caso>`. La contraseña es la
  documentada para QA-USR-001. Anotar email e ID del usuario creado, no la contraseña.
  Comprobar antes que ese email no existe; cada intento usa otro sufijo.
- La sesión usada en refresh se crea exclusivamente para ese caso. El backend
  actual revoca otros refresh activos del usuario al detectar reutilización de
  uno rotado: observarlo sin asumir una regla nueva ni depender de esas sesiones.
- Conservar evidencias antes de limpiar. Al cerrar la tanda, restablecer el seed
  por el procedimiento seguro y con la autorización correspondiente. No ejecutar
  borrados SQL improvisados ni alterar los catálogos para ocultar un fallo.

## Preparación común de una sesión (no es un resultado por sí misma)

1. Enviar `POST /auth/login` con email y contraseña del fixture del caso.
2. Conservar el token previo únicamente en memoria/local sin exportarlo. Buscar
   en Mailpit un mensaje **nuevo**, posterior a la solicitud, para ese destinatario.
   No usar un código que haya quedado de otro desafío.
3. Enviar `POST /auth/verificar-codigo` con cuerpo `{"codigo":"<codigo_actual>"}`
   y encabezado `Authorization: Bearer <token_previo>`.
4. Conservar `accessToken` y `refreshToken` solo para el caso. Cuando la preparación
   falle, registrar el impedimento y no fingir que las operaciones dependientes
   fueron comprobadas. `POST /auth/logout` puede usarse al finalizar una sesión
   aún disponible; eso no aprueba automáticamente el caso de logout de la matriz.

Los estados HTTP siguientes son el contrato técnico a contrastar con el backend,
no resultados observados durante esta preparación. Un `2xx` solo no alcanza:
comprobar también contenido, identidad, efectos y ausencia de filtración.

## Casos seleccionados y verificaciones

### 01. AUT-REG-001 — Registro válido

- **Datos:** email único `...reg001.<intento>@mismascotas.test`, nombre no vacío y
  contraseña válida indicada en la sección de datos.
- **Acción:** `POST /auth/register` con `nombre`, `email` y `password`.
- **Verificar:** HTTP 200; exactamente una cuenta con ese email; contraseña no
  almacenada en texto; se emite un desafío y llega un correo nuevo al destinatario
  correcto. La respuesta tiene token previo, no pareja de sesión definitiva.
  Usarlo en `GET /usuario/me` debe devolver 401 y no datos del usuario.
- **Evidencia:** respuesta saneada, cantidades e ID de usuario, presencia del
  correo y resultado de `/usuario/me`. No guardar hash de contraseña, código ni token.

### 02. AUT-LOG-001 — Credenciales válidas

- **Datos:** QA-USR-001, sin reutilizar el desafío de otro caso.
- **Acción:** `POST /auth/login` con sus credenciales correctas.
- **Verificar:** HTTP 200 y correo nuevo; el token recibido es previo a 2FA y no
  concede acceso a `GET /usuario/me` (401). Todavía no hay una sesión definitiva
  ni un refresh emitido por este intento.
- **Evidencia:** respuestas saneadas y diferencia de refresh antes/después.

### 03. AUT-LOG-003 — Contraseña equivocada

- **Datos:** email de QA-USR-003 y contraseña deliberadamente distinta de la
  documentada, por ejemplo `NoEsLaClaveQA2026!`.
- **Acción:** `POST /auth/login`.
- **Verificar:** HTTP 401; no se devuelve token de ningún tipo, no se crea refresh
  ni nuevo código 2FA y no llega correo por ese intento. La cuenta sigue igual.
- **Evidencia:** error saneado y diferencias de conteos por usuario/destinatario.

### 04. AUT-2FA-001 — Código correcto desde registro

- **Datos:** usuario nuevo propio `...2fa001.<intento>@mismascotas.test`, distinto
  del de AUT-REG-001. Preparar su registro para este caso.
- **Acción:** verificar el código vigente recibido con su token previo de registro.
- **Verificar:** HTTP 200; `accessToken`, `refreshToken`, tipo Bearer y vencimiento
  informados; código consumido; `GET /usuario/me` con el access devuelve 200 y
  exactamente la identidad del usuario recién registrado.
- **Evidencia:** campos sensibles sustituidos por `[omitido]`, IDs y estado del
  código (`usado`), sin guardar su valor.

### 05. AUT-2FA-003 — Código incorrecto

- **Datos:** desafío nuevo de QA-USR-006. Leer su código en memoria y cambiar el
  último dígito por otro, manteniendo el formato. No adivinar un valor que por
  casualidad pudiera coincidir con el correcto.
- **Acción:** verificar el código alterado con el token previo correspondiente.
- **Verificar:** HTTP 400, sin pareja de sesión ni refresh nuevo; el código correcto
  no queda consumido por ese intento. El token previo sigue sin permitir `/usuario/me`.
- **Evidencia:** error saneado, estado del código sin su valor y diferencias de refresh.

### 06. AUT-SES-001 — Recurso protegido con token válido

- **Datos:** sesión nueva verificada de QA-USR-002.
- **Acción:** `GET /usuario/me` con su access vigente.
- **Verificar:** HTTP 200; UUID `10000000-0000-4000-8000-000000000002`, email y
  nombre correctos, plan Free. La respuesta no incluye contraseña, hash, códigos,
  tokens ni datos de otro usuario.
- **Evidencia:** estado HTTP y cuerpo del perfil, sin el encabezado Authorization.

### 07. AUT-SES-002 — Sin token o token inválido

- **Variantes obligatorias de la misma ejecución:** (a) sin Authorization;
  (b) `Bearer qa-token-invalido`; (c) JWT de una sesión propia de QA-USR-002 con
  el primer carácter de su firma cambiado por otro válido del alfabeto Base64URL.
  La preparación de la variante (c) no depende de AUT-SES-001.
- **Acción:** `GET /usuario/me` en cada variante, sin otro mecanismo de autenticación.
- **Verificar:** las tres responden 401 y ninguna devuelve datos protegidos.
- **Evidencia:** un subregistro saneado por variante. El caso se aprueba solo si
  todas pasan; no almacenar el JWT íntegro ni siquiera si está alterado.

### 08. AUT-SES-003 — Renovación normal

- **Datos:** sesión propia nueva de QA-USR-003; denominar a su refresh `R0` solo
  en memoria.
- **Acción:** `POST /auth/refresh` con `{"refreshToken":"<R0>"}`.
- **Verificar:** HTTP 200 y pareja de sesión utilizable; el nuevo refresh es
  distinto de R0; el access permite consultar el perfil correcto. R0 queda
  revocado y vinculado a su reemplazo; intentar renovarlo vuelve a dar 401.
- **Evidencia:** respuestas saneadas y estados/IDs de refresh sin sus valores ni
  hashes. No exigir que dos JWT emitidos dentro del mismo segundo tengan textos
  distintos si ese requisito de unicidad no está definido.

### 09. AUT-SES-004 — Reutilización del refresh anterior

- **Datos:** sesión nueva independiente de QA-USR-004. Rotar su refresh una vez
  como preparación y anotar solo los IDs y conteos.
- **Acción:** volver a enviar el refresh ya rotado.
- **Verificar:** HTTP 401, sin tokens en la respuesta ni otra fila de refresh
  creada por la repetición. La preparación no se toma de AUT-SES-003.
- **Evidencia:** respuesta saneada y conteos antes/después. Registrar por separado
  la revocación adicional de sesiones si se observa; no cambiar el esperado para
  encubrir una emisión nueva.

### 10. MAS-DET-002 — Acceso directo a mascota ajena

- **Datos y variantes:** QA-USR-005 intenta consultar Toby QA-MAS-001; QA-USR-006
  intenta consultar Nala QA-MAS-002. Cada actor obtiene una sesión propia nueva.
  Sus homónimos propios son QA-MAS-014 y QA-MAS-015, respectivamente.
- **Control previo:** cada propietario debe poder consultar su mascota propia
  por ID (200). Esto evita aprobar un servidor que rechace absolutamente todo.
- **Acción:** `GET /mascotas/<UUID_ajeno>` con la sesión del actor correspondiente.
- **Verificar:** HTTP 403 `ACCESO_DENEGADO` en ambas variantes, sin ficha, historial,
  datos de propietario ni cambios en la mascota. Un 500 o una respuesta vacía 200
  no equivale a autorización denegada. Si se decide ocultar existencia con 404,
  primero actualizar y aprobar ese contrato; no aceptarlo silenciosamente aquí.
- **Evidencia:** actor por ID de fixture, UUID objetivo, control propio y errores
  saneados. No guardar los encabezados con tokens.

## Exclusiones conscientes

- **MAS-LIS-001** requiere mascotas propias **y accesos aceptados**. El seed no
  tiene accesos compartidos y el servicio actual solo lista propias. No reducir
  ese caso a un listado propio y marcarlo aprobado. Se abordará con cuidado compartido.
- MAS-DET-001 incluye acciones según rol; no se aprueba entero por usar un detalle
  propio como control de preparación de MAS-DET-002.
- Estados pendientes/reenvío, simultaneidad de refresh, vencimientos, límites y
  validaciones completas quedan para tandas posteriores. No se presumen aprobados.
- Calendario, notificaciones, historial de ocurrencias y UI/Selenium quedan fuera.

## Registro, fallos y cierre

Usar `../registro_resultados.md`. Una fila por caso e intento, con capa API y
referencia a la ficha. No completar filas al preparar este documento.

Si una precondición falla, no marcar el caso dependiente como aprobado. Si se
ejecutó la acción y su resultado contradice el esperado, es **Fallida**; si no se
pudo llegar a ejecutarla por un impedimento externo, **Bloqueada** con motivo.
No cambiar el backend ni el seed para hacer pasar un caso durante la tanda.

El smoke se considera satisfactorio únicamente con sus 10 casos aprobados en
la capa API y evidencia suficiente; casos bloqueados/no ejecutados no cuentan
como aprobados. Un fallo crítico detiene la ampliación a otras tandas, aunque se
pueden terminar comprobaciones independientes y seguras para completar el diagnóstico.
Conservar resultados e incidencias antes de restaurar datos y registrar el cierre.
