# Registro de resultados y evidencias

## Archivos y separación de responsabilidades

- `casos_prueba.csv`: catálogo maestro de qué se debe verificar. No escribir aquí
  resultados, estados de ejecución ni evidencias de una tanda.
- `ejecuciones_pruebas.csv`: historial único de resultados, una fila por ejecución
  de un caso. Una repetición agrega otra fila; no se sobrescribe el intento anterior.
- `pruebas_qa.ods`: vista de los dos CSV para LibreOffice Calc. Incluye las hojas
  visibles **Ejecuciones** y **Casos**, filtros, encabezados fijos, texto ajustado
  y colores según estado o prioridad. No constituye otro historial editable.
- `tandas/smoke_api_01.md`: primera selección y sus instrucciones. Es un plan, no
  una prueba ejecutada ni un catálogo alternativo.
- `evidencias/<id_corrida>/`: ficha del entorno y material saneado de esa corrida.
  La preparación crea `entorno.json` y `esquema.sql`; no crea resultados.

Las copias de trabajo de un chat no se usan como otro historial. Registrar las
ejecuciones en `docs/testing/` del repositorio activo `D:\AAAIES\MisMascotas-front`.

## Consultar y actualizar la planilla

1. Abrir `pruebas_qa.ods` en LibreOffice Calc y aceptar la actualización de vínculos.
2. Consultar **Ejecuciones** para los resultados o **Casos** para el catálogo.
   Las primeras columnas muestran lo principal; versiones, datos y referencias
   técnicas quedan a la derecha. Filtrar desde las flechas del encabezado.
3. Agregar o corregir datos en el CSV correspondiente, conservando su estructura.
   No escribir resultados sobre las fórmulas de las vistas ni sobre las hojas
   internas `CSV_Ejecuciones` y `CSV_Casos`, que permanecen ocultas.
4. Si la planilla ya está abierta, ir a **Editar > Enlaces a archivos externos**
   (o **Enlaces externos**), seleccionar ambos CSV y pulsar **Actualizar**.
   También se pueden actualizar al volver a abrir el archivo. Guardar la planilla
   para conservar la última vista consultada.

No necesita macros ni un programa adicional. La actualización es al abrir o
mediante el botón indicado, no un monitoreo continuo del archivo mientras está
abierto. Si se rechaza la actualización o falta un CSV, puede verse la copia
guardada anteriormente: actualizar correctamente antes de usarla para un reporte.

Los vínculos son relativos: mantener la planilla y ambos CSV en la misma carpeta.
Para compartirla o moverla, llevar esos tres archivos juntos. El formato elegido
es `.ods` porque esta PC usa LibreOffice. Convertirlo a `.xlsx` exige volver a
verificar los vínculos y el refresco en Microsoft Excel.

Las vistas están preparadas para **5000 ejecuciones y 1000 casos**. El contador
lee todos los registros de la importación y aparece un aviso **AMPLIAR VISTA** si
se supera la capacidad; ampliar fórmulas, tabla, filtros y formato antes de usar
la vista como listado completo. El CSV sigue conservando todos sus registros.
Las filas reservadas vacías no representan pruebas ejecutadas.

La fecha visible es numérica y se presenta en hora argentina con precisión de
minutos. Registrar `fecha_hora` con offset `-03:00`; el CSV y la importación interna
conservan el ISO original completo, incluidos segundos y fracciones. Ordenar
por fecha desde el filtro, sin modificar los datos de origen.

El 24 de octubre de 2026 se verificó en LibreOffice 26.2 que el archivo importa
240 casos y una ejecución real. En una copia descartable se comprobó el agregado
y retiro de una fila, la edición de observaciones con `;`, ñ y salto de línea,
la conservación de estilos y la portabilidad de los vínculos al mover la carpeta.
Esas filas de verificación no se incorporaron al historial real.

