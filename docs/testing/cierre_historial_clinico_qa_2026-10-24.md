# Cierre de Historial Clínico con Selenium — 24 de octubre de 2026

Se ejecutaron cuatro tandas sobre los **17 casos ECL existentes**: **9 Aprobada, 2 Fallida y 6 Bloqueada**. Se agregaron 17 filas al historial, una por resultado, conservando las 112 anteriores.

Interfaz: Selenium WebDriver 4.51.0 y Microsoft Edge 156.0.4314.42, con navegador aislado por caso y zona horaria argentina. Las altas válidas se realizaron desde formularios; los intentos malformados o sin autorización se enviaron directamente a la API desde Selenium y se identifican con ese alcance. SQL se usó solo para observación.

## Resultado por caso

| Caso | Resultado | Evidencia |
|---|---|---|
| ECL-EDI-001 | Bloqueada | [Resultado](evidencias/ECL-SELENIUM-PERMISOS-20261025T011912682Z-582d26/ECL-EDI-001__01/resultado.json) |
| ECL-EDI-002 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-PERMISOS-20261025T011912682Z-582d26/ECL-EDI-002__01/resultado.json) |
| ECL-ELI-001 | Bloqueada | [Resultado](evidencias/ECL-SELENIUM-PERMISOS-20261025T011912682Z-582d26/ECL-ELI-001__01/resultado.json) |
| ECL-ELI-002 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-PERMISOS-20261025T011912682Z-582d26/ECL-ELI-002__01/resultado.json) |
| ECL-LIS-001 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-CONSULTA-20261025T011820104Z-35d74a/ECL-LIS-001__01/resultado.json) |
| ECL-LIS-002 | Bloqueada | [Resultado](evidencias/ECL-SELENIUM-CONSULTA-20261025T011820104Z-35d74a/ECL-LIS-002__01/resultado.json) |
| ECL-LIS-003 | Bloqueada | [Resultado](evidencias/ECL-SELENIUM-CONSULTA-20261025T011820104Z-35d74a/ECL-LIS-003__01/resultado.json) |
| ECL-LIS-004 | Bloqueada | [Resultado](evidencias/ECL-SELENIUM-CONSULTA-20261025T011820104Z-35d74a/ECL-LIS-004__01/resultado.json) |
| ECL-REG-001 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-001__01/resultado.json) |
| ECL-REG-002 | Fallida | [Resultado](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-002__01/resultado.json) |
| ECL-REG-003 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-003__01/resultado.json) |
| ECL-REG-004 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-REGISTRO-20261025T011612943Z-2d8a3f/ECL-REG-004__01/resultado.json) |
| ECL-REG-005 | Bloqueada | [Resultado](evidencias/ECL-SELENIUM-PESOS-20261025T011723347Z-90942e/ECL-REG-005__01/resultado.json) |
| ECL-REG-006 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-PESOS-20261025T011723347Z-90942e/ECL-REG-006__01/resultado.json) |
| ECL-REG-007 | Fallida | [Resultado](evidencias/ECL-SELENIUM-PESOS-20261025T011723347Z-90942e/ECL-REG-007__01/resultado.json) |
| ECL-REG-008 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-PESOS-20261025T011723347Z-90942e/ECL-REG-008__01/resultado.json) |
| ECL-REG-009 | Aprobada | [Resultado](evidencias/ECL-SELENIUM-PESOS-20261025T011723347Z-90942e/ECL-REG-009__01/resultado.json) |

## Funciones comprobadas

- Registro desde la interfaz de consulta, vacuna, medicación/tratamiento y peso con campos propios, fecha y autoría persistida.
- Validación de requeridos y fechas futuras, con conservación de datos ante rechazo.
- Nuevas mediciones, fronteras válidas 0,01 y 300 kg, conservación de los registros y orden cronológico.
- Carga tardía de una medición antigua: el peso actual sigue tomado de la fecha de medición más reciente.
- Historial completo y separado de otra mascota; filtros por tipo y opción Todos para limpiar.
- Registro, edición y eliminación directos por tercero o sin token: 403/401 y datos intactos.

## Fallos y defectos

**ECL-REG-002** falló por cinco respuestas HTTP 500: fecha no interpretable, fecha inexistente, nombre de 256 caracteres, dosis de 101 caracteres y observaciones de 256 caracteres. **ECL-REG-007** falló porque un peso no numérico también devuelve HTTP 500. Se esperaba validación de cliente controlada, no error interno. El historial y el peso conservaron su estado en todas esas variantes.

Los seis defectos tienen reproducción y evidencia en [defectos_historial_clinico.md](defectos_historial_clinico.md).

## Bloqueos que permanecen

| Caso | Impedimento |
|---|---|
| ECL-EDI-001 | El catálogo mantiene Pendiente de permisos para corrección permitida. Falta cerrar autorización y trazabilidad antes de ejecutar la mutación. |
| ECL-ELI-001 | El catálogo mantiene Pendiente de permisos para eliminación permitida y su semántica. No se ejecuta el borrado mientras siga pendiente. |
| ECL-LIS-002 | Se probaron tipo y limpiar, pero falta el filtro por fecha del catálogo. Caso parcial que permanece Bloqueada. |
| ECL-LIS-003 | No hay gráfico de evolución disponible para comprobar ejes y valores. El listado de pesos no sustituye al gráfico. |
| ECL-LIS-004 | Falta el acceso compartido aceptado de Familia/Cuidador en la aplicación. |
| ECL-REG-005 | No hay contrato específico de PUT de carga inicial que cree una medición histórica. El PUT del perfil básico no se sustituye por ese flujo; POST PESO se prueba en otros casos. |

ECL-LIS-002 conserva cinco comprobaciones satisfactorias de tipo/limpiar; ECL-LIS-003 conserva dos mediciones reales como precondición. Ninguna de esas partes se presenta como aprobación del filtro por fecha o del gráfico ausentes. El PUT del perfil no se usa para fingir la carga inicial definida en el catálogo. Los casos de modificación permitida siguen el estado «Pendiente de permisos» del CSV.

## Evidencia e integridad

Cada carpeta de caso conserva el resultado y las capturas correspondientes. Las fichas registran imágenes completas, salud, hashes del catálogo/ejecutor y versiones reales. El commit de origen del build no se infiere del checkout. Las cuatro tandas conservaron idéntica huella SQL del seed antes/después.

Se crearon actores y mascotas propios. Se retiraron solo las mascotas de los casos mediante borrado lógico y se cerraron las sesiones. Los eventos, cuentas, correos y auditoría de pruebas permanecen para inspección. No se editó ni eliminó ningún evento con permisos pendientes; no se restauró el volumen.

El CSV conserva UTF-8, separador punto y coma, columnas, fechas con offset -03:00 y el historial previo. Las evidencias exportadas no contienen credenciales, 2FA, JWT ni refresh tokens. Actualizar vínculos de pruebas_qa.ods para consultar las filas nuevas.

## Repetición

Guía y comandos: [historial_clinico_selenium.md](historial_clinico_selenium.md). Plan y cobertura: [historial_clinico_qa_01.md](tandas/historial_clinico_qa_01.md). El ejecutor registra una nueva fila y conserva los intentos anteriores.
