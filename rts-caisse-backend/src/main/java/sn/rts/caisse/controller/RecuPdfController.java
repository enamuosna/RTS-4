package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.service.RecuPdfService;

/**
 * Exposition du reçu PDF d'une opération.
 * <p>
 * Deux variantes :
 * <ul>
 *   <li><code>?inline=true</code> : ouvre dans un onglet (aperçu avant impression)</li>
 *   <li><code>?inline=false</code> (défaut) : téléchargement direct</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/recus")
@RequiredArgsConstructor
@Tag(name = "Reçus PDF", description = "Génération de reçus au format PDF")
public class RecuPdfController {

    private final RecuPdfService recuPdfService;

    @GetMapping("/operation/{operationId}")
    @Operation(summary = "Génère le PDF du reçu d'une opération de caisse")
    public ResponseEntity<ByteArrayResource> telechargerRecu(
            @PathVariable Long operationId,
            @Parameter(description = "true pour affichage inline, false pour téléchargement")
            @RequestParam(defaultValue = "false") boolean inline) {

        byte[] pdf = recuPdfService.genererRecu(operationId);
        String nomFichier = "recu-" + operationId + ".pdf";

        ContentDisposition disposition = (inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(nomFichier)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdf.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ByteArrayResource(pdf));
    }
}
