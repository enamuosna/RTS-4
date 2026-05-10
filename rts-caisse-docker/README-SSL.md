# RTS Caisse - Déploiement HTTPS avec Let's Encrypt + DuckDNS

Guide complet pour mettre l'application en production sécurisée (HTTPS) avec un nom de domaine gratuit.

## Architecture réseau

```
┌─────────────────────────────────────────────────────────────────┐
│                          INTERNET                                │
│                                                                  │
│  Navigateur / JavaFX desktop                                    │
│           │                                                      │
│           │ HTTPS (443) vers rts-caisse.duckdns.org             │
│           ▼                                                      │
│   DNS DuckDNS  ──►  41.216.16.108  (IP publique SONATEL)        │
└───────────────────────────────────┬──────────────────────────────┘
                                    │
                                    │ NAT / Port Forwarding
                                    │ 80  → 192.168.150.251:80
                                    │ 443 → 192.168.150.251:443
                                    ▼
┌──────────────────────────────────────────────────────────────────┐
│  ROUTEUR / FIREWALL  (LAN entreprise)                            │
└───────────────────────────────────┬──────────────────────────────┘
                                    │
                                    ▼  192.168.150.251
┌──────────────────────────────────────────────────────────────────┐
│  SERVEUR DOCKER                                                   │
│                                                                   │
│  ┌─────────────────────────┐    ┌──────────────────────────┐    │
│  │   gateway (nginx)       │    │   certbot                │    │
│  │   - 80  : ACME + 301    │◄───┤   renew toutes les 12h   │    │
│  │   - 443 : SSL           │    │   shared volume certs    │    │
│  └────────────┬────────────┘    └──────────────────────────┘    │
│               │                                                   │
│      ┌────────┴────────┐                                         │
│      ▼                 ▼                                         │
│  ┌─────────┐      ┌──────────────┐      ┌──────────────────┐   │
│  │frontend │      │ backend      │─────►│ postgres         │   │
│  │Angular  │      │ Spring Boot  │      │ rts-caisse-db    │   │
│  │(:80)    │      │ (:9090)      │      │                  │   │
│  └─────────┘      └──────────────┘      └──────────────────┘   │
└───────────────────────────────────────────────────────────────────┘
```

## Prérequis

### Côté hébergement
- [x] Serveur Docker accessible sur `192.168.150.251` (LAN)
- [x] IP publique : `41.216.16.108`
- [x] Routeur/box capable de faire du **port forwarding** des ports `80` et `443`
- [x] Docker Engine 20.10+ et Docker Compose v2

