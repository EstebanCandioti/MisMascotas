# Defectos consolidados de Historial Clínico

Se observaron **6 defectos de validación** en ECL-REG-002 y ECL-REG-007 mediante Selenium/API directa, con consultas SQL de solo lectura. Todos devuelven HTTP 500 donde se espera un error de cliente controlado. El historial y el peso conservaron su estado durante esos rechazos.

Las funciones ausentes y los permisos pendientes se mantienen como bloqueos en el [cierre](cierre_historial_clinico_qa_2026-10-24.md); no se cuentan como defectos ejecutados. No se modificó el producto.

| Defecto | Casos | Estado | Hallazgo |
|---|---|---|---|
| [DEF-ECL-VAL-FECHA_NO_PARSEABLE](#def-ecl-val-fecha_no_parseable) | ECL-REG-002 | Abierto | Una fecha no interpretable devuelve HTTP 500 |
| [DEF-ECL-VAL-FECHA_INEXISTENTE](#def-ecl-val-fecha_inexistente) | ECL-REG-002 | Abierto | Una fecha inexistente devuelve HTTP 500 |
| [DEF-ECL-VAL-NOMBRE_256](#def-ecl-val-nombre_256) | ECL-REG-002 | Abierto | Un nombre de evento de 256 caracteres devuelve HTTP 500 |
| [DEF-ECL-VAL-DOSIS_101](#def-ecl-val-dosis_101) | ECL-REG-002 | Abierto | Una dosis de 101 caracteres devuelve HTTP 500 |
| [DEF-ECL-VAL-OBSERVACIONES_256](#def-ecl-val-observaciones_256) | ECL-REG-002 | Abierto | Observaciones de 256 caracteres devuelven HTTP 500 |
| [DEF-ECL-PESO-NO_NUMERICO](#def-ecl-peso-no_numerico) | ECL-REG-007 | Abierto | Un peso no numérico devuelve HTTP 500 |

## DEF-ECL-VAL-FECHA_NO_PARSEABLE

**Estado:** Abierto. **Severidad:** Media. **Caso:** ECL-REG-002.

Una fecha no interpretable devuelve HTTP 500. Una entrada inválida del usuario termina como error interno. Debe devolverse una validación controlada que permita corregir el dato.

Las instantáneas SQL de la variante conservaron historial y peso actual.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:23.426079893Z"}}.

**Solicitud reproducible:** `POST /mascotas/ded71044-772b-460f-b6f3-68e497aa8bbd/eventos-clinicos`, con el cuerpo ficticio siguiente y una sesión válida del propietario:

```json
{
  "tipo": "VACUNA",
  "fecha": "no-es-un-instante",
  "nombre": "Vacuna ficticia QA",
  "observaciones": "Sin uso clínico."
}
```

**Reproducción automatizada:** `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`. Los UUID, emails de los actores, capturas, pasos de Selenium y snapshots SQL están en el resultado enlazado; `../entorno.json` contiene las imágenes y la versión del ejecutor.

## DEF-ECL-VAL-FECHA_INEXISTENTE

**Estado:** Abierto. **Severidad:** Media. **Caso:** ECL-REG-002.

Una fecha inexistente devuelve HTTP 500. Una entrada inválida del usuario termina como error interno. Debe devolverse una validación controlada que permita corregir el dato.

Las instantáneas SQL de la variante conservaron historial y peso actual.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:23.821562184Z"}}.

**Solicitud reproducible:** `POST /mascotas/ded71044-772b-460f-b6f3-68e497aa8bbd/eventos-clinicos`, con el cuerpo ficticio siguiente y una sesión válida del propietario:

```json
{
  "tipo": "VACUNA",
  "fecha": "2026-02-30T12:00:00Z",
  "nombre": "Vacuna ficticia QA",
  "observaciones": "Sin uso clínico."
}
```

**Reproducción automatizada:** `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`. Los UUID, emails de los actores, capturas, pasos de Selenium y snapshots SQL están en el resultado enlazado; `../entorno.json` contiene las imágenes y la versión del ejecutor.

## DEF-ECL-VAL-NOMBRE_256

**Estado:** Abierto. **Severidad:** Media. **Caso:** ECL-REG-002.

Un nombre de evento de 256 caracteres devuelve HTTP 500. Una entrada inválida del usuario termina como error interno. Debe devolverse una validación controlada que permita corregir el dato.

Las instantáneas SQL de la variante conservaron historial y peso actual.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:24.266700723Z"}}.

**Solicitud reproducible:** `POST /mascotas/ded71044-772b-460f-b6f3-68e497aa8bbd/eventos-clinicos`, con el cuerpo ficticio siguiente y una sesión válida del propietario:

```json
{
  "tipo": "VACUNA",
  "fecha": "2026-10-22T12:00:00-03:00",
  "nombre": "NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN",
  "observaciones": "Sin uso clínico."
}
```

**Reproducción automatizada:** `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`. Los UUID, emails de los actores, capturas, pasos de Selenium y snapshots SQL están en el resultado enlazado; `../entorno.json` contiene las imágenes y la versión del ejecutor.

## DEF-ECL-VAL-DOSIS_101

**Estado:** Abierto. **Severidad:** Media. **Caso:** ECL-REG-002.

Una dosis de 101 caracteres devuelve HTTP 500. Una entrada inválida del usuario termina como error interno. Debe devolverse una validación controlada que permita corregir el dato.

Las instantáneas SQL de la variante conservaron historial y peso actual.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:24.653138613Z"}}.

**Solicitud reproducible:** `POST /mascotas/ded71044-772b-460f-b6f3-68e497aa8bbd/eventos-clinicos`, con el cuerpo ficticio siguiente y una sesión válida del propietario:

```json
{
  "tipo": "MEDICACION",
  "fecha": "2026-10-22T12:00:00-03:00",
  "nombre": "Tratamiento ficticio QA",
  "dosis": "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD",
  "observaciones": "Sin indicación veterinaria."
}
```

**Reproducción automatizada:** `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`. Los UUID, emails de los actores, capturas, pasos de Selenium y snapshots SQL están en el resultado enlazado; `../entorno.json` contiene las imágenes y la versión del ejecutor.

## DEF-ECL-VAL-OBSERVACIONES_256

**Estado:** Abierto. **Severidad:** Media. **Caso:** ECL-REG-002.

Observaciones de 256 caracteres devuelven HTTP 500. Una entrada inválida del usuario termina como error interno. Debe devolverse una validación controlada que permita corregir el dato.

Las instantáneas SQL de la variante conservaron historial y peso actual.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:25.014979141Z"}}.

**Solicitud reproducible:** `POST /mascotas/ded71044-772b-460f-b6f3-68e497aa8bbd/eventos-clinicos`, con el cuerpo ficticio siguiente y una sesión válida del propietario:

```json
{
  "tipo": "VACUNA",
  "fecha": "2026-10-22T12:00:00-03:00",
  "nombre": "Vacuna ficticia QA",
  "observaciones": "OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO"
}
```

**Reproducción automatizada:** `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`. Los UUID, emails de los actores, capturas, pasos de Selenium y snapshots SQL están en el resultado enlazado; `../entorno.json` contiene las imágenes y la versión del ejecutor.

## DEF-ECL-PESO-NO_NUMERICO

**Estado:** Abierto. **Severidad:** Media. **Caso:** ECL-REG-007.

Un peso no numérico devuelve HTTP 500. Una entrada inválida del usuario termina como error interno. Debe devolverse una validación controlada que permita corregir el dato.

Las instantáneas SQL de la variante conservaron historial y peso actual.

- [ECL-SELENIUM-PESOS-20261025T011723347Z-90942e__ECL-REG-007__01](evidencias/ECL-SELENIUM-PESOS-20261025T011723347Z-90942e/ECL-REG-007__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:17:31.212368997Z"}}.

**Solicitud reproducible:** `POST /mascotas/4dd7caf9-d839-4818-9c50-c8bf4c42141f/eventos-clinicos`, con el cuerpo ficticio siguiente y una sesión válida del propietario:

```json
{
  "tipo": "PESO",
  "fecha": "2026-10-22T12:00:00-03:00",
  "valorPeso": "no-es-numero"
}
```

**Reproducción automatizada:** `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-007`. Los UUID, emails de los actores, capturas, pasos de Selenium y snapshots SQL están en el resultado enlazado; `../entorno.json` contiene las imágenes y la versión del ejecutor.

Para repetir manualmente una solicitud, usar una mascota activa y una sesión propia; los UUID de las evidencias pertenecen a fixtures retirados lógicamente al cerrar el caso. El comando automatizado crea datos nuevos y conserva el intento anterior. Las futuras correcciones requieren una nueva ejecución y evidencia; el HTTP 500 no pasa a ser el esperado.
