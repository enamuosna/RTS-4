package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.ClotureCaisseRequest;
import sn.rts.caisse.dto.JournalCaisseResponse;
import sn.rts.caisse.dto.OuvertureCaisseRequest;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.*;
import sn.rts.caisse.repository.JournalCaisseRepository;
import sn.rts.caisse.repository.OperationCaisseRepository;
import sn.rts.caisse.repository.UtilisateurRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Gestion du journal de caisse :
 *   - <b>ouverture</b> : crée un journal, passe la caisse à OUVERTE,
 *     initialise le solde avec le fond d'ouverture
 *   - <b>clôture</b> : fige les totaux, calcule l'écart, interdit toute
 *     nouvelle opération tant que le journal n'est pas rouvert
 *   - <b>validation</b> : un superviseur contresigne la clôture
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JournalCaisseService {

    private final JournalCaisseRepository journalRepository;
    private final OperationCaisseRepository operationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CaisseService caisseService;

    // ------------------------------------------------------------------
    //  Ouverture
    // ------------------------------------------------------------------

    public JournalCaisseResponse ouvrir(Long caisseId,
                                        OuvertureCaisseRequest request,
                                        String loginCaissier) {

        Caisse caisse = caisseService.trouver(caisseId);

        if (caisse.getStatut() == StatutCaisse.OUVERTE) {
            throw new BusinessException("La caisse " + caisse.getCode() + " est déjà ouverte.");
        }
        if (caisse.getStatut() == StatutCaisse.SUSPENDUE) {
            throw new BusinessException(
                    "La caisse " + caisse.getCode() + " est suspendue : contactez l'administrateur.");
        }

        LocalDate aujourdHui = LocalDate.now();
        journalRepository.findByCaisseIdAndDateJournal(caisseId, aujourdHui)
                .ifPresent(j -> {
                    throw new BusinessException(
                            "Un journal existe déjà pour cette caisse aujourd'hui (id=" + j.getId() + ").");
                });

        Utilisateur caissier = utilisateurRepository.findByLogin(loginCaissier)
                .orElseThrow(() -> new BusinessException("Caissier introuvable : " + loginCaissier));

        JournalCaisse journal = JournalCaisse.builder()
                .dateJournal(aujourdHui)
                .caisse(caisse)
                .caissier(caissier)
                .fondOuverture(request.fondOuverture())
                .totalEntrees(BigDecimal.ZERO)
                .totalSorties(BigDecimal.ZERO)
                .soldeTheorique(request.fondOuverture())
                .ouvertLe(LocalDateTime.now())
                .cloture(false)
                .build();

        // Ouverture physique de la caisse
        caisse.setStatut(StatutCaisse.OUVERTE);
        caisse.setSoldeCourant(request.fondOuverture());
        caisse.setCaissier(caissier);

        JournalCaisse saved = journalRepository.save(journal);
        log.info("Ouverture caisse {} par {} avec fond {} FCFA",
                caisse.getCode(), caissier.getLogin(), request.fondOuverture());

        return JournalCaisseResponse.from(saved);
    }

    // ------------------------------------------------------------------
    //  Clôture
    // ------------------------------------------------------------------

    public JournalCaisseResponse cloturer(Long caisseId,
                                          ClotureCaisseRequest request,
                                          String loginCaissier) {

        JournalCaisse journal = journalRepository.findByCaisseIdAndClotureFalse(caisseId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun journal ouvert pour cette caisse."));

        if (!journal.getCaissier().getLogin().equals(loginCaissier)) {
            throw new BusinessException(
                    "Seul le caissier qui a ouvert la caisse peut la clôturer.");
        }

        LocalDateTime debut = journal.getDateJournal().atStartOfDay();
        LocalDateTime fin = debut.plusDays(1);

        BigDecimal totalEntrees = operationRepository.sommerMontants(
                caisseId, TypeOperation.ENTREE, debut, fin);
        BigDecimal totalSorties = operationRepository.sommerMontants(
                caisseId, TypeOperation.SORTIE, debut, fin);

        BigDecimal soldeTheorique = journal.getFondOuverture()
                .add(totalEntrees)
                .subtract(totalSorties);

        BigDecimal ecart = request.soldeReel().subtract(soldeTheorique);

        journal.setTotalEntrees(totalEntrees);
        journal.setTotalSorties(totalSorties);
        journal.setSoldeTheorique(soldeTheorique);
        journal.setSoldeReel(request.soldeReel());
        journal.setEcart(ecart);
        journal.setCommentaire(request.commentaire());
        journal.setClotureLe(LocalDateTime.now());
        journal.setCloture(true);

        // Rattachement des opérations de la journée au journal (historique)
        operationRepository
                .findByCaisseIdAndDateOperationBetweenAndAnnuleeFalse(caisseId, debut, fin)
                .forEach(op -> {
                    if (op.getJournal() == null) {
                        op.setJournal(journal);
                    }
                });

        // Fermeture physique de la caisse
        Caisse caisse = journal.getCaisse();
        caisse.setStatut(StatutCaisse.FERMEE);
        caisse.setSoldeCourant(BigDecimal.ZERO);

        log.info("Clôture caisse {} : théorique={}, réel={}, écart={}",
                caisse.getCode(), soldeTheorique, request.soldeReel(), ecart);

        return JournalCaisseResponse.from(journal);
    }

    // ------------------------------------------------------------------
    //  Validation superviseur
    // ------------------------------------------------------------------

    public JournalCaisseResponse valider(Long journalId, String loginSuperviseur) {
        JournalCaisse journal = trouver(journalId);

        if (!journal.isCloture()) {
            throw new BusinessException("Seul un journal clôturé peut être validé.");
        }
        if (journal.getValideePar() != null) {
            throw new BusinessException("Journal déjà validé.");
        }

        Utilisateur superviseur = utilisateurRepository.findByLogin(loginSuperviseur)
                .orElseThrow(() -> new BusinessException("Superviseur introuvable."));

        journal.setValideePar(superviseur);
        log.info("Journal {} validé par {}", journalId, superviseur.getLogin());
        return JournalCaisseResponse.from(journal);
    }

    // ------------------------------------------------------------------
    //  Lectures
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public JournalCaisseResponse obtenir(Long id) {
        return JournalCaisseResponse.from(trouver(id));
    }

    @Transactional(readOnly = true)
    public List<JournalCaisseResponse> journauxParCaisse(Long caisseId) {
        return journalRepository.findByCaisseIdOrderByDateJournalDesc(caisseId).stream()
                .map(JournalCaisseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JournalCaisseResponse> journauxDuJour(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return journalRepository.findByDateJournal(target).stream()
                .map(JournalCaisseResponse::from)
                .toList();
    }

    private JournalCaisse trouver(Long id) {
        return journalRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Journal", id));
    }
}
