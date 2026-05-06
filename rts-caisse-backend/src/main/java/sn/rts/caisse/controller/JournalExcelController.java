package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.service.JournalExcelService;

import java.util.List;

/**
 * Endpoints REST pour l'export Excel d'un journal de caisse.
 *
 * <p>Chaque téléchargement (succès comme échec) est tracé dans la table
 * {@code audit_logs} via {@link AuditService} avec l'action
 * {@link AuditAction#EXPORTER_JOURNAL_EXCEL}, en incluant la taille du
 * fichier généré et le nom du fichier renvoyé au client.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/journaux")
@RequiredArgsConstructor
@Tag(name = "Journal de caisse", description = "Ouverture, clôture, export")
public class JournalExcelController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final JournalExcelService journalExcelService;
    private final AuditService auditService;

    /**
     * Export Excel du journal.
     * Accepte plusieurs URLs pour la rétro-compatibilité.
     */
    @GetMapping({
            "/{id}/export/excel",
            "/{id}/export.xlsx",
            "/{id}/export"
    })
    @Operation(summary = "Télécharge le journal au format Excel (.xlsx)")
    public ResponseEntity<ByteArrayResource> exporterExcel(@PathVariable Long id) {
        log.info("Requête d'export Excel pour le journal {}", id);

        byte[] xlsx;
        String nomFichier;
        try {
            xlsx = journalExcelService.exporterJournal(id);
            nomFichier = journalExcelService.nomFichier(id);
        } catch (RuntimeException e) {
            // -------- AUDIT : échec --------
            // Capture les ResourceNotFoundException, erreurs Apache POI,
            // erreurs d'I/O… sans masquer l'exception (relancée à la fin).
            auditService.logFailure(
                    AuditAction.EXPORTER_JOURNAL_EXCEL,
                    "JournalCaisse",
                    id,
                    null,
                    e.getMessage());
            throw e;
        }

        HttpHeaders headers = new HttpHeaders();
        // IMPORTANT : PAS de StandardCharsets.UTF_8 ici !
        // Le nom de fichier est en pur ASCII (journal-CAI-02-2026-04-21.xlsx),
        // donc on utilise simplement filename="..." (RFC 6266 forme simple).
        // Avec un charset, Spring génère filename*=UTF-8''... que certains proxies
        // re-encodent mal en =?UTF-8?Q?...?= (format MIME RFC 2047).
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(nomFichier)
                .build());
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentLength(xlsx.length);
        headers.setAccessControlExposeHeaders(List.of("Content-Disposition"));
        headers.setCacheControl("no-store, no-cache, must-revalidate");

        log.info("Export Excel généré pour le journal {} ({} octets, fichier {})",
                id, xlsx.length, nomFichier);

        // -------- AUDIT : succès --------
        auditService.logSuccess(
                AuditAction.EXPORTER_JOURNAL_EXCEL,
                "JournalCaisse",
                id,
                nomFichier,
                "Fichier=" + nomFichier
                        + " Taille=" + xlsx.length + " octets");

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ByteArrayResource(xlsx));
    }
}
