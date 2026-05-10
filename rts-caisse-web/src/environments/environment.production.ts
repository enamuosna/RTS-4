// =============================================================
//  Environnement Angular - PRODUCTION (HTTPS)
//  À placer dans : rts-caisse-web/src/environments/environment.production.ts
// =============================================================
//
//  L'URL de l'API est RELATIVE : "/api" au lieu de "https://...".
//  Avantages :
//    1. Une seule build, deployable sous n'importe quel domaine
//       (rts-caisse.duckdns.org, autre.example.com, IP directe...)
//    2. Pas de probleme de mixed-content : si le SPA est servi en
//       HTTPS, /api est aussi en HTTPS automatiquement.
//    3. Pas de CORS : le gateway nginx fait passer /api/* vers
//       le backend sur la MEME origine. Le navigateur ne voit
//       qu'une seule origine, donc CORS n'est pas declenche.
//
//  Le gateway nginx (rts-caisse-docker/gateway/default.conf) route :
//    https://rts-caisse.duckdns.org/api/*  ->  backend:9090
//    https://rts-caisse.duckdns.org/        ->  frontend:80 (cette SPA)
// =============================================================

export const environment = {
  production: true,

  // URL de l'API en RELATIF (cf. explication ci-dessus)
  // ATTENTION : ne JAMAIS mettre "https://localhost:9090/api" ou
  // "https://rts-caisse.duckdns.org/api" en dur ici. Toujours "/api".
  apiUrl: '/api',

  // Nom de l'application affiche dans la barre de titre, le footer, etc.
  appName: 'RTS Caisse',

  // Version (peut etre injectee au build via npm version + sed)
  version: '1.0.0',

  // Active les logs verbeux dans la console du navigateur
  // En production : false pour ne pas exposer les details internes
  debug: false,

  // Timeout par defaut des appels HTTP (en ms)
  // En prod, derriere un reverse proxy : 30s suffisent largement
  httpTimeoutMs: 30000,

  // URL du backoffice de monitoring (optionnel, pour les admins)
  // Affiche dans le menu admin si non vide
  // En interne, on peut pointer vers /actuator (protege par Spring Security)
  adminMonitoringUrl: '/actuator',
};
