# Pruebas de Mascotas: automatización, ejecución y seguimiento

## Objetivo y alcance

Automatizar los **19 casos MAS existentes** del catálogo `casos_prueba.csv`, conservar
sus IDs y resultados esperados, ejecutar lo disponible y registrar cada resultado
en `ejecuciones_pruebas.csv`. No se modifican el backend, la interfaz ni el catálogo
para hacer pasar una prueba. Las funciones ausentes se registran como Bloqueada.

La colección dedicada es `postman/MisMascotas Mascotas QA.postman_collection.json`.
La colección anterior de Auth + Mascotas + Auditoría se conserva como referencia;
para estos casos usar la colección nueva con los IDs del CSV.

## Arquitectura y decisiones

- `scripts/qa/mascotas-casos.cjs` contiene preparación, acciones y verificaciones
  comunes al ejecutor y a Postman. Así se evita mantener dos interpretaciones del CSV.
- `scripts/qa/Generar-MascotasPostman.cjs` incorpora esa biblioteca a los scripts de
  colección. Conserva el esperado del catálogo en cada solicitud y rechaza cambios
  de IDs o cantidad que todavía no tengan implementación.
- `scripts/qa/Ejecutar-MascotasQA.cjs` ejecuta API y SQL, captura versiones reales
  de imágenes, guarda evidencia saneada y agrega una fila por caso inmediatamente
  después de completarlo. Los reintentos abren otra corrida y no sobrescriben filas.
- `scripts/qa/mascotas-ui.cjs` usa Playwright con Edge en un contexto aislado por caso
  para probar estado vacío, confirmación y cancelación. La sesión se prepara mediante
  2FA real capturado en Mailpit; no se falsifican JWT ni se exporta almacenamiento.
- Las consultas PostgreSQL se ejecutan con `BEGIN READ ONLY`. Se contrastan las
  instantáneas anteriores/posteriores para no confundir un HTTP favorable con
  persistencia o integridad. No hay inserciones SQL para inventar funciones ausentes.

## Tandas y cobertura por caso

| ID | Tanda | Verificación o impedimento |
|---|---|---|
| MAS-REG-001 | API | Alta, lectura persistida, una fila adicional y aislamiento de un segundo actor. |
| MAS-REG-002 | API | Nombre/especie vacíos y nulos, validación por campo y ausencia de filas parciales. |
| MAS-REG-003 | API | Límites exactos y excedidos de textos, edad desconocida, fecha válida/inexistente/futura, cuatro unidades de edad, edad negativa y pesos inválidos. |
| MAS-REG-004 | API | Cuenta Free propia con cinco mascotas, rechazo de sexta y conservación exacta de las cinco. |
| MAS-REG-005 | API | Cuenta Premium QA con más de cinco mascotas, alta adicional y conservación de sus mascotas previas. |
| MAS-LIS-001 | API bloqueada | Falta listado combinado con acceso compartido aceptado. |
| MAS-LIS-002 | Web | API vacía, mensaje sin errores y acción inicial que abre formulario. |
| MAS-LIS-003 | API bloqueada | Faltan invitación pendiente y aceptación. |
| MAS-LIS-004 | API bloqueada | Faltan revocación del acceso compartido y actualización de permisos. |
| MAS-DET-001 | API | Variante propietario: detalle completo e identidad. El catálogo admite propietario o usuario autorizado; los colaboradores se cubren mediante sus bloqueos específicos. Acciones visibles según roles no certificados por esta variante. |
| MAS-DET-002 | API | Detalle de mascota propia consultado por tercero: 403 sin datos de mascota. |
| MAS-EDI-001 | API | Edición del propietario persistida, eventos e historial SQL intactos. |
| MAS-EDI-002 | API bloqueada | Faltan perfiles Familia/Cuidador con acceso aceptado. |
| MAS-EDI-003 | API | Edición por tercero rechazada y mascota/historial sin cambios. |
| MAS-EDI-004 | API | PUT básico no debe sobrescribir el peso obtenido del historial. Se preservan las mediciones. |
| MAS-ELI-001 | Web | Confirmación previa sin DELETE, confirmación efectiva, ausencia del listado, detalle 404, fila lógica e historial retenidos. |
| MAS-ELI-002 | Web | Cancelar la confirmación sin DELETE, mascota accesible y relaciones intactas en SQL. |
| MAS-ELI-003 | API bloqueada | Faltan perfiles Familia/Cuidador aceptados para probar el borrado. |
| MAS-ELI-004 | API bloqueada | Falta pérdida efectiva de acceso compartido de cuentas vinculadas. La purga programada tampoco está implementada. |

Los límites de texto probados corresponden al esquema y al formulario actuales:
nombre 100, especie 50, raza 100, notas 255. La fecha futura, edad negativa y pesos
inválidos se contrastan también con las validaciones del formulario. No se inventa
un límite máximo de edad. La diferencia entre peso permitido por el formulario
(9999,99) y el evento PESO (300,00) permanece como contrato pendiente; esta tanda
no decide que 300,01 sea inválido en un alta por el solo contrato clínico.

