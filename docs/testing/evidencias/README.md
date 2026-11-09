# Evidencias de las corridas QA

Cada subcarpeta identifica una corrida. `entorno.json` y `esquema.sql` son evidencia
de **preparación**, no resultados de casos ni garantía de integridad de todo el seed.

La ficha se genera con `scripts/qa/Preparar-CorridaQA.ps1`; no modifica la base ni
el CSV de ejecuciones. Repetir la captura usa otro ID y no sobrescribe la anterior.
La referencia del código de las imágenes puede estar sin verificar; sus IDs reales
sí se registran, sin atribuirles por suposición el commit actual del repositorio.

Antes de compartir material, aplicar `../registro_resultados.md`. Nunca incluir
contraseñas, JWT, refresh tokens, códigos 2FA, archivos `.env` ni dumps con datos.
El volcado aquí guardado contiene solamente la estructura y no ejecuta cambios
en PostgreSQL. No ejecutarlo como si fuera un procedimiento de restauración QA.
