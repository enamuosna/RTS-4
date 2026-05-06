package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.UtilisateurDTO;
import sn.rts.caisse.dto.auth.RegisterRequest;
import sn.rts.caisse.security.SuperAdminPolicy;
import sn.rts.caisse.service.UtilisateurService;

import java.util.List;
import java.util.Map;

/**
 * Endpoints d'administration des utilisateurs RTS.
 *
 * <p>Tous les endpoints nécessitent le rôle ADMIN
 * (filtrage au niveau {@code SecurityConfig}).</p>
 *
 * <h2>Restrictions super-admin</h2>
 * <p>Certaines actions sont en plus restreintes au seul
 * <b>administrateur général</b> via {@link SuperAdminPolicy} :</p>
 * <ul>
 *   <li>{@code PATCH /{id}/login}      — modifier le login d'un autre utilisateur</li>
 *   <li>{@code PATCH /{id}/mot-de-passe} — réinitialiser le mot de passe d'un autre utilisateur</li>
 * </ul>
 * <p>Le self-service (changer son propre mot de passe) reste autorisé pour
 * tout ADMIN.</p>
 */
@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Administration des agents RTS (rôle ADMIN requis)")
public class UtilisateurController {

    private final UtilisateurService service;
    private final SuperAdminPolicy superAdminPolicy;

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

    /**
     * Modifie le login d'un utilisateur.
     * <b>Réservé à l'administrateur général</b> — la vérification est faite
     * dans {@link UtilisateurService#modifierLogin(Long, String)}.
     */
    @PatchMapping("/{id}/login")
    @Operation(summary = "Modifier le login d'un utilisateur (super-admin uniquement)")
    public ResponseEntity<UtilisateurDTO> modifierLogin(@PathVariable Long id,
                                                        @RequestParam String nouveau) {
        return ResponseEntity.ok(service.modifierLogin(id, nouveau));
    }

    @PatchMapping("/{id}/mot-de-passe")
    @Operation(summary = "Changer le mot de passe (self-service, "
            + "ou super-admin pour un autre utilisateur)")
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

    /**
     * Endpoint utilitaire pour le frontend : permet à l'IHM de savoir si
     * l'utilisateur courant est super-admin et ainsi d'afficher / cacher
     * les boutons « Modifier login » et « Réinitialiser mot de passe ».
     */
    @GetMapping("/me/super-admin")
    @Operation(summary = "Vérifie si l'utilisateur courant est l'administrateur général")
    public ResponseEntity<Map<String, Object>> estSuperAdmin() {
        return ResponseEntity.ok(Map.of(
                "superAdmin", superAdminPolicy.isCurrentUserSuperAdmin(),
                "superAdminLogin", superAdminPolicy.getSuperAdminLogin()
        ));
    }
}
