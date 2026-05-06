package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
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
 *
 * <p><b>Audit :</b> chaque ouverture, clôture et validation est tracée
 * dans la table {@code audit_logs} via {@link AuditService}, en succès
 * comme en échec. Les écritures d'audit utilisent une transaction
 * {@code REQUIRES_NEW} et survivent donc à un rollback métier.</p>
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
    private final AuditService auditService;

    // ------------------------------------------------------------------
    //  Ouverture
    // ------------------------------------------------------------------

    public JournalCaisseResponse ouvrir(Long caisseId,
                                        OuvertureCaisseRequest request,
                                        String loginCaissier) {
        try {
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

            // -------- AUDIT : succès --------
            auditService.logSuccess(
                    AuditAction.OUVRIR_CAISSE,
                    "JournalCaisse",
                    saved.getId(),
                    caisse.getCode() + " — " + saved.getDateJournal(),
                    "Caisse=" + caisse.getCode()
                            + " Date=" + saved.getDateJournal()
                            + " Fond=" + request.fondOuverture() + " FCFA"
                            + " Caissier=" + caissier.getLogin()
                            + " (" + caissier.getMatricule() + ")");

            return JournalCaisseResponse.from(saved);

        } catch (BusinessException | ResourceNotFoundException e) {
            // -------- AUDIT : échec --------
            auditService.logFailure(
                    AuditAction.OUVRIR_CAISSE,
                    "Caisse",
                    caisseId,
                    "fond=" + (request != null ? request.fondOuverture() : "?")
                            + " caissier=" + loginCaissier,
                    e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Clôture
    // ------------------------------------------------------------------

    public JournalCaisseResponse cloturer(Long caisseId,
                                          ClotureCaisseRequest request,
                                          String loginCaissier) {
        try {
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
            List<OperationCaisse> operationsJournee = operationRepository
                    .findByCaisseIdAndDateOperationBetweenAndAnnuleeFalse(caisseId, debut, fin);
            int nbRattachees = 0;
            for (OperationCaisse op : operationsJournee) {
                if (op.getJournal() == null) {
                    op.setJournal(journal);
                    nbRattachees++;
                }
            }

            // Fermeture physique de la caisse
            Caisse caisse = journal.getCaisse();
            caisse.setStatut(StatutCaisse.FERMEE);
            caisse.setSoldeCourant(BigDecimal.ZERO);

            log.info("Clôture caisse {} : théorique={}, réel={}, écart={}",
                    caisse.getCode(), soldeTheorique, request.soldeReel(), ecart);

            // -------- AUDIT : succès --------
            String typeEcart;
            if (ecart.signum() == 0)      typeEcart = "aucun";
            else if (ecart.signum() > 0)  typeEcart = "excédent";
            else                          typeEcart = "manquant";

            auditService.logSuccess(
                    AuditAction.CLOTURER_CAISSE,
                    "JournalCaisse",
                    journal.getId(),
                    caisse.getCode() + " — " + journal.getDateJournal(),
                    "Caisse=" + caisse.getCode()
                            + " Date=" + journal.getDateJournal()
                            + " Fond=" + journal.getFondOuverture() + " FCFA"
                            + " Entrées=" + totalEntrees + " FCFA"
                            + " Sorties=" + totalSorties + " FCFA"
                            + " SoldeTheorique=" + soldeTheorique + " FCFA"
                            + " SoldeReel=" + request.soldeReel() + " FCFA"
                            + " Ecart=" + ecart + " FCFA (" + typeEcart + ")"
                            + " NbOpérationsRattachées=" + nbRattachees
                            + (request.commentaire() != null && !request.commentaire().isBlank()
                                    ? " Commentaire=" + request.commentaire() : ""));

            return JournalCaisseResponse.from(journal);

        } catch (BusinessException | ResourceNotFoundException e) {
            // -------- AUDIT : échec --------
            auditService.logFailure(
                    AuditAction.CLOTURER_CAISSE,
                    "Caisse",
                    caisseId,
                    "soldeReel=" + (request != null ? request.soldeReel() : "?")
                            + " caissier=" + loginCaissier,
                    e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Validation superviseur
    // ------------------------------------------------------------------

    public JournalCaisseResponse valider(Long journalId, String loginSuperviseur) {
        try {
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

            // -------- AUDIT : succès --------
            auditService.logSuccess(
                    AuditAction.VALIDER_JOURNAL,
                    "JournalCaisse",
                    journal.getId(),
                    journal.getCaisse().getCode() + " — " + journal.getDateJournal(),
                    "Caisse=" + journal.getCaisse().getCode()
                            + " Date=" + journal.getDateJournal()
                            + " Caissier=" + journal.getCaissier().getLogin()
                            + " Ecart=" + journal.getEcart() + " FCFA"
                            + " ValidéPar=" + superviseur.getLogin()
                            + " (" + superviseur.getMatricule() + ")");

            return JournalCaisseResponse.from(journal);

        } catch (BusinessException | ResourceNotFoundException e) {
            // -------- AUDIT : échec --------
            auditService.logFailure(
                    AuditAction.VALIDER_JOURNAL,
                    "JournalCaisse",
                    journalId,
                    "superviseur=" + loginSuperviseur,
                    e.getMessage());
            throw e;
        }
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
