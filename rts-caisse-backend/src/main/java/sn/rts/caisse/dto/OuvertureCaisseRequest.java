package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OuvertureCaisseRequest(
        @NotNull @PositiveOrZero BigDecimal fondOuverture
) {
}
