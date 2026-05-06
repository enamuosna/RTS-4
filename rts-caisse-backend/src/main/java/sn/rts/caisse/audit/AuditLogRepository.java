package sn.rts.caisse.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository pour la consultation paginée et filtrée du journal d'audit.
 *
 * <p>L'écriture se fait via {@link AuditService}. Cette interface n'expose
 * que des méthodes de lecture pour l'IHM admin.</p>
 *
 * <p>L'extension {@link JpaSpecificationExecutor} permet de construire des
 * requêtes filtrées dynamiquement (via {@link AuditLogSpecifications}),
 * ce qui évite les pièges des paramètres {@code null} typés sur enum
 * dans JPQL.</p>
 */
@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>,
                JpaSpecificationExecutor<AuditLog> {

    /**
     * Liste les N dernières connexions échouées pour un login donné.
     * Utilisé par le futur mécanisme anti-bruteforce (pas branché ici).
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.action = sn.rts.caisse.audit.AuditAction.LOGIN_FAILED
              AND a.userLogin = :login
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> dernieresConnexionsEchouees(
            @Param("login") String login,
            Pageable pageable);

    /**
     * Compte les actions d'un type donné survenues depuis une date.
     * Utile pour des stats (ex. nb d'exports Excel sur les 30 derniers jours).
     */
    long countByActionAndCreatedAtAfter(AuditAction action, LocalDateTime since);
}
