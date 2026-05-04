# Backend RTS Caisse — Améliorations v1.1

Ce paquet apporte trois améliorations majeures au backend :

1. **Migration Flyway** remplace `ddl-auto=update` (schéma versionné et reproductible)
2. **Export PDF des reçus** via OpenPDF (nouveau endpoint `/api/recus/operation/{id}`)
3. **Tests d'intégration JUnit 5 + Testcontainers** sur un PostgreSQL isolé

---

## Installation dans le projet existant

### 1. Remplacer `pom.xml`

Remplace ton fichier `pom.xml` par celui fourni ici. Nouvelles dépendances ajoutées :

- `org.flywaydb:flyway-core` et `flyway-database-postgresql` (dans la BOM Spring Boot)
- `com.github.librepdf:openpdf:2.0.3`
- `org.testcontainers:junit-jupiter` et `postgresql` (scope test) via BOM
- La BOM Testcontainers est importée pour gérer les versions transitives

### 2. Remplacer `application.properties`

Remplace `src/main/resources/application.properties` par celui fourni. Changements clés :

```properties
spring.jpa.hibernate.ddl-auto=validate   # était 'update'
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
```

**`baseline-on-migrate=true`** est important : si ta base contient déjà des
tables créées par Hibernate, Flyway posera un repère "baseline" au lieu d'échouer
en voulant rejouer V1. Si tu démarres sur une base vierge, le comportement est
identique.

### 3. Ajouter les fichiers de migration

Copie les scripts SQL dans `src/main/resources/db/migration/` :

- `V1__initial_schema.sql` : reprend le schéma complet généré jusqu'ici par Hibernate
- `V2__indexes_and_constraints.sql` : index trigram + contraintes métier supplémentaires

**Convention Flyway** : le nommage `V{version}__{description}.sql` est strict —
le double underscore est obligatoire.

### 4. Ajouter le service et le controller PDF

Copie les deux nouveaux fichiers :

- `src/main/java/sn/rts/caisse/service/RecuPdfService.java`
- `src/main/java/sn/rts/caisse/controller/RecuPdfController.java`

### 5. Patcher `SecurityConfig.java`

Ouvre `src/main/java/sn/rts/caisse/config/SecurityConfig.java` et ajoute une
règle d'accès pour `/api/recus/**`. Voir `SecurityConfig-patch.txt` pour
l'emplacement exact.

### 6. Copier les tests

Les fichiers de test vont dans `src/test/java/sn/rts/caisse/integration/` :

- `AbstractTestcontainersIT.java` — classe de base qui démarre PostgreSQL
- `OperationCaisseServiceIT.java` — tests du cœur métier (6 scénarios)
- `JournalCaisseServiceIT.java` — ouverture/clôture/validation (5 scénarios)
- `AuthControllerIT.java` — endpoint de connexion via MockMvc (3 scénarios)
- `RecuPdfServiceIT.java` — génération PDF (2 scénarios)

---

## Utilisation

### Lancer les tests d'intégration

**Prérequis** : Docker doit tourner sur la machine (Testcontainers utilise Docker).

```bash
mvn test
```

Au premier lancement, Testcontainers télécharge l'image `postgres:16-alpine`
(~80 Mo) et démarre un conteneur éphémère. Ce conteneur est **réutilisé** entre
les tests grâce à `withReuse(true)`, ce qui rend la suite rapide
(~4–5 secondes pour les 16 tests une fois l'image téléchargée).

Pour voir les logs détaillés :
```bash
mvn test -Dspring-boot.run.profiles=test -X
```

### Tester le PDF

Une fois le backend démarré, connecte-toi pour obtenir un JWT, puis :

```bash
# Télécharger le reçu (sauvegarde en local)
curl http://localhost:9090/api/recus/operation/1 \
  -H "Authorization: Bearer $TOKEN" \
  -o recu-1.pdf

# Affichage inline dans un onglet navigateur
curl http://localhost:9090/api/recus/operation/1?inline=true \
  -H "Authorization: Bearer $TOKEN" \
  --output recu-inline.pdf
```

Le PDF généré est au format **A6** (105 × 148 mm) — taille ticket — avec :

- En-tête RTS bleu
- Numéro de reçu proéminent
- Tableau des détails (caisse, caissier, catégorie, mode paiement, client,
  référence)
- Bloc montant en très grandes lettres (encadré vert pour les entrées,
  rouge pour les sorties)
- Motif en pleine largeur
- Bandeau d'annulation rouge si l'opération a été annulée
- Pied de page

### Intégration avec le client Angular

Pour ajouter un bouton "Télécharger PDF" dans l'écran des opérations Angular,
appelle l'endpoint en tant que blob :

```typescript
import { HttpClient } from '@angular/common/http';

telechargerRecu(operationId: number): void {
  this.http.get(`${environment.apiUrl}/recus/operation/${operationId}`, {
    responseType: 'blob'
  }).subscribe(blob => {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `recu-${operationId}.pdf`;
    a.click();
    window.URL.revokeObjectURL(url);
  });
}
```

---

## Flyway — commandes utiles

**Vérifier l'état des migrations** :

```bash
mvn flyway:info
```

**Forcer une baseline** (si tu as une base existante et veux partir proprement) :

```bash
mvn flyway:baseline
```

**Créer une nouvelle migration** : crée un fichier
`V3__description_changement.sql` dans `src/main/resources/db/migration/` et
redémarre l'application — Flyway l'applique automatiquement au démarrage.

**Règles d'or** :

- Jamais modifier une migration déjà appliquée en production (créer plutôt `V4`)
- Toujours tester les migrations sur une copie avant de les déployer
- Préférer des migrations **idempotentes** (`IF NOT EXISTS`, `IF EXISTS`) quand
  c'est possible

---

## Vérifications post-installation

Après avoir redémarré le backend, vérifie :

1. **Logs Flyway** au démarrage — tu dois voir quelque chose comme :
   ```
   Flyway Community Edition ... by Redgate
   Database: jdbc:postgresql://localhost:5432/rts_caisse_db
   Successfully validated 2 migrations
   Schema baselined with version: 1 (si base préexistante)
   ```

2. **Table `flyway_schema_history`** créée dans PostgreSQL — elle trace toutes
   les migrations appliquées.

3. **Swagger UI** accessible sur `http://localhost:9090/swagger-ui.html` — le
   nouveau endpoint `/api/recus/operation/{operationId}` apparaît dans la
   section "Reçus PDF".

4. **Tests passent** : `mvn test` → 16 tests verts.

---

© RTS — Direction des Systèmes d'Information, 2026
