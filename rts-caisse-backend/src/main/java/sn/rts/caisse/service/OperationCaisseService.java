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

import sn.rts.caisse.dto.EnvoiWhatsAppResponse;
import java.time.format.DateTimeFormatter;


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
    private final RecuPdfService recuPdfService;
    private final WhatsAppCloudService whatsAppCloudService;

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
    private static final DateTimeFormatter FMT_DATE_RECU =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");


    /**
     * Envoie le reçu PDF d'une opération par WhatsApp via l'API Cloud Meta.
     *
     * Workflow :
     * 1) Récupère l'opération en base (404 si introuvable)
     * 2) Génère le PDF via RecuPdfService existant
     * 3) Normalise le numéro destinataire (ajout indicatif 221 si absent)
     * 4) Délègue l'envoi à WhatsAppCloudService qui appelle Meta
     *
     * @param operationId identifiant de l'opération de caisse
     * @param telephone   numéro WhatsApp brut saisi par le caissier
     * @return réponse contenant le wamid ou la raison de l'échec
     */
    @Transactional(readOnly = true)
    public EnvoiWhatsAppResponse envoyerWhatsApp(Long operationId, String telephone) {

        // 1) Vérifie l'existence de l'opération
        OperationCaisse operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Opération introuvable : id=" + operationId));

        // 2) Normalisation du téléphone
        String numeroNorm = normaliserTelephoneWhatsApp(telephone);
        if (numeroNorm == null) {
            return EnvoiWhatsAppResponse.echec(telephone,
                    "Numéro invalide. Format attendu : 77 123 45 67 ou +221 77 123 45 67.");
        }

        // 3) Génération du PDF (réutilise le service existant)
        byte[] pdfBytes;
        try {
            pdfBytes = recuPdfService.genererRecu(operationId);
        } catch (Exception e) {
            log.error("Échec génération PDF pour opération {} : {}",
                    operationId, e.getMessage());
            return EnvoiWhatsAppResponse.echec(numeroNorm,
                    "Impossible de générer le PDF du reçu : " + e.getMessage());
        }

        // 4) Construction de la légende et du nom de fichier
        String dateStr = operation.getDateOperation() != null
                ? operation.getDateOperation().format(FMT_DATE_RECU)
                : "";
        String legende = "Reçu RTS N° " + operation.getNumeroRecu()
                + (dateStr.isEmpty() ? "" : " du " + dateStr);
        String filename = "recu-" + operation.getNumeroRecu() + ".pdf";

        // 5) Appel à l'API WhatsApp Cloud Meta
        try {
            String wamid = whatsAppCloudService.envoyerDocument(
                    numeroNorm, pdfBytes, filename, legende);
            log.info("Reçu n° {} envoyé sur WhatsApp à {} (wamid={})",
                    operation.getNumeroRecu(), numeroNorm, wamid);
            return EnvoiWhatsAppResponse.succes(wamid, numeroNorm);

        } catch (BusinessException e) {
            return EnvoiWhatsAppResponse.echec(numeroNorm, e.getMessage());
        }
    }


    /**
     * Normalise un numéro saisi par l'humain au format requis par Meta :
     * chiffres seulement, indicatif pays inclus, sans le +.
     *
     * Exemples :
     *   "+221 77 123 45 67"  -> "221771234567"
     *   "77 123 45 67"       -> "221771234567"
     *   "00221 77 123 45 67" -> "221771234567"
     *
     * Retourne null si le numéro est invalide.
     */
    private static String normaliserTelephoneWhatsApp(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;

        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0")) {
            digits = "221" + digits.substring(1);
        }
        if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("3"))) {
            digits = "221" + digits;
        }
        if (digits.length() < 10 || digits.length() > 15) {
            return null;
        }
        return digits;
    }

}
