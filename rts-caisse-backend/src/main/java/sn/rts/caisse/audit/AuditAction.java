package sn.rts.caisse.audit;

/**
 * Liste exhaustive des actions auditables du système RTS Caisse.
 *
 * <p>Chaque appel à {@link AuditService#log(AuditAction, String, Long, String, boolean, String)}
 * doit utiliser une de ces valeurs. Pour ajouter un nouveau type d'action,
 * étendre cette énumération.</p>
 *
 * <p>Les valeurs sont stockées en base sous forme de chaîne (VARCHAR), ce
 * qui permet de modifier l'ordre / d'ajouter des valeurs sans perdre
 * l'historique.</p>
 */
public enum AuditAction {

    // ==================================================================
    //  Authentification
    // ==================================================================

    /** Tentative de connexion réussie. */
    LOGIN_SUCCESS,

    /** Tentative de connexion refusée (mauvais mot de passe, compte désactivé…). */
    LOGIN_FAILED,

    /** Déconnexion explicite (le côté client signale la fin de session). */
    LOGOUT,

    // ==================================================================
    //  Caisse / Journal
    // ==================================================================

    /** Ouverture d'une journée de caisse (saisie du fond d'ouverture). */
    OUVRIR_CAISSE,

    /** Clôture d'une journée de caisse (saisie du solde réel + écart). */
    CLOTURER_CAISSE,

    /** Validation par un superviseur d'un journal clôturé. */
    VALIDER_JOURNAL,

    /** Création / modification / suppression d'une caisse (admin). */
    CREER_CAISSE,
    MODIFIER_CAISSE,
    SUPPRIMER_CAISSE,
    SUSPENDRE_CAISSE,
    REACTIVER_CAISSE,

    // ==================================================================
    //  Opérations
    // ==================================================================

    /** Enregistrement d'une opération (encaissement ou décaissement). */
    CREER_OPERATION,

    /** Annulation d'une opération existante (contre-passation du solde). */
    ANNULER_OPERATION,

    // ==================================================================
    //  Reçu / Documents
    // ==================================================================

    /** Réimpression d'un reçu (PDF). */
    IMPRIMER_RECU,

    /** Téléchargement du PDF d'un reçu. */
    TELECHARGER_RECU_PDF,

    /** Envoi du reçu par WhatsApp. */
    ENVOYER_WHATSAPP,

    /** Export Excel d'un journal de caisse. */
    EXPORTER_JOURNAL_EXCEL,

    // ==================================================================
    //  Référentiels
    // ==================================================================

    CREER_CATEGORIE,
    MODIFIER_CATEGORIE,
    SUPPRIMER_CATEGORIE,

    CREER_CLIENT,
    MODIFIER_CLIENT,
    SUPPRIMER_CLIENT,

    // ==================================================================
    //  Administration des utilisateurs
    // ==================================================================

    CREER_UTILISATEUR,
    MODIFIER_UTILISATEUR,
    DESACTIVER_UTILISATEUR,
    REACTIVER_UTILISATEUR,
    REINITIALISER_MOT_DE_PASSE,
    CHANGER_MOT_DE_PASSE,

    // ==================================================================
    //  Reporting / consultations sensibles
    // ==================================================================

    CONSULTER_AUDIT_LOG,
    CONSULTER_REPORTING_GLOBAL,

    // ==================================================================
    //  Erreurs systeme
    // ==================================================================

    /** Tentative d'accès non autorisée (403/401 capturés explicitement). */
    ACCES_REFUSE,

    /** Erreur métier (BusinessException) capturée explicitement par un service. */
    ERREUR_METIER
}
