package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.StatutCaisse;

import java.math.BigDecimal;

public record CaisseDTO(
        Long id,
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String libelle,
        @Size(max = 150) String emplacement,
        StatutCaisse statut,
        BigDecimal soldeCourant,
        Long caissierId,
        String caissierNomComplet
) {
    public static CaisseDTO from(Caisse c) {
        return new CaisseDTO(
                c.getId(),
                c.getCode(),
                c.getLibelle(),
                c.getEmplacement(),
                c.getStatut(),
                c.getSoldeCourant(),
                c.getCaissier() != null ? c.getCaissier().getId() : null,
                c.getCaissier() != null ? c.getCaissier().getNomComplet() : null
        );
    }
}
