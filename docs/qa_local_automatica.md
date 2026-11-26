# Actualización automática del QA local

## Activación

Ejecutar desde esta copia con PowerShell 7.

```powershell
pwsh -File scripts/Configurar-QALocal.ps1
```

La configuración identifica el proyecto Docker `mismascotas-qa`. Se guarda en `.qa-local/config.json`, excluido de Git. El instalador activa `.githooks/post-commit` únicamente en el worktree actual y registra la ruta de PowerShell para que funcione también desde un cliente Git.

## Flujo después de un commit

1. Git termina de crear el commit. Si cambió `backend/`, `DB/`, `docker/qa-db/` o los scripts de este flujo, se inicia la actualización.
2. Se extrae el commit con `git archive` y se compilan backend y DB con la etiqueta `local-<SHA>`. La imagen incorpora el SHA como etiqueta OCI.
3. Se descubre el Compose activo mediante las etiquetas del contenedor y se conservan sus archivos, configuración, puertos y servicios. Un override local selecciona las imágenes nuevas.
4. Se respalda PostgreSQL en formato binario y se verifica la copia con SHA-256.
5. Se recrean DB y backend sobre el volumen existente. Flyway aplica las migraciones pendientes al arrancar el backend.
6. Se espera la salud de ambos servicios y se comprueban el SHA de las imágenes y el nombre del volumen. El resultado exitoso queda en `.qa-local/ultima-actualizacion.json`.

El commit se construye desde su contenido guardado. Los cambios pendientes y archivos sin seguimiento se incorporarán cuando formen parte de otro commit. Una actualización por proyecto se ejecuta a la vez.

## Reintento y resultado

```powershell
pwsh -File scripts/Actualizar-QALocal.ps1
Get-Content .qa-local/ultima-actualizacion.json
```

Cada ejecución deja su log y respaldo en una subcarpeta de `.qa-local/`. Si Docker está apagado o falla la compilación, el hook informa el error y el commit queda creado. Una compilación fallida termina antes de recrear servicios. Si el fallo ocurre durante las migraciones o el arranque, revisar el log y corregir la causa antes de reintentar. Restaurar una base requiere decidir qué respaldo recuperar.

La compilación usa el Dockerfile actual y las comprobaciones locales verifican arranque, salud, revisión y volumen. Las pruebas funcionales y de regresión se ejecutan en Maven/Postman y en GitHub Actions según el flujo existente.

## Migraciones

Agregar una migración nueva para cada cambio de esquema, por ejemplo `V13__nombre_del_cambio.sql`. Flyway valida su historial y ejecuta las pendientes sobre los datos actuales. La imagen DB prepara las instalaciones nuevas. El volumen persistente se actualiza mediante Flyway.

## Verificación del flujo

Se ejecutó el hook mediante commits reales en un repositorio temporal con su propio proyecto Docker. Se verificaron el SHA servido, una migración V13 de prueba y la conservación de las seis cuentas, quince mascotas y quince recordatorios del seed. Un archivo inválido sin seguimiento quedó fuera de la compilación del commit. Un commit documental omitió la actualización. Una compilación fallida informó el error y mantuvo el backend anterior. El siguiente commit válido volvió a actualizar las imágenes y terminó saludable.

## Desactivación en esta copia

```powershell
git config --worktree --unset core.hooksPath
git config --worktree --unset qalocal.powershell
```
