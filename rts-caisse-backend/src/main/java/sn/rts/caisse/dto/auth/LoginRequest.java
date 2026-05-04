package sn.rts.caisse.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Le login est obligatoire.") String login,
        @NotBlank(message = "Le mot de passe est obligatoire.") String motDePasse
) {
}
