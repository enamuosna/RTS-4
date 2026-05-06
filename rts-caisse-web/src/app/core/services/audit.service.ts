// =====================================================================
//  Service Angular pour la consultation du journal d'audit RTS Caisse
//
//  À placer dans : rts-caisse-web/src/app/services/audit.service.ts
//  (ou ajuster le chemin relatif vers environment selon votre arbo)
//
//  Endpoints backend ciblés :
//    GET /api/audit/logs        — recherche paginée filtrée (ADMIN)
//    GET /api/audit/logs/{id}   — détail (ADMIN)
//
//  Sécurité : le contrôleur backend porte @PreAuthorize("hasRole('ADMIN')")
//  donc les autres rôles recevront 403. Côté client, protéger la route
//  par un adminGuard.
// =====================================================================

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/models';

// =====================================================================
//  Types — alignés sur le backend (sn.rts.caisse.audit.*)
// =====================================================================

/**
 * Toutes les actions auditables du système RTS Caisse.
 * Doit rester synchronisé avec l'enum Java {@code AuditAction}.
 */
export type AuditAction =
  // Authentification
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILED'
  | 'LOGOUT'
  // Caisse / Journal
  | 'OUVRIR_CAISSE'
  | 'CLOTURER_CAISSE'
  | 'VALIDER_JOURNAL'
  | 'CREER_CAISSE'
  | 'MODIFIER_CAISSE'
  | 'SUPPRIMER_CAISSE'
  | 'SUSPENDRE_CAISSE'
  | 'REACTIVER_CAISSE'
  // Opérations
  | 'CREER_OPERATION'
  | 'ANNULER_OPERATION'
  // Reçus / Documents
  | 'IMPRIMER_RECU'
  | 'TELECHARGER_RECU_PDF'
  | 'ENVOYER_WHATSAPP'
  | 'EXPORTER_JOURNAL_EXCEL'
  // Référentiels
  | 'CREER_CATEGORIE'
  | 'MODIFIER_CATEGORIE'
  | 'SUPPRIMER_CATEGORIE'
  | 'CREER_CLIENT'
  | 'MODIFIER_CLIENT'
  | 'SUPPRIMER_CLIENT'
  // Administration utilisateurs
  | 'CREER_UTILISATEUR'
  | 'MODIFIER_UTILISATEUR'
  | 'DESACTIVER_UTILISATEUR'
  | 'REACTIVER_UTILISATEUR'
  | 'REINITIALISER_MOT_DE_PASSE'
  | 'CHANGER_MOT_DE_PASSE'
  // Reporting / sensibles
  | 'CONSULTER_AUDIT_LOG'
  | 'CONSULTER_REPORTING_GLOBAL'
  // Erreurs système
  | 'ACCES_REFUSE'
  | 'ERREUR_METIER';

export type AuditRole = 'ADMIN' | 'SUPERVISEUR' | 'CAISSIER';

/**
 * Représentation TypeScript de {@code AuditLogResponse}.
 * Tous les champs sauf {@code id}, {@code createdAt}, {@code action} et
 * {@code success} peuvent être {@code null} (le backend les exclut du
 * JSON via {@code @JsonInclude(NON_NULL)}).
 */
export interface AuditLog {
  id: number;
  createdAt: string;             // ISO-8601 : "2026-05-05T22:19:01.435"
  action: AuditAction;
  actionLibelle: string;         // libellé fr fourni par le backend

  // Auteur
  userId?: number;
  userLogin?: string;
  userMatricule?: string;
  userNomComplet?: string;
  userRole?: AuditRole;

  // Entité affectée
  entityType?: string;
  entityId?: number;
  entityLabel?: string;

  // Contexte HTTP
  ipAddress?: string;
  userAgent?: string;
  httpMethod?: string;
  httpPath?: string;

  // Résultat
  success: boolean;
  errorMessage?: string;
  details?: string;
}

/**
 * Critères de recherche envoyés à {@code GET /api/audit/logs}.
 * Tous les champs sont optionnels.
 */
export interface AuditFiltres {
  action?: AuditAction;
  userId?: number;
  entityType?: string;
  entityId?: number;
  success?: boolean;
  /** ISO-8601 ou objet Date. Borne inférieure inclusive. */
  dateFrom?: string | Date;
  /** ISO-8601 ou objet Date. Borne supérieure inclusive. */
  dateTo?: string | Date;
}

/**
 * Options de pagination + tri pour la recherche.
 * Le tri par défaut côté backend est {@code createdAt,desc}.
 */
export interface AuditPagination {
  page?: number;       // défaut 0
  size?: number;       // défaut 50, max 200 côté serveur
  sort?: string;       // ex. "createdAt,desc" ou "action,asc"
}

