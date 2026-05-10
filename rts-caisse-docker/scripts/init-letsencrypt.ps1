# =============================================================
#  RTS Caisse - Bootstrap Let's Encrypt (Windows PowerShell)
# =============================================================
#  Equivalent Windows de scripts/init-letsencrypt.sh
#
#  Usage :
#    cd rts-caisse-docker\
#    powershell -ExecutionPolicy Bypass -File scripts\init-letsencrypt.ps1
# =============================================================

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Write-Info  { param($Message) Write-Host "[INFO]  $Message" -ForegroundColor Green }
function Write-Warn  { param($Message) Write-Host "[WARN]  $Message" -ForegroundColor Yellow }
function Write-Err   { param($Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }

# -----------------------------------------------------------------
# Verifier qu'on est dans le bon repertoire
# -----------------------------------------------------------------
if (-not (Test-Path 'docker-compose.prod.yml')) {
    Write-Err 'docker-compose.prod.yml introuvable.'
    Write-Err 'Lancer ce script depuis le dossier rts-caisse-docker\'
    exit 1
}

# -----------------------------------------------------------------
# Charger le .env
# -----------------------------------------------------------------
if (-not (Test-Path '.env')) {
    Write-Err 'Fichier .env introuvable. Copier .env.prod.example en .env'
    exit 1
}

$envVars = @{}
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $key, $val = $line -split '=', 2
        $envVars[$key.Trim()] = $val.Trim()
        # Injecter aussi dans l'env du processus pour docker compose
        [Environment]::SetEnvironmentVariable($key.Trim(), $val.Trim(), 'Process')
    }
}

$Domain  = $envVars['SERVER_NAME']
$Email   = $envVars['LETSENCRYPT_EMAIL']
$Staging = $envVars['LETSENCRYPT_STAGING']
if (-not $Staging) { $Staging = '1' }

if (-not $Domain) { Write-Err 'SERVER_NAME non defini dans .env'; exit 1 }
if (-not $Email)  { Write-Err 'LETSENCRYPT_EMAIL non defini dans .env'; exit 1 }

Write-Info "Domaine cible : $Domain"
Write-Info "Email contact : $Email"
if ($Staging -eq '1') {
    Write-Warn 'Mode STAGING active (certs Let''s Encrypt de TEST, non valides)'
    Write-Warn 'Passer LETSENCRYPT_STAGING=0 dans .env quand tout fonctionne'
} else {
    Write-Info 'Mode PRODUCTION : certificats reels'
}

# -----------------------------------------------------------------
# Verification DNS basique (Resolve-DnsName)
# -----------------------------------------------------------------
Write-Info "Verification DNS de $Domain..."
try {
    $dns = Resolve-DnsName -Name $Domain -Type A -ErrorAction Stop | Select-Object -First 1
    Write-Info "DNS resolu : $Domain -> $($dns.IPAddress)"
    if ($dns.IPAddress -ne '41.216.16.108') {
        Write-Warn "DNS pointe vers $($dns.IPAddress), attendu 41.216.16.108"
        $confirm = Read-Host 'Continuer quand meme ? [y/N]'
        if ($confirm -notmatch '^[yY]') { exit 1 }
    }
} catch {
    Write-Warn "DNS non resoluble (encore ?). Lance d'abord scripts/duckdns-update.ps1"
    $confirm = Read-Host 'Continuer quand meme ? [y/N]'
    if ($confirm -notmatch '^[yY]') { exit 1 }
}

$CertPath = "/etc/letsencrypt/live/$Domain"

# -----------------------------------------------------------------
# Etape 1 : cert factice
# -----------------------------------------------------------------
Write-Info 'Etape 1/4 : Generation d''un cert factice pour bootstrap...'

$bootstrapCmd = @"
mkdir -p $CertPath && \
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '$CertPath/privkey.pem' \
    -out '$CertPath/fullchain.pem' \
    -subj '/CN=localhost' && \
openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048
"@

docker compose -f docker-compose.prod.yml run --rm --entrypoint sh certbot -c $bootstrapCmd
if ($LASTEXITCODE -ne 0) { Write-Err 'Echec generation cert factice'; exit 1 }

Write-Info "Cert factice cree dans $CertPath"

# -----------------------------------------------------------------
# Etape 2 : nginx
# -----------------------------------------------------------------
Write-Info 'Etape 2/4 : Demarrage de nginx avec le cert factice...'
docker compose -f docker-compose.prod.yml up -d gateway
if ($LASTEXITCODE -ne 0) { Write-Err 'Echec demarrage gateway'; exit 1 }

Write-Info 'Attente de nginx (max 30s)...'
for ($i = 1; $i -le 30; $i++) {
    try {
        Invoke-WebRequest -Uri 'http://localhost/' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null
        Write-Info "nginx pret apres ${i}s"
        break
    } catch {
        Start-Sleep -Seconds 1
    }
}

# -----------------------------------------------------------------
# Etape 3 : supprimer le faux cert + demander le vrai
# -----------------------------------------------------------------
Write-Info 'Etape 3/4 : Suppression du cert factice...'

$removeCmd = "rm -rf /etc/letsencrypt/live/$Domain /etc/letsencrypt/archive/$Domain /etc/letsencrypt/renewal/$Domain.conf"
docker compose -f docker-compose.prod.yml run --rm --entrypoint sh certbot -c $removeCmd

Write-Info 'Etape 3.b : Demande du vrai certificat a Let''s Encrypt...'

$stagingArg = ''
if ($Staging -eq '1') { $stagingArg = '--staging' }

$certCmd = @"
certbot certonly --webroot -w /var/www/certbot \
    $stagingArg \
    --email $Email \
    -d $Domain \
    --rsa-key-size 2048 \
    --agree-tos \
    --no-eff-email \
    --force-renewal
"@

docker compose -f docker-compose.prod.yml run --rm --entrypoint sh certbot -c $certCmd
if ($LASTEXITCODE -ne 0) {
    Write-Err 'Echec obtention du vrai certificat'
    Write-Err 'Verifie :'
    Write-Err '  - Le DNS pointe bien vers ton IP publique 41.216.16.108'
    Write-Err '  - Les ports 80 et 443 sont forwardes vers 192.168.150.251'
    Write-Err '  - Aucun firewall ne bloque les requetes Let''s Encrypt'
    exit 1
}

# -----------------------------------------------------------------
# Etape 4 : recharger nginx
# -----------------------------------------------------------------
Write-Info 'Etape 4/4 : Rechargement de nginx avec le vrai certificat...'
docker compose -f docker-compose.prod.yml exec gateway nginx -s reload

Write-Info 'Demarrage du reste de la stack...'
docker compose -f docker-compose.prod.yml up -d

Write-Info '============================================================'
Write-Info '  TERMINE'
Write-Info '============================================================'
Write-Info "  Application accessible sur : https://$Domain"
Write-Info "  API health check           : https://$Domain/health"
Write-Info "  Swagger UI                 : https://$Domain/swagger-ui.html"
Write-Info ''
if ($Staging -eq '1') {
    Write-Warn '  ATTENTION : cert STAGING (le navigateur affichera une alerte)'
    Write-Warn '  Pour passer en PRODUCTION :'
    Write-Warn '    1. Editer .env  -> LETSENCRYPT_STAGING=0'
    Write-Warn '    2. Relancer ce script'
}
Write-Info '============================================================'
