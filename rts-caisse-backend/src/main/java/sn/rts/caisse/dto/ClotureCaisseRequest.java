package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ClotureCaisseRequest(
        @NotNull @PositiveOrZero BigDecimal soldeReel,
        @Size(max = 500) String commentaire
) {
}
