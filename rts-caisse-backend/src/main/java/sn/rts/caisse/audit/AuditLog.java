package sn.rts.caisse.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sn.rts.caisse.model.Role;

import java.time.LocalDateTime;

/**
 * Entrée du journal d'audit applicatif.
 *
 * <p>Chaque ligne représente une action sensible exécutée par un utilisateur :
 * connexion, ouverture de caisse, saisie d'opération, annulation, export…</p>
 *
 * <p>L'identité de l'utilisateur est <b>dénormalisée</b> (login, matricule,
 * nom complet, rôle) pour préserver la traçabilité même si l'utilisateur
 * est désactivé ou renommé ultérieurement.</p>
 *
 * <p>Index posés sur {@code created_at}, {@code action} et {@code user_id}
 * pour accélérer les requêtes de consultation depuis l'IHM admin.</p>
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_action",     columnList = "action"),
                @Index(name = "idx_audit_user_id",    columnList = "user_id"),
                @Index(name = "idx_audit_entity",     columnList = "entity_type, entity_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Date/heure exacte de l'action (UTC ou heure serveur, selon JVM). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Type d'action effectuée. Voir {@link AuditAction}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    // ----- Auteur (dénormalisé pour résister aux modifications) -----

    /** ID utilisateur (peut être null si LOGIN_FAILED avec login inconnu). */
    @Column(name = "user_id")
    private Long userId;

    /** Login utilisé (utile pour LOGIN_FAILED même sans userId). */
    @Column(name = "user_login", length = 50)
    private String userLogin;

    @Column(name = "user_matricule", length = 30)
    private String userMatricule;

    @Column(name = "user_nom_complet", length = 200)
    private String userNomComplet;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 20)
    private Role userRole;

    // ----- Entité affectée (dénormalisée) -----

    /** Type d'entité affectée : "OperationCaisse", "JournalCaisse", "Caisse"… */
    @Column(name = "entity_type", length = 60)
    private String entityType;

    /** ID de l'entité affectée. */
    @Column(name = "entity_id")
    private Long entityId;

    /**
     * Étiquette lisible de l'entité (numéro de reçu, code caisse…)
     * pour éviter une jointure à la consultation.
     */
    @Column(name = "entity_label", length = 200)
    private String entityLabel;

    // ----- Contexte HTTP -----

    @Column(name = "ip_address", length = 45)  // IPv6 jusqu'à 39 + marge
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "http_path", length = 255)
    private String httpPath;

    // ----- Résultat -----

    /** {@code true} si l'action a réussi, {@code false} en cas d'échec. */
    @Column(nullable = false)
    private boolean success;

    /** Message d'erreur si {@code success == false}. */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * Détails libres en JSON ou texte simple (montant, motif, ancien/nouveau
     * solde…). Limité à 2000 caractères pour rester lisible.
     */
    @Column(name = "details", length = 2000)
    private String details;
}
