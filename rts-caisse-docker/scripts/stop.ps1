# =============================================================
#  RTS Caisse - Arret de la stack
#  Usage :
#    .\scripts\stop.ps1         -> arrete la stack "build local"
#    .\scripts\stop.ps1 dev     -> arrete la stack DEV
#    .\scripts\stop.ps1 prod    -> arrete la stack PROD
#    .\scripts\stop.ps1 all     -> arrete tout
# =============================================================

param(
    [Parameter(Position=0)]
    [ValidateSet("default", "dev", "prod", "all")]
    [string]$Target = "default"
)

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

function Stop-Stack($composeFile, $label) {
    if (Test-Path $composeFile) {
        Write-Host "Arret de la stack $label ($composeFile)..." -ForegroundColor Cyan
        docker compose -f $composeFile down
    }
}

switch ($Target) {
    "default" { Stop-Stack "docker-compose.yml"      "BUILD LOCAL" }
    "dev"     { Stop-Stack "docker-compose.dev.yml"  "DEV" }
    "prod"    { Stop-Stack "docker-compose.prod.yml" "PROD" }
    "all" {
        Stop-Stack "docker-compose.dev.yml"  "DEV"
        Stop-Stack "docker-compose.yml"      "BUILD LOCAL"
        Stop-Stack "docker-compose.prod.yml" "PROD"
    }
}

Write-Host ""
Write-Host "Stack(s) arretee(s)." -ForegroundColor Green
Write-Host ""
