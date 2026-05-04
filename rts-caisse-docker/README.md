# RTS Caisse — Déploiement Docker

Stack complète : **PostgreSQL 16** + **Spring Boot 3** + **Angular 17+** + **Nginx (reverse proxy)**.

Trois modes de déploiement sont supportés :

| Mode | Compose file | Quand l'utiliser |
|------|-----|-----|
| **DEV** | `docker-compose.dev.yml` | Tu codes dans Rider/IntelliJ et `ng serve`. Seuls Postgres + pgAdmin tournent en conteneur. |
| **BUILD LOCAL** | `docker-compose.yml` | Stack complète buildée depuis le code source local. Sert à valider le build avant de pousser sur Docker Hub. |
| **PROD** | `docker-compose.prod.yml` | Stack complète tirée de Docker Hub. À déployer sur le serveur cible (aucun code source nécessaire). |

---

## Arborescence attendue

Les 3 dépôts doivent être **côte à côte** :

```
projects/
├── rts-caisse-backend/      <-- projet Spring Boot
├── rts-caisse-web/          <-- projet Angular
└── rts-caisse-docker/       <-- ce dépôt
    ├── .env.example
    ├── .env                 (généré par init-env.ps1, NON commit)
    ├── docker-compose.dev.yml
    ├── docker-compose.yml
    ├── docker-compose.prod.yml
    ├── backend/
    │   ├── Dockerfile
    │   └── application-docker.properties   <-- à copier dans rts-caisse-backend/src/main/resources/
    ├── frontend/
    │   ├── Dockerfile
    │   ├── nginx.conf
    │   ├── environment.ts                  <-- à copier dans rts-caisse-web/src/environments/
    │   ├── environment.production.ts       <-- idem
    │   └── proxy.conf.json                 <-- à copier dans rts-caisse-web/
    ├── gateway/
    │   ├── Dockerfile
    │   └── default.conf
    └── scripts/
        ├── init-env.ps1
        ├── start-dev.ps1
        ├── start.ps1
        ├── start-prod.ps1
        ├── stop.ps1
        ├── logs.ps1
        ├── build-and-push.ps1
        └── reset-db.ps1
```

---

## Architecture réseau

```
                 ┌─ Hôte (port 8090) ─┐
                 │                    │
   Navigateur ───┼──► gateway:80 ─────┼──► /api/*  → backend:9090
                 │     (nginx)        │   /        → frontend:80
                 └────────────────────┘
                         │
                         └──► postgres:5432 (réseau interne uniquement)
```

**Une seule porte d'entrée** : le port `GATEWAY_PORT` (8090 par défaut). Postgres et le backend ne sont **jamais** exposés directement en prod.

| Composant | Port interne | Exposé en DEV | Exposé en PROD |
|---|---|---|---|
| Postgres | 5432 | ✅ (5432) | ❌ |
| Backend Spring | 9090 | ❌ (lancé hors Docker en dev) | ❌ |
| Frontend nginx | 80 | ❌ (`ng serve` en dev) | ❌ |
| Gateway nginx | 80 | — | ✅ (8090) |
| pgAdmin | 80 | ✅ (5050) | ❌ |

---

## Prérequis

- **Docker Desktop** (Windows/Mac) ou **Docker Engine** (Linux) avec `docker compose` v2
- **PowerShell 5.1+** (Windows) ou bash + adaptation des scripts
- Pour le mode **DEV** uniquement : Java 21, Maven, Node 20+, Angular CLI

---

## Démarrage rapide

### 1. Initialiser le `.env`

```powershell
cd rts-caisse-docker
.\scripts\init-env.ps1
```

Cela génère un `.env` avec un `JWT_SECRET` aléatoire (64 octets Base64) et un `POSTGRES_PASSWORD` fort. Ouvre le fichier pour ajuster `GATEWAY_PORT` si nécessaire.

### 2. Choisir un mode

