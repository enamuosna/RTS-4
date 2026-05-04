# =============================================================
#  RTS Caisse - Frontend Angular (multi-stage build)
# =============================================================

# ==============================
#  STAGE 1 : build Angular
# ==============================
FROM node:20-alpine AS build

WORKDIR /build

# Cache des dépendances npm : install avant le code
COPY package*.json ./
RUN npm ci --no-audit --no-fund --prefer-offline

COPY . .

# Build production (optimisations + AOT + tree-shaking)
RUN npm run build -- --configuration=production


# ==============================
#  STAGE 2 : nginx pour servir
# ==============================
FROM nginx:1.27-alpine

# Suppression de la conf par défaut de nginx
RUN rm /etc/nginx/conf.d/default.conf

# Copie de notre conf SPA (gère le fallback vers index.html)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Le build Angular sort dans dist/<nom-projet>/browser depuis Angular 17+
# (nouveau builder "application"). On copie tout le contenu du dossier
# browser/ à la racine de nginx.
COPY --from=build /build/dist/rts-caisse-web/browser /usr/share/nginx/html

# Healthcheck : nginx répond sur /index.html
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://localhost/index.html || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
