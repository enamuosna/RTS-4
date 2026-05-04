# RTS Caisse — Client Web (Angular)

Interface web d'administration et de supervision du système de gestion de caisse de la
**Radiodiffusion Télévision Sénégalaise**. Construite avec **Angular 17** (standalone components),
**Angular Material** et **Chart.js** pour les graphiques.

Consomme l'API REST du backend Spring Boot sur `http://localhost:9090`.

---

## Stack technique

| Composant          | Version  |
|--------------------|----------|
| Angular            | 17.3     |
| Angular Material   | 17.3     |
| Chart.js + ng2-charts | 4.4 / 5.0 |
| TypeScript         | 5.4      |
| RxJS               | 7.8      |

Approche moderne : **standalone components**, **signals**, **inject()**, **lazy-loading** par route,
**OnPush** partout.

---

## Prérequis

1. **Node.js 18+** (`node --version`)
2. **npm 9+**
3. **Angular CLI** : `npm install -g @angular/cli`
4. Le **backend RTS Caisse** doit tourner sur `http://localhost:9090`
   (voir le README du backend).

---

## Installation

```bash
cd rts-caisse-web
npm install
```

## Démarrage en développement

```bash
npm start
# ou : ng serve
```

L'application est alors disponible sur <http://localhost:4200>.

Se connecter avec le compte admin par défaut (créé automatiquement par le backend) :

- **Login** : `admin`
- **Mot de passe** : `Admin@2026`

## Build de production

```bash
npm run build
```

Les artéfacts sont générés dans `dist/rts-caisse-web/`.

---

## Architecture

```
src/
├── environments/          # URL de l'API (port 9090)
├── app/
│   ├── app.config.ts      # Providers globaux (router, http, interceptors, locale fr)
│   ├── app.routes.ts      # Routes avec lazy-loading et guards par rôle
│   ├── app.component.ts   # Racine (router-outlet)
│   ├── core/
│   │   ├── models/        # Interfaces TS miroirs des DTOs Spring
│   │   ├── services/      # AuthService, et services métier par domaine
│   │   ├── guards/        # authGuard + roleGuard(['ADMIN', ...])
│   │   └── interceptors/  # jwtInterceptor, errorInterceptor
│   ├── layout/
│   │   └── main-layout/   # Sidenav + toolbar, navigation filtrée par rôle
│   └── features/
│       ├── auth/login/
│       ├── dashboard/     # KPIs + Chart.js (doughnut + bar)
│       ├── utilisateurs/
│       ├── caisses/       # Cards + affectation caissier + suspension
│       ├── categories/    # Liste + filtre type + CRUD
│       ├── clients/       # Recherche avec debounce
│       ├── operations/    # Table paginée + annulation
│       └── journaux/      # Table écarts + validation superviseur
```

---

## Fonctionnalités par rôle

### 🔵 ADMIN
- Accès total (dashboard, utilisateurs, caisses, catégories, clients, opérations, journaux)
- Création/modification/désactivation d'utilisateurs
- Paramétrage des catégories comptables
- Validation finale des clôtures de caisse

### 🟡 SUPERVISEUR
- Dashboard consolidé avec répartition par caisse et par catégorie
- Consultation toutes caisses, clients, opérations
- Validation des clôtures

### ⚪ CAISSIER
- Vue sur les caisses, clients, opérations (lecture)
- Le gros de son travail se fait depuis le client **JavaFX** au guichet

La sidenav n'affiche que les entrées de menu autorisées pour le rôle courant
(voir `MainLayoutComponent.visibleNavItems`).

---

## Sécurité

- **JWT** stocké dans `localStorage` (clés : `rts_caisse_token` et `rts_caisse_user`)
- `jwtInterceptor` injecte automatiquement `Authorization: Bearer <token>` sur chaque requête
  (sauf `/auth/login` qui reste public)
- `errorInterceptor` gère globalement :
  - `401` → déconnexion automatique + redirection `/login`
  - `403` → snackbar "Accès refusé"
  - validation backend → snackbar listant les champs invalides
  - 0 (serveur injoignable) → message approprié
- `authGuard` bloque les routes privées et conserve l'URL demandée via `queryParams.redirect`
  pour y retourner après login
- `roleGuard(['ADMIN'])` restreint par rôle

---

## Dashboard

Le dashboard (accessible aux ADMIN et SUPERVISEUR) propose :

- **4 KPIs** : Entrées, Sorties, Solde net, Nombre d'opérations + caisses ouvertes
- **Doughnut Chart.js** : répartition des entrées par caisse (palette RTS)
- **Bar Chart.js** : entrées (vert) vs sorties (rouge) par catégorie
- **Table de détail par caisse** : entrées, sorties, solde
- **Table de détail par catégorie** : avec badge type + nombre d'opérations + montant

Les graphiques sont construits en `computed()` : changement de date → nouveau chargement → charts se rafraîchissent automatiquement.

---

## Configuration

L'URL du backend se configure dans `src/environments/environment.ts` et
`environment.development.ts` :

```ts
apiUrl: 'http://localhost:9090/api'
```

Pour un déploiement en production, mettre à jour `environment.ts` pour pointer
sur l'URL publique derrière nginx, avec HTTPS obligatoire.

---

## Dépendances externes (CDN)

Le fichier `index.html` charge :
- **Roboto** (Google Fonts)
- **Material Icons** (Google)

Pour un usage en intranet sans Internet, télécharger ces ressources localement et mettre à jour les liens.

---

## Commandes utiles

```bash
npm start              # Serveur de développement sur :4200
npm run build          # Build de production
npm run watch          # Build en mode watch (dev)
npm test               # Tests unitaires (Karma + Jasmine)
```

---

© RTS - Direction des Systèmes d'Information — 2026


 attaquer :

🖥️ Client JavaFX guichet — login, écran caissier (ouvrir caisse, saisir opération, clôturer), impression reçu
🔧 Affiner le backend — tests JUnit + Testcontainers, export PDF reçus (OpenPDF), Flyway à la place de ddl-auto=update
📊 Enrichir l'Angular — filtres avancés sur opérations (date, type, montant), export Excel des journaux, graphique d'évolution temporelle sur le dashboard
🚀 Dockerisation — Dockerfile backend + frontend + docker-compose avec PostgreSQL pour déploiement one-shot