package sn.rts.caisse.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import sn.rts.caisse.model.Role;

import java.time.LocalDateTime;

/**
 * DTO d'une entrée d'audit pour exposition REST.
 *
 * <p>Représente une vue lecture seule, dénormalisée et stable de
 * {@link AuditLog}. Les nullables sont exclus de la sérialisation JSON
 * pour limiter le poids de la réponse.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        Long          id,
        LocalDateTime createdAt,
        AuditAction   action,
        String        actionLibelle,

        // Auteur
        Long          userId,
        String        userLogin,
        String        userMatricule,
        String        userNomComplet,
        Role          userRole,

        // Entité affectée
        String        entityType,
        Long          entityId,
        String        entityLabel,

        // Contexte HTTP
        String        ipAddress,
        String        userAgent,
        String        httpMethod,
        String        httpPath,

        // Résultat
        boolean       success,
        String        errorMessage,
        String        details
) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getCreatedAt(),
                a.getAction(),
                libelleFor(a.getAction()),
                a.getUserId(),
                a.getUserLogin(),
                a.getUserMatricule(),
                a.getUserNomComplet(),
                a.getUserRole(),
                a.getEntityType(),
                a.getEntityId(),
                a.getEntityLabel(),
                a.getIpAddress(),
                a.getUserAgent(),
                a.getHttpMethod(),
                a.getHttpPath(),
                a.isSuccess(),
                a.getErrorMessage(),
                a.getDetails()
        );
    }

    /** Libellé fr humain pour l'IHM. */
    private static String libelleFor(AuditAction a) {
        if (a == null) return null;
        return switch (a) {
            case LOGIN_SUCCESS              -> "Connexion réussie";
            case LOGIN_FAILED               -> "Connexion refusée";
            case LOGOUT                     -> "Déconnexion";
            case OUVRIR_CAISSE              -> "Ouverture de caisse";
            case CLOTURER_CAISSE            -> "Clôture de caisse";
            case VALIDER_JOURNAL            -> "Validation de journal";
            case CREER_CAISSE               -> "Création de caisse";
            case MODIFIER_CAISSE            -> "Modification de caisse";
            case SUPPRIMER_CAISSE           -> "Suppression de caisse";
            case SUSPENDRE_CAISSE           -> "Suspension de caisse";
            case REACTIVER_CAISSE           -> "Réactivation de caisse";
            case CREER_OPERATION            -> "Saisie d'opération";
            case ANNULER_OPERATION          -> "Annulation d'opération";
            case IMPRIMER_RECU              -> "Impression de reçu";
            case TELECHARGER_RECU_PDF       -> "Téléchargement de reçu PDF";
            case ENVOYER_WHATSAPP           -> "Envoi WhatsApp";
            case EXPORTER_JOURNAL_EXCEL     -> "Export Excel du journal";
            case CREER_CATEGORIE            -> "Création de catégorie";
            case MODIFIER_CATEGORIE         -> "Modification de catégorie";
            case SUPPRIMER_CATEGORIE        -> "Suppression de catégorie";
            case CREER_CLIENT               -> "Création de client";
            case MODIFIER_CLIENT            -> "Modification de client";
            case SUPPRIMER_CLIENT           -> "Suppression de client";
            case CREER_UTILISATEUR          -> "Création d'utilisateur";
            case MODIFIER_UTILISATEUR       -> "Modification d'utilisateur";
            case DESACTIVER_UTILISATEUR     -> "Désactivation d'utilisateur";
            case REACTIVER_UTILISATEUR      -> "Réactivation d'utilisateur";
            case REINITIALISER_MOT_DE_PASSE -> "Réinitialisation de mot de passe";
            case CHANGER_MOT_DE_PASSE       -> "Changement de mot de passe";
            case CONSULTER_AUDIT_LOG        -> "Consultation des logs d'audit";
            case CONSULTER_REPORTING_GLOBAL -> "Consultation du reporting";
            case ACCES_REFUSE               -> "Accès refusé";
            case ERREUR_METIER              -> "Erreur métier";
        };
    }
}