Referencia de uso: [Editar enlaces externos, ayuda de LibreOffice](https://help.libreoffice.org/latest/es/text/shared/01/02180000.html).

## Cómo completar las columnas existentes

| Columna | Contenido |
|---|---|
| `id_ejecucion` | Identificador único: `<id_corrida>__<id_caso>__<intento>`. No reutilizarlo. |
| `id_caso` | ID exacto existente en el catálogo. No inventar otro por una variante. |
| `fecha_hora` | Momento real de ejecución en ISO 8601 con zona; por ejemplo, `2026-10-22T22:00:00-03:00`. |
| `version_codigo` | Commit abreviado por componente, solo si el origen del build está verificado: `back@<commit> / web@<commit>`. Si no, usar `back img:<12 caracteres> / web img:<12 caracteres>`. Las referencias completas quedan en `entorno.json`. |
| `entorno` | Proyecto y capa; por ejemplo, `mismascotas-web-testing / API`. La corrida se identifica en `id_ejecucion` y en la ruta de evidencia. |
| `datos_utilizados` | Versión del seed y referencias cortas a fixtures. En altas, conservar email e ID ficticios en la evidencia enlazada. Sin contraseña ni tokens. |
| `resultado` | Uno de los cuatro estados definidos abajo. |
| `observaciones` | Una o dos frases con comprobaciones y diferencias concretas. En pruebas API, aclarar `UI sin probar`. El detalle va en la evidencia, sin repetir toda la ficha. |
| `evidencia` | Ruta relativa al repositorio del material del caso y su ficha; sin rutas dependientes de la PC de un ejecutor. |
| `incidencia` | ID/enlace de incidencia si hay un defecto. Si no aplica, dejar vacío; no crear un ID sin documentarlo. |
| `ejecutor` | Persona o agente/herramienta que efectivamente ejecutó el caso. No atribuir al usuario pruebas que no hizo. |

El CSV conserva UTF-8 y separador `;`. Al abrirlo en Excel, tratar los IDs como
texto y conservar el formato de fechas con zona. Si un campo contiene punto y
coma, comillas o salto de línea, entrecomillarlo correctamente; preferir un editor
CSV que lo resuelva. Revisar el diff antes de guardar cambios amplios.

Las referencias abreviadas deben identificar inequívocamente una versión dentro
de la ficha. Ampliarlas si colisionan. No reemplazar el origen desconocido de una
imagen por el commit actual del checkout. La ejecución piloto conserva esa
incertidumbre y sus IDs completos de imagen en la ficha original.

## Significado de resultados

- **Aprobada:** acción ejecutada, verificaciones previstas satisfechas y evidencia
  suficiente para la capa indicada. No implica aprobación de otras capas o módulos.
- **Fallida:** se ejecutó y el comportamiento observado contradice el esperado.
- **Bloqueada:** no se pudo ejecutar la acción por una precondición o dependencia
  indisponible. Registrar cuál y cómo se comprobó.
- **No ejecutada:** caso incluido en el alcance de una corrida que finalmente no
  se intentó. No implica que la funcionalidad funcione ni que esté rota.

No rellenar ahora filas de ejemplo ni resultados de preparación. Si un caso
incluye varias variantes obligatorias, registrar sus evidencias por separado y
aprobar el caso solo cuando todas pasen. Un fallo en una variante hace fallar
esa ejecución del caso; una repetición requiere otro intento.

## Evidencias e incidencias

Dentro de cada corrida, usar una carpeta `<id_caso>__<intento>/` para resultados
observados, estados HTTP, verificaciones SQL de solo lectura y capturas necesarias.
Identificar fecha, actor, solicitud sin secretos, esperado, observado y subvariantes.
No alcanza una captura de un 200 si el caso exige comprobar identidad o persistencia.

Sustituir por `[omitido]` contraseñas, valores y hashes de tokens, códigos 2FA,
encabezados Authorization/cookies y secretos. No guardar mensajes Mailpit completos
ni exportar entornos Postman con variables de sesión. Para verificar efectos de
autenticación, consultar únicamente IDs, fechas, flags y conteos necesarios.

Para cada defecto, documentar: ID, resumen, caso/intento, entorno/versiones, datos,
pasos reproducibles, esperado, observado, impacto y enlace a evidencia saneada.
Puede guardarse como `incidencia.md` dentro de la evidencia del caso hasta disponer
de un tracker. No enviar automáticamente incidencias a servicios externos.

La evidencia saneada puede versionarse junto al registro. Revisarla antes de un
commit/push; capturas o archivos pesados pueden adjuntarse al PR y enlazarse desde
el CSV. Los respaldos con datos de `.qa/restauraciones/` permanecen fuera de Git y
no se copian a esta carpeta. No borrar evidencia para reiniciar los datos.

## Cierre de una tanda

Resumir seleccionadas, aprobadas, fallidas, bloqueadas y no ejecutadas sin contar
reintentos como más casos distintos. Registrar incidencias abiertas y alcance real.
No presentar resultados de la API como pruebas de Selenium ni una aprobación global
del producto. Si cambia la versión durante la tanda, abrir otra corrida y conservar
la ficha anterior. Restaurar QA solo después de preservar los resultados y evidencias.
