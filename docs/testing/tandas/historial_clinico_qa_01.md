# Historial Clínico: plan de automatización con Selenium

## Objetivo y alcance

Ejecutar los 17 casos ECL existentes de `casos_prueba.csv`, con Selenium WebDriver
para la interfaz y comprobaciones HTTP/SQL complementarias. Cada resultado agrega
una fila al historial y conserva su evidencia. No se modifica el producto ni se
reinterpreta el catálogo para hacer pasar los casos.

## Decisiones y contratos

- Herramienta de interfaz: **Selenium**, con Edge aislado y esperas explícitas.
  No se importa ni se utiliza Playwright en esta automatización.
- Los actores y mascotas son ficticios y exclusivos de cada caso. Las sesiones
  se preparan con registro y 2FA real leído de Mailpit. Esto no evalúa la UI de Auth.
- Los formularios se operan con Selenium; fechas/horas nativas pueden completarse
  mediante `executeScript` y eventos del control, y el guardado se hace con un clic
  real de WebDriver. No se reemplaza una alta de interfaz por una llamada HTTP.
- Los intentos malformados y de autorización se envían por HTTP desde Selenium
  (`executeAsyncScript`), identificados como pruebas directas de API. No se los
  presenta como comprobación de botones o mensajes de interfaz.
- SQL se usa exclusivamente en transacciones de solo lectura para observar
  persistencia, peso actual, autoría y ausencia de cambios. Los fixtures se crean
  por la API; no se insertan eventos con SQL para simular una función implementada.

## Cobertura prevista

| Casos | Comprobación |
|---|---|
| ECL-REG-001 | Consulta creada desde el formulario; historial, fecha y autoría. |
| ECL-REG-002 | Campos requeridos, fechas inválidas/futuras y límites; errores y ausencia de persistencia. |
| ECL-REG-003 | Evento enviado por tercero sin acceso: rechazo y estado intacto. |
| ECL-REG-004 | Consulta, vacuna, medicación/tratamiento y peso: campos específicos y listado. |
| ECL-REG-005 | PUT inicial: bloqueado mientras no exista un contrato específico de carga inicial que cree historial. No se sustituye por POST ni por PUT del perfil básico. |
| ECL-REG-006 | Nueva medición distinta sin sobrescribir la anterior; peso actual correcto. |
| ECL-REG-007 | Pesos nulos, cero, negativos, no numéricos y fuera de 0,01–300 kg. |
| ECL-REG-008 | Dos fechas distintas y orden por fecha de medición. |
| ECL-REG-009 | Carga tardía de medición anterior: no debe reemplazar el peso más reciente. |
| ECL-LIS-001 | Historial variado, completo, ordenado y separado de otra mascota. |
| ECL-LIS-002 | Tipo y limpiar, comprobados con Selenium; el cierre del caso queda bloqueado si falta filtro por fecha. |
| ECL-LIS-003 | Dos pesos y presencia del gráfico; si falta la función, queda bloqueado y no se aprueba a partir del listado. |
| ECL-LIS-004 | Bloqueado por acceso compartido Familia/Cuidador ausente. |
| ECL-EDI-001 | Bloqueado: el catálogo conserva permisos pendientes para la corrección permitida. |
| ECL-EDI-002 | Edición directa por tercero/sin token: denegada y sin cambios. |
| ECL-ELI-001 | Bloqueado: el catálogo conserva permisos y semántica de borrado pendientes. |
| ECL-ELI-002 | Eliminación directa por tercero/sin token: denegada y evento conservado. |

La estrategia contiene reglas generales de Familia/Cuidador, pero los dos casos
de mutación permitida siguen expresamente «Pendiente de permisos» en el catálogo.
Se respeta esa restricción y la instrucción del usuario de dejar pendiente bloqueado.
No se infiere autorización para editar o eliminar por el solo hecho de existir el
endpoint o un botón en pantalla. Los rechazos de un tercero sí están definidos.

## Entrada, orden y salida

1. Verificar backend, PostgreSQL, Mailpit y frontend del proyecto Docker
   `mismascotas-web-testing`; asociar sus puertos e imágenes reales a la ficha.
2. Comprobar Selenium/Edge y registrar su versión. Capturar la huella del seed y
   el respaldo del CSV antes de iniciar la primera operación de un caso.
3. Ejecutar los casos seleccionados secuencialmente, con navegador y datos propios.
4. Guardar comprobaciones, respuestas saneadas, instantáneas SQL y capturas antes
   de limpiar; agregar una fila por resultado. Los reintentos conservan lo anterior.
5. Consolidar cada defecto observado con su caso, variante, versión y evidencia.
   Los bloqueos por función ausente o decisión pendiente quedan separados.
6. Comparar la huella del seed al terminar y verificar IDs únicos y enlaces reales
   del historial. No restaurar ni borrar volúmenes. Actualizar vínculos de Calc para
   consultar las filas nuevas.

## Límites y limpieza

Se conservan eventos, cuentas y auditoría de los fixtures para inspección. La
limpieza solo elimina lógicamente la mascota creada por el caso y cierra sesiones;
no invoca edición ni borrado de eventos con permisos pendientes. Nunca se exportan
contraseñas, códigos 2FA, JWT, refresh tokens, cookies ni almacenamiento de sesión.
La ejecución solo aprueba la capa probada; cada variante fallida hace fallar el caso.

Referencias: [primer script Selenium](https://www.selenium.dev/documentation/webdriver/getting_started/first_script/),
[esperas explícitas](https://www.selenium.dev/documentation/en/webdriver/waits/),
[JavaScript WebDriver](https://www.selenium.dev/selenium/docs/api/javascript/WebDriver.html).
