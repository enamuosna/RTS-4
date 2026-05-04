package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.DashboardResponse;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.StatutCaisse;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.repository.CaisseRepository;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agrégations pour le tableau de bord Angular Material.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingService {

    private final OperationCaisseRepository operationRepository;
    private final CaisseRepository caisseRepository;

    public DashboardResponse dashboard(LocalDate date) {
        LocalDate target = (date != null) ? date : LocalDate.now();
        LocalDateTime debut = target.atStartOfDay();
        LocalDateTime fin = debut.plusDays(1);

        List<OperationCaisse> operations = operationRepository
                .findByDateOperationBetween(debut, fin).stream()
                .filter(o -> !o.isAnnulee())
                .toList();

        BigDecimal totalEntrees = sommerParType(operations, TypeOperation.ENTREE);
        BigDecimal totalSorties = sommerParType(operations, TypeOperation.SORTIE);
        BigDecimal soldeNet = totalEntrees.subtract(totalSorties);

        long caissesOuvertes = caisseRepository.findByStatut(StatutCaisse.OUVERTE).size();

        return new DashboardResponse(
                target,
                totalEntrees,
                totalSorties,
                soldeNet,
                operations.size(),
                caissesOuvertes,
                repartitionParCaisse(operations),
                repartitionParCategorie(operations));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private BigDecimal sommerParType(List<OperationCaisse> operations, TypeOperation type) {
        return operations.stream()
                .filter(o -> o.getTypeOperation() == type)
                .map(OperationCaisse::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<DashboardResponse.LigneCaisse> repartitionParCaisse(List<OperationCaisse> operations) {
        Map<Caisse, List<OperationCaisse>> parCaisse = operations.stream()
                .collect(Collectors.groupingBy(OperationCaisse::getCaisse));

        return parCaisse.entrySet().stream()
                .map(entry -> {
                    Caisse c = entry.getKey();
                    BigDecimal entrees = sommerParType(entry.getValue(), TypeOperation.ENTREE);
                    BigDecimal sorties = sommerParType(entry.getValue(), TypeOperation.SORTIE);
                    return new DashboardResponse.LigneCaisse(
                            c.getId(), c.getCode(), c.getLibelle(),
                            entrees, sorties, entrees.subtract(sorties));
                })
                .sorted(Comparator.comparing(DashboardResponse.LigneCaisse::codeCaisse))
                .toList();
    }

    private List<DashboardResponse.LigneCategorie> repartitionParCategorie(List<OperationCaisse> operations) {
        return operations.stream()
                .collect(Collectors.groupingBy(OperationCaisse::getCategorie))
                .entrySet().stream()
                .map(entry -> {
                    var cat = entry.getKey();
                    BigDecimal total = entry.getValue().stream()
                            .map(OperationCaisse::getMontant)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new DashboardResponse.LigneCategorie(
                            cat.getId(), cat.getCode(), cat.getLibelle(),
                            cat.getTypeOperation().name(),
                            total, entry.getValue().size());
                })
                .sorted(Comparator.comparing(DashboardResponse.LigneCategorie::codeCategorie))
                .toList();
    }
}
