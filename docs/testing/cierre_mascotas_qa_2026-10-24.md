# Cierre de las tandas de Mascotas — 24 de octubre de 2026

Los 19 casos existentes tienen resultado: **11 Aprobada, 2 Fallida y 6 Bloqueada** en las tandas completas finales. La capa de cada caso queda indicada en el historial. No hay casos pendientes de intentar en ese cierre.

## Resultado por caso

| Caso | Resultado | Evidencia |
|---|---|---|
| MAS-REG-001 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-001__01/resultado.json) |
| MAS-REG-002 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-002__01/resultado.json) |
| MAS-REG-003 | Fallida | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-003__01/resultado.json) |
| MAS-REG-004 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-004__01/resultado.json) |
| MAS-REG-005 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-REG-005__01/resultado.json) |
| MAS-LIS-001 | Bloqueada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-LIS-001__01/resultado.json) |
| MAS-LIS-003 | Bloqueada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-LIS-003__01/resultado.json) |
| MAS-LIS-004 | Bloqueada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-LIS-004__01/resultado.json) |
| MAS-DET-001 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-DET-001__01/resultado.json) |
| MAS-DET-002 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-DET-002__01/resultado.json) |
| MAS-EDI-001 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-EDI-001__01/resultado.json) |
| MAS-EDI-002 | Bloqueada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-EDI-002__01/resultado.json) |
| MAS-EDI-003 | Aprobada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-EDI-003__01/resultado.json) |
| MAS-EDI-004 | Fallida | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-EDI-004__01/resultado.json) |
| MAS-ELI-003 | Bloqueada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-ELI-003__01/resultado.json) |
| MAS-ELI-004 | Bloqueada | [Resultado](evidencias/MAS-API-20261025T002313850Z-7bd9f5/MAS-ELI-004__01/resultado.json) |
| MAS-LIS-002 | Aprobada | [Resultado](evidencias/MAS-WEB-20261025T002445326Z-4b912d/MAS-LIS-002__01/resultado.json) |
| MAS-ELI-001 | Aprobada | [Resultado](evidencias/MAS-WEB-20261025T002445326Z-4b912d/MAS-ELI-001__01/resultado.json) |
| MAS-ELI-002 | Aprobada | [Resultado](evidencias/MAS-WEB-20261025T002445326Z-4b912d/MAS-ELI-002__01/resultado.json) |

## Defectos observados

- **MAS-REG-003:** once defectos de validación. Nombre/especie/raza/notas excedidos, fecha inexistente, unidad de edad inválida y peso fuera de rango provocan HTTP 500. Fecha futura, edad negativa, peso cero y peso negativo se aceptan y persisten. Las variantes válidas de texto, fecha y edad pasan.
- **MAS-EDI-004:** PUT básico cambia peso de 10 a 99 kg mientras el historial conserva la medición de 10 kg.

Lista completa con reproducciones y vínculos: [defectos_mascotas.md](defectos_mascotas.md). No se corrigió el backend.

## Bloqueos

MAS-LIS-001, MAS-LIS-003, MAS-LIS-004, MAS-EDI-002, MAS-ELI-003 y MAS-ELI-004 permanecen bloqueados por funciones de acceso compartido aún ausentes. No se simulan con usuarios ajenos y no se marcan aprobados. La aceptación, revocación y perfiles Familia/Cuidador deben implementarse antes de retomar esos casos.

## Historial y revisión de la instrumentación

Se agregaron 39 filas al historial: dos tandas API de 16 casos, una tanda Web interrumpida de tres casos, la tanda Web completada de tres y un smoke Postman de un caso. Las filas previas se conservaron. No son 39 casos distintos: son intentos de los mismos 19 casos.

La primera tanda marcó diferencias de precisión en creadoEn que se descartaron como defectos del producto. Se corrigió el comparador y se repitió API. La primera ejecución Web se interrumpió por el paso de argumentos del ejecutor: se registró un bloqueo de instrumentación y dos casos no ejecutados. La siguiente tanda Web aprobó los tres. Las revisiones y evidencias originales permanecen enlazadas en sus corridas.

La colección importable se ejecutó también en Newman: MAS-REG-001 aprobó seis aserciones. Ese smoke certifica su capa API y no reemplaza la evidencia SQL de la tanda completa.

## Integridad y entregables

Las tandas completas finales conservaron idéntica huella SQL del seed original antes/después. Se usaron fixtures propios, con limpieza lógica por API; se conservaron las cuentas, auditoría, correos y filas lógicas para inspección. No se restauró el volumen ni se cambiaron imágenes. Las fichas contienen los IDs de imágenes; el commit de origen del build sigue sin verificarse.

Colección y entorno: carpeta postman, archivos «MisMascotas Mascotas QA». [Guía de ejecución](mascotas_postman.md). Los resultados y las capturas no contienen credenciales, tokens ni códigos 2FA. Actualizar los vínculos de pruebas_qa.ods para ver las filas nuevas.
