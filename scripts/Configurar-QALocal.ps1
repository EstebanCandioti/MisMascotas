[CmdletBinding()]
param([string]$Proyecto = 'mismascotas-qa')

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
if ($Proyecto -notmatch '^[a-z0-9][a-z0-9_-]+$') { throw 'Nombre de proyecto QA inválido' }
if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'Ejecutá este script con pwsh (PowerShell 7)' }
$hooksActuales = & git -C $raiz config --get core.hooksPath
if ($hooksActuales -and $hooksActuales -ne '.githooks') {
    throw "Ya hay un core.hooksPath configurado: $hooksActuales. Integrar el hook existente antes de continuar."
}
# La extensión permite que cada worktree active el hook independientemente.
$coreWorktree = & git -C $raiz config --local --get core.worktree
$coreBare = & git -C $raiz config --local --get core.bare
if ($coreWorktree -or $coreBare -eq 'true') { throw 'Revisar core.worktree/core.bare antes de activar worktreeConfig' }
& git -C $raiz config --local extensions.worktreeConfig true
if ($LASTEXITCODE -ne 0) { throw 'No se pudo activar la configuración por worktree' }
& git -C $raiz config --worktree core.hooksPath .githooks
if ($LASTEXITCODE -ne 0) { throw 'No se pudo activar el hook de esta copia' }
$powershell = (Get-Process -Id $PID).Path.Replace('\', '/')
& git -C $raiz config --worktree qalocal.powershell $powershell
if ($LASTEXITCODE -ne 0) { throw 'No se pudo guardar la ruta de PowerShell 7' }
$carpeta = Join-Path $raiz '.qa-local'
New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
@{ proyecto = $Proyecto } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $carpeta 'config.json') -Encoding utf8NoBOM
Write-Host "Hook post-commit activo en esta copia. Proyecto Docker: $Proyecto."
