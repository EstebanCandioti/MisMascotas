param(
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$')]
    [string]$IdCorrida = ('SMK-API-01-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
)

# Captura preparatoria de solo lectura: no restaura datos, no inicia sesiones,
# no ejecuta casos y no escribe filas en ejecuciones_pruebas.csv.
$ErrorActionPreference = 'Stop'
$raizFichaQA = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$proyectoFichaQA = 'mismascotas-web-testing'
$composeFichaQA = @('compose', '--project-directory', $raizFichaQA, '--env-file',
    (Join-Path $raizFichaQA '.env.web-testing'), '-f',
    (Join-Path $raizFichaQA 'docker-compose.web-testing.yml'))
$codificacionFichaQA = $OutputEncoding
$carpetaFichaQA = Join-Path $raizFichaQA "docs\testing\evidencias\$IdCorrida"

function Invocar-DockerFichaQA {
    param([string[]]$Argumentos)
    $salidaFichaQA = & docker @Argumentos
    if ($LASTEXITCODE -ne 0) { throw 'Fallo Docker durante la captura de entorno QA.' }
    return $salidaFichaQA
}

function Variables-FichaQA {
    param($Contenedor)
    $variablesFichaQA = @{}
    foreach ($entradaFichaQA in $Contenedor.Config.Env) {
        $partesFichaQA = $entradaFichaQA -split '=', 2
        $variablesFichaQA[$partesFichaQA[0]] = $partesFichaQA[1]
    }
    return $variablesFichaQA
}

try {
    if (Test-Path -LiteralPath $carpetaFichaQA) {
        throw 'El ID de corrida ya existe. Usar otro para no sobrescribir evidencia.'
    }
    $contenedoresFichaQA = @{}
    $serviciosFichaQA = [ordered]@{}
    foreach ($servicioFichaQA in @('db-web-testing', 'backend-web-testing', 'frontend-web-testing', 'mailpit-web-testing')) {
        $idsFichaQA = @(Invocar-DockerFichaQA ($composeFichaQA + @('ps', '-a', '-q', $servicioFichaQA)))
        if ($idsFichaQA.Count -ne 1 -or [string]::IsNullOrWhiteSpace($idsFichaQA[0])) {
            throw "Se requiere un contenedor unico de $servicioFichaQA."
        }
        $contenedorFichaQA = @(Invocar-DockerFichaQA @('inspect', $idsFichaQA[0]) | ConvertFrom-Json)[0]
        $etiquetasFichaQA = $contenedorFichaQA.Config.Labels
        if ($etiquetasFichaQA.'com.docker.compose.project' -ne $proyectoFichaQA -or
            $etiquetasFichaQA.'com.docker.compose.service' -ne $servicioFichaQA -or
            [IO.Path]::GetFullPath($etiquetasFichaQA.'com.docker.compose.project.working_dir') -ne $raizFichaQA) {
            throw "Destino no reconocido como QA: $servicioFichaQA."
        }
        if (-not $contenedorFichaQA.State.Running -or $contenedorFichaQA.State.Health.Status -ne 'healthy') {
            throw "El servicio $servicioFichaQA debe estar saludable antes de preparar la corrida."
        }
        $imagenFichaQA = @(Invocar-DockerFichaQA @('image', 'inspect', $contenedorFichaQA.Image) | ConvertFrom-Json)[0]
        $puertosFichaQA = @()
        foreach ($puertoFichaQA in $contenedorFichaQA.NetworkSettings.Ports.PSObject.Properties) {
            foreach ($publicacionFichaQA in $puertoFichaQA.Value) {
                if ($null -ne $publicacionFichaQA) {
                    $puertosFichaQA += [ordered]@{puerto_contenedor=$puertoFichaQA.Name;
                        interfaz_host=$publicacionFichaQA.HostIp; puerto_host=$publicacionFichaQA.HostPort}
                }
            }
        }
        $serviciosFichaQA[$servicioFichaQA] = [ordered]@{
            contenedor_id=$contenedorFichaQA.Id
            imagen_id=$contenedorFichaQA.Image
            imagen_referencia=$contenedorFichaQA.Config.Image
            imagen_digests=@($imagenFichaQA.RepoDigests)
            imagen_creada_en=$imagenFichaQA.Created
            plataforma=($imagenFichaQA.Os + '/' + $imagenFichaQA.Architecture)
            revision_declarada=$imagenFichaQA.Config.Labels.'org.opencontainers.image.revision'
            origen_codigo_build='No verificado: no se deduce del commit actual ni solo de una etiqueta.'
            salud=$contenedorFichaQA.State.Health.Status
            puertos=$puertosFichaQA
        }
        $contenedoresFichaQA[$servicioFichaQA] = $contenedorFichaQA
    }

    $baseFichaQA = $contenedoresFichaQA['db-web-testing']
    $backendFichaQA = $contenedoresFichaQA['backend-web-testing']
    $mailFichaQA = $contenedoresFichaQA['mailpit-web-testing']
    $montajesFichaQA = @($baseFichaQA.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
    if ($montajesFichaQA.Count -ne 1 -or $montajesFichaQA[0].Name -ne 'mismascotas_web_testing_db' -or
        $montajesFichaQA[0].Type -ne 'volume') {
        throw 'El volumen no corresponde a la base QA esperada.'
    }
    $volumenFichaQA = @(Invocar-DockerFichaQA @('volume', 'inspect', 'mismascotas_web_testing_db') | ConvertFrom-Json)[0]
    if ($volumenFichaQA.Labels.'com.docker.compose.project' -ne $proyectoFichaQA) {
        throw 'El volumen no pertenece al proyecto QA.'
    }
    $variablesBaseFichaQA = Variables-FichaQA $baseFichaQA
    $variablesBackendFichaQA = Variables-FichaQA $backendFichaQA
    if ($variablesBackendFichaQA.DB_URL -notmatch '^jdbc:postgresql://db-web-testing(?::5432)?/([^?]+)(?:\?.*)?$' -or
        $Matches[1] -ne $variablesBaseFichaQA.POSTGRES_DB) {
        throw 'El backend y PostgreSQL no usan la misma base QA.'
    }
    if ($variablesBackendFichaQA.MAIL_HOST -ne 'mailpit-web-testing' -or
        $variablesBackendFichaQA.MAIL_PORT -ne '1025' -or
        $variablesBackendFichaQA.MAIL_USERNAME -or $variablesBackendFichaQA.MAIL_PASSWORD -or
        $variablesBackendFichaQA.MAIL_SMTP_AUTH -ne 'false') {
        throw 'El correo no esta aislado en Mailpit QA.'
    }
    $publicacionesMailFichaQA = @($mailFichaQA.NetworkSettings.Ports.'8025/tcp')
    if ($publicacionesMailFichaQA.Count -ne 1 -or $publicacionesMailFichaQA[0].HostIp -ne '127.0.0.1') {
        throw 'La bandeja Mailpit no esta limitada a localhost.'
    }
    $urlMailFichaQA = 'http://127.0.0.1:' + $publicacionesMailFichaQA[0].HostPort
    $bandejaFichaQA = Invoke-RestMethod -Uri "$urlMailFichaQA/api/v1/messages" -TimeoutSec 15

    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    $consultaFichaQA = @'
SELECT json_build_object(
    'version_motor', current_setting('server_version'), 'base', current_database(),
    'capturado_en', clock_timestamp(),
    'fecha_argentina', (clock_timestamp() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date,
    'usuarios', (SELECT count(*) FROM public.usuario),
    'mascotas', (SELECT count(*) FROM public.mascota),
    'eventos_clinicos', (SELECT count(*) FROM public.evento_clinico),
    'recordatorios', (SELECT count(*) FROM public.recordatorio),
    'codigos_2fa', (SELECT count(*) FROM public.codigo_verificacion),
    'refresh_tokens', (SELECT count(*) FROM public.refresh_token),
    'logs_auditoria', (SELECT count(*) FROM public.log_auditoria));
'@
    $salidaBaseFichaQA = $consultaFichaQA | & docker exec -i $baseFichaQA.Id sh -c `
        'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q -t -A -v ON_ERROR_STOP=1'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo leer la version y los conteos de la base QA.' }
    $resumenBaseFichaQA = ($salidaBaseFichaQA -join "`n").Trim() | ConvertFrom-Json
    $estructuraFichaQA = @(Invocar-DockerFichaQA @('exec', $baseFichaQA.Id, 'sh', '-c',
        'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --schema-only --no-owner --no-privileges --no-comments --lock-wait-timeout=5s'))
    if ($estructuraFichaQA.Count -eq 0) { throw 'El volcado de estructura esta vacio.' }

    $hashesFichaQA = [ordered]@{}
    foreach ($relativoFichaQA in @('DB/01-init.sql','DB/02-seed.sql','DB/qa/restablecer-seed.sql',
        'docs/testing/casos_prueba.csv','docs/testing/tandas/smoke_api_01.md','docker-compose.web-testing.yml')) {
        $hashesFichaQA[$relativoFichaQA] = (Get-FileHash -LiteralPath (Join-Path $raizFichaQA $relativoFichaQA) -Algorithm SHA256).Hash
    }
    $referenciaSeedFichaQA = $null
    $ultimasRestauracionesFichaQA = @(Get-ChildItem -LiteralPath (Join-Path $raizFichaQA '.qa\restauraciones') -Filter '*.json' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
    if ($ultimasRestauracionesFichaQA.Count -gt 0) {
        $restauracionFichaQA = [IO.File]::ReadAllText($ultimasRestauracionesFichaQA[0].FullName) | ConvertFrom-Json
        if ($restauracionFichaQA.volumen -eq 'mismascotas_web_testing_db' -and
            $restauracionFichaQA.base -eq $resumenBaseFichaQA.base) {
            $referenciaSeedFichaQA = [ordered]@{
                version=$restauracionFichaQA.version_seed
                hash_aplicado=$restauracionFichaQA.hash_seed_sha256
                hash_coincide_con_archivo=($restauracionFichaQA.hash_seed_sha256 -eq $hashesFichaQA['DB/qa/restablecer-seed.sql'])
                d0=$restauracionFichaQA.d0; t0=$restauracionFichaQA.t0
                evidencia_restauracion=('.qa/restauraciones/' + $ultimasRestauracionesFichaQA[0].Name)
                advertencia='Referencia a una restauracion anterior; no certifica que los datos no se hayan modificado despues.'
            }
        }
    }
    $commitFichaQA = $null
    $ramaFichaQA = $null
    $sucioFichaQA = $null
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $salidaGitFichaQA = & git -C $raizFichaQA rev-parse HEAD 2>$null
        if ($LASTEXITCODE -eq 0) {
            $commitFichaQA = ($salidaGitFichaQA -join '').Trim()
            $ramaFichaQA = (& git -C $raizFichaQA branch --show-current) -join ''
            $sucioFichaQA = [bool](& git -C $raizFichaQA status --porcelain)
        }
    }
    $conteosBaseFichaQA = ($resumenBaseFichaQA.usuarios -eq 6 -and $resumenBaseFichaQA.mascotas -eq 15 -and
        $resumenBaseFichaQA.eventos_clinicos -eq 20 -and $resumenBaseFichaQA.recordatorios -eq 15)
    $configuracionPublicaFichaQA = [ordered]@{}
    foreach ($nombreVariableFichaQA in @('JWT_ACCESS_TOKEN_EXPIRATION_MINUTES','JWT_REFRESH_TOKEN_EXPIRATION_DAYS',
        'JWT_PRE_AUTH_EXPIRATION_MINUTES','AUTH_CODIGO_VERIFICACION_EXPIRACION_MINUTOS',
        'MAIL_HOST','MAIL_PORT','MAIL_SMTP_AUTH','MAIL_SMTP_STARTTLS_ENABLE','MAIL_SMTP_STARTTLS_REQUIRED','JPA_SHOW_SQL')) {
        $configuracionPublicaFichaQA[$nombreVariableFichaQA] = $variablesBackendFichaQA[$nombreVariableFichaQA]
    }
    $fichaQA = [ordered]@{
        id_corrida=$IdCorrida
        estado='Preparada; no iniciada. Sin resultados de casos.'
        capturada_en_utc=[DateTimeOffset]::UtcNow.ToString('o')
        plan='docs/testing/tandas/smoke_api_01.md'
        capa='API'; proyecto=$proyectoFichaQA; volumen='mismascotas_web_testing_db'
        repositorio_actual=[ordered]@{commit=$commitFichaQA; rama=$ramaFichaQA; cambios_locales=$sucioFichaQA;
            advertencia='Contexto del checkout; no demuestra la procedencia de las imagenes ejecutadas.'}
        servicios=$serviciosFichaQA
        configuracion_backend_no_sensible=$configuracionPublicaFichaQA
        base=$resumenBaseFichaQA
        conteos_compatibles_con_seed_base=$conteosBaseFichaQA
        seed_referenciado=$referenciaSeedFichaQA
        correos_mailpit=$bandejaFichaQA.total
        hashes_archivos=$hashesFichaQA
        navegador='No aplica: esta tanda no verifica la interfaz.'
        limitaciones=@('No verifica toda la integridad de los datos a partir de conteos.',
            'Captura valida solo mientras no cambien imagenes, estructura, configuracion o datos iniciales.',
            'Las imagenes locales deben conservarse para repetir exactamente la misma version.')
    }
    # Las inspecciones completas y las variables sensibles nunca se guardan.
    $jsonFichaQA = $fichaQA | ConvertTo-Json -Depth 12
    foreach ($secretoFichaQA in @($variablesBaseFichaQA.POSTGRES_PASSWORD, $variablesBackendFichaQA.DB_PASSWORD,
        $variablesBackendFichaQA.JWT_SECRET, $variablesBackendFichaQA.MAIL_PASSWORD)) {
        if ($secretoFichaQA -and $secretoFichaQA.Length -ge 6 -and $jsonFichaQA.Contains($secretoFichaQA)) {
            throw 'La ficha contiene un valor sensible; no se guarda.'
        }
    }
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $carpetaFichaQA) -Force
    $null = New-Item -ItemType Directory -Path $carpetaFichaQA
    $archivoEstructuraFichaQA = Join-Path $carpetaFichaQA 'esquema.sql'
    [IO.File]::WriteAllText($archivoEstructuraFichaQA, (($estructuraFichaQA -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
    $fichaQA['estructura_capturada'] = [ordered]@{archivo='esquema.sql';
        sha256=(Get-FileHash -LiteralPath $archivoEstructuraFichaQA -Algorithm SHA256).Hash;
        alcance='Solo estructura real; sin datos, propietarios ni permisos. El hash identifica este archivo, no una equivalencia semantica entre volcados.'}
    [IO.File]::WriteAllText((Join-Path $carpetaFichaQA 'entorno.json'), ($fichaQA | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
    Write-Host "Ficha preparada: $carpetaFichaQA"
    Write-Host 'No se ejecutaron casos ni se modificaron datos o resultados.'
    if (-not $conteosBaseFichaQA -or $null -eq $referenciaSeedFichaQA -or
        -not $referenciaSeedFichaQA.hash_coincide_con_archivo -or $resumenBaseFichaQA.codigos_2fa -ne 0 -or
        $resumenBaseFichaQA.refresh_tokens -ne 0 -or $resumenBaseFichaQA.logs_auditoria -ne 0 -or $bandejaFichaQA.total -ne 0) {
        Write-Warning 'Revisar/restaurar las condiciones iniciales antes de ejecutar. Este script no las restablece.'
    }
} finally {
    $OutputEncoding = $codificacionFichaQA
}