### Côté DNS
- [x] Compte DuckDNS (gratuit, login GitHub/Google) sur https://www.duckdns.org/
- [x] Un sous-domaine choisi (ex: `rts-caisse.duckdns.org`)
- [x] Token DuckDNS (visible sur la page d'accueil après login)

## Étape 1 — Configurer le port forwarding

Sur ton routeur, créer **deux règles NAT** vers `192.168.150.251` :

| Port externe (WAN) | Protocole | IP interne | Port interne |
|---|---|---|---|
| 80 | TCP | 192.168.150.251 | 80 |
| 443 | TCP | 192.168.150.251 | 443 |

> **Pourquoi le port 80 aussi ?** Let's Encrypt utilise le challenge HTTP-01 sur le port 80 pour vérifier que tu contrôles bien le domaine. Sans ce port, **le certificat ne peut pas être généré ni renouvelé**.

Vérifier depuis l'extérieur (téléphone en 4G par exemple) :

```bash
curl -I http://41.216.16.108
# Doit retourner du nginx (pas timeout)
```

## Étape 2 — Créer le sous-domaine DuckDNS

1. Aller sur https://www.duckdns.org/ et se connecter
2. Dans **subdomain**, taper `rts-caisse` (ou autre) puis cliquer **add domain**
3. Dans la colonne **current ip**, entrer `41.216.16.108` puis cliquer **update ip**
4. Récupérer ton **token** affiché en haut de la page

Vérification :

```bash
nslookup rts-caisse.duckdns.org
# Doit retourner 41.216.16.108
```

## Étape 3 — Préparer la configuration Docker

Structure attendue :

```
rts-caisse-docker/
├── docker-compose.prod.yml
├── .env                          ← à créer depuis .env.prod.example
├── gateway/
│   ├── Dockerfile
│   └── default.conf
└── scripts/
    ├── init-letsencrypt.sh
    ├── init-letsencrypt.ps1
    ├── duckdns-update.sh
    ├── duckdns-update.ps1
    └── check-ssl.sh
```

Copier le template d'environnement :

```bash
cp .env.prod.example .env
```

Éditer `.env` et remplir au minimum :

```ini
SERVER_NAME=rts-caisse.duckdns.org
DUCKDNS_TOKEN=ton-token-duckdns-xxxxx
LETSENCRYPT_EMAIL=ton.email@exemple.com
LETSENCRYPT_STAGING=1                     # ← commencer en staging !

POSTGRES_PASSWORD=mot-de-passe-fort-16-car
JWT_SECRET=                                # générer avec : openssl rand -base64 64
```

## Étape 4 — Construire les images Docker

Si tu utilises ton `docker-compose.yml` existant pour le build local :

```bash
docker compose build
docker tag vnsu/rts-caisse-gateway:latest vnsu/rts-caisse-gateway:latest
```

Sinon, builder uniquement le gateway (qui a changé) :

```bash
cd gateway/
docker build -t vnsu/rts-caisse-gateway:latest .
cd ..
```

## Étape 5 — Premier démarrage (bootstrap SSL)

> ⚠️ **Garde `LETSENCRYPT_STAGING=1` pour le premier essai.** Let's Encrypt limite à **5 certificats par semaine et par domaine** en production. En staging, la limite est de 30 000/semaine — on peut se tromper sans risque.

Linux/macOS :

```bash
chmod +x scripts/*.sh
bash scripts/init-letsencrypt.sh
```

Windows :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-letsencrypt.ps1
```

Le script va :
1. Générer un cert auto-signé temporaire
2. Démarrer nginx avec ce faux cert
3. Demander le vrai cert à Let's Encrypt (challenge HTTP-01)
4. Recharger nginx avec le vrai cert
5. Démarrer toute la stack

## Étape 6 — Vérifier que tout fonctionne

```bash
bash scripts/check-ssl.sh
```

Tester depuis un navigateur :
- https://rts-caisse.duckdns.org/login → SPA Angular
- https://rts-caisse.duckdns.org/health → `{"status":"UP"}`
- https://rts-caisse.duckdns.org/swagger-ui.html → doc API

> En mode staging, le navigateur affichera **« connexion non sécurisée »**. C'est normal : le certificat de test n'est pas signé par une autorité reconnue. Cliquer **avancé → continuer** pour valider que le pipeline fonctionne.

## Étape 7 — Passer en production réelle

Une fois le pipeline staging validé :

1. Éditer `.env` :
   ```ini
   LETSENCRYPT_STAGING=0
   ```
2. Re-lancer le bootstrap (il va supprimer le cert staging et demander un vrai) :
   ```bash
   bash scripts/init-letsencrypt.sh
   ```
3. Le navigateur affiche maintenant le cadenas vert ✓

## Renouvellement automatique

Aucune action manuelle requise. Le service `certbot` tourne en boucle dans Docker :
- Tente un renouvellement **toutes les 12 heures**
- Let's Encrypt accepte le renouvellement quand le cert a **moins de 30 jours** restants
- nginx **se recharge automatiquement toutes les 6 heures** pour prendre en compte les nouveaux certs

Vérifier l'état du renouvellement :

```bash
docker compose -f docker-compose.prod.yml logs certbot --tail=50
docker compose -f docker-compose.prod.yml exec certbot certbot certificates
```

## Mise à jour DuckDNS si IP dynamique

Si SONATEL te donne une IP **dynamique** (vérifie avec ton FAI), planifier la mise à jour DuckDNS toutes les 5 minutes :

**Linux (cron) :**
```cron
*/5 * * * * cd /chemin/rts-caisse-docker && bash scripts/duckdns-update.sh > /dev/null 2>&1
```

**Windows (Task Scheduler en admin) :**
```powershell
$action  = New-ScheduledTaskAction -Execute 'powershell.exe' `
           -Argument '-NoProfile -ExecutionPolicy Bypass -File C:\rts-caisse-docker\scripts\duckdns-update.ps1' `
           -WorkingDirectory 'C:\rts-caisse-docker'
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
           -RepetitionInterval (New-TimeSpan -Minutes 5)
Register-ScheduledTask -TaskName 'DuckDNS-RTS-Caisse' `
           -Action $action -Trigger $trigger `
           -RunLevel Highest -User 'SYSTEM'
```

## Mises à jour côté Angular (frontend)

Vérifier `src/environments/environment.production.ts` : l'URL doit rester **relative** (`/api`) — comme ça l'application fonctionne automatiquement en HTTPS sans rebuild :

```typescript
export const environment = {
  production: true,
  apiUrl: '/api'   // ← relatif, pas https://... en dur
};
```

## Mises à jour côté JavaFX desktop

Dans le client lourd, l'utilisateur peut maintenant saisir l'URL HTTPS depuis l'écran de configuration :

```
https://rts-caisse.duckdns.org/api
```

Le `Config.normalize()` existant accepte déjà `https://`. La JVM Java 11+ reconnaît le cert Let's Encrypt nativement (root **ISRG Root X1** présent dans le `cacerts` depuis 2016).

> Si la JVM est < 11 ou un very-old JRE, importer manuellement la racine ISRG :
> ```bash
> keytool -import -trustcacerts -keystore "$JAVA_HOME/lib/security/cacerts" \
>   -alias isrg-root-x1 -file isrgrootx1.pem -storepass changeit
> ```

## Mises à jour côté backend (CORS)

Le `docker-compose.prod.yml` injecte automatiquement `CORS_ALLOWED_ORIGINS=https://${SERVER_NAME}` à partir de ta variable `SERVER_NAME`. Aucun changement manuel dans `application-docker.properties` n'est nécessaire.

Si tu veux autoriser d'autres origines (mobile app, partenaires...) :

```ini
# .env
CORS_ALLOWED_ORIGINS=https://rts-caisse.duckdns.org,https://app-mobile.example.com
```

Et dans `docker-compose.prod.yml`, remplacer la ligne :
```yaml
CORS_ALLOWED_ORIGINS: https://${SERVER_NAME},http://localhost:8090
```
par :
```yaml
CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
```

## Dépannage

### Le challenge HTTP-01 échoue : « Connection refused »
Le port 80 n'est pas accessible depuis Internet. Vérifier :
- Port forwarding du routeur (WAN 80 → 192.168.150.251:80)
- Aucun pare-feu Windows/iptables n'écoute sur 80
- Le service `gateway` est bien `Up` : `docker compose ps`

### « DNS problem: NXDOMAIN looking up A »
DuckDNS n'a pas propagé. Attendre 1–2 min. Vérifier :
```bash
nslookup rts-caisse.duckdns.org 8.8.8.8
```

### Limite de 5 certs/semaine atteinte
Tu es en mode production et tu as relancé trop de fois. Repasser en `LETSENCRYPT_STAGING=1` pendant 7 jours, puis revenir en prod.

### Le client JavaFX échoue avec « PKIX path building failed »
La JVM est trop ancienne. Mettre à jour vers Java 11+ ou importer manuellement la racine ISRG (voir section JavaFX ci-dessus).

### nginx redémarre en boucle au bootstrap
Le cert factice n'a pas été créé. Vérifier :
```bash
docker compose -f docker-compose.prod.yml exec gateway ls /etc/letsencrypt/live/rts-caisse.duckdns.org/
# Doit lister fullchain.pem et privkey.pem
```

## Commandes utiles

```bash
# Logs en temps reel
docker compose -f docker-compose.prod.yml logs -f gateway certbot

# Recharger nginx sans redemarrer (utile apres changement de conf)
docker compose -f docker-compose.prod.yml exec gateway nginx -s reload

# Forcer un renouvellement (test du process)
docker compose -f docker-compose.prod.yml run --rm certbot renew --force-renewal

# Voir les certs et dates d'expiration
docker compose -f docker-compose.prod.yml exec certbot certbot certificates

# Suppression complete des certs (re-bootstrap necessaire)
docker volume rm rts-caisse-letsencrypt-certs

# Sauvegarde des certs (a faire avant migration serveur)
docker run --rm -v rts-caisse-letsencrypt-certs:/data \
    -v "$(pwd)":/backup alpine \
    tar czf /backup/letsencrypt-backup.tar.gz -C /data .
```

## Récapitulatif de ce qui a été ajouté

| Fichier | Rôle |
|---|---|
| `gateway/default.conf` | Conf nginx avec serveur HTTP (ACME + redirect) + serveur HTTPS |
| `gateway/Dockerfile` | Image gateway avec templating de `${SERVER_NAME}` |
| `docker-compose.prod.yml` | Stack avec service `certbot` et volumes partagés |
| `.env.prod.example` | Template avec `SERVER_NAME`, `DUCKDNS_TOKEN`, `LETSENCRYPT_*` |
| `scripts/init-letsencrypt.sh/.ps1` | Bootstrap initial du certificat |
| `scripts/duckdns-update.sh/.ps1` | Synchro DNS DuckDNS ↔ IP publique |
| `scripts/check-ssl.sh` | Diagnostic du pipeline HTTPS |
