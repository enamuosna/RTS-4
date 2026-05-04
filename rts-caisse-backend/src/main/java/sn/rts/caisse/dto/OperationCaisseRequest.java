package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.TypeOperation;

import java.math.BigDecimal;

public record OperationCaisseRequest(
        @NotNull Long caisseId,
        @NotNull Long categorieId,
        Long clientId,
        @NotNull TypeOperation typeOperation,
        @NotNull @Positive BigDecimal montant,
        @NotNull ModePaiement modePaiement,
        @NotBlank @Size(max = 255) String motif,
        @Size(max = 100) String reference
) {
}