// =====================================================================
//  Service
// =====================================================================

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/audit/logs`;

  /**
   * Recherche paginée et filtrée du journal d'audit.
   *
   * @example
   * audit.rechercher({ action: 'LOGIN_FAILED' }, { page: 0, size: 100 });
   * audit.rechercher({ userId: 12, dateFrom: new Date('2026-05-01') });
   */
  rechercher(
    filtres: AuditFiltres = {},
    pagination: AuditPagination = {}
  ): Observable<Page<AuditLog>> {
    return this.http.get<Page<AuditLog>>(this.base, {
      params: this.construireParams(filtres, pagination)
    });
  }

  /** Récupère le détail d'une entrée d'audit. */
  obtenir(id: number): Observable<AuditLog> {
    return this.http.get<AuditLog>(`${this.base}/${id}`);
  }

  // ------------------------------------------------------------------
  //  Construction des HttpParams
  // ------------------------------------------------------------------

  private construireParams(
    filtres: AuditFiltres,
    pagination: AuditPagination
  ): HttpParams {
    let params = new HttpParams()
      .set('page', String(pagination.page ?? 0))
      .set('size', String(pagination.size ?? 50))
      .set('sort', pagination.sort ?? 'createdAt,desc');

    if (filtres.action) {
      params = params.set('action', filtres.action);
    }
    if (filtres.userId != null) {
      params = params.set('userId', String(filtres.userId));
    }
    if (filtres.entityType) {
      params = params.set('entityType', filtres.entityType);
    }
    if (filtres.entityId != null) {
      params = params.set('entityId', String(filtres.entityId));
    }
    if (filtres.success != null) {
      params = params.set('success', String(filtres.success));
    }
    if (filtres.dateFrom) {
      params = params.set('dateFrom', this.toIso(filtres.dateFrom));
    }
    if (filtres.dateTo) {
      params = params.set('dateTo', this.toIso(filtres.dateTo));
    }
    return params;
  }

  /**
   * Convertit une date au format attendu par l'API (ISO-8601 sans
   * timezone : "2026-05-05T00:00:00").
   */
  private toIso(d: string | Date): string {
    if (d instanceof Date) {
      // toISOString() retourne "...Z" — on retire la zone Z pour matcher
      // @DateTimeFormat(iso = ISO.DATE_TIME) côté Spring
      return d.toISOString().replace(/\.\d{3}Z$/, '');
    }
    return d;
  }
}

// =====================================================================
//  Helpers facultatifs (pour l'IHM)
// =====================================================================

/**
 * Retourne un libellé court fr pour une action d'audit.
 * Permet d'afficher proprement dans un mat-select de filtre par exemple.
 * Le backend renvoie déjà {@code actionLibelle}, mais ce helper sert
 * pour les listes de filtres côté client.
 */
export const AUDIT_ACTION_LIBELLES: Record<AuditAction, string> = {
  LOGIN_SUCCESS: 'Connexion réussie',
  LOGIN_FAILED: 'Connexion refusée',
  LOGOUT: 'Déconnexion',

  OUVRIR_CAISSE: 'Ouverture de caisse',
  CLOTURER_CAISSE: 'Clôture de caisse',
  VALIDER_JOURNAL: 'Validation de journal',
  CREER_CAISSE: 'Création de caisse',
  MODIFIER_CAISSE: 'Modification de caisse',
  SUPPRIMER_CAISSE: 'Suppression de caisse',
  SUSPENDRE_CAISSE: 'Suspension de caisse',
  REACTIVER_CAISSE: 'Réactivation de caisse',

  CREER_OPERATION: 'Saisie d\'opération',
  ANNULER_OPERATION: 'Annulation d\'opération',

  IMPRIMER_RECU: 'Impression de reçu',
  TELECHARGER_RECU_PDF: 'Téléchargement de reçu PDF',
  ENVOYER_WHATSAPP: 'Envoi WhatsApp',
  EXPORTER_JOURNAL_EXCEL: 'Export Excel du journal',

  CREER_CATEGORIE: 'Création de catégorie',
  MODIFIER_CATEGORIE: 'Modification de catégorie',
  SUPPRIMER_CATEGORIE: 'Suppression de catégorie',
  CREER_CLIENT: 'Création de client',
  MODIFIER_CLIENT: 'Modification de client',
  SUPPRIMER_CLIENT: 'Suppression de client',

  CREER_UTILISATEUR: 'Création d\'utilisateur',
  MODIFIER_UTILISATEUR: 'Modification d\'utilisateur',
  DESACTIVER_UTILISATEUR: 'Désactivation d\'utilisateur',
  REACTIVER_UTILISATEUR: 'Réactivation d\'utilisateur',
  REINITIALISER_MOT_DE_PASSE: 'Réinitialisation de mot de passe',
  CHANGER_MOT_DE_PASSE: 'Changement de mot de passe',

  CONSULTER_AUDIT_LOG: 'Consultation des logs d\'audit',
  CONSULTER_REPORTING_GLOBAL: 'Consultation du reporting',

  ACCES_REFUSE: 'Accès refusé',
  ERREUR_METIER: 'Erreur métier'
};

/**
 * Liste ordonnée pour peupler un &lt;mat-select&gt; de filtre.
 */
export const AUDIT_ACTIONS_LIST: { value: AuditAction; label: string }[] =
  (Object.keys(AUDIT_ACTION_LIBELLES) as AuditAction[])
    .map(value => ({ value, label: AUDIT_ACTION_LIBELLES[value] }));
