# Defectos consolidados de Mascotas

**12 defectos abiertos en dos casos:** MAS-REG-003 y MAS-EDI-004. Se observaron en dos tandas API. Las funciones compartidas ausentes permanecen bloqueadas y no se cuentan como defectos ejecutados. No se modificó el producto.

| Defecto | Caso | Comportamiento observado |
|---|---|---|
| [DEF-MAS-VAL-NOMBRE-DE-101-CARACTERES](#def-mas-val-nombre-de-101-caracteres) | MAS-REG-003 | Nombre de 101 caracteres provoca HTTP 500 |
| [DEF-MAS-VAL-ESPECIE-DE-51-CARACTERES](#def-mas-val-especie-de-51-caracteres) | MAS-REG-003 | Especie de 51 caracteres provoca HTTP 500 |
| [DEF-MAS-VAL-RAZA-DE-101-CARACTERES](#def-mas-val-raza-de-101-caracteres) | MAS-REG-003 | Raza de 101 caracteres provoca HTTP 500 |
| [DEF-MAS-VAL-NOTAS-DE-256-CARACTERES](#def-mas-val-notas-de-256-caracteres) | MAS-REG-003 | Notas de 256 caracteres provoca HTTP 500 |
| [DEF-MAS-VAL-FECHA-INEXISTENTE](#def-mas-val-fecha-inexistente) | MAS-REG-003 | Fecha inexistente provoca HTTP 500 |
| [DEF-MAS-VAL-FECHA-FUTURA](#def-mas-val-fecha-futura) | MAS-REG-003 | Fecha futura se acepta con HTTP 201 |
| [DEF-MAS-VAL-EDAD-NEGATIVA](#def-mas-val-edad-negativa) | MAS-REG-003 | Edad negativa se acepta con HTTP 201 |
| [DEF-MAS-VAL-UNIDAD-DE-EDAD-INVALIDA](#def-mas-val-unidad-de-edad-invalida) | MAS-REG-003 | Unidad de edad inválida provoca HTTP 500 |
| [DEF-MAS-VAL-PESO-CERO](#def-mas-val-peso-cero) | MAS-REG-003 | Peso cero se acepta con HTTP 201 |
| [DEF-MAS-VAL-PESO-NEGATIVO](#def-mas-val-peso-negativo) | MAS-REG-003 | Peso negativo se acepta con HTTP 201 |
| [DEF-MAS-VAL-PESO-FUERA-DEL-LIMITE-DEL-FORMULARIO](#def-mas-val-peso-fuera-del-limite-del-formulario) | MAS-REG-003 | Peso fuera del límite del formulario provoca HTTP 500 |
| [DEF-MAS-PESO-BASICO](#def-mas-peso-basico) | MAS-EDI-004 | El perfil básico sobrescribe el peso sin crear una medición |

## DEF-MAS-VAL-NOMBRE-DE-101-CARACTERES

**Estado:** Abierto. **Caso:** MAS-REG-003.

Nombre de 101 caracteres provoca HTTP 500. Una entrada inválida del usuario termina como error interno en lugar de una validación controlada.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Nombre de 101 caracteres» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-ESPECIE-DE-51-CARACTERES

**Estado:** Abierto. **Caso:** MAS-REG-003.

Especie de 51 caracteres provoca HTTP 500. Una entrada inválida del usuario termina como error interno en lugar de una validación controlada.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Especie de 51 caracteres» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-RAZA-DE-101-CARACTERES

**Estado:** Abierto. **Caso:** MAS-REG-003.

Raza de 101 caracteres provoca HTTP 500. Una entrada inválida del usuario termina como error interno en lugar de una validación controlada.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Raza de 101 caracteres» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-NOTAS-DE-256-CARACTERES

**Estado:** Abierto. **Caso:** MAS-REG-003.

Notas de 256 caracteres provoca HTTP 500. Una entrada inválida del usuario termina como error interno en lugar de una validación controlada.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Notas de 256 caracteres» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-FECHA-INEXISTENTE

**Estado:** Abierto. **Caso:** MAS-REG-003.

Fecha inexistente provoca HTTP 500. Una entrada inválida del usuario termina como error interno en lugar de una validación controlada.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Fecha inexistente» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-FECHA-FUTURA

**Estado:** Abierto. **Caso:** MAS-REG-003.

Fecha futura se acepta con HTTP 201. Se persiste una mascota con datos que deberían rechazarse; se retira lógicamente el fixture después de conservar evidencia.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Fecha futura» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-EDAD-NEGATIVA

**Estado:** Abierto. **Caso:** MAS-REG-003.

Edad negativa se acepta con HTTP 201. Se persiste una mascota con datos que deberían rechazarse; se retira lógicamente el fixture después de conservar evidencia.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Edad negativa» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-UNIDAD-DE-EDAD-INVALIDA

**Estado:** Abierto. **Caso:** MAS-REG-003.

Unidad de edad inválida provoca HTTP 500. Una entrada inválida del usuario termina como error interno en lugar de una validación controlada.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Unidad de edad inválida» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-PESO-CERO

**Estado:** Abierto. **Caso:** MAS-REG-003.

Peso cero se acepta con HTTP 201. Se persiste una mascota con datos que deberían rechazarse; se retira lógicamente el fixture después de conservar evidencia.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Peso cero» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-PESO-NEGATIVO

**Estado:** Abierto. **Caso:** MAS-REG-003.

Peso negativo se acepta con HTTP 201. Se persiste una mascota con datos que deberían rechazarse; se retira lógicamente el fixture después de conservar evidencia.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 201.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Peso negativo» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-VAL-PESO-FUERA-DEL-LIMITE-DEL-FORMULARIO

**Estado:** Abierto. **Caso:** MAS-REG-003.

Peso fuera del límite del formulario provoca HTTP 500. Una entrada inválida del usuario termina como error interno en lugar de una validación controlada.

- [MAS-API-20261025T002135696Z-63d28e__MAS-REG-003__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-REG-003__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json). Esperado: "400 o 422". Observado: 500.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-REG-003`. Identificar la variante «Peso fuera del límite del formulario» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## DEF-MAS-PESO-BASICO

**Estado:** Abierto. **Caso:** MAS-EDI-004.

El perfil básico sobrescribe el peso sin crear una medición. El perfil queda en 99 kg mientras el historial conserva su última medición de 10 kg.

- [MAS-API-20261025T002135696Z-63d28e__MAS-EDI-004__01](evidencias/MAS-API-20261025T002135696Z-63d28e/MAS-EDI-004__01/resultado.json). Esperado: "El peso permanece 10 kg o se rechaza la modificación básica". Observado: {"httpPut":200,"pesoActual":99}.
- [MAS-API-20261025T002313850Z-7bd9f5__MAS-EDI-004__01](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-EDI-004__01/resultado.json). Esperado: "El peso permanece 10 kg o se rechaza la modificación básica". Observado: {"httpPut":200,"pesoActual":99}.

**Reproducción:** ejecutar `node scripts/qa/Ejecutar-MascotasQA.cjs --tanda api --caso MAS-EDI-004`. Identificar la variante «PUT del perfil no sobrescribe peso actual» en `solicitudes` y `comprobaciones` del JSON enlazado. La ficha `../entorno.json` de cada evidencia contiene las imágenes ejecutadas. Se usan actores y mascotas ficticios; los UUID y emails están en cada resultado.

## Hallazgos descartados de la instrumentación

Estos tres IDs se conservan para explicar los fallos de la primera tanda. **No son defectos abiertos del producto.**

- **DEF-MAS-PERSISTENCIA** (MAS-REG-001, MAS-REG-003, MAS-REG-005): Se revisaron los valores esperados y observados: solo cambia el redondeo temporal por debajo de un milisegundo. Se corrigió el comparador y se repite la tanda. Los registros históricos de la primera ejecución se conservan, con esta aclaración. [Revisión del ejecutor](evidencias/MAS-API-20261025T002135696Z-63d28e/revision_ejecutor.md).
- **DEF-MAS-DETALLE** (MAS-DET-001): Se revisaron los valores esperados y observados: solo cambia el redondeo temporal por debajo de un milisegundo. Se corrigió el comparador y se repite la tanda. Los registros históricos de la primera ejecución se conservan, con esta aclaración. [Revisión del ejecutor](evidencias/MAS-API-20261025T002135696Z-63d28e/revision_ejecutor.md).
- **DEF-MAS-EDICION-AJENA** (MAS-EDI-003): Se revisaron los valores esperados y observados: solo cambia el redondeo temporal por debajo de un milisegundo. Se corrigió el comparador y se repite la tanda. Los registros históricos de la primera ejecución se conservan, con esta aclaración. [Revisión del ejecutor](evidencias/MAS-API-20261025T002135696Z-63d28e/revision_ejecutor.md).

Las correcciones futuras requieren repetir sus casos, anexar evidencia y actualizar el estado del defecto. No se cierran por cambiar el esperado ni por tener una corrida de otra capa aprobada.
