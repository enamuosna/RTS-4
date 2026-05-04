# =============================================================
#  RTS Caisse - Demarrage environnement DEV
#  (Postgres + pgAdmin uniquement)
#
#  Le backend tourne dans Rider/IntelliJ, le frontend en ng serve.
# =============================================================

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Test-Path ".env")) {
    Write-Host "ERREUR : .env manquant. Lancez d'abord : .\scripts\init-env.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "Demarrage des services DEV (postgres + pgadmin)..." -ForegroundColor Cyan
docker compose -f docker-compose.dev.yml up -d

# Lecture des ports depuis .env
$pgPort = (Select-String -Path .env -Pattern '^POSTGRES_HOST_PORT=(.*)$').Matches.Groups[1].Value
if (-not $pgPort) { $pgPort = "5432" }

$adminPort = (Select-String -Path .env -Pattern '^PGADMIN_PORT=(.*)$').Matches.Groups[1].Value
if (-not $adminPort) { $adminPort = "5050" }

$adminEmail = (Select-String -Path .env -Pattern '^PGADMIN_EMAIL=(.*)$').Matches.Groups[1].Value
if (-not $adminEmail) { $adminEmail = "admin@rts.local" }

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  Environnement DEV pret" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Postgres : localhost:$pgPort"
Write-Host "  pgAdmin  : http://localhost:$adminPort  ($adminEmail)"
Write-Host ""
Write-Host "  Etapes suivantes :"
Write-Host "    1. Lancer le backend Spring Boot dans Rider/IntelliJ"
Write-Host "       (profil par defaut, pointe deja sur localhost:$pgPort)"
Write-Host "    2. Lancer le frontend :   cd ..\rts-caisse-web ; ng serve"
Write-Host "    3. Application : http://localhost:4200"
Write-Host ""
Write-Host "  Logs   : docker compose -f docker-compose.dev.yml logs -f"
Write-Host "  Arret  : .\scripts\stop.ps1 dev"
Write-Host ""
