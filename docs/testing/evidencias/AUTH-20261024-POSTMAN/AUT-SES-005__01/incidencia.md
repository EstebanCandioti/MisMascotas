# Incidencia local: AUT-SES-005

Ejecución: AUTH-20261024-POSTMAN__AUT-SES-005__01. Entorno: mismascotas-web-testing / API.

Registrar una cuenta QA nueva, completar 2FA y enviar dos POST /auth/refresh concurrentes con el mismo refresh. Se esperaba como máximo una emisión y se observaron dos respuestas 200 con sesión. Permite reutilización concurrente; requiere investigar la protección transaccional.

Datos sintéticos propios de cada caso; no se conservaron los destinatarios individuales. Imagen histórica no registrada.

Evidencia: [resultado saneado](resultado.json), [ficha](../entorno.json). No se modificó el backend.
