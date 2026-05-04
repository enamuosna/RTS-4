package sn.rts.caisse.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.Role;

public record RegisterRequest(
        @NotBlank @Size(max = 30) String matricule,
        @NotBlank @Size(min = 3, max = 50) String login,
        @NotBlank @Size(min = 8, max = 100) String motDePasse,
        @NotBlank @Size(max = 80) String prenom,
        @NotBlank @Size(max = 80) String nom,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String telephone,
        @NotNull Role role
) {
}
