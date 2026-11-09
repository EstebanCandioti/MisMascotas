param([switch]$ConfirmarRestablecimiento)

# Solo necesita PowerShell y Docker Desktop. Sin el switch no modifica datos.
$ErrorActionPreference = 'Stop'
$raizQA = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$proyectoQA = 'mismascotas-web-testing'
$volumenQA = 'mismascotas_web_testing_db'
$composeQA = @('compose', '--project-directory', $raizQA, '--env-file',
    (Join-Path $raizQA '.env.web-testing'), '-f',
    (Join-Path $raizQA 'docker-compose.web-testing.yml'))

function Invocar-Docker {
    param([string[]]$Argumentos)
    $salidaDocker = & docker @Argumentos
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker no pudo completar la operacion. No continuar manualmente sin revisar el error.'
    }
    return $salidaDocker
}

function Obtener-ContenedorQA {
    param([string]$Servicio)
    $idsQA = @(Invocar-Docker ($composeQA + @('ps', '-a', '-q', $Servicio)))
    if ($idsQA.Count -ne 1 -or [string]::IsNullOrWhiteSpace($idsQA[0])) {
        throw "Se requiere exactamente un contenedor de $Servicio. Iniciar el stack QA primero."
    }
    $inspeccionQA = @(Invocar-Docker @('inspect', $idsQA[0]) | ConvertFrom-Json)[0]
    $etiquetasQA = $inspeccionQA.Config.Labels
    if ($etiquetasQA.'com.docker.compose.project' -ne $proyectoQA -or
        $etiquetasQA.'com.docker.compose.service' -ne $Servicio -or
        [IO.Path]::GetFullPath($etiquetasQA.'com.docker.compose.project.working_dir') -ne $raizQA) {
        throw "Identidad o directorio de $Servicio distinto al entorno QA esperado."
    }
    return $inspeccionQA
}

function Obtener-VariablesQA {
    param($Contenedor)
    $variablesQA = @{}
    foreach ($entradaQA in $Contenedor.Config.Env) {
        $partesQA = $entradaQA -split '=', 2
        $variablesQA[$partesQA[0]] = $partesQA[1]
    }
    return $variablesQA
}

