# Defectos consolidados de Historial Clínico

Solo defectos observados. Funciones ausentes y permisos pendientes se registran como bloqueos, con su motivo en la evidencia y en el cierre. Interfaz probada con Selenium.

| Defecto | Casos | Estado | Hallazgo |
|---|---|---|---|
| [DEF-ECL-VAL-FECHA_NO_PARSEABLE](#def-ecl-val-fecha_no_parseable) | ECL-REG-002 | Abierto | Una fecha no interpretable devuelve HTTP 500 |
| [DEF-ECL-VAL-FECHA_INEXISTENTE](#def-ecl-val-fecha_inexistente) | ECL-REG-002 | Abierto | Una fecha inexistente devuelve HTTP 500 |
| [DEF-ECL-VAL-NOMBRE_256](#def-ecl-val-nombre_256) | ECL-REG-002 | Abierto | Un nombre de evento de 256 caracteres devuelve HTTP 500 |
| [DEF-ECL-VAL-DOSIS_101](#def-ecl-val-dosis_101) | ECL-REG-002 | Abierto | Una dosis de 101 caracteres devuelve HTTP 500 |
| [DEF-ECL-VAL-OBSERVACIONES_256](#def-ecl-val-observaciones_256) | ECL-REG-002 | Abierto | Observaciones de 256 caracteres devuelven HTTP 500 |
| [DEF-ECL-PESO-NO_NUMERICO](#def-ecl-peso-no_numerico) | ECL-REG-007 | Abierto | Un peso no numérico devuelve HTTP 500 |

## DEF-ECL-VAL-FECHA_NO_PARSEABLE

**Estado:** Abierto. **Casos:** ECL-REG-002.

Una fecha no interpretable devuelve HTTP 500.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:23.426079893Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.

La evidencia contiene los datos ficticios, solicitud y respuesta saneadas; su ficha `../entorno.json` identifica las imágenes y las versiones del ejecutor. Un dato inválido no debe persistirse ni convertirse en error interno.

## DEF-ECL-VAL-FECHA_INEXISTENTE

**Estado:** Abierto. **Casos:** ECL-REG-002.

Una fecha inexistente devuelve HTTP 500.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:23.821562184Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.

La evidencia contiene los datos ficticios, solicitud y respuesta saneadas; su ficha `../entorno.json` identifica las imágenes y las versiones del ejecutor. Un dato inválido no debe persistirse ni convertirse en error interno.

## DEF-ECL-VAL-NOMBRE_256

**Estado:** Abierto. **Casos:** ECL-REG-002.

Un nombre de evento de 256 caracteres devuelve HTTP 500.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:24.266700723Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.
- [ECL-SELENIUM-REGISTRO-20261110T033809948Z-7e1f43__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261110T033809948Z-7e1f43/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-11-10T03:38:20.188506161Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.

La evidencia contiene los datos ficticios, solicitud y respuesta saneadas; su ficha `../entorno.json` identifica las imágenes y las versiones del ejecutor. Un dato inválido no debe persistirse ni convertirse en error interno.

## DEF-ECL-VAL-DOSIS_101

**Estado:** Abierto. **Casos:** ECL-REG-002.

Una dosis de 101 caracteres devuelve HTTP 500.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:24.653138613Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.
- [ECL-SELENIUM-REGISTRO-20261110T033809948Z-7e1f43__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261110T033809948Z-7e1f43/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-11-10T03:38:20.539644132Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.

La evidencia contiene los datos ficticios, solicitud y respuesta saneadas; su ficha `../entorno.json` identifica las imágenes y las versiones del ejecutor. Un dato inválido no debe persistirse ni convertirse en error interno.

## DEF-ECL-VAL-OBSERVACIONES_256

**Estado:** Abierto. **Casos:** ECL-REG-002.

Observaciones de 256 caracteres devuelven HTTP 500.

- [ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:16:25.014979141Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.
- [ECL-SELENIUM-REGISTRO-20261110T033809948Z-7e1f43__ECL-REG-002__01](evidencias/ECL-SELENIUM-REGISTRO-20261110T033809948Z-7e1f43/ECL-REG-002__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-11-10T03:38:20.863547235Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002`.

La evidencia contiene los datos ficticios, solicitud y respuesta saneadas; su ficha `../entorno.json` identifica las imágenes y las versiones del ejecutor. Un dato inválido no debe persistirse ni convertirse en error interno.

## DEF-ECL-PESO-NO_NUMERICO

**Estado:** Abierto. **Casos:** ECL-REG-007.

Un peso no numérico devuelve HTTP 500.

- [ECL-SELENIUM-PESOS-20261025T011723347Z-90942e__ECL-REG-007__01](evidencias/ECL-SELENIUM-PESOS-20261025T011723347Z-90942e/ECL-REG-007__01/resultado.json). Esperado: "HTTP 400 o 422". Observado: {"http":500,"respuesta":{"codigo":"ERROR_INTERNO","mensaje":"Ocurrio un error inesperado","timestamp":"2026-10-25T01:17:31.212368997Z"}}. Reproducción: `node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-007`.

La evidencia contiene los datos ficticios, solicitud y respuesta saneadas; su ficha `../entorno.json` identifica las imágenes y las versiones del ejecutor. Un dato inválido no debe persistirse ni convertirse en error interno.
