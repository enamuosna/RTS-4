package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.CategorieOperationDTO;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.service.CategorieOperationService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Catégories d'opération")
public class CategorieOperationController {

    private final CategorieOperationService service;

    @GetMapping
    @Operation(summary = "Lister les catégories (filtrage optionnel par type ENTREE/SORTIE)")
    public ResponseEntity<List<CategorieOperationDTO>> lister(
            @RequestParam(required = false) TypeOperation type) {
        return ResponseEntity.ok(service.lister(type));
    }

    @PostMapping
    public ResponseEntity<CategorieOperationDTO> creer(
            @Valid @RequestBody CategorieOperationDTO dto) {
        return ResponseEntity.ok(service.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieOperationDTO> modifier(
            @PathVariable Long id,
            @Valid @RequestBody CategorieOperationDTO dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }
}
