package sn.rts.caisse.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.model.Utilisateur;

import java.time.LocalDateTime;

/**
 * Service central d'audit.
 *
 * <p>Toutes les méthodes {@code log...} écrivent une entrée dans
 * {@code audit_logs} en récupérant automatiquement l'utilisateur
 * Spring Security courant et le contexte HTTP (IP, User-Agent…) via
 * {@link AuditContextHelper}.</p>
 *
 * <h2>Robustesse</h2>
 * Les écritures d'audit ne doivent <b>jamais</b> faire échouer la
 * transaction métier qui les déclenche. Pour cela :
 * <ul>
 *   <li>{@link Propagation#REQUIRES_NEW} → l'audit est commit dans sa
 *       propre transaction, indépendante;</li>
 *   <li>les exceptions sont attrapées et loguées (via SLF4J), pas
 *       remontées à l'appelant;</li>
 *   <li>une variante {@link Async} {@code logAsync} permet de découpler
 *       complètement (utile pour les actions à très haute fréquence).</li>
 * </ul>
 *
 * <h2>Usage type</h2>
 * <pre>{@code
 * @Service
 * public class OperationCaisseService {
 *     private final AuditService audit;
 *
 *     public OperationCaisseResponse enregistrer(OperationCaisseRequest req) {
 *         try {
 *             OperationCaisse op = ...;
 *             audit.logSuccess(AuditAction.CREER_OPERATION,
 *                     "OperationCaisse", op.getId(), op.getNumeroRecu(),
 *                     "Montant: " + op.getMontant() + " " + op.getModePaiement());
 *             return OperationCaisseResponse.from(op);
 *         } catch (BusinessException e) {
 *             audit.logFailure(AuditAction.CREER_OPERATION,
 *                     "OperationCaisse", null, null, e.getMessage());
 *             throw e;
 *         }
 *     }
 * }
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /**
     * Self-injection (lazy) pour que les appels internes à {@link #saveInNewTx}
     * passent par le proxy Spring : sans cela, l'annotation
     * {@link Transactional}{@code (REQUIRES_NEW)} serait ignorée pour les
     * appels {@code this.saveInNewTx(...)} (auto-invocation), et l'audit
     * serait rollback en même temps que la transaction métier.
     */
    @Autowired
    @Lazy
    private AuditService self;

    // ==================================================================
    //  API publique : variantes pratiques
    // ==================================================================

    /** Log un succès en tirant l'utilisateur courant du SecurityContext. */
    public void logSuccess(AuditAction action,
                           String entityType,
                           Long entityId,
                           String entityLabel,
                           String details) {
        log(action, entityType, entityId, entityLabel, true, null, details);
    }

    /** Log un échec (exception métier, validation refusée…). */
    public void logFailure(AuditAction action,
                           String entityType,
                           Long entityId,
                           String entityLabel,
                           String errorMessage) {
        log(action, entityType, entityId, entityLabel, false, errorMessage, null);
    }

    /**
     * Log un échec de connexion : on n'a pas d'utilisateur dans le
     * SecurityContext, mais on a un login tenté. Cas particulier qui
     * justifie une méthode dédiée.
     */
    public void logLoginFailed(String loginTente, String errorMessage) {
        try {
            AuditLog entry = AuditLog.builder()
                    .createdAt(LocalDateTime.now())
                    .action(AuditAction.LOGIN_FAILED)
                    .userLogin(loginTente)
                    .ipAddress(AuditContextHelper.currentIp())
                    .userAgent(AuditContextHelper.currentUserAgent())
                    .httpMethod(AuditContextHelper.currentMethod())
                    .httpPath(AuditContextHelper.currentPath())
                    .success(false)
                    .errorMessage(safeTruncate(errorMessage, 500))
                    .build();
            self.saveInNewTx(entry);
        } catch (Exception ex) {
            log.error("Échec écriture audit LOGIN_FAILED pour {}", loginTente, ex);
        }
    }

    /** Log explicite avec un utilisateur donné (cas du LOGIN_SUCCESS). */
    public void logForUser(AuditAction action,
                           Utilisateur user,
                           String entityType,
                           Long entityId,
                           String entityLabel,
                           boolean success,
                           String errorMessage,
                           String details) {
        try {
            AuditLog entry = build(action, user, entityType, entityId, entityLabel,
                    success, errorMessage, details);
            self.saveInNewTx(entry);
        } catch (Exception ex) {
            log.error("Échec écriture audit {} pour user {}", action,
                    user == null ? null : user.getLogin(), ex);
        }
    }

    // ==================================================================
    //  Implémentation
    // ==================================================================

    /**
     * Coeur de l'écriture d'audit : récupère l'utilisateur courant via
     * Spring Security puis délègue à {@link #saveInNewTx(AuditLog)}.
     */
    public void log(AuditAction action,
                    String entityType,
                    Long entityId,
                    String entityLabel,
                    boolean success,
                    String errorMessage,
                    String details) {
        try {
            Utilisateur user = AuditContextHelper.currentUser();
            AuditLog entry = build(action, user, entityType, entityId, entityLabel,
                    success, errorMessage, details);
            self.saveInNewTx(entry);
        } catch (Exception ex) {
            // Ne JAMAIS faire échouer la transaction métier à cause de l'audit
            log.error("Échec écriture audit {} ({} #{})",
                    action, entityType, entityId, ex);
        }
    }

    /**
     * Variante asynchrone pour les actions très fréquentes ou non bloquantes.
     * Nécessite l'activation de {@code @EnableAsync} sur l'application.
     */
    @Async
    public void logAsync(AuditAction action,
                         String entityType,
                         Long entityId,
                         String entityLabel,
                         boolean success,
                         String errorMessage,
                         String details) {
        log(action, entityType, entityId, entityLabel, success, errorMessage, details);
    }

    private AuditLog build(AuditAction action,
                           Utilisateur user,
                           String entityType,
                           Long entityId,
                           String entityLabel,
                           boolean success,
                           String errorMessage,
                           String details) {

        AuditLog.AuditLogBuilder b = AuditLog.builder()
                .createdAt(LocalDateTime.now())
                .action(action)
                .entityType(safeTruncate(entityType, 60))
                .entityId(entityId)
                .entityLabel(safeTruncate(entityLabel, 200))
                .ipAddress(AuditContextHelper.currentIp())
                .userAgent(AuditContextHelper.currentUserAgent())
                .httpMethod(AuditContextHelper.currentMethod())
                .httpPath(AuditContextHelper.currentPath())
                .success(success)
                .errorMessage(safeTruncate(errorMessage, 500))
                .details(safeTruncate(details, 2000));

        if (user != null) {
            b.userId(user.getId())
             .userLogin(safeTruncate(user.getLogin(), 50))
             .userMatricule(safeTruncate(user.getMatricule(), 30))
             .userNomComplet(safeTruncate(user.getNomComplet(), 200))
             .userRole(user.getRole());
        }
        return b.build();
    }

    /**
     * Écriture isolée dans une nouvelle transaction : si la transaction
     * métier rollback, l'entrée d'audit reste persistée (utile pour tracer
     * les échecs).
     *
     * <p><b>Important :</b> cette méthode est {@code public} parce qu'elle
     * doit être appelée à travers le proxy Spring (via {@code self}) pour
     * que l'annotation {@link Transactional} prenne effet. Ne jamais
     * l'appeler directement avec {@code this.saveInNewTx(...)} depuis
     * cette classe.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInNewTx(AuditLog entry) {
        repository.save(entry);
    }

    private static String safeTruncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
