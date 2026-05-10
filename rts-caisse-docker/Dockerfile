# =============================================================
#  RTS Caisse - Gateway nginx (reverse proxy + HTTPS)
# =============================================================
#  Build :  docker build -t vnsu/rts-caisse-gateway:latest .
#  Depuis : rts-caisse-docker/gateway/
#
#  Cette image utilise le mecanisme de TEMPLATING nginx :
#    Tout fichier place dans /etc/nginx/templates/*.template
#    est traite par envsubst au demarrage et ecrit dans
#    /etc/nginx/conf.d/<meme-nom-sans-.template>
#  Cela permet d'injecter ${SERVER_NAME} depuis docker-compose.
# =============================================================

FROM nginx:1.27-alpine

# Timezone Dakar
RUN apk add --no-cache tzdata \
 && cp /usr/share/zoneinfo/Africa/Dakar /etc/localtime \
 && echo "Africa/Dakar" > /etc/timezone

# Suppression de la conf par defaut
RUN rm /etc/nginx/conf.d/default.conf

# La conf est un TEMPLATE : nginx remplace ${SERVER_NAME} au demarrage
# en utilisant la variable d'env SERVER_NAME passee par docker-compose.
COPY default.conf /etc/nginx/templates/default.conf.template

# Healthcheck : on verifie le port HTTPS (le HTTP redirige juste)
# Note : --no-check-certificate car le cert peut etre Let's Encrypt staging
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD wget -q --spider --no-check-certificate https://localhost/health \
        || wget -q --spider http://localhost/.well-known/acme-challenge/healthz \
        || exit 1

# IMPORTANT : on n'expose PAS forcement les variables ${SERVER_NAME}
# Liste blanche des variables substituees par envsubst (sinon nginx
# essaye de remplacer toutes les variables, y compris $host, $uri...)
ENV NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx/conf.d
ENV NGINX_ENVSUBST_TEMPLATE_SUFFIX=.template
# CRITIQUE : ne substitue QUE SERVER_NAME, pas $host, $uri, $remote_addr...
# Sans cette ligne, envsubst remplace TOUTES les variables nginx par "",
# ce qui casse completement le reverse proxy.
ENV NGINX_ENVSUBST_FILTER_VARIABLES="SERVER_NAME"

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
