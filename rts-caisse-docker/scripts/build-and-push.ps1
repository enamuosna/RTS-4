# =============================================================
#  RTS Caisse - Build + Push Docker Hub
#
#  Build les 3 images (backend, frontend, gateway), les tag avec
#  la version courante ET 'latest', puis push sur Docker Hub.
#
#  Usage :
#    .\scripts\build-and-push.ps1                  -> tag = APP_VERSION du .env
#    .\scripts\build-and-push.ps1 -Version 1.2.3   -> tag specifique
# =============================================================

param(
    [string]$Version = "",

    [switch]$NoCache,

    [switch]$SkipPush
)

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Test-Path ".env")) {
    Write-Host "ERREUR : .env manquant." -ForegroundColor Red
    exit 1
}

# Lecture du registry (namespace Docker Hub) depuis le .env
$registry = (Select-String -Path .env -Pattern '^DOCKER_REGISTRY=(.*)$').Matches.Groups[1].Value
if (-not $registry) { $registry = "vnsu" }

# Determine la version a tagger
if (-not $Version) {
    $Version = (Select-String -Path .env -Pattern '^APP_VERSION=(.*)$').Matches.Groups[1].Value
    if (-not $Version) { $Version = "latest" }
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Build & Push - RTS Caisse" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "  Registry : $registry"
Write-Host "  Version  : $Version"
Write-Host ""

# Verification login Docker Hub
$loginCheck = docker info 2>$null | Select-String "Username"
if (-not $loginCheck) {
    Write-Host "Connexion a Docker Hub requise." -ForegroundColor Yellow
    docker login
}

# Verification que les projets voisins existent
$BackendDir = "..\rts-caisse-backend"
$WebDir     = "..\rts-caisse-web"

if (-not (Test-Path $BackendDir) -or -not (Test-Path $WebDir)) {
    Write-Host "ERREUR : projets backend ou frontend introuvables." -ForegroundColor Red
    exit 1
}

# -------------------------------------------------------------
#  Build (via docker compose, qui orchestre tout)
# -------------------------------------------------------------
$buildArgs = @("compose", "build")
if ($NoCache) { $buildArgs += "--no-cache" }

Write-Host "Build des 3 images..." -ForegroundColor Cyan
$env:APP_VERSION    = $Version
$env:DOCKER_REGISTRY = $registry
& docker @buildArgs

# -------------------------------------------------------------
#  Tag 'latest' supplementaire si on a tagge une version specifique
# -------------------------------------------------------------
if ($Version -ne "latest") {
    Write-Host ""
    Write-Host "Tag supplementaire 'latest'..." -ForegroundColor Cyan
    docker tag "${registry}/rts-caisse-backend:$Version"  "${registry}/rts-caisse-backend:latest"
    docker tag "${registry}/rts-caisse-frontend:$Version" "${registry}/rts-caisse-frontend:latest"
    docker tag "${registry}/rts-caisse-gateway:$Version"  "${registry}/rts-caisse-gateway:latest"
}

# -------------------------------------------------------------
#  Push
# -------------------------------------------------------------
if ($SkipPush) {
    Write-Host ""
    Write-Host "SkipPush actif : images construites mais non publiees." -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "Push vers Docker Hub..." -ForegroundColor Cyan
docker push "${registry}/rts-caisse-backend:$Version"
docker push "${registry}/rts-caisse-frontend:$Version"
docker push "${registry}/rts-caisse-gateway:$Version"

if ($Version -ne "latest") {
    docker push "${registry}/rts-caisse-backend:latest"
    docker push "${registry}/rts-caisse-frontend:latest"
    docker push "${registry}/rts-caisse-gateway:latest"
}

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  Publication terminee" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Images publiees :"
Write-Host "    ${registry}/rts-caisse-backend:$Version"
Write-Host "    ${registry}/rts-caisse-frontend:$Version"
Write-Host "    ${registry}/rts-caisse-gateway:$Version"
Write-Host ""
Write-Host "  Sur le serveur de prod :"
Write-Host "    docker compose -f docker-compose.prod.yml pull"
Write-Host "    docker compose -f docker-compose.prod.yml up -d"
Write-Host ""
