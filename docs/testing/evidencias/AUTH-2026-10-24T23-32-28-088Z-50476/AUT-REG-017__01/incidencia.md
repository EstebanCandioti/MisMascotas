# Incidencia local: AUT-REG-017

Ejecución: AUTH-2026-10-24T23-32-28-088Z-50476__AUT-REG-017__01. Entorno: mismascotas-web-testing / API.

Registrar una cuenta QA nueva y repetir el registro con el mismo email en mayúsculas y espacios externos. Se esperaba 409 por duplicado y se observó 400. La validación de formato parece ejecutarse antes de la normalización; requiere investigación del backend.

Datos sintéticos propios de cada caso; no se conservaron los destinatarios individuales. Imagen histórica no registrada.

Evidencia: [resultado saneado](resultado.json), [ficha](../entorno.json). No se modificó el backend.
