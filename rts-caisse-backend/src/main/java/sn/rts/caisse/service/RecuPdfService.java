package sn.rts.caisse.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Génère le reçu PDF d'une opération de caisse.
 * <p>
 * Le document produit est un <b>ticket A6</b> (~105 × 148 mm) adapté à la fois
 * à l'impression sur imprimante de bureau et à la consultation à l'écran.
 * Le layout reprend la charte RTS : bleu #0a4d8c en en-tête, doré #e8a317 pour
 * les accents, montant en très gros caractères, pied de page neutre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecuPdfService {

    private static final Color RTS_PRIMARY = new Color(10, 77, 140);
    private static final Color RTS_ACCENT = new Color(232, 163, 23);
    private static final Color RTS_GRAY_900 = new Color(33, 33, 33);
    private static final Color RTS_GRAY_500 = new Color(158, 158, 158);
    private static final Color RTS_SUCCESS = new Color(46, 125, 50);
    private static final Color RTS_DANGER = new Color(198, 40, 40);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm:ss");

    private static final NumberFormat MONTANT_FMT;
    static {
        MONTANT_FMT = NumberFormat.getNumberInstance(Locale.FRANCE);
        MONTANT_FMT.setMinimumFractionDigits(0);
        MONTANT_FMT.setMaximumFractionDigits(0);
    }

    private final OperationCaisseRepository operationRepository;

    /**
     * Génère le PDF d'un reçu et renvoie les octets prêts à être envoyés dans
     * une réponse HTTP ou écrits sur disque.
     */
    public byte[] genererRecu(Long operationId) {
        OperationCaisse op = operationRepository.findById(operationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Opération", operationId));

        log.debug("Génération du reçu PDF pour l'opération {}", op.getNumeroRecu());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A6, 20, 20, 20, 20);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // En-tête
            ecrireEnTete(document);
            saut(document, 4);

            // Titre du ticket
            Paragraph titreTicket = new Paragraph("REÇU D'OPÉRATION",
                    font(11, Font.BOLD, RTS_PRIMARY));
            titreTicket.setAlignment(Element.ALIGN_CENTER);
            document.add(titreTicket);
            saut(document, 4);

            // Bandeau numéro + date
            document.add(bandeauNumero(op));
            saut(document, 6);

            // Corps : tableau clé / valeur (caisse, agent, catégorie...)
            document.add(tableauDetails(op));
            saut(document, 6);

            // Bloc CLIENT (si renseigné) — raison sociale, téléphone, adresse
            PdfPTable blocClient = tableauClient(op);
            if (blocClient != null) {
                ecrireTitreSection(document, "CLIENT");
                saut(document, 2);
                document.add(blocClient);
                saut(document, 8);
            }

            // Bloc montant en évidence
            document.add(blocMontant(op));


            // Motif
            ecrireMotif(document, op);
            saut(document, 8);

            // Annulation si applicable
            if (op.isAnnulee()) {
                ecrireBandeauAnnulation(document, op);
                saut(document, 6);
            }

            // Pied de page
            ecrirePiedPage(document);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Échec de la génération du PDF pour l'opération {}", operationId, e);
            throw new RuntimeException("Impossible de générer le reçu PDF : " + e.getMessage(), e);
        }
    }

    // ==================================================================
    //  Blocs de construction
    // ==================================================================

    private void ecrireEnTete(Document document) throws Exception {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setBackgroundColor(RTS_PRIMARY);
        cell.setPaddingTop(8);
        cell.setPaddingBottom(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph rts = new Paragraph("R T S",
                font(16, Font.BOLD, Color.WHITE));
        rts.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(rts);

        Paragraph sub = new Paragraph("Radiodiffusion Télévision Sénégalaise",
                font(8, Font.NORMAL, new Color(220, 220, 220)));
        sub.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(sub);

        header.addCell(cell);
        document.add(header);
    }

    private PdfPTable bandeauNumero(OperationCaisse op) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setBackgroundColor(new Color(250, 245, 230));
        cell.setPadding(8);

        Paragraph numero = new Paragraph("N° " + op.getNumeroRecu(),
                font(11, Font.BOLD, RTS_PRIMARY));
        numero.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(numero);

        Paragraph date = new Paragraph(op.getDateOperation().format(DATE_FMT),
                font(8, Font.NORMAL, RTS_GRAY_500));
        date.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(date);

        table.addCell(cell);
        return table;
    }

    private PdfPTable tableauDetails(OperationCaisse op) {
        PdfPTable table = new PdfPTable(new float[]{35, 65});
        table.setWidthPercentage(100);

        ajouterLigne(table, "Caisse",    op.getCaisse().getLibelle());
        ajouterLigne(table, "Caissier",  op.getCaissier().getNomComplet());
        ajouterLigne(table, "Catégorie", op.getCategorie().getLibelle());
        ajouterLigne(table, "Mode",      op.getModePaiement().name().replace('_', ' '));

        if (op.getReference() != null && !op.getReference().isBlank()) {
            ajouterLigne(table, "Référence", op.getReference());
        }
        return table;
    }
    /**
     * Bloc CLIENT séparé : raison sociale, téléphone, adresse.
     * Affiché uniquement si au moins un champ est renseigné.
     */
    private PdfPTable tableauClient(OperationCaisse op) {
        if (op.getClient() == null) return null;

        String raison    = op.getClient().getRaisonSociale();
        String telephone = op.getClient().getTelephone();
        String adresse   = op.getClient().getAdresse();

        boolean aQuelqueChose =
                (raison    != null && !raison.isBlank())
                        || (telephone != null && !telephone.isBlank())
                        || (adresse   != null && !adresse.isBlank());

        if (!aQuelqueChose) return null;

        PdfPTable table = new PdfPTable(new float[]{35, 65});
        table.setWidthPercentage(100);

        if (raison != null && !raison.isBlank()) {
            ajouterLigne(table, "M.", raison);
        }
        if (telephone != null && !telephone.isBlank()) {
            ajouterLigne(table, "Téléphone", telephone);
        }
        if (adresse != null && !adresse.isBlank()) {
            ajouterLigne(table, "Adresse", adresse);
        }
        return table;
    }


    /**
     * Petit titre de section (ex. "CLIENT") en gris, utilisé au-dessus
     * du bloc client pour le distinguer des autres infos.
     */
    private void ecrireTitreSection(Document document, String titre) throws Exception {
        Paragraph p = new Paragraph(titre, font(7, Font.BOLD, RTS_GRAY_500));
        p.setAlignment(Element.ALIGN_LEFT);
        document.add(p);
    }




    private void ajouterLigne(PdfPTable table, String label, String valeur) {
        PdfPCell cLabel = new PdfPCell(new Paragraph(label.toUpperCase() + " :",
                font(7, Font.BOLD, RTS_GRAY_500)));
        cLabel.setBorder(PdfPCell.NO_BORDER);
        cLabel.setPaddingTop(3);
        cLabel.setPaddingBottom(3);
        table.addCell(cLabel);

        PdfPCell cValeur = new PdfPCell(new Paragraph(valeur == null ? "—" : valeur,
                font(9, Font.NORMAL, RTS_GRAY_900)));
        cValeur.setBorder(PdfPCell.NO_BORDER);
        cValeur.setPaddingTop(3);
        cValeur.setPaddingBottom(3);
        table.addCell(cValeur);
    }

    private PdfPTable blocMontant(OperationCaisse op) {
        boolean entree = "ENTREE".equals(op.getTypeOperation().name());

        PdfPTable bloc = new PdfPTable(1);
        bloc.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.BOX);
        cell.setBorderColor(entree ? RTS_SUCCESS : RTS_DANGER);
        cell.setBorderWidth(1.5f);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph type = new Paragraph(
                entree ? "ENCAISSEMENT" : "DÉCAISSEMENT",
                font(9, Font.BOLD, entree ? RTS_SUCCESS : RTS_DANGER));
        type.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(type);

        Paragraph montant = new Paragraph(
                formatMontant(op.getMontant()) + " FCFA",
                font(20, Font.BOLD, RTS_GRAY_900));
        montant.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(montant);

        bloc.addCell(cell);
        return bloc;
    }

    private void ecrireMotif(Document document, OperationCaisse op) throws Exception {
        Paragraph label = new Paragraph("MOTIF", font(7, Font.BOLD, RTS_GRAY_500));
        document.add(label);

        Paragraph motif = new Paragraph(op.getMotif(),
                font(9, Font.NORMAL, RTS_GRAY_900));
        motif.setSpacingBefore(2);
        document.add(motif);
    }

    private void ecrireBandeauAnnulation(Document document, OperationCaisse op) throws Exception {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(255, 235, 238));
        cell.setBorderColor(RTS_DANGER);
        cell.setBorderWidth(1f);
        cell.setPadding(6);

        Paragraph p1 = new Paragraph("⚠  OPÉRATION ANNULÉE",
                font(9, Font.BOLD, RTS_DANGER));
        p1.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p1);

        if (op.getMotifAnnulation() != null) {
            Paragraph p2 = new Paragraph(op.getMotifAnnulation(),
                    font(7, Font.NORMAL, RTS_DANGER));
            p2.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(p2);
        }

        table.addCell(cell);
        document.add(table);
    }

    private void ecrirePiedPage(Document document) throws Exception {
        Paragraph sep = new Paragraph("─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─",
                font(7, Font.NORMAL, RTS_ACCENT));
        sep.setAlignment(Element.ALIGN_CENTER);
        document.add(sep);

        Paragraph merci = new Paragraph("Merci de votre passage.",
                font(8, Font.ITALIC, RTS_GRAY_900));
        merci.setAlignment(Element.ALIGN_CENTER);
        document.add(merci);

        Paragraph footer = new Paragraph("RTS - Conservez ce reçu comme preuve.",
                font(6, Font.NORMAL, RTS_GRAY_500));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(2);
        document.add(footer);
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private Font font(float size, int style, Color color) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, size, style);
        f.setColor(color);
        return f;
    }

    private void saut(Document document, float height) throws Exception {
        Chunk espace = new Chunk(" ");
        Paragraph p = new Paragraph(espace);
        p.setSpacingAfter(height);
        document.add(p);
    }

    private String formatMontant(BigDecimal montant) {
        return montant == null ? "0" : MONTANT_FMT.format(montant);
    }
}
