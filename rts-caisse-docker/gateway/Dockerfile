# =============================================================
#  RTS Caisse - Gateway nginx (reverse proxy)
# =============================================================
#  Build :  docker build -t vnsu/rts-caisse-gateway:latest .
#  Depuis : rts-caisse-docker/gateway/
# =============================================================

FROM nginx:1.27-alpine

# Timezone Dakar
RUN apk add --no-cache tzdata \
 && cp /usr/share/zoneinfo/Africa/Dakar /etc/localtime \
 && echo "Africa/Dakar" > /etc/timezone

# Suppression de la conf par defaut
RUN rm /etc/nginx/conf.d/default.conf

# Notre conf de reverse proxy
COPY default.conf /etc/nginx/conf.d/default.conf

# Healthcheck : nginx doit servir au moins une 4xx (pas de 5xx)
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
