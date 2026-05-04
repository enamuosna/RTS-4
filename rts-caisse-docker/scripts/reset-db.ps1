# =============================================================
#  RTS Caisse - Reset de la base de donnees (DEV uniquement)
#
#  ATTENTION : supprime DEFINITIVEMENT toutes les donnees Postgres.
#  A n'utiliser qu'en developpement (Flyway re-applique les
#  migrations au prochain demarrage).
# =============================================================

param(
    [ValidateSet("dev", "default")]
    [string]$Mode = "dev"
)

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

$composeFile = if ($Mode -eq "dev") { "docker-compose.dev.yml" } else { "docker-compose.yml" }
$volumeName  = if ($Mode -eq "dev") { "rts-caisse-dev-postgres-data" } else { "rts-caisse-postgres-data" }

Write-Host "ATTENTION : ceci va supprimer la base de donnees ($volumeName)." -ForegroundColor Yellow
$confirm = Read-Host "Confirmer ? Tapez 'reset' pour continuer"
if ($confirm -ne "reset") {
    Write-Host "Annule." -ForegroundColor Yellow
    exit 0
}

Write-Host "Arret de la stack..." -ForegroundColor Cyan
docker compose -f $composeFile down

Write-Host "Suppression du volume Postgres..." -ForegroundColor Cyan
docker volume rm $volumeName 2>$null

Write-Host "Redemarrage..." -ForegroundColor Cyan
docker compose -f $composeFile up -d

Write-Host ""
Write-Host "Base de donnees reinitialisee." -ForegroundColor Green
Write-Host "Flyway va re-appliquer les migrations au prochain demarrage du backend."
Write-Host ""