#### Mode DEV (le plus courant)

```powershell
.\scripts\start-dev.ps1
```

Démarre Postgres + pgAdmin. Ensuite :

1. **Backend** : ouvre `rts-caisse-backend` dans Rider, configure `application.properties` pour pointer sur `localhost:5432`, lance la classe principale.
2. **Frontend** :
   ```powershell
   cd ..\rts-caisse-web
   ng serve --proxy-config proxy.conf.json
   ```
3. Ouvre http://localhost:4200

> Le `proxy.conf.json` (fourni dans `frontend/`) relaie les appels `/api/*` du frontend vers `localhost:9090`. Le code Angular utilise donc la **même URL relative `/api`** en dev et en prod.

#### Mode BUILD LOCAL

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

Builde les 3 images depuis le code source local et démarre la stack complète. Application disponible sur http://localhost:8090.

#### Mode PROD

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-prod.ps1
```

Tire les images depuis Docker Hub (`vnsu/rts-caisse-*`) et démarre.

---

## Publier de nouvelles versions sur Docker Hub

```powershell
# Connexion Docker Hub la première fois
docker login

# Build + push avec le tag du .env (APP_VERSION)
powershell -ExecutionPolicy Bypass -File .\scripts\build-and-push.ps1

# Build + push avec un tag spécifique
powershell -ExecutionPolicy Bypass -File .\scripts\build-and-push.ps1 -Version 1.2.3

# Build complet sans cache
powershell -ExecutionPolicy Bypass -File .\scripts\build-and-push.ps1 -Version 1.2.3 -NoCache
```

Le script tag automatiquement les images en `:1.2.3` ET `:latest`, puis les pousse.

---

## Mettre à jour la production

Sur le serveur cible :

```powershell
cd rts-caisse-docker
powershell -ExecutionPolicy Bypass -File .\scripts\start-prod.ps1
```

Le script fait `docker compose pull` puis `up -d`. Docker remplace les conteneurs un par un de manière transparente (zero-downtime tant que les migrations Flyway sont compatibles).

---

## Configuration côté projets backend & frontend

### Backend (`rts-caisse-backend`)

1. Copier `backend/application-docker.properties` vers `src/main/resources/application-docker.properties`.
2. S'assurer que les dépendances Maven incluent :
   - `spring-boot-starter-actuator` (healthcheck)
   - `spring-boot-starter-data-jpa` + `postgresql`
   - `flyway-core` + `flyway-database-postgresql`
   - `springdoc-openapi-starter-webmvc-ui` (Swagger)

### Frontend (`rts-caisse-web`)

1. Copier `frontend/environment.ts` vers `src/environments/environment.ts`.
2. Copier `frontend/environment.production.ts` vers `src/environments/environment.production.ts`.
3. Copier `frontend/proxy.conf.json` à la **racine** du projet Angular.
4. Vérifier `angular.json` :
   ```json
   "configurations": {
     "production": {
       "fileReplacements": [{
         "replace": "src/environments/environment.ts",
         "with":    "src/environments/environment.production.ts"
       }]
     }
   },
   "serve": {
     "options": {
       "proxyConfig": "proxy.conf.json"
     }
   }
   ```

---

## Variables d'environnement (`.env`)

| Variable | Obligatoire | Défaut | Description |
|---|---|---|---|
| `POSTGRES_DB` | non | `rts_caisse_db` | Nom de la base |
| `POSTGRES_USER` | non | `rts_user` | User DB |
| `POSTGRES_PASSWORD` | **oui** | — | Mot de passe DB (fort) |
| `POSTGRES_HOST_PORT` | non | `5432` | Port Postgres exposé en DEV |
| `JWT_SECRET` | **oui** | — | Clé HMAC pour signer les JWT (≥ 64 octets Base64) |
| `GATEWAY_PORT` | non | `8090` | Port HTTP exposé sur l'hôte |
| `CORS_ALLOWED_ORIGINS` | non | `http://localhost:8090` | Origines CORS acceptées |
| `APP_VERSION` | non | `latest` | Tag des images Docker Hub |
| `DOCKER_REGISTRY` | non | `vnsu` | Namespace Docker Hub |
| `PGADMIN_EMAIL` | non | `admin@rts.local` | Login pgAdmin (DEV) |
| `PGADMIN_PASSWORD` | non | `admin` | Mot de passe pgAdmin (DEV) |
| `PGADMIN_PORT` | non | `5050` | Port pgAdmin (DEV) |