## Ejecución y resultados

Desde `D:\AAAIES\MisMascotas-front`, con el Compose `mismascotas-web-testing`
saludable, sin reconstruir imágenes entre casos:

```powershell
node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api
node scripts/qa/Ejecutar-MascotasQA.cjs --tanda web
```

Para repetir un caso, indicar su tanda:

```powershell
node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003
node scripts/qa/Ejecutar-MascotasQA.cjs --tanda web --caso MAS-ELI-002
```

La tanda API tiene 16 casos, incluidos los seis bloqueos de acceso compartido; la
tanda Web tiene tres. Requiere Node con fetch, Docker CLI y acceso a los servicios
locales. Para UI requiere Playwright y Edge; el ejecutor encuentra el runtime
incluido en esta PC o acepta `MASCOTAS_PLAYWRIGHT_MODULE` con una instalación local.
La ausencia de una dependencia del ejecutor es un bloqueo de preparación, no un
defecto de la aplicación. El código de salida es 1 si hay fallos o bloqueos y 2 si
se interrumpe el ejecutor. Conservar los informes de intentos interrumpidos.

Estados del CSV: Aprobada, Fallida, Bloqueada y No ejecutada. Una aprobación se
limita a la capa que figura en la fila. Un error en una variante hace fallar el caso;
las demás variantes seguras continúan para reunir evidencia. Una excepción de red
o preparación no se convierte en defecto del producto sin evidencia suficiente.

## Evidencias y lista consolidada de defectos

Cada corrida usa `docs/testing/evidencias/MAS-<tanda>-<fecha>-<sufijo>/`:

- `entorno.json`: imágenes completas, salud, fechas, hashes de ejecutor/catálogo y
  huellas del seed antes/después. El origen del commit de cada build no se deduce
  del checkout actual.
- `<id_caso>__01/resultado.json`: inicio y fin reales, actores/email/UUID ficticios,
  solicitudes saneadas, esperados/observados, comprobaciones y snapshots SQL.
- Capturas PNG en los casos Web. Nunca se guardan códigos, JWT ni refresh tokens.
- `resumen.json`: estados y enlaces de todos los casos completados de la tanda.

`docs/testing/defectos_mascotas.md` es la lista consolidada para revisión humana.
`defectos_mascotas.json` conserva las ocurrencias que la generan. Cada defecto tiene
ID estable, caso, intento, esperado, observado, pasos de reproducción e impacto,
con enlace a la evidencia y sus versiones. Los bloqueos de funciones ausentes se
mantienen separados de los defectos observados; nunca se aprueban por documentación.
No se abre ni se envía nada a un servicio externo.

## Datos, aislamiento y limpieza

Se crean actores Free propios `qa.mas.<uuid>@mismascotas.test`. Premium usa la cuenta
QA documentada en `cuentas_qa.md` sin cambiar su plan ni sus mascotas existentes.
Solo se eliminan lógicamente las mascotas creadas por cada caso, mediante la API,
después de preservar las comprobaciones. Las altas inválidas que sean aceptadas se
registran como fallos y se retiran antes de la siguiente variante para aislar el cupo.
No se borran físicamente cuentas, eventos, auditoría ni correos. Se cierran las
sesiones temporales al finalizar. El seed original se compara mediante huella SQL;
no se restaura la base ni se borra el volumen.

El ejecutor guarda respaldo del historial antes de cada tanda y verifica que no
cambie concurrentemente antes de agregar una fila. Si detecta un cambio, conserva
la evidencia y detiene el agregado para evitar perder datos. Los CSV y la vista
`pruebas_qa.ods` mantienen sus columnas: actualizar vínculos en Calc para ver filas
nuevas. No se escriben resultados sobre el catálogo.

## Importación y uso en Postman

Importar la colección y `MisMascotas Mascotas QA.postman_environment.json`, seleccionar
**MisMascotas Mascotas QA local** y ejecutar una iteración. Para MAS-REG-005, cargar
la contraseña de la cuenta Premium QA en el valor local `qa_premium_password`; no
sincronizar ni exportar esa credencial. Los demás casos crean actores propios.

Cada solicitud representa su caso: el script prepara fixtures/2FA, la petición
principal ejecuta la acción y el post-response hace verificaciones adicionales.
Test Results identifica cada comprobación. Un bloqueo impide enviar una acción
sin precondiciones. Los tres casos Web requieren el ejecutor y se muestran bloqueados
en Postman. La colección evalúa API; no certifica SQL, UI ni actualiza archivos locales.
Para ejecutar y registrar resultados completos, usar el script de las tandas.

Para regenerar después de editar la biblioteca:

```powershell
node scripts/qa/Generar-MascotasPostman.cjs
```

Referencias: [scripts de Postman](https://learning.postman.com/latest-v-12/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-send-request),
[aislamiento en Playwright](https://playwright.dev/docs/browser-contexts),
[localizadores](https://playwright.dev/docs/locators).
