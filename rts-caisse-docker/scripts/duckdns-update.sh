#!/bin/bash
# =============================================================
#  RTS Caisse - DuckDNS Updater (Linux / macOS)
# =============================================================
#  Synchronise le sous-domaine DuckDNS avec l'IP publique du
#  reseau (utile si ton FAI te donne une IP dynamique).
#
#  Si ton IP publique 41.216.16.108 est statique chez SONATEL,
#  ce script ne sert pas regulierement, mais il faut l'executer
#  AU MOINS UNE FOIS pour pointer le sous-domaine vers ton IP.
#
#  Pour l'IP dynamique : ajouter en cron toutes les 5 minutes :
#    */5 * * * * /chemin/vers/duckdns-update.sh > /dev/null 2>&1
#
#  Variables requises dans .env (memes qu'init-letsencrypt) :
#    SERVER_NAME    -> rts-caisse.duckdns.org
#    DUCKDNS_TOKEN  -> token recupere sur https://www.duckdns.org/
# =============================================================

set -euo pipefail

if [ ! -f ".env" ]; then
    echo "[ERROR] Fichier .env introuvable" >&2
    exit 1
fi

# Charge .env
set -a
# shellcheck disable=SC1091
source .env
set +a

if [ -z "${SERVER_NAME:-}" ] || [ -z "${DUCKDNS_TOKEN:-}" ]; then
    echo "[ERROR] SERVER_NAME ou DUCKDNS_TOKEN non defini dans .env" >&2
    exit 1
fi

# Le sous-domaine c'est la PARTIE AVANT .duckdns.org
# Ex: rts-caisse.duckdns.org -> rts-caisse
SUBDOMAIN="${SERVER_NAME%.duckdns.org}"

if [ "${SUBDOMAIN}" = "${SERVER_NAME}" ]; then
    echo "[ERROR] SERVER_NAME doit finir par .duckdns.org (ex: rts-caisse.duckdns.org)" >&2
    exit 1
fi

# Detection automatique de l'IP publique (DuckDNS utilise l'IP source)
# Si on veut forcer une IP : passer ?ip=41.216.16.108
URL="https://www.duckdns.org/update?domains=${SUBDOMAIN}&token=${DUCKDNS_TOKEN}&ip="

RESPONSE=$(curl -fsS "${URL}")

case "${RESPONSE}" in
    OK)
        echo "[OK] DuckDNS : ${SERVER_NAME} synchronise avec IP publique"
        ;;
    KO)
        echo "[ERROR] DuckDNS a refuse la mise a jour. Verifier :" >&2
        echo "  - DUCKDNS_TOKEN correct ?" >&2
        echo "  - Sous-domaine ${SUBDOMAIN} bien proprietaire du compte ?" >&2
        exit 1
        ;;
    *)
        echo "[ERROR] Reponse DuckDNS inattendue : ${RESPONSE}" >&2
        exit 1
        ;;
esac
