# =============================================================
#  RTS Caisse - DuckDNS Updater (Windows PowerShell)
# =============================================================
#  Equivalent Windows de scripts/duckdns-update.sh
#
#  Pour planifier toutes les 5 minutes (en admin PowerShell) :
#    $action  = New-ScheduledTaskAction -Execute 'powershell.exe' `
#               -Argument '-NoProfile -ExecutionPolicy Bypass -File C:\rts-caisse-docker\scripts\duckdns-update.ps1'
#    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
#               -RepetitionInterval (New-TimeSpan -Minutes 5)
#    Register-ScheduledTask -TaskName 'DuckDNS-RTS-Caisse' `
#               -Action $action -Trigger $trigger `
#               -RunLevel Highest -User 'SYSTEM'
# =============================================================

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

if (-not (Test-Path '.env')) {
    Write-Host '[ERROR] Fichier .env introuvable' -ForegroundColor Red
    exit 1
}

$envVars = @{}
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $key, $val = $line -split '=', 2
        $envVars[$key.Trim()] = $val.Trim()
    }
}

$ServerName = $envVars['SERVER_NAME']
$Token      = $envVars['DUCKDNS_TOKEN']

if (-not $ServerName -or -not $Token) {
    Write-Host '[ERROR] SERVER_NAME ou DUCKDNS_TOKEN non defini dans .env' -ForegroundColor Red
    exit 1
}

if (-not $ServerName.EndsWith('.duckdns.org')) {
    Write-Host '[ERROR] SERVER_NAME doit finir par .duckdns.org' -ForegroundColor Red
    exit 1
}

$Subdomain = $ServerName.Substring(0, $ServerName.Length - '.duckdns.org'.Length)
$Url = "https://www.duckdns.org/update?domains=$Subdomain&token=$Token&ip="

try {
    $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 10
    if ($response -eq 'OK') {
        Write-Host "[OK] DuckDNS : $ServerName synchronise avec IP publique" -ForegroundColor Green
    } elseif ($response -eq 'KO') {
        Write-Host '[ERROR] DuckDNS a refuse la mise a jour' -ForegroundColor Red
        Write-Host '  - DUCKDNS_TOKEN correct ?' -ForegroundColor Red
        Write-Host "  - Sous-domaine $Subdomain bien proprietaire du compte ?" -ForegroundColor Red
        exit 1
    } else {
        Write-Host "[ERROR] Reponse DuckDNS inattendue : $response" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "[ERROR] Echec de l'appel DuckDNS : $_" -ForegroundColor Red
    exit 1
}
