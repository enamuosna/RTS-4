package sn.rts.caisse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Vue agrégée pour le tableau de bord Angular Material : totaux
 * journaliers, répartition par caisse, répartition par catégorie.
 */
public record DashboardResponse(
        LocalDate date,
        BigDecimal totalEntreesJour,
        BigDecimal totalSortiesJour,
        BigDecimal soldeNetJour,
        long nombreOperations,
        long nombreCaissesOuvertes,
        List<LigneCaisse> repartitionParCaisse,
        List<LigneCategorie> repartitionParCategorie
) {
    public record LigneCaisse(
            Long caisseId,
            String codeCaisse,
            String libelleCaisse,
            BigDecimal entrees,
            BigDecimal sorties,
            BigDecimal solde
    ) {
    }

    public record LigneCategorie(
            Long categorieId,
            String codeCategorie,
            String libelleCategorie,
            String typeOperation,
            BigDecimal montantTotal,
            long nombre
    ) {
    }
}
