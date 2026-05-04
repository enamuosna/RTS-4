package sn.rts.caisse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.Client;

public record ClientDTO(
        Long id,
        @NotBlank @Size(max = 150) String raisonSociale,
        @Size(max = 50) String identifiantFiscal,
        @Size(max = 20) String telephone,
        @Email @Size(max = 150) String email,
        @Size(max = 255) String adresse,
        boolean actif
) {
    public static ClientDTO from(Client c) {
        return new ClientDTO(
                c.getId(),
                c.getRaisonSociale(),
                c.getIdentifiantFiscal(),
                c.getTelephone(),
                c.getEmail(),
                c.getAdresse(),
                c.isActif()
        );
    }
}
