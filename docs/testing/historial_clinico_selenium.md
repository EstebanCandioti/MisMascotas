# Historial Clínico: ejecución con Selenium

## Fuente y alcance

Los 17 casos `ECL-*` de `casos_prueba.csv` se mantienen con sus IDs, prioridades y
resultados esperados. El plan y la matriz están en
[historial_clinico_qa_01.md](tandas/historial_clinico_qa_01.md). La automatización de
interfaz utiliza **Selenium WebDriver con Edge**, sin Playwright.

Los tratamientos se representan mediante el tipo actual `MEDICACION`; los otros
tipos del contrato son `VACUNA`, `CONSULTA_VETERINARIA` y `PESO`. No se agrega un
tipo ficticio para simular una funcionalidad ni se llama al calendario historial.

## Preparación

Desde `D:\AAAIES\MisMascotas-front`, comprobar los cuatro servicios saludables del
Compose QA `mismascotas-web-testing`, con backend 8080, Mailpit 8025 y frontend 3000.
El ejecutor valida proyecto, servicio, volumen, puertos y aislamiento SMTP antes de
crear fixtures. No reconstruir imágenes entre casos.

Requiere Node.js con fetch y Selenium local. En esta PC quedó instalado en `.qa`,
sin dependencias globales nuevas:

```powershell
npm.cmd install --prefix .qa/selenium-runtime --no-audit --no-fund selenium-webdriver@4.51.0
```

Edge debe estar instalado. Selenium Manager resuelve el controlador compatible;
puede necesitar red durante la primera preparación. Cada caso funcional abre una
sesión headless propia y la cierra al terminar. La zona horaria del navegador se
configura en Argentina sin modificar la zona horaria del sistema.

## Ejecutar las tandas

```powershell
node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda registro
node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda pesos
node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda consulta
node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda permisos
```

También puede usarse `--tanda completa`. Un reintento de un solo caso crea otra
corrida con un ID nuevo:

```powershell
node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda registro --caso ECL-REG-002
```

Registro selecciona REG 001–004; Pesos, REG 005–009; Consulta, LIS 001–004; Permisos,
EDI 001–002 y ELI 001–002. No se ejecutan en paralelo. Las esperas se basan en
elementos/estados reales y tienen límites explícitos. El código de salida es 1 si
hay fallos o bloqueos y 2 si se interrumpe el ejecutor.

## Qué se comprueba

Las altas válidas se completan en los formularios con Selenium. Se comprueban la
tarjeta visible, datos específicos del tipo, fecha, identidad del autor y fila SQL.
Las mediciones conservan IDs propios; se comprueba el orden por fecha y el peso
actual después de una carga reciente o histórica. Se prueban también las fronteras
válidas de peso 0,01 y 300 kg.

Las validaciones incluyen campos requeridos, fecha inexistente/no interpretable,
fecha futura y longitudes del formulario/esquema. Los pesos incluyen nulo, cero,
negativo, menor al mínimo, mayor al máximo y texto no numérico. La validación HTML
se prueba desde el formulario. Los datos que ese formulario no permite enviar se
envían directamente a la API desde Selenium y se etiquetan como tales; deben
rechazarse sin persistencia ni cambios de peso, con errores de cliente controlados.

El historial se contrasta con sus eventos reales y los de otra mascota para
comprobar ausencia de omisiones/cruces. Tipo y limpiar se operan mediante botones.
El gráfico y el filtro por fecha requieren sus funciones reales; ver el listado
de puntos de peso no aprueba el gráfico.

## Operaciones pendientes

- **REG-005:** no hay un contrato específico de PUT de carga inicial que cree la
  primera medición. El PUT de perfil básico no representa ese flujo. La primera
  medición por POST sí se usa y comprueba en los otros casos de peso.
- **LIS-002:** si falta el filtro por fecha, se conserva evidencia del filtro por
  tipo y limpiar y el caso completo queda Bloqueada.
- **LIS-003:** sin gráfico disponible, permanece Bloqueada, aunque se verifiquen
  las dos mediciones que forman su precondición.
- **LIS-004:** requiere acceso compartido aceptado todavía no disponible.
- **EDI-001 y ELI-001:** se respetan los permisos expresamente pendientes del CSV;
  no se interpreta la existencia de un botón/endpoint como decisión aprobada.

Los intentos directos de un tercero o sin credencial en REG-003, EDI-002 y ELI-002
sí están definidos y se ejecutan, verificando 403/401, ausencia de filtración y
conservación exacta de mascota/eventos/peso.

## Registro y evidencia

Después de cada caso se escribe `resultado.json` en su carpeta de evidencia y se
agrega una fila a `docs/testing/ejecuciones_pruebas.csv`. Se mantienen las columnas
y los cuatro estados existentes; el estado Bloqueada conserva el motivo y las
partes que sí pudieron comprobarse. Una variante fallida hace fallar todo el caso.
Las fechas del CSV usan `-03:00`; los JSON conservan inicio/fin precisos de cada caso.

Las carpetas son `docs/testing/evidencias/ECL-SELENIUM-<tanda>-<fecha>-<sufijo>/`:

- `entorno.json`: imágenes reales, salud, hashes, Node y Selenium; origen del build
  no verificado si no puede demostrarse, sin inferirlo del checkout.
- `<caso>__01/resultado.json`: esperado, observado, actores y UUID ficticios,
  acciones de Selenium, solicitudes directas de API y snapshots SQL.
- Capturas PNG: antes/después, formulario/historial, filtros y funciones ausentes.
- `resumen.json`: resultados de todos los casos registrados en la corrida.

Los defectos observados se consolidan en `defectos_historial_clinico.md` y su JSON
con ID, caso, intento, esperado/observado, reproducción y enlace a la evidencia.
Los bloqueos por falta de función o de decisión se mantienen separados de los
defectos. No se cambia la aplicación para alterar el resultado.

## Datos y conservación

Se usan actores `qa.ecl.<uuid>@mismascotas.test` y mascotas nuevas. Las sesiones se
preparan con 2FA real desde Mailpit, sin probar nuevamente la interfaz de Auth.
No se usan ni se alteran las mascotas del seed; se compara su huella antes/después.
Las consultas SQL son de solo lectura.

Se guarda respaldo del CSV antes de cada tanda y se detectan cambios concurrentes
para no perder filas. Tras preservar resultados, solo se retiran lógicamente las
mascotas del propio caso y se cierran las sesiones. No se editan/eliminan eventos,
no se restauran volúmenes y no se borran cuentas, mensajes ni auditoría.

Contraseñas, 2FA, JWT, refresh tokens, cookies y almacenamiento de sesión no forman
parte del material exportado. Los datos de salud son ficticios. En Calc, actualizar
los vínculos de `pruebas_qa.ods` para consultar las filas nuevas.
