// =============================================================
//  Environnement Angular - PRODUCTION
//  À placer dans : rts-caisse-web/src/environments/environment.production.ts
// =============================================================
//
//  L'URL de l'API est RELATIVE : "/api" au lieu de "http://localhost:9090/api".
//  Le reverse proxy nginx (conteneur "gateway") relaie /api/* vers
//  le backend Spring Boot.
//
//  Avantages :
//    - pas de CORS a configurer (meme origine)
//    - pas d'URL backend en dur dans le JS livre au navigateur
//    - portable : fonctionne identique en staging/prod sur n'importe
//      quel domaine sans rebuild
//
//  Le swap est fait automatiquement par Angular CLI grace a
//  "fileReplacements" dans angular.json (configuration "production").
// =============================================================

export const environment = {
  production: true,
  apiUrl: '/api',
  appName: 'RTS Caisse',
  tokenKey: 'rts_caisse_token',
  userKey: 'rts_caisse_user'
};
