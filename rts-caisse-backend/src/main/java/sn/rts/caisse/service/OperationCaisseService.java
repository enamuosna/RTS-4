package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.EnvoiWhatsAppResponse;
import sn.rts.caisse.dto.OperationCaisseRequest;
import sn.rts.caisse.dto.OperationCaisseResponse;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.*;
import sn.rts.caisse.repository.OperationCaisseRepository;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.util.NumeroRecuGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 *
 * <p><b>Audit :</b> chaque création, annulation et envoi WhatsApp est tracé
 * dans la table {@code audit_logs} via {@link AuditService}, en succès comme
 * en échec. Les écritures d'audit utilisent une transaction
 * {@code REQUIRES_NEW} et survivent donc à un rollback métier.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OperationCaisseService {

    private static final DateTimeFormatter FMT_DATE_RECU =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OperationCaisseRepository operationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CaisseService caisseService;
    private final CategorieOperationService categorieService;
    private final ClientService clientService;
    private final NumeroRecuGenerator numeroRecuGenerator;
    private final RecuPdfService recuPdfService;
    private final WhatsAppCloudService whatsAppCloudService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    //  Enregistrement
    // ------------------------------------------------------------------

    public OperationCaisseResponse enregistrer(OperationCaisseRequest request, String loginCaissier) {
        try {
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

            // -------- AUDIT : succès --------
            auditService.logSuccess(
                    AuditAction.CREER_OPERATION,
                    "OperationCaisse",
                    saved.getId(),
                    saved.getNumeroRecu(),
                    "Type=" + saved.getTypeOperation()
                            + " Montant=" + saved.getMontant() + " FCFA"
                            + " Mode=" + saved.getModePaiement()
                            + " Caisse=" + caisse.getCode()
                            + " Catégorie=" + categorie.getLibelle()
                            + (client != null ? " Client=" + client.getRaisonSociale() : "")
                            + " NouveauSolde=" + caisse.getSoldeCourant() + " FCFA");

            return OperationCaisseResponse.from(saved);

        } catch (BusinessException | ResourceNotFoundException e) {
            // -------- AUDIT : échec --------
            // L'audit est écrit en transaction REQUIRES_NEW et survit au
            // rollback de la transaction métier.
            auditService.logFailure(
                    AuditAction.CREER_OPERATION,
                    "OperationCaisse",
                    null,
                    request != null
                            ? "type=" + request.typeOperation()
                                    + " montant=" + request.montant()
                                    + " caisseId=" + request.caisseId()
                            : null,
                    e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Annulation (contre-passation)
    // ------------------------------------------------------------------

    public OperationCaisseResponse annuler(Long operationId, String motif, String loginAnnulateur) {

        OperationCaisse operation;
        try {
            operation = trouver(operationId);

            if (operation.isAnnulee()) {
                throw new BusinessException("Opération déjà annulée.");
            }
            if (operation.getJournal() != null && operation.getJournal().isCloture()) {
                throw new BusinessException(
                        "Impossible d'annuler : la journée de caisse est déjà clôturée.");
            }

            Caisse caisse = operation.getCaisse();
            BigDecimal soldeAvant = caisse.getSoldeCourant();

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

            // -------- AUDIT : succès --------
            auditService.logSuccess(
                    AuditAction.ANNULER_OPERATION,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Type=" + operation.getTypeOperation()
                            + " Montant=" + operation.getMontant() + " FCFA"
                            + " Mode=" + operation.getModePaiement()
                            + " Caisse=" + caisse.getCode()
                            + " SoldeAvant=" + soldeAvant + " FCFA"
                            + " SoldeApres=" + caisse.getSoldeCourant() + " FCFA"
                            + " Motif=" + motif
                            + " AnnuléePar=" + loginAnnulateur);

            return OperationCaisseResponse.from(operation);

        } catch (BusinessException | ResourceNotFoundException e) {
            // -------- AUDIT : échec --------
            auditService.logFailure(
                    AuditAction.ANNULER_OPERATION,
                    "OperationCaisse",
                    operationId,
                    "motif=" + motif + " par=" + loginAnnulateur,
                    e.getMessage());
            throw e;
        }
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
    //  Envoi WhatsApp
    // ------------------------------------------------------------------

    /**
     * Envoie le reçu PDF d'une opération par WhatsApp via l'API Cloud Meta.
     *
     * <p>Workflow :</p>
     * <ol>
     *   <li>Récupère l'opération en base (404 si introuvable)</li>
     *   <li>Génère le PDF via RecuPdfService existant</li>
     *   <li>Normalise le numéro destinataire (ajout indicatif 221 si absent)</li>
     *   <li>Délègue l'envoi à WhatsAppCloudService qui appelle Meta</li>
     * </ol>
     *
     * <p>Chaque étape est auditée : numéro invalide, échec PDF, échec API
     * Meta et succès sont tracés distinctement dans {@code audit_logs}.</p>
     *
     * @param operationId identifiant de l'opération de caisse
     * @param telephone   numéro WhatsApp brut saisi par le caissier
     * @return réponse contenant le wamid ou la raison de l'échec
     */
    @Transactional(readOnly = true)
    public EnvoiWhatsAppResponse envoyerWhatsApp(Long operationId, String telephone) {

        // 1) Vérifie l'existence de l'opération
        OperationCaisse operation;
        try {
            operation = operationRepository.findById(operationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Opération introuvable : id=" + operationId));
        } catch (ResourceNotFoundException e) {
            auditService.logFailure(
                    AuditAction.ENVOYER_WHATSAPP,
                    "OperationCaisse",
                    operationId,
                    "tel=" + telephone,
                    e.getMessage());
            throw e;
        }

        // 2) Normalisation du téléphone
        String numeroNorm = normaliserTelephoneWhatsApp(telephone);
        if (numeroNorm == null) {
            String msg = "Numéro invalide. Format attendu : 77 123 45 67 ou +221 77 123 45 67.";
            auditService.logFailure(
                    AuditAction.ENVOYER_WHATSAPP,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Numéro invalide saisi=" + telephone);
            return EnvoiWhatsAppResponse.echec(telephone, msg);
        }

        // 3) Génération du PDF (réutilise le service existant)
        byte[] pdfBytes;
        try {
            pdfBytes = recuPdfService.genererRecu(operationId);
        } catch (Exception e) {
            log.error("Échec génération PDF pour opération {} : {}",
                    operationId, e.getMessage());
            auditService.logFailure(
                    AuditAction.ENVOYER_WHATSAPP,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Échec génération PDF: " + e.getMessage());
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

            // -------- AUDIT : succès --------
            auditService.logSuccess(
                    AuditAction.ENVOYER_WHATSAPP,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Tel=" + numeroNorm
                            + " Montant=" + operation.getMontant() + " FCFA"
                            + " TaillePDF=" + pdfBytes.length + " octets"
                            + " wamid=" + wamid);

            return EnvoiWhatsAppResponse.succes(wamid, numeroNorm);

        } catch (BusinessException e) {
            // -------- AUDIT : échec API Meta --------
            auditService.logFailure(
                    AuditAction.ENVOYER_WHATSAPP,
                    "OperationCaisse",
                    operation.getId(),
                    operation.getNumeroRecu(),
                    "Tel=" + numeroNorm + " API Meta: " + e.getMessage());
            return EnvoiWhatsAppResponse.echec(numeroNorm, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static BigDecimal applyMontant(BigDecimal soldeCourant,
                                           TypeOperation type,
                                           BigDecimal montant) {
        return (type == TypeOperation.ENTREE)
                ? soldeCourant.add(montant)
                : soldeCourant.subtract(montant);
    }

    /**
     * Normalise un numéro saisi par l'humain au format requis par Meta :
     * chiffres seulement, indicatif pays inclus, sans le +.
     *
     * <p>Exemples :</p>
     * <ul>
     *   <li>{@code "+221 77 123 45 67"}  → {@code "221771234567"}</li>
     *   <li>{@code "77 123 45 67"}       → {@code "221771234567"}</li>
     *   <li>{@code "00221 77 123 45 67"} → {@code "221771234567"}</li>
     * </ul>
     *
     * <p>Retourne {@code null} si le numéro est invalide.</p>
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
