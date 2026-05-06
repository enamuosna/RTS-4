package sn.rts.caisse.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.exception.ResourceNotFoundException;

import java.time.LocalDateTime;

/**
 * Endpoints de consultation du journal d'audit.
 *
 * <p><b>Sécurité</b> : réservé aux administrateurs (ROLE_ADMIN).
 * Les superviseurs n'y ont pas accès car les logs peuvent contenir
 * des informations sensibles (IP, user-agent, montants…).</p>
 *
 * <p>Toutes les requêtes paginées renvoient la dernière action en haut
 * (tri descendant sur {@code createdAt}). Le client peut surcharger
 * en passant {@code ?sort=createdAt,asc}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/audit/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Audit", description = "Consultation du journal d'audit système")
public class AuditLogController {

    /** Plafond du nombre de lignes par page pour éviter une charge excessive. */
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repository;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Recherche paginée et filtrée du journal d'audit")
    public ResponseEntity<Page<AuditLogResponse>> rechercher(
            @Parameter(description = "Type d'action exact (ex. LOGIN_SUCCESS)")
            @RequestParam(required = false) AuditAction action,

            @Parameter(description = "ID utilisateur auteur de l'action")
            @RequestParam(required = false) Long userId,

            @Parameter(description = "Type d'entité affectée (ex. OperationCaisse)")
            @RequestParam(required = false) String entityType,

            @Parameter(description = "ID de l'entité affectée")
            @RequestParam(required = false) Long entityId,

            @Parameter(description = "Filtrer succès / échec")
            @RequestParam(required = false) Boolean success,

            @Parameter(description = "Borne inférieure (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,

            @Parameter(description = "Borne supérieure (ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,

            @Parameter(description = "Numéro de page (0-indexé)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Taille de la page (max " + MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "50") int size,

            @Parameter(description = "Champ et sens de tri (ex. createdAt,desc)")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        try {
            int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            Pageable pageable = PageRequest.of(
                    Math.max(page, 0),
                    safeSize,
                    parseSort(sort));

            // Build dynamic specification (ignore les filtres null)
            Specification<AuditLog> spec = AuditLogSpecifications.withFilters(
                    action, userId, entityType, entityId, success, dateFrom, dateTo);

            // findAll(spec, pageable) provient de JpaSpecificationExecutor
            Page<AuditLogResponse> result = repository
                    .findAll(spec, pageable)
                    .map(AuditLogResponse::from);

            // Audit-meta : qui consulte les logs (après le SELECT pour ne
            // pas masquer l'erreur si la requete principale plante)
            auditService.logSuccess(
                    AuditAction.CONSULTER_AUDIT_LOG,
                    "AuditLog", null, null,
                    "page=" + page + " size=" + safeSize
                            + " resultats=" + result.getNumberOfElements()
                            + (action != null ? " action=" + action : "")
                            + (userId != null ? " userId=" + userId : ""));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Erreur lors de la consultation des logs d'audit", e);
            auditService.logFailure(
                    AuditAction.CONSULTER_AUDIT_LOG,
                    "AuditLog", null, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une entree d'audit")
    public ResponseEntity<AuditLogResponse> obtenir(@PathVariable Long id) {
        AuditLog entry = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AuditLog", id));
        return ResponseEntity.ok(AuditLogResponse.from(entry));
    }

    private static Sort parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = raw.split(",");
        String prop = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(dir, prop.isBlank() ? "createdAt" : prop);
    }
}
