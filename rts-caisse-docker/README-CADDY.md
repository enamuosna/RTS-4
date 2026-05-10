# RTS Caisse — Migration vers Caddy

## Pourquoi Caddy ?

Pour ce projet avec **2 services derrière un reverse proxy + SSL Let's Encrypt**, Caddy élimine 90 % de la complexité du setup nginx + certbot.

| | nginx + certbot (avant) | Caddy (maintenant) |
|---|---|---|
| Fichiers de config | `default.conf` (200 lignes) + `Dockerfile` + `init-letsencrypt.sh` + `init-letsencrypt.ps1` | `Caddyfile` (50 lignes) |
| Bootstrap initial | Script de 200 lignes (cert factice → vrai cert) | `docker compose up -d` |
| Renouvellement | Service certbot dédié + cron + reload nginx | Automatique en interne |
| HTTP/3 | Non | Oui (port UDP 443) |
| OCSP stapling | Manuel à activer | Automatique |
| HSTS | Manuel | 1 ligne |
| Mode staging Let's Encrypt | Variable + relancer le bootstrap | Décommenter 1 ligne |

## Démarrage rapide

### 1. Arrêter l'ancienne stack nginx

```bash
cd rts-caisse-docker
docker compose -f docker-compose.prod.yml down
```

> Les volumes Postgres et tes données restent intacts. On supprime juste les conteneurs.

### 2. Préparer la structure Caddy

```
rts-caisse-docker/
├── docker-compose.caddy.yml         ← nouveau
├── caddy/
│   └── Caddyfile                    ← nouveau
├── .env                              ← inchangé
└── (anciens fichiers gateway/, scripts/ → peuvent être supprimés)
```

### 3. Variables d'env (`.env`)

Identique à avant — Caddy lit `SERVER_NAME` et `LETSENCRYPT_EMAIL` :

```ini
SERVER_NAME=rts-caisse.duckdns.org
LETSENCRYPT_EMAIL=admin@rts.sn
POSTGRES_PASSWORD=mot-de-passe-fort
JWT_SECRET=base64-standard-genere-avec-openssl
APP_VERSION=latest
DOCKER_REGISTRY=vnsu
```

> ⚠️ Le `JWT_SECRET` doit toujours être en **Base64 standard** (sans `_` ni `-`). Voir le patch précédent de `JwtService.java`.

### 4. Premier démarrage

```bash
docker compose -f docker-compose.caddy.yml up -d
```

C'est tout. Caddy va :
1. Démarrer immédiatement avec un cert auto-signé temporaire (10 secondes)
2. Lancer en arrière-plan le challenge ACME HTTP-01 sur le port 80
3. Recevoir le vrai cert Let's Encrypt (~30 secondes)
4. Recharger automatiquement la config TLS
5. Servir l'app en HTTPS reconnu

Suivre la progression :

```bash
docker compose -f docker-compose.caddy.yml logs -f caddy
```

Tu verras :
```
{"level":"info","msg":"obtaining certificate","domain":"rts-caisse.duckdns.org"}
{"level":"info","msg":"certificate obtained successfully"}
{"level":"info","msg":"served","status":200,...}
```

### 5. Vérifier

Depuis n'importe quel poste :

```powershell
# Test du cert
$tcp = [System.Net.Sockets.TcpClient]::new('rts-caisse.duckdns.org', 443)
$ssl = [System.Net.Security.SslStream]::new($tcp.GetStream(), $false, {$true})
$ssl.AuthenticateAsClient('rts-caisse.duckdns.org')
"Issuer: $($ssl.RemoteCertificate.Issuer)"
$tcp.Close()
```

