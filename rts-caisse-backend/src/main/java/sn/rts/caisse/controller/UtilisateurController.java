package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.UtilisateurDTO;
import sn.rts.caisse.dto.auth.RegisterRequest;
import sn.rts.caisse.service.UtilisateurService;

import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Administration des agents RTS (rôle ADMIN requis)")
public class UtilisateurController {

    private final UtilisateurService service;

    @GetMapping
    @Operation(summary = "Lister tous les utilisateurs")
    public ResponseEntity<List<UtilisateurDTO>> lister() {
        return ResponseEntity.ok(service.lister());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UtilisateurDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur")
    public ResponseEntity<UtilisateurDTO> creer(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.creer(request));
    }

    @PatchMapping("/{id}/activer")
    public ResponseEntity<UtilisateurDTO> activer(@PathVariable Long id,
                                                  @RequestParam boolean actif) {
        return ResponseEntity.ok(service.activer(id, actif));
    }

    @PatchMapping("/{id}/mot-de-passe")
    public ResponseEntity<Void> changerMotDePasse(@PathVariable Long id,
                                                  @RequestParam String nouveau) {
        service.changerMotDePasse(id, nouveau);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Désactive l'utilisateur (pas de suppression physique)")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
