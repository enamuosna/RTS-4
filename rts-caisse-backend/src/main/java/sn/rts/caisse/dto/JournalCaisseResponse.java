package sn.rts.caisse.dto;

import sn.rts.caisse.model.JournalCaisse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record JournalCaisseResponse(
        Long id,
        LocalDate dateJournal,
        Long caisseId,
        String caisseLibelle,
        Long caissierId,
        String caissierNomComplet,
        BigDecimal fondOuverture,
        BigDecimal totalEntrees,
        BigDecimal totalSorties,
        BigDecimal soldeTheorique,
        BigDecimal soldeReel,
        BigDecimal ecart,
        String commentaire,
        LocalDateTime ouvertLe,
        LocalDateTime clotureLe,
        boolean cloture,
        Long valideeParId,
        String valideeParNom
) {
    public static JournalCaisseResponse from(JournalCaisse j) {
        return new JournalCaisseResponse(
                j.getId(),
                j.getDateJournal(),
                j.getCaisse().getId(),
                j.getCaisse().getLibelle(),
                j.getCaissier().getId(),
                j.getCaissier().getNomComplet(),
                j.getFondOuverture(),
                j.getTotalEntrees(),
                j.getTotalSorties(),
                j.getSoldeTheorique(),
                j.getSoldeReel(),
                j.getEcart(),
                j.getCommentaire(),
                j.getOuvertLe(),
                j.getClotureLe(),
                j.isCloture(),
                j.getValideePar() != null ? j.getValideePar().getId() : null,
                j.getValideePar() != null ? j.getValideePar().getNomComplet() : null
        );
    }
}
