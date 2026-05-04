// =============================================================
//  Environnement Angular - DEV (par defaut)
//  À placer dans : rts-caisse-web/src/environments/environment.ts
// =============================================================
//
//  En dev, on tourne avec :
//    - backend Spring Boot dans Rider/IntelliJ sur localhost:9090
//    - frontend en "ng serve" sur localhost:4200
//
//  L'URL de l'API reste RELATIVE ("/api") : ng serve relaie /api/*
//  vers localhost:9090 grace a proxy.conf.json (voir ce fichier).
//  Le code Angular est donc identique en dev et en prod.
// =============================================================

export const environment = {
  production: false,
  apiUrl: '/api',
  appName: 'RTS Caisse (DEV)',
  tokenKey: 'rts_caisse_token',
  userKey: 'rts_caisse_user'
};
