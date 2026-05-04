package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.ClotureCaisseRequest;
import sn.rts.caisse.dto.JournalCaisseResponse;
import sn.rts.caisse.dto.OuvertureCaisseRequest;
import sn.rts.caisse.service.JournalCaisseService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/journaux")
@RequiredArgsConstructor
@Tag(name = "Journal de caisse", description = "Ouverture, clôture et validation journalières")
public class JournalCaisseController {

    private final JournalCaisseService service;

    @PostMapping("/caisse/{caisseId}/ouvrir")
    @Operation(summary = "Ouvrir la caisse pour la journée (crée un journal)")
    public ResponseEntity<JournalCaisseResponse> ouvrir(@PathVariable Long caisseId,
                                                        @Valid @RequestBody OuvertureCaisseRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(service.ouvrir(caisseId, request, authentication.getName()));
    }

    @PostMapping("/caisse/{caisseId}/cloturer")
    @Operation(summary = "Clôturer la caisse : fige les totaux et calcule l'écart")
    public ResponseEntity<JournalCaisseResponse> cloturer(@PathVariable Long caisseId,
                                                          @Valid @RequestBody ClotureCaisseRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(service.cloturer(caisseId, request, authentication.getName()));
    }

    @PostMapping("/{journalId}/valider")
    @Operation(summary = "Validation par un superviseur (ADMIN/SUPERVISEUR)")
    public ResponseEntity<JournalCaisseResponse> valider(@PathVariable Long journalId,
                                                         Authentication authentication) {
        return ResponseEntity.ok(service.valider(journalId, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalCaisseResponse> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @GetMapping("/caisse/{caisseId}")
    public ResponseEntity<List<JournalCaisseResponse>> parCaisse(@PathVariable Long caisseId) {
        return ResponseEntity.ok(service.journauxParCaisse(caisseId));
    }

    @GetMapping("/jour")
    public ResponseEntity<List<JournalCaisseResponse>> duJour(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.journauxDuJour(date));
    }
}