→ Doit afficher `CN=R10` ou `R11` (Let's Encrypt production).

Le client JavaFX se connecte alors directement à `https://rts-caisse.duckdns.org/api`.

## Mode test (staging Let's Encrypt)

Pour tester le pipeline sans consommer le quota de 5 certs/semaine en production, **décommenter une ligne** dans `caddy/Caddyfile` :

```caddyfile
{
    email {$LETSENCRYPT_EMAIL}
    acme_ca https://acme-staging-v02.api.letsencrypt.org/directory
}
```

Puis :

```bash
# Supprimer les certs staging du volume avant de repasser en prod
docker compose -f docker-compose.caddy.yml down
docker volume rm rts-caisse-caddy-data
docker compose -f docker-compose.caddy.yml up -d
```

> Avec Caddy, tu peux faire l'aller-retour staging/prod en éditant 1 ligne. Pas de script complexe.

## Renouvellement automatique

**Aucune configuration**. Caddy tente un renouvellement automatique chaque jour. Quand un cert atteint 30 jours avant expiration, il est renouvelé en arrière-plan, et la nouvelle clé est rechargée à chaud — aucune interruption de service.

Vérifier la prochaine date de renouvellement :

```bash
docker compose -f docker-compose.caddy.yml exec caddy caddy list-certs
```

## Sauvegarde des certs

Pour migrer vers un autre serveur sans redemander de cert (et consommer un crédit) :

```bash
# Sauvegarde
docker run --rm -v rts-caisse-caddy-data:/data -v "$(pwd)":/backup alpine \
    tar czf /backup/caddy-backup.tar.gz -C /data .

# Restauration sur nouveau serveur
docker volume create rts-caisse-caddy-data
docker run --rm -v rts-caisse-caddy-data:/data -v "$(pwd)":/backup alpine \
    tar xzf /backup/caddy-backup.tar.gz -C /data
```

## Commandes utiles

```bash
# Logs Caddy en temps reel
docker compose -f docker-compose.caddy.yml logs -f caddy

# Recharger la conf apres modif du Caddyfile (sans downtime)
docker compose -f docker-compose.caddy.yml exec caddy caddy reload \
    --config /etc/caddy/Caddyfile

# Forcer un renouvellement de cert (test)
docker compose -f docker-compose.caddy.yml exec caddy caddy reload

# Tester la syntaxe du Caddyfile avant reload
docker compose -f docker-compose.caddy.yml exec caddy caddy validate \
    --config /etc/caddy/Caddyfile

# Stop complet
docker compose -f docker-compose.caddy.yml down

# Recreate complet (sans toucher aux volumes)
docker compose -f docker-compose.caddy.yml down
docker compose -f docker-compose.caddy.yml up -d
```

## Dépannage

### « Le cert n'est pas obtenu »
- Vérifier que les ports 80 ET 443 sont accessibles depuis Internet
- Vérifier que `rts-caisse.duckdns.org` résout vers `41.216.16.108`
- Logs : `docker compose -f docker-compose.caddy.yml logs caddy | grep -i acme`

### « Je veux servir mon propre cert (pas Let's Encrypt) »
Mettre les fichiers dans `caddy/certs/` puis modifier le Caddyfile :

```caddyfile
{$SERVER_NAME} {
    tls /certs/cert.pem /certs/key.pem
    # ... reste de la config
}
```

Et mounter le dossier dans le compose :
```yaml
volumes:
  - ./caddy/certs:/certs:ro
```

### « Trop de logs »
Mettre `level WARN` dans le bloc `log` du Caddyfile et reload.

### « Caddy redémarre en boucle »
Valider la syntaxe avant de redémarrer :
```bash
docker run --rm -v "$(pwd)/caddy/Caddyfile":/etc/caddy/Caddyfile caddy:2.8-alpine \
    caddy validate --config /etc/caddy/Caddyfile
```

## Anciens fichiers à supprimer

Une fois Caddy validé, ces fichiers ne servent plus à rien :

```
rts-caisse-docker/
├── gateway/                          ❌ supprimer
│   ├── Dockerfile
│   └── default.conf
├── scripts/
│   ├── init-letsencrypt.sh           ❌ supprimer
│   ├── init-letsencrypt.ps1          ❌ supprimer
│   ├── promote-cert-to-prod.sh       ❌ supprimer
│   └── check-ssl.sh                  ❌ supprimer
├── docker-compose.prod.yml           ❌ supprimer
└── (garder duckdns-update.sh/.ps1 si IP dynamique)
```

À garder : les scripts `duckdns-update.*` si SONATEL te donne une IP publique dynamique.
