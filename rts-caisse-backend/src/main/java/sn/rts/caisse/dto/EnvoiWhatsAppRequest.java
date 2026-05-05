package sn.rts.caisse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de la requête {@code POST /api/operations/{id}/whatsapp}.
 * Le numéro est saisi par le caissier (ou pré-rempli depuis la fiche client).
 */
public record EnvoiWhatsAppRequest(
        @NotBlank
        @Size(max = 20)
        String telephone
) {}
