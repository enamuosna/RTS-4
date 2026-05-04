# =============================================================
#  RTS Caisse - Suivi des logs
#  Usage :
#    .\scripts\logs.ps1                -> tous les logs (build local)
#    .\scripts\logs.ps1 backend        -> logs du backend uniquement
#    .\scripts\logs.ps1 -Mode prod backend
# =============================================================

param(
    [Parameter(Position=0)]
    [string]$Service = "",

    [ValidateSet("default", "dev", "prod")]
    [string]$Mode = "default"
)

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

$composeFile = switch ($Mode) {
    "dev"  { "docker-compose.dev.yml" }
    "prod" { "docker-compose.prod.yml" }
    default { "docker-compose.yml" }
}

if ($Service) {
    docker compose -f $composeFile logs -f --tail=200 $Service
} else {
    docker compose -f $composeFile logs -f --tail=200
}
