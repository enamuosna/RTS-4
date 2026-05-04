# =============================================================
#  RTS Caisse - Build LOCAL complet (test avant push)
#  Stack complete buildee depuis le code source.
# =============================================================

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Test-Path ".env")) {
    Write-Host "ERREUR : .env manquant. Lancez d'abord : .\scripts\init-env.ps1" -ForegroundColor Red
    exit 1
}

# Verification que les projets voisins existent
$BackendDir = "..\rts-caisse-backend"
$WebDir     = "..\rts-caisse-web"

if (-not (Test-Path $BackendDir)) {
    Write-Host "ERREUR : $BackendDir introuvable." -ForegroundColor Red
    Write-Host "Le projet backend doit etre a cote de rts-caisse-docker\"
    exit 1
}

if (-not (Test-Path $WebDir)) {
    Write-Host "ERREUR : $WebDir introuvable." -ForegroundColor Red
    Write-Host "Le projet frontend doit etre a cote de rts-caisse-docker\"
    exit 1
}

Write-Host "Build et demarrage de la stack complete..." -ForegroundColor Cyan
Write-Host "(cela peut prendre 3-5 minutes la premiere fois)" -ForegroundColor DarkGray
docker compose up -d --build

# Lecture du port gateway
$GatewayPort = (Select-String -Path .env -Pattern '^GATEWAY_PORT=(.*)$').Matches.Groups[1].Value
if (-not $GatewayPort) { $GatewayPort = "8090" }

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  RTS Caisse demarre (build local)" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Application : http://localhost:$GatewayPort"
Write-Host "  Swagger UI  : http://localhost:$GatewayPort/swagger-ui.html"
Write-Host "  Health API  : http://localhost:$GatewayPort/actuator/health"
Write-Host ""
Write-Host "  Logs   : docker compose logs -f [backend|frontend|gateway|postgres]"
Write-Host "  Arret  : .\scripts\stop.ps1"
Write-Host ""
Write-Host "  Si tout fonctionne, push vers Docker Hub :"
Write-Host "    .\scripts\build-and-push.ps1"
Write-Host ""
