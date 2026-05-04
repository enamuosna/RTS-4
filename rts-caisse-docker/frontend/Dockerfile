# =============================================================
#  RTS Caisse - Frontend Angular 17+ (multi-stage build)
# =============================================================
#  Build :  docker build -t vnsu/rts-caisse-frontend:latest .
#  Depuis : rts-caisse-web/   (la racine du projet Angular)
# =============================================================

# ==============================
#  STAGE 1 : build Angular
# ==============================
FROM node:20-alpine AS build

WORKDIR /build

# Cache des deps : install AVANT copie du code
COPY package*.json ./
RUN npm ci --no-audit --no-fund --prefer-offline

# Copie du reste du code source
COPY . .

# Build production : Angular swap automatiquement environment.ts
# par environment.production.ts via fileReplacements (angular.json)
RUN npm run build -- --configuration=production


# ==============================
#  STAGE 2 : nginx pour servir
# ==============================
FROM nginx:1.27-alpine

# Timezone Dakar
RUN apk add --no-cache tzdata \
 && cp /usr/share/zoneinfo/Africa/Dakar /etc/localtime \
 && echo "Africa/Dakar" > /etc/timezone

# Suppression de la conf par defaut de nginx
RUN rm /etc/nginx/conf.d/default.conf

# Copie de notre conf SPA (gere le fallback vers index.html)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Le builder "application" d'Angular 17+ sort dans dist/<projet>/browser
# Adapte le nom "rts-caisse-web" si le projet a un autre nom dans angular.json
COPY --from=build /build/dist/rts-caisse-web/browser /usr/share/nginx/html

# Healthcheck : nginx repond sur /index.html
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost/index.html || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
