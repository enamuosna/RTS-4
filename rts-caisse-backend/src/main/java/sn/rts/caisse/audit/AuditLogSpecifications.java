package sn.rts.caisse.audit;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Spécifications JPA dynamiques pour la recherche paginée du journal d'audit.
 *
 * <p>Cette classe remplace l'ancienne {@code @Query} JPQL utilisant
 * {@code (:param IS NULL OR ...)} : ce pattern posait problème avec
 * Hibernate 6 + PostgreSQL quand le paramètre était {@code null} et typé
 * sur un enum (Hibernate ne sait pas alors caster le {@code null} pour la
 * comparaison, ce qui déclenchait une {@code SQLException}).</p>
 *
 * <p>Avec Specifications, seuls les prédicats correspondant à un filtre
 * <b>non-null</b> sont ajoutés à la requête finale → plus simple, plus
 * performant, plus robuste.</p>
 */
@UtilityClass
public class AuditLogSpecifications {

    /**
     * Construit une Specification combinant tous les filtres optionnels.
     * Tous les paramètres {@code null} sont ignorés.
     */
    public Specification<AuditLog> withFilters(
            AuditAction action,
            Long userId,
            String entityType,
            Long entityId,
            Boolean success,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()                  // pas de filtre = TRUE
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
