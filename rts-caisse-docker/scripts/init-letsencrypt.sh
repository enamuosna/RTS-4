#!/bin/bash
# =============================================================
#  RTS Caisse - Bootstrap Let's Encrypt (Linux / macOS)
# =============================================================
#  Genere le PREMIER certificat SSL Let's Encrypt pour le domaine
#  defini dans .env (SERVER_NAME).
#
#  Probleme classique : nginx ne peut pas demarrer sans cert
#  (config HTTPS qui reference des fichiers absents), mais certbot
#  ne peut pas obtenir un cert sans nginx (challenge HTTP-01).
#
#  Solution (idee de @wmnnd, https://github.com/wmnnd/nginx-certbot) :
#    1. Generer un cert factice auto-signe
#    2. Demarrer nginx avec ce faux cert
#    3. Demander le vrai cert a Let's Encrypt
#    4. Remplacer le faux par le vrai et recharger nginx
#
#  Usage :
#    cd rts-caisse-docker/
#    bash scripts/init-letsencrypt.sh
# =============================================================

set -euo pipefail

# Couleurs pour les logs
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()    { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

# -----------------------------------------------------------------
# Verifier qu'on est dans le bon repertoire
# -----------------------------------------------------------------
if [ ! -f "docker-compose.prod.yml" ]; then
    log_error "docker-compose.prod.yml introuvable."
    log_error "Lancer ce script depuis le dossier rts-caisse-docker/"
    exit 1
fi

# -----------------------------------------------------------------
# Charger les variables d'env depuis .env
# -----------------------------------------------------------------
if [ ! -f ".env" ]; then
    log_error "Fichier .env introuvable. Copier .env.prod.example en .env"
    exit 1
fi

# Charge .env en ignorant les commentaires et lignes vides
set -a
# shellcheck disable=SC1091
source .env
set +a

# -----------------------------------------------------------------
# Validation
# -----------------------------------------------------------------
if [ -z "${SERVER_NAME:-}" ]; then
    log_error "SERVER_NAME non defini dans .env"
    exit 1
fi
if [ -z "${LETSENCRYPT_EMAIL:-}" ]; then
    log_error "LETSENCRYPT_EMAIL non defini dans .env"
    exit 1
fi

DOMAIN="${SERVER_NAME}"
EMAIL="${LETSENCRYPT_EMAIL}"
STAGING="${LETSENCRYPT_STAGING:-1}"

log_info "Domaine cible : ${DOMAIN}"
log_info "Email contact : ${EMAIL}"
if [ "${STAGING}" = "1" ]; then
    log_warn "Mode STAGING active (certs Let's Encrypt de TEST, non valides)"
    log_warn "Passer LETSENCRYPT_STAGING=0 dans .env quand tout fonctionne"
else
    log_info "Mode PRODUCTION : certificats reels"
fi

# -----------------------------------------------------------------
# Verifier que le DNS est OK avant de tout lancer
# -----------------------------------------------------------------
log_info "Verification DNS de ${DOMAIN}..."
if command -v dig > /dev/null 2>&1; then
    DNS_IP=$(dig +short "${DOMAIN}" | tail -n 1)
elif command -v nslookup > /dev/null 2>&1; then
    DNS_IP=$(nslookup "${DOMAIN}" | awk '/^Address: / { print $2 ; exit }')
else
    DNS_IP=""
    log_warn "Ni dig ni nslookup disponibles, pas de verif DNS"
fi

if [ -n "${DNS_IP}" ]; then
    log_info "DNS resolu : ${DOMAIN} -> ${DNS_IP}"
    if [ "${DNS_IP}" != "41.216.16.108" ]; then
        log_warn "DNS pointe vers ${DNS_IP}, attendu 41.216.16.108"
        log_warn "Verifie sur https://www.duckdns.org/ que ton sous-domaine"
        log_warn "pointe bien vers ton IP publique."
        read -r -p "Continuer quand meme ? [y/N] " confirm
        case "${confirm}" in
            [yY][eE][sS]|[yY]) ;;
            *) exit 1 ;;
        esac
    fi
fi

# -----------------------------------------------------------------
# Etape 1 : Generer un cert factice pour bootstrap nginx
# -----------------------------------------------------------------
log_info "Etape 1/4 : Generation d'un cert factice pour bootstrap..."

CERT_PATH="/etc/letsencrypt/live/${DOMAIN}"

docker compose -f docker-compose.prod.yml run --rm --entrypoint "\
  sh -c \"\
    mkdir -p ${CERT_PATH} && \
    openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
      -keyout '${CERT_PATH}/privkey.pem' \
      -out '${CERT_PATH}/fullchain.pem' \
      -subj '/CN=localhost' && \
    openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048 \
  \"" certbot

log_info "Cert factice cree dans ${CERT_PATH}"

# -----------------------------------------------------------------
# Etape 2 : Demarrer nginx (avec le cert factice)
# -----------------------------------------------------------------
log_info "Etape 2/4 : Demarrage de nginx avec le cert factice..."

docker compose -f docker-compose.prod.yml up -d gateway

# Attendre que nginx soit pret
log_info "Attente de nginx (max 30s)..."
for i in $(seq 1 30); do
    if curl -fsS -o /dev/null "http://localhost/.well-known/acme-challenge/healthz" 2>/dev/null \
       || curl -fsS -o /dev/null "http://localhost/" 2>/dev/null; then
        log_info "nginx pret apres ${i}s"
        break
    fi
    sleep 1
done

# -----------------------------------------------------------------
# Etape 3 : Supprimer le cert factice puis demander le vrai
# -----------------------------------------------------------------
log_info "Etape 3/4 : Suppression du cert factice..."

docker compose -f docker-compose.prod.yml run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/${DOMAIN} \
         /etc/letsencrypt/archive/${DOMAIN} \
         /etc/letsencrypt/renewal/${DOMAIN}.conf" certbot

log_info "Etape 3.b : Demande du vrai certificat a Let's Encrypt..."

# Argument staging si LETSENCRYPT_STAGING=1
STAGING_ARG=""
if [ "${STAGING}" = "1" ]; then
    STAGING_ARG="--staging"
fi

docker compose -f docker-compose.prod.yml run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    ${STAGING_ARG} \
    --email ${EMAIL} \
    -d ${DOMAIN} \
    --rsa-key-size 2048 \
    --agree-tos \
    --no-eff-email \
    --force-renewal" certbot

# -----------------------------------------------------------------
# Etape 4 : Recharger nginx pour prendre en compte le vrai cert
# -----------------------------------------------------------------
log_info "Etape 4/4 : Rechargement de nginx avec le vrai certificat..."

docker compose -f docker-compose.prod.yml exec gateway nginx -s reload

# -----------------------------------------------------------------
# Demarrer toute la stack
# -----------------------------------------------------------------
log_info "Demarrage du reste de la stack..."
docker compose -f docker-compose.prod.yml up -d

log_info "============================================================"
log_info "  TERMINE"
log_info "============================================================"
log_info "  Application accessible sur : https://${DOMAIN}"
log_info "  API health check          : https://${DOMAIN}/health"
log_info "  Swagger UI                : https://${DOMAIN}/swagger-ui.html"
log_info ""
if [ "${STAGING}" = "1" ]; then
    log_warn "  ATTENTION : cert STAGING (le navigateur affichera une alerte)"
    log_warn "  Pour passer en PRODUCTION :"
    log_warn "    1. Editer .env  -> LETSENCRYPT_STAGING=0"
    log_warn "    2. Relancer ce script"
fi
log_info "============================================================"
