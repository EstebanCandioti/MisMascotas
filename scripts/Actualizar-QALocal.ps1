[CmdletBinding()]
param([switch]$DesdeHook)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$carpetaLocal = Join-Path $raiz '.qa-local'
$bloqueo = $null
$disponible = $false
$transcripcion = $false

function Ejecutar {
    param([string]$Programa, [string[]]$Argumentos)
    & $Programa @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "$Programa terminó con código $LASTEXITCODE" }
}
function LeerJsonDocker {
    param([string[]]$Argumentos)
    $resultado = Ejecutar docker $Argumentos
    return (($resultado -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable)
}

try {
    $configuracion = Join-Path $carpetaLocal 'config.json'
    if (-not (Test-Path -LiteralPath $configuracion)) { throw 'Ejecutá scripts/Configurar-QALocal.ps1 primero' }
    $proyecto = (Get-Content -Raw -LiteralPath $configuracion | ConvertFrom-Json).proyecto
    if ($proyecto -notmatch '^[a-z0-9][a-z0-9_-]+$') { throw 'Nombre de proyecto QA inválido' }
    $revision = (Ejecutar git @('-C', $raiz, 'rev-parse', 'HEAD')).Trim()
    if ($DesdeHook) {
        & git -C $raiz rev-parse --verify 'HEAD^' *> $null
        $archivos = if ($LASTEXITCODE -eq 0) {
            Ejecutar git @('-C', $raiz, 'diff', '--name-only', 'HEAD^', 'HEAD')
        } else {
            Ejecutar git @('-C', $raiz, 'ls-tree', '-r', '--name-only', 'HEAD')
        }
        if (-not ($archivos -match '^(backend/|DB/|docker/qa-db/|scripts/(Actualizar|Configurar)-QALocal\.ps1$|\.githooks/)')) {
            Write-Host 'QA local: el commit no requiere actualizar las imágenes.'
            exit 0
        }
    }
    $bloqueo = [Threading.Mutex]::new($false, "Local\MisMascotas-QALocal-$proyecto")
    try { $disponible = $bloqueo.WaitOne(0) }
    catch [Threading.AbandonedMutexException] { $disponible = $true }
    if (-not $disponible) { throw 'Ya hay una actualización de este QA en curso. Reintentá al terminar.' }

    $ejecucion = Join-Path $carpetaLocal (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
    New-Item -ItemType Directory -Path $ejecucion -Force | Out-Null
    Start-Transcript -LiteralPath (Join-Path $ejecucion 'actualizacion.log') | Out-Null
    $transcripcion = $true
    $contenedor = @(LeerJsonDocker @('inspect', "$proyecto-backend-1"))[0]
    if ($contenedor.Config.Labels.'com.docker.compose.project' -ne $proyecto) { throw 'Proyecto Docker inesperado' }
    $directorioCompose = $contenedor.Config.Labels.'com.docker.compose.project.working_dir'
    $archivosCompose = @($contenedor.Config.Labels.'com.docker.compose.project.config_files'.Split(',') |
        Where-Object { (Split-Path -Leaf $_) -ne 'compose.ci-local.json' })
    if (-not $archivosCompose.Count) { throw 'Faltan los archivos Compose originales' }
    $argumentosCompose = @('compose', '-p', $proyecto, '--project-directory', $directorioCompose)
    foreach ($archivo in $archivosCompose) {
        if (-not (Test-Path -LiteralPath $archivo)) { throw "No existe el archivo Compose $archivo" }
        $argumentosCompose += @('-f', $archivo)
    }
    $original = LeerJsonDocker ($argumentosCompose + @('config', '--format', 'json'))
    if ($original.services.backend.environment.FLYWAY_ENABLED -ne 'true') { throw 'QA debe tener Flyway habilitado' }
    if (-not $original.services.backend.healthcheck) { throw 'Falta el healthcheck del backend' }
    $db = @(LeerJsonDocker @('inspect', "$proyecto-db-1"))[0]
    $volumen = @($db.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' -and $_.Type -eq 'volume' })
    if ($db.Config.Labels.'com.docker.compose.project' -ne $proyecto -or $volumen.Count -ne 1) {
        throw 'No se pudo verificar el volumen de PostgreSQL QA'
    }
    if ($db.State.Status -ne 'running') { throw 'Iniciá PostgreSQL QA antes de actualizar' }

    $archivoZip = Join-Path $ejecucion 'commit.zip'
    Ejecutar git @('-C', $raiz, 'archive', '--format=zip', "--output=$archivoZip", $revision)
    $fuentes = Join-Path $ejecucion 'fuentes'
    Expand-Archive -LiteralPath $archivoZip -DestinationPath $fuentes
    $backendImagen = "mismascotas-backend:local-$revision"
    $dbImagen = "mismascotas-db-qa:local-$revision"
    $etiqueta = "org.opencontainers.image.revision=$revision"
    Write-Host "Compilando QA local del commit $revision"
    Ejecutar docker @('build', '--label', $etiqueta, '-t', $backendImagen, (Join-Path $fuentes 'backend'))
    Ejecutar docker @('build', '--label', $etiqueta, '-f', (Join-Path $fuentes 'docker/qa-db/Dockerfile'), '-t', $dbImagen, $fuentes)

    $dump = Join-Path $ejecucion 'antes.dump'
    Ejecutar docker @('exec', "$proyecto-db-1", 'sh', '-ec',
        'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/qa-local.dump; pg_restore --list /tmp/qa-local.dump >/dev/null')
    Ejecutar docker @('cp', "$($proyecto)-db-1:/tmp/qa-local.dump", $dump)
    $hashDocker = ((Ejecutar docker @('exec', "$proyecto-db-1", 'sha256sum', '/tmp/qa-local.dump')) -split '\s+')[0]
    if ((Get-FileHash -LiteralPath $dump).Hash.ToLowerInvariant() -ne $hashDocker) { throw 'El respaldo copiado no coincide' }

    $override = Join-Path $carpetaLocal 'compose.ci-local.json'
    $candidato = Join-Path $ejecucion 'compose.ci-local.json'
    @{
        services = @{
            backend = @{ image = $backendImagen; pull_policy = 'never' }
            db = @{ image = $dbImagen; pull_policy = 'never' }
        }
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $candidato -Encoding utf8NoBOM
    $propuesto = LeerJsonDocker ($argumentosCompose + @('-f', $candidato, 'config', '--format', 'json'))
    foreach ($servicio in @('backend', 'db')) {
        foreach ($campo in @('image', 'pull_policy')) {
            [void]$original.services.$servicio.Remove($campo)
            [void]$propuesto.services.$servicio.Remove($campo)
        }
    }
    if (($original | ConvertTo-Json -Depth 60 -Compress) -cne ($propuesto | ConvertTo-Json -Depth 60 -Compress)) {
        throw 'La actualización cambiaría otra configuración de Compose'
    }
    if (Test-Path -LiteralPath $override) {
        Copy-Item -LiteralPath $override -Destination (Join-Path $ejecucion 'compose.anterior.json')
    }
    Copy-Item -LiteralPath $candidato -Destination $override
    $despliegue = $argumentosCompose + @('-f', $override)
    # Flyway migra el volumen existente durante el arranque.
    Ejecutar docker ($despliegue + @('up', '-d', '--pull', 'never', '--no-deps', '--wait', '--wait-timeout', '150', 'db'))
    Ejecutar docker ($despliegue + @('up', '-d', '--pull', 'never', '--no-deps', '--wait', '--wait-timeout', '150', 'backend'))
    foreach ($servicio in @('db', 'backend')) {
        $actual = @(LeerJsonDocker @('inspect', "$proyecto-$servicio-1"))[0]
        if ($actual.State.Health.Status -ne 'healthy') { throw "$servicio no está saludable" }
        $imagen = @(LeerJsonDocker @('image', 'inspect', $actual.Image))[0]
        if ($imagen.Config.Labels.'org.opencontainers.image.revision' -ne $revision) {
            throw "$servicio no está ejecutando el commit esperado"
        }
    }
    $dbActual = @(LeerJsonDocker @('inspect', "$proyecto-db-1"))[0]
    $volumenActual = @($dbActual.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
    if ($volumenActual[0].Name -ne $volumen[0].Name) { throw 'Cambió el volumen de PostgreSQL' }
    $version = Ejecutar docker @('exec', "$proyecto-db-1", 'sh', '-ec',
        'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -XAt -v ON_ERROR_STOP=1 -c "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;"')
    @{
        commit = $revision; proyecto = $proyecto; flyway = "$version"
        fechaUtc = [DateTime]::UtcNow.ToString('o'); respaldo = $dump
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $carpetaLocal 'ultima-actualizacion.json') -Encoding utf8NoBOM
    Write-Host "QA actualizado al commit $revision. Flyway $version. Backend y DB saludables."
} catch {
    Write-Host "QA LOCAL: FALLÓ LA ACTUALIZACIÓN. $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Reintentá con pwsh -File scripts/Actualizar-QALocal.ps1. Revisá .qa-local para el log y el respaldo."
    exit 1
} finally {
    if ($transcripcion) { Stop-Transcript | Out-Null }
    if ($bloqueo) {
        if ($disponible) { $bloqueo.ReleaseMutex() }
        $bloqueo.Dispose()
    }
}
