package sn.rts.caisse.dto;

import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.TypeOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Réponse renvoyée par l'API après enregistrement / consultation
 * d'une opération de caisse.
 *
 * <p><b>Évolution :</b> ajout des champs {@code clientTelephone} et
 * {@code clientAdresse} pour permettre au client lourd JavaFX d'imprimer
 * un reçu enrichi avec les coordonnées complètes du client et de
 * l'envoyer directement par WhatsApp.</p>
 */
public record OperationCaisseResponse(
        Long id,
        String numeroRecu,
        TypeOperation typeOperation,
        BigDecimal montant,
        String motif,
        ModePaiement modePaiement,
        String reference,
        LocalDateTime dateOperation,
        Long caisseId,
        String caisseLibelle,
        Long caissierId,
        String caissierNomComplet,
        Long categorieId,
        String categorieLibelle,
        Long clientId,
        String clientRaisonSociale,
        String clientTelephone,
        String clientAdresse,
        boolean annulee,
        String motifAnnulation
) {
    public static OperationCaisseResponse from(OperationCaisse o) {
        return new OperationCaisseResponse(
                o.getId(),
                o.getNumeroRecu(),
                o.getTypeOperation(),
                o.getMontant(),
                o.getMotif(),
                o.getModePaiement(),
                o.getReference(),
                o.getDateOperation(),
                o.getCaisse().getId(),
                o.getCaisse().getLibelle(),
                o.getCaissier().getId(),
                o.getCaissier().getNomComplet(),
                o.getCategorie().getId(),
                o.getCategorie().getLibelle(),
                o.getClient() != null ? o.getClient().getId() : null,
                o.getClient() != null ? o.getClient().getRaisonSociale() : null,
                o.getClient() != null ? o.getClient().getTelephone() : null,
                o.getClient() != null ? o.getClient().getAdresse() : null,
                o.isAnnulee(),
                o.getMotifAnnulation()
        );
    }
}
