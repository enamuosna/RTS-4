package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.CategorieOperation;
import sn.rts.caisse.model.TypeOperation;

public record CategorieOperationDTO(
        Long id,
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 120) String libelle,
        @NotNull TypeOperation typeOperation,
        boolean actif
) {
    public static CategorieOperationDTO from(CategorieOperation c) {
        return new CategorieOperationDTO(
                c.getId(),
                c.getCode(),
                c.getLibelle(),
                c.getTypeOperation(),
                c.isActif()
        );
    }
}
