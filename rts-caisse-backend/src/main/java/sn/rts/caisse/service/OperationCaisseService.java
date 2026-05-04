package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.OperationCaisseRequest;
import sn.rts.caisse.dto.OperationCaisseResponse;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.*;
import sn.rts.caisse.repository.OperationCaisseRepository;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.util.NumeroRecuGenerator;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cœur métier : enregistrement d'une opération de caisse avec toutes les règles :
 * <ul>
 *   <li>la caisse doit être <b>OUVERTE</b></li>
 *   <li>la catégorie doit correspondre au type d'opération demandé</li>
 *   <li>le solde courant doit rester positif pour les sorties</li>
 *   <li>le solde courant est mis à jour transactionnellement</li>
 *   <li>un numéro de reçu unique est généré</li>
 *   <li>une opération n'est jamais supprimée : elle est <b>annulée</b>
 *       avec contre-passation du solde</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OperationCaisseService {

    private final OperationCaisseRepository operationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CaisseService caisseService;
    private final CategorieOperationService categorieService;
    private final ClientService clientService;
    private final NumeroRecuGenerator numeroRecuGenerator;

    // ------------------------------------------------------------------
    //  Enregistrement
    // ------------------------------------------------------------------

    public OperationCaisseResponse enregistrer(OperationCaisseRequest request, String loginCaissier) {

        Caisse caisse = caisseService.trouver(request.caisseId());
        if (caisse.getStatut() != StatutCaisse.OUVERTE) {
            throw new BusinessException(
                    "La caisse " + caisse.getCode() + " doit être ouverte pour saisir une opération.");
        }

        CategorieOperation categorie = categorieService.trouver(request.categorieId());
        if (categorie.getTypeOperation() != request.typeOperation()) {
            throw new BusinessException(
                    "La catégorie '" + categorie.getLibelle()
                            + "' ne correspond pas au type d'opération demandé ("
                            + request.typeOperation() + ").");
        }
        if (!categorie.isActif()) {
            throw new BusinessException("Catégorie désactivée : " + categorie.getLibelle());
        }

        // Pour les sorties : le solde ne doit pas devenir négatif.
        if (request.typeOperation() == TypeOperation.SORTIE
                && caisse.getSoldeCourant().compareTo(request.montant()) < 0) {
            throw new BusinessException(
                    "Solde insuffisant : solde courant = " + caisse.getSoldeCourant()
                            + " FCFA, montant demandé = " + request.montant() + " FCFA.");
        }

        Utilisateur caissier = utilisateurRepository.findByLogin(loginCaissier)
                .orElseThrow(() -> new BusinessException(
                        "Caissier introuvable : " + loginCaissier));

        Client client = request.clientId() != null
                ? clientService.trouver(request.clientId())
                : null;

        OperationCaisse operation = OperationCaisse.builder()
                .numeroRecu(numeroRecuGenerator.generer(caisse.getCode()))
                .typeOperation(request.typeOperation())
                .montant(request.montant())
                .motif(request.motif())
                .modePaiement(request.modePaiement())
                .reference(request.reference())
                .dateOperation(LocalDateTime.now())
                .caisse(caisse)
                .caissier(caissier)
                .categorie(categorie)
                .client(client)
                .annulee(false)
                .build();

        // Mise à jour transactionnelle du solde courant
        caisse.setSoldeCourant(applyMontant(
                caisse.getSoldeCourant(),
                request.typeOperation(),
                request.montant()));

        OperationCaisse saved = operationRepository.save(operation);
        log.info("Opération enregistrée : {} ({} FCFA) - caisse {}",
                saved.getNumeroRecu(), saved.getMontant(), caisse.getCode());

        return OperationCaisseResponse.from(saved);
    }

    // ------------------------------------------------------------------
    //  Annulation (contre-passation)
    // ------------------------------------------------------------------

    public OperationCaisseResponse annuler(Long operationId, String motif, String loginAnnulateur) {

        OperationCaisse operation = trouver(operationId);

        if (operation.isAnnulee()) {
            throw new BusinessException("Opération déjà annulée.");
        }
        if (operation.getJournal() != null && operation.getJournal().isCloture()) {
            throw new BusinessException(
                    "Impossible d'annuler : la journée de caisse est déjà clôturée.");
        }

        Caisse caisse = operation.getCaisse();

        // Contre-passation : on inverse l'effet initial sur le solde
        TypeOperation inverse = (operation.getTypeOperation() == TypeOperation.ENTREE)
                ? TypeOperation.SORTIE
                : TypeOperation.ENTREE;

        if (inverse == TypeOperation.SORTIE
                && caisse.getSoldeCourant().compareTo(operation.getMontant()) < 0) {
            throw new BusinessException(
                    "Impossible d'annuler : solde courant insuffisant pour contre-passer.");
        }

        caisse.setSoldeCourant(applyMontant(
                caisse.getSoldeCourant(),
                inverse,
                operation.getMontant()));

        operation.setAnnulee(true);
        operation.setMotifAnnulation(motif + " (par " + loginAnnulateur + ")");

        log.info("Annulation de l'opération {} par {} : {}",
                operation.getNumeroRecu(), loginAnnulateur, motif);

        return OperationCaisseResponse.from(operation);
    }

    // ------------------------------------------------------------------
    //  Lectures
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<OperationCaisseResponse> historiqueParCaisse(Long caisseId, Pageable pageable) {
        return operationRepository.findByCaisseId(caisseId, pageable)
                .map(OperationCaisseResponse::from);
    }

    @Transactional(readOnly = true)
    public List<OperationCaisseResponse> historiqueDuJour(Long caisseId) {
        LocalDateTime debut = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime fin = debut.plusDays(1);
        return operationRepository
                .findByCaisseIdAndDateOperationBetweenAndAnnuleeFalse(caisseId, debut, fin)
                .stream()
                .map(OperationCaisseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OperationCaisseResponse obtenir(Long id) {
        return OperationCaisseResponse.from(trouver(id));
    }

    private OperationCaisse trouver(Long id) {
        return operationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Opération", id));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static java.math.BigDecimal applyMontant(java.math.BigDecimal soldeCourant,
                                                      TypeOperation type,
                                                      java.math.BigDecimal montant) {
        return (type == TypeOperation.ENTREE)
                ? soldeCourant.add(montant)
                : soldeCourant.subtract(montant);
    }
}
