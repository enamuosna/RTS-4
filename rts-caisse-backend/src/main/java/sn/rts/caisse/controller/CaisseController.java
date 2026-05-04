package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.CaisseDTO;
import sn.rts.caisse.service.CaisseService;

import java.util.List;

@RestController
@RequestMapping("/api/caisses")
@RequiredArgsConstructor
@Tag(name = "Caisses", description = "Guichets physiques RTS")
public class CaisseController {

    private final CaisseService service;

    @GetMapping
    @Operation(summary = "Lister toutes les caisses")
    public ResponseEntity<List<CaisseDTO>> lister() {
        return ResponseEntity.ok(service.lister());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaisseDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping
    @Operation(summary = "Créer une caisse (ADMIN)")
    public ResponseEntity<CaisseDTO> creer(@Valid @RequestBody CaisseDTO dto) {
        return ResponseEntity.ok(service.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CaisseDTO> modifier(@PathVariable Long id,
                                              @Valid @RequestBody CaisseDTO dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @PatchMapping("/{id}/caissier")
    @Operation(summary = "Affecter un caissier à une caisse")
    public ResponseEntity<CaisseDTO> affecterCaissier(@PathVariable Long id,
                                                      @RequestParam Long caissierId) {
        return ResponseEntity.ok(service.affecterCaissier(id, caissierId));
    }

    @PatchMapping("/{id}/suspendre")
    public ResponseEntity<CaisseDTO> suspendre(@PathVariable Long id,
                                               @RequestParam boolean suspendre) {
        return ResponseEntity.ok(service.suspendre(id, suspendre));
    }
}