$mutexQA = New-Object Threading.Mutex($false, 'Local\MisMascotas-QA-Restablecer')
$bloqueadoQA = $false
$backendDetenidoQA = $false
$codificacionAnteriorQA = $OutputEncoding
try {
    $bloqueadoQA = $mutexQA.WaitOne(0)
    if (-not $bloqueadoQA) { throw 'Ya hay otro restablecimiento QA en curso.' }
    $baseQA = Obtener-ContenedorQA 'db-web-testing'
    $backendQA = Obtener-ContenedorQA 'backend-web-testing'
    $mailpitQA = Obtener-ContenedorQA 'mailpit-web-testing'
    if (-not $baseQA.State.Running -or $baseQA.State.Health.Status -ne 'healthy' -or
        -not $mailpitQA.State.Running -or $mailpitQA.State.Health.Status -ne 'healthy') {
        throw 'La base QA y Mailpit deben estar saludables antes de continuar.'
    }
    $montajesQA = @($baseQA.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
    if ($montajesQA.Count -ne 1 -or $montajesQA[0].Type -ne 'volume' -or $montajesQA[0].Name -ne $volumenQA) {
        throw 'El contenedor no utiliza exclusivamente el volumen de datos QA esperado.'
    }
    $volumenInspeccionQA = @(Invocar-Docker @('volume', 'inspect', $volumenQA) | ConvertFrom-Json)[0]
    if ($volumenInspeccionQA.Labels.'com.docker.compose.project' -ne $proyectoQA -or
        $volumenInspeccionQA.Labels.'com.docker.compose.volume' -ne 'web_testing_db') {
        throw 'Las etiquetas del volumen no corresponden a QA.'
    }
    $variablesBaseQA = Obtener-VariablesQA $baseQA
    $variablesBackendQA = Obtener-VariablesQA $backendQA
    if ($variablesBackendQA.DB_URL -notmatch '^jdbc:postgresql://db-web-testing(?::5432)?/([^?]+)(?:\?.*)?$' -or
        $Matches[1] -ne $variablesBaseQA.POSTGRES_DB) {
        throw 'El backend no apunta a la misma base del servicio QA.'
    }
    if ($variablesBackendQA.MAIL_HOST -ne 'mailpit-web-testing' -or
        $variablesBackendQA.MAIL_PORT -ne '1025' -or
        $variablesBackendQA.MAIL_SMTP_AUTH -ne 'false' -or
        $variablesBackendQA.MAIL_USERNAME -or $variablesBackendQA.MAIL_PASSWORD) {
        throw 'SMTP no aislado en Mailpit; se rechaza el restablecimiento.'
    }
    $puertosMailpitQA = @($mailpitQA.NetworkSettings.Ports.'8025/tcp')
    if ($puertosMailpitQA.Count -ne 1 -or $puertosMailpitQA[0].HostIp -ne '127.0.0.1') {
        throw 'Mailpit debe estar publicado solamente en la interfaz local.'
    }
    $urlMailpitQA = 'http://127.0.0.1:' + $puertosMailpitQA[0].HostPort
    $null = Invoke-RestMethod -Uri "$urlMailpitQA/api/v1/messages" -TimeoutSec 15
    $archivoSeedQA = Join-Path $raizQA 'DB\qa\restablecer-seed.sql'
    $sqlQA = [IO.File]::ReadAllText($archivoSeedQA, [Text.Encoding]::UTF8)
    $hashSeedQA = (Get-FileHash -LiteralPath $archivoSeedQA -Algorithm SHA256).Hash
    Write-Host "Destino validado: $proyectoQA / $volumenQA / $($variablesBaseQA.POSTGRES_DB)"
    if (-not $ConfirmarRestablecimiento) {
        Write-Host 'Sin cambios. Usar -ConfirmarRestablecimiento para reemplazar los datos QA por el seed.'
        return
    }

    # Se detiene solo el backend QA; el volumen y los contenedores se conservan.
    if ($backendQA.State.Running) {
        $null = Invocar-Docker @('stop', '--time', '20', $backendQA.Id)
        $backendDetenidoQA = $true
    }
    $carpetaEvidenciasQA = Join-Path $raizQA '.qa\restauraciones'
    $null = New-Item -ItemType Directory -Path $carpetaEvidenciasQA -Force
    $nombreCorridaQA = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N')
    $respaldoInternoQA = "/tmp/mismascotas-qa-$nombreCorridaQA.dump"
    $respaldoLocalQA = Join-Path $carpetaEvidenciasQA "$nombreCorridaQA.dump"
    $null = Invocar-Docker @('exec', $baseQA.Id, 'sh', '-c',
        'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f "$1"', 'qa', $respaldoInternoQA)
    $null = Invocar-Docker @('cp', "$($baseQA.Id):$respaldoInternoQA", $respaldoLocalQA)
    if (-not (Test-Path -LiteralPath $respaldoLocalQA) -or (Get-Item -LiteralPath $respaldoLocalQA).Length -eq 0) {
        throw 'No se genero un respaldo utilizable. Se cancela la carga.'
    }
    Write-Host "Respaldo anterior: $respaldoLocalQA"

    $OutputEncoding = New-Object Text.UTF8Encoding($false)
    $entradaSqlQA = "SET mismascotas.entorno = 'qa-web';`n" + $sqlQA
    $resultadoSqlQA = $entradaSqlQA | & docker exec -i $baseQA.Id sh -c `
        'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q -t -A -v ON_ERROR_STOP=1'
    if ($LASTEXITCODE -ne 0) {
        throw 'Carga SQL fallida: la transaccion se revierte; no se limpia Mailpit.'
    }
    $resumenQA = ($resultadoSqlQA -join "`n").Trim() | ConvertFrom-Json
    $resumenQA | Add-Member -NotePropertyName hash_seed_sha256 -NotePropertyValue $hashSeedQA
    $resumenQA | Add-Member -NotePropertyName volumen -NotePropertyValue $volumenQA
    $resumenQA | Add-Member -NotePropertyName respaldo_anterior -NotePropertyValue $respaldoLocalQA
    $resumenQA | Add-Member -NotePropertyName mailpit_limpio -NotePropertyValue $false
    $commitQA = $null
    $cambiosLocalesQA = $null
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $salidaGitQA = & git -C $raizQA rev-parse HEAD 2>$null
        if ($LASTEXITCODE -eq 0) {
            $commitQA = ($salidaGitQA -join '').Trim()
            $estadoGitQA = & git -C $raizQA status --porcelain 2>$null
            if ($LASTEXITCODE -eq 0) { $cambiosLocalesQA = [bool]$estadoGitQA }
        }
    }
    $resumenQA | Add-Member -NotePropertyName commit_codigo -NotePropertyValue $commitQA
    $resumenQA | Add-Member -NotePropertyName codigo_con_cambios_locales -NotePropertyValue $cambiosLocalesQA
    $archivoEvidenciaQA = Join-Path $carpetaEvidenciasQA "$nombreCorridaQA.json"
    [IO.File]::WriteAllText($archivoEvidenciaQA, ($resumenQA | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    # Mailpit no participa de la transaccion SQL. Solo se limpia tras COMMIT.
    try {
        $null = Invoke-RestMethod -Uri "$urlMailpitQA/api/v1/messages" -Method Delete -TimeoutSec 15
        $bandejaQA = Invoke-RestMethod -Uri "$urlMailpitQA/api/v1/messages" -TimeoutSec 15
        if ($bandejaQA.total -ne 0) { throw 'La bandeja no quedo vacia.' }
        $resumenQA.mailpit_limpio = $true
    } catch {
        throw 'El seed YA se confirmo en la base, pero fallo la limpieza de Mailpit. Revisar la evidencia antes de reintentar.'
    } finally {
        [IO.File]::WriteAllText($archivoEvidenciaQA, ($resumenQA | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    }
    Write-Host "Seed cargado: 6 cuentas, 15 mascotas, 20 eventos clinicos y 15 recordatorios."
    Write-Host "Evidencia: $archivoEvidenciaQA"
} finally {
    $OutputEncoding = $codificacionAnteriorQA
    try {
        if ($backendDetenidoQA) {
            # Se reinicia exactamente el contenedor validado, sin recrear servicios
            # ni volver a interpretar una configuracion Compose que pudiera cambiar.
            $null = Invocar-Docker @('start', $backendQA.Id)
            $limiteBackendQA = [DateTime]::UtcNow.AddSeconds(45)
            do {
                $saludBackendQA = @(Invocar-Docker @('inspect', $backendQA.Id) | ConvertFrom-Json)[0]
                if ($saludBackendQA.State.Health.Status -eq 'healthy') { break }
                if (-not $saludBackendQA.State.Running) { throw 'El backend QA se detuvo al reiniciar.' }
                Start-Sleep -Milliseconds 500
            } while ([DateTime]::UtcNow -lt $limiteBackendQA)
            if ($saludBackendQA.State.Health.Status -ne 'healthy') {
                throw 'El backend QA no alcanzo estado saludable; revisar la evidencia y sus logs.'
            }
        }
    } finally {
        if ($bloqueadoQA) { $mutexQA.ReleaseMutex() }
        $mutexQA.Dispose()
    }
}