---

## Commandes utiles

```powershell
# Logs
powershell -ExecutionPolicy Bypass -File .\scripts\logs.ps1                          # tous les logs (build local)
powershell -ExecutionPolicy Bypass -File .\scripts\logs.ps1 backend                  # un service
powershell -ExecutionPolicy Bypass -File .\scripts\logs.ps1 backend -Mode prod       # mode prod

# Arrêt
powershell -ExecutionPolicy Bypass -File .\scripts\stop.ps1                          # build local
powershell -ExecutionPolicy Bypass -File .\scripts\stop.ps1 dev                      # dev
powershell -ExecutionPolicy Bypass -File .\scripts\stop.ps1 prod                     # prod
powershell -ExecutionPolicy Bypass -File .\scripts\stop.ps1 all                      # tout

# Reset DB (dev uniquement)
powershell -ExecutionPolicy Bypass -File .\scripts\reset-db.ps1

# Inspection
docker compose ps
docker stats
docker exec -it rts-caisse-postgres psql -U rts_user -d rts_caisse_db
```

---

## Dépannage

### Le backend redémarre en boucle

Vérifier les logs : `docker compose logs backend`

Causes courantes :
- Postgres pas encore prêt → ajuster `start_period` dans le healthcheck
- Mauvais mot de passe DB → vérifier `.env`
- Migration Flyway en échec → checker la table `flyway_schema_history`
- `JWT_SECRET` trop court → minimum 64 octets Base64

### Le frontend reste en blanc

Vérifier que `dist/rts-caisse-web/browser` existe bien après le build. Si Angular utilise un autre nom de projet, ajuster le `COPY --from=build` dans `frontend/Dockerfile`.

### `502 Bad Gateway` sur les appels API

Le gateway n'arrive pas à joindre `backend:9090`. Vérifier :
- `server.port=9090` dans `application-docker.properties`
- Le backend est `healthy` : `docker compose ps`
- Le réseau Docker est bien partagé : `docker network inspect rts-caisse-network`

### Port 8090 déjà utilisé

Modifier `GATEWAY_PORT` dans le `.env` (ex: `8091`).

### CORS errors en dev

Si tu utilises `ng serve` SANS `proxy.conf.json`, le frontend appelle `localhost:9090` directement et déclenche CORS. Solution :
- soit lancer avec `ng serve --proxy-config proxy.conf.json`
- soit ajouter `http://localhost:4200` dans `CORS_ALLOWED_ORIGINS`

---

## Points de sécurité pour la production

1. **HTTPS obligatoire** : placer un Caddy / Traefik / Nginx avec Let's Encrypt **devant** le gateway (ou activer TLS dans le gateway).
2. **`server_tokens off`** : déjà appliqué dans la conf nginx.
3. **Désactiver Swagger** : commenter le bloc `swagger-ui` dans `gateway/default.conf` en prod, ou utiliser `springdoc.swagger-ui.enabled=false`.
4. **Restreindre `/actuator`** : exposer uniquement `/actuator/health` publiquement, le reste derrière auth.
5. **Backups Postgres** : volume `rts-caisse-postgres-data` à backuper régulièrement (`pg_dump`).
6. **Rotation `JWT_SECRET`** : prévoir un mécanisme de rotation ; un changement invalide tous les tokens existants.
