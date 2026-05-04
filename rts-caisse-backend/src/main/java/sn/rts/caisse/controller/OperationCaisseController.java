package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.OperationCaisseRequest;
import sn.rts.caisse.dto.OperationCaisseResponse;
import sn.rts.caisse.service.OperationCaisseService;

import java.util.List;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
@Tag(name = "Opérations de caisse", description = "Encaissements et décaissements")
public class OperationCaisseController {

    private final OperationCaisseService service;

    @PostMapping
    @Operation(summary = "Enregistrer une nouvelle opération (encaissement / décaissement)")
    public ResponseEntity<OperationCaisseResponse> enregistrer(
            @Valid @RequestBody OperationCaisseRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.enregistrer(request, authentication.getName()));
    }

    @PatchMapping("/{id}/annuler")
    @Operation(summary = "Annuler une opération (contre-passation, pas de suppression)")
    public ResponseEntity<OperationCaisseResponse> annuler(@PathVariable Long id,
                                                           @RequestParam String motif,
                                                           Authentication authentication) {
        return ResponseEntity.ok(service.annuler(id, motif, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OperationCaisseResponse> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @GetMapping("/caisse/{caisseId}")
    @Operation(summary = "Historique paginé des opérations pour une caisse")
    public ResponseEntity<Page<OperationCaisseResponse>> historique(
            @PathVariable Long caisseId,
            Pageable pageable) {
        return ResponseEntity.ok(service.historiqueParCaisse(caisseId, pageable));
    }

    @GetMapping("/caisse/{caisseId}/jour")
    @Operation(summary = "Opérations de la journée en cours pour une caisse")
    public ResponseEntity<List<OperationCaisseResponse>> historiqueJour(@PathVariable Long caisseId) {
        return ResponseEntity.ok(service.historiqueDuJour(caisseId));
    }
}
