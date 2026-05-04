# =============================================================
#  RTS Caisse - Demarrage PRODUCTION
#  Tire les images depuis Docker Hub (aucun build local)
# =============================================================

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Test-Path ".env")) {
    Write-Host "ERREUR : .env manquant." -ForegroundColor Red
    Write-Host "Generez le avec : .\scripts\init-env.ps1"
    Write-Host "Verifiez ensuite que JWT_SECRET et POSTGRES_PASSWORD sont definis."
    exit 1
}

# Lecture du registry pour les messages d'erreur
$registry = (Select-String -Path .env -Pattern '^DOCKER_REGISTRY=(.*)$').Matches.Groups[1].Value
if (-not $registry) { $registry = "vnsu" }

# -------------------------------------------------------------
#  1. Pull des images
# -------------------------------------------------------------
Write-Host "Pull des dernieres images depuis Docker Hub ($registry)..." -ForegroundColor Cyan
docker compose -f docker-compose.prod.yml pull

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host "  ECHEC DU PULL" -ForegroundColor Red
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Les images $registry/rts-caisse-* n'existent pas encore sur Docker Hub." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Causes possibles :"
    Write-Host "    1. Tu n'as jamais publie les images. Solution :"
    Write-Host "         docker login"
    Write-Host "         .\scripts\build-and-push.ps1"
    Write-Host ""
    Write-Host "    2. Le namespace DOCKER_REGISTRY de .env n'est pas le bon."
    Write-Host "       Actuel : $registry"
    Write-Host "       Verifie que c'est bien ton compte Docker Hub."
    Write-Host ""
    Write-Host "    3. Tu cherches juste a tester la stack en local sans publier ?"
    Write-Host "       Utilise plutot :"
    Write-Host "         .\scripts\start.ps1     (build depuis le code source local)"
    Write-Host ""
    exit 1
}

# -------------------------------------------------------------
#  2. Demarrage
# -------------------------------------------------------------
Write-Host ""
Write-Host "Demarrage de la stack..." -ForegroundColor Cyan
docker compose -f docker-compose.prod.yml up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ECHEC du demarrage. Voir les logs :" -ForegroundColor Red
    Write-Host "  docker compose -f docker-compose.prod.yml logs"
    exit 1
}

# -------------------------------------------------------------
#  3. Banniere de succes
# -------------------------------------------------------------
$GatewayPort = (Select-String -Path .env -Pattern '^GATEWAY_PORT=(.*)$').Matches.Groups[1].Value
if (-not $GatewayPort) { $GatewayPort = "8090" }

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  RTS Caisse PRODUCTION demarre" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Application : http://localhost:$GatewayPort"
Write-Host ""
Write-Host "  Mise a jour applicative :"
Write-Host "    .\scripts\start-prod.ps1   (pull + up -d, zero downtime)"
Write-Host ""
Write-Host "  Logs   : docker compose -f docker-compose.prod.yml logs -f"
Write-Host "  Arret  : .\scripts\stop.ps1 prod"
Write-Host ""
