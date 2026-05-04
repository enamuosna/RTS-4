package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.JournalCaisse;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.repository.JournalCaisseRepository;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Génère l'export Excel (.xlsx) d'un journal de caisse.
 * <p>
 * Le classeur produit contient <b>deux onglets</b> :
 * <ol>
 *   <li><b>Récapitulatif</b> — informations d'en-tête, totaux,
 *       calcul d'écart</li>
 *   <li><b>Opérations</b> — toutes les opérations de la journée avec
 *       numéro de reçu, type, catégorie, montant, mode de paiement, client</li>
 * </ol>
 * <p>
 * Mise en forme professionnelle : charte RTS (bleu primaire, doré accent),
 * en-têtes gras centrés fond bleu, lignes alternées pour lisibilité,
 * formats monétaires FCFA, bordures, gel de la ligne d'en-tête,
 * autofilter activé pour tri/filtrage côté Excel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalExcelService {

    // ----- Palette RTS (reprise du thème web) -----
    private static final byte[] RGB_RTS_PRIMARY = {0x0A, 0x4D, (byte) 0x8C};
    private static final byte[] RGB_RTS_PRIMARY_DARK = {0x07, 0x3A, 0x6B};
    private static final byte[] RGB_RTS_ACCENT = {(byte) 0xE8, (byte) 0xA3, 0x17};
    private static final byte[] RGB_ROW_ALT = {(byte) 0xF5, (byte) 0xF5, (byte) 0xF7};
    private static final byte[] RGB_SUCCESS_BG = {(byte) 0xE8, (byte) 0xF5, (byte) 0xE9};
    private static final byte[] RGB_DANGER_BG = {(byte) 0xFF, (byte) 0xEB, (byte) 0xEE};

    private final JournalCaisseRepository journalRepository;
    private final OperationCaisseRepository operationRepository;

    // ==================================================================
    //  API publique
    // ==================================================================

    public byte[] exporterJournal(Long journalId) {
        JournalCaisse journal = journalRepository.findById(journalId)
                .orElseThrow(() -> ResourceNotFoundException.of("Journal", journalId));

        log.info("Export Excel du journal {} (caisse {}, date {})",
                journalId, journal.getCaisse().getCode(), journal.getDateJournal());

        List<OperationCaisse> operations = operationRepository.findByJournalId(journalId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            StylePack styles = creerStyles(workbook);

            ecrireOngletRecap(workbook, journal, styles);
            ecrireOngletOperations(workbook, operations, styles);

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Échec de l'export Excel du journal {}", journalId, e);
            throw new RuntimeException(
                    "Impossible de générer l'export Excel : " + e.getMessage(), e);
        }
    }

    /**
     * Suggère un nom de fichier du type
     * {@code journal-CAI-01-2026-04-21.xlsx}.
     */
    public String nomFichier(Long journalId) {
        JournalCaisse j = journalRepository.findById(journalId)
                .orElseThrow(() -> ResourceNotFoundException.of("Journal", journalId));
        return "journal-" + j.getCaisse().getCode() + "-" + j.getDateJournal() + ".xlsx";
    }

    // ==================================================================
    //  Onglet 1 : Récapitulatif
    // ==================================================================

    private void ecrireOngletRecap(XSSFWorkbook workbook,
                                   JournalCaisse j,
                                   StylePack s) {
        XSSFSheet sheet = workbook.createSheet("Récapitulatif");
        sheet.setDefaultColumnWidth(22);
        sheet.setColumnWidth(0, 9000);  // ~30 caractères
        sheet.setColumnWidth(1, 7000);  // ~23 caractères

        // ----- Ligne 0 : Titre RTS -----
        Row r0 = sheet.createRow(0);
        r0.setHeightInPoints(32);
        Cell titre = r0.createCell(0);
        titre.setCellValue("RADIODIFFUSION TÉLÉVISION SÉNÉGALAISE");
        titre.setCellStyle(s.titrePrincipal);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        // ----- Ligne 1 : Sous-titre -----
        Row r1 = sheet.createRow(1);
        r1.setHeightInPoints(22);
        Cell sousTitre = r1.createCell(0);
        sousTitre.setCellValue("Journal de caisse — Récapitulatif");
        sousTitre.setCellStyle(s.sousTitre);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 1));

        int row = 3;

        // ----- Section : identification -----
        row = ecrireSection(sheet, row, "IDENTIFICATION", s);
        row = ecrireLigne(sheet, row, "Caisse", j.getCaisse().getCode() + " — "
                + j.getCaisse().getLibelle(), s);
        row = ecrireLigne(sheet, row, "Date du journal", j.getDateJournal().toString(), s);
        row = ecrireLigne(sheet, row, "Caissier", j.getCaissier().getNomComplet()
                + " (" + j.getCaissier().getMatricule() + ")", s);
        row = ecrireLigneDateTime(sheet, row, "Ouvert le", j.getOuvertLe(), s);
        row = ecrireLigneDateTime(sheet, row, "Clôturé le", j.getClotureLe(), s);

        row++;

        // ----- Section : totaux -----
        row = ecrireSection(sheet, row, "TOTAUX", s);
        row = ecrireLigneMontant(sheet, row, "Fond d'ouverture", j.getFondOuverture(), s);
        row = ecrireLigneMontant(sheet, row, "Total des entrées", j.getTotalEntrees(),
                s.montantVert);
        row = ecrireLigneMontant(sheet, row, "Total des sorties", j.getTotalSorties(),
                s.montantRouge);
        row = ecrireLigneMontant(sheet, row, "Solde théorique", j.getSoldeTheorique(),
                s.montantImportant);
        row = ecrireLigneMontant(sheet, row, "Solde réel compté", j.getSoldeReel(),
                s.montantImportant);

        // ----- Écart (coloré selon signe) -----
        BigDecimal ecart = j.getEcart();
        CellStyle styleEcart;
        String libEcart;
        if (ecart == null) {
            styleEcart = s.montantImportant;
            libEcart = "Écart";
        } else if (ecart.signum() == 0) {
            styleEcart = s.montantImportant;
            libEcart = "Écart (aucun)";
        } else if (ecart.signum() > 0) {
            styleEcart = s.montantVertImportant;
            libEcart = "Écart (excédent)";
        } else {
            styleEcart = s.montantRougeImportant;
            libEcart = "Écart (manquant)";
        }
        row = ecrireLigneMontant(sheet, row, libEcart, ecart, styleEcart);

        row++;

        // ----- Section : validation -----
        row = ecrireSection(sheet, row, "VALIDATION", s);
        row = ecrireLigne(sheet, row, "Clôturé",
                j.isCloture() ? "Oui" : "Non", s);
        row = ecrireLigne(sheet, row, "Validé par superviseur",
                j.getValideePar() != null
                        ? j.getValideePar().getNomComplet()
                        : "Non validé", s);
        if (j.getCommentaire() != null && !j.getCommentaire().isBlank()) {
            row = ecrireLigne(sheet, row, "Commentaire", j.getCommentaire(), s);
        }

        row += 2;

        // ----- Pied de page -----
        Row footer = sheet.createRow(row);
        Cell cf = footer.createCell(0);
        cf.setCellValue("Document généré le "
                + LocalDateTime.now().format(java.time.format.DateTimeFormatter
                .ofPattern("dd/MM/yyyy 'à' HH:mm")));
        cf.setCellStyle(s.pieds);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 1));
    }

    // ==================================================================
    //  Onglet 2 : Opérations
    // ==================================================================

    private void ecrireOngletOperations(XSSFWorkbook workbook,
                                        List<OperationCaisse> operations,
                                        StylePack s) {
        XSSFSheet sheet = workbook.createSheet("Opérations");

        // Largeurs : Date(20), N°(20), Type(10), Catégorie(24), Mode(14),
        //            Client(22), Motif(40), Référence(18), Montant(14), Annulée(10)
        int[] widths = {4800, 4800, 2800, 6400, 4000, 6400, 10000, 4800, 4000, 3200};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }

        // ----- Ligne 0 : en-têtes -----
        Row header = sheet.createRow(0);
        header.setHeightInPoints(26);
        String[] entetes = {
                "Date & heure", "N° Reçu", "Type", "Catégorie",
                "Mode paiement", "Client", "Motif", "Référence",
                "Montant (FCFA)", "Annulée"
        };
        for (int i = 0; i < entetes.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(entetes[i]);
            c.setCellStyle(s.entete);
        }

        // ----- Corps -----
        int rowIdx = 1;
        for (OperationCaisse op : operations) {
            Row row = sheet.createRow(rowIdx);
            boolean pair = rowIdx % 2 == 0;
            CellStyle base = pair ? s.celluleAlt : s.cellule;

            // Date & heure
            Cell c0 = row.createCell(0);
            c0.setCellValue(op.getDateOperation() != null
                    ? op.getDateOperation().toString().replace('T', ' ')
                    : "");
            c0.setCellStyle(base);

            // Numéro
            Cell c1 = row.createCell(1);
            c1.setCellValue(op.getNumeroRecu());
            c1.setCellStyle(s.celluleMonospace);

            // Type (avec couleur conditionnelle)
            Cell c2 = row.createCell(2);
            c2.setCellValue(op.getTypeOperation() == TypeOperation.ENTREE ? "ENTRÉE" : "SORTIE");
            c2.setCellStyle(op.getTypeOperation() == TypeOperation.ENTREE
                    ? s.typeEntree : s.typeSortie);

            // Catégorie
            Cell c3 = row.createCell(3);
            c3.setCellValue(op.getCategorie().getLibelle());
            c3.setCellStyle(base);

            // Mode paiement
            Cell c4 = row.createCell(4);
            c4.setCellValue(op.getModePaiement().name().replace('_', ' '));
            c4.setCellStyle(base);

            // Client
            Cell c5 = row.createCell(5);
            c5.setCellValue(op.getClient() != null
                    ? op.getClient().getRaisonSociale() : "");
            c5.setCellStyle(base);

            // Motif
            Cell c6 = row.createCell(6);
            c6.setCellValue(op.getMotif());
            c6.setCellStyle(base);

            // Référence
            Cell c7 = row.createCell(7);
            c7.setCellValue(op.getReference() != null ? op.getReference() : "");
            c7.setCellStyle(base);

            // Montant (numérique, formaté FCFA)
            Cell c8 = row.createCell(8);
            if (op.getMontant() != null) {
                c8.setCellValue(op.getMontant().doubleValue());
            }
            c8.setCellStyle(op.getTypeOperation() == TypeOperation.ENTREE
                    ? s.montantVert : s.montantRouge);

            // Annulée (Oui/Non avec surbrillance)
            Cell c9 = row.createCell(9);
            c9.setCellValue(op.isAnnulee() ? "OUI" : "");
            c9.setCellStyle(op.isAnnulee() ? s.cellAnnulee : base);

            rowIdx++;
        }

        // ----- Ligne de totaux -----
        if (!operations.isEmpty()) {
            rowIdx++;
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.setHeightInPoints(22);

            Cell totalLabel = totalRow.createCell(7);
            totalLabel.setCellValue("TOTAL");
            totalLabel.setCellStyle(s.totalLabel);

            // Formule SUMIF : somme des montants non annulés
            int derniereDonnee = operations.size() + 1;
            Cell totalValue = totalRow.createCell(8);
            totalValue.setCellFormula(
                    "SUMIF(J2:J" + derniereDonnee + ",\"\",I2:I" + derniereDonnee + ")");
            totalValue.setCellStyle(s.totalValeur);
        }

        // ----- Fonctionnalités Excel : gel + autofilter -----
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(
                0, Math.max(rowIdx, 1), 0, entetes.length - 1));
    }

    // ==================================================================
    //  Helpers écriture
    // ==================================================================

    private int ecrireSection(XSSFSheet sheet, int row, String titre, StylePack s) {
        Row r = sheet.createRow(row);
        r.setHeightInPoints(22);
        Cell c = r.createCell(0);
        c.setCellValue(titre);
        c.setCellStyle(s.sectionTitre);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 1));
        return row + 1;
    }

    private int ecrireLigne(XSSFSheet sheet, int row, String label,
                            String valeur, StylePack s) {
        Row r = sheet.createRow(row);
        Cell cl = r.createCell(0);
        cl.setCellValue(label);
        cl.setCellStyle(s.labelRecap);

        Cell cv = r.createCell(1);
        cv.setCellValue(valeur != null ? valeur : "—");
        cv.setCellStyle(s.valeurRecap);
        return row + 1;
    }

    private int ecrireLigneDateTime(XSSFSheet sheet, int row, String label,
                                    LocalDateTime dt, StylePack s) {
        return ecrireLigne(sheet, row, label,
                dt == null ? "—" : dt.toString().replace('T', ' '), s);
    }

    private int ecrireLigneMontant(XSSFSheet sheet, int row, String label,
                                   BigDecimal montant, StylePack s) {
        return ecrireLigneMontant(sheet, row, label, montant, s.montantRecap);
    }

    private int ecrireLigneMontant(XSSFSheet sheet, int row, String label,
                                   BigDecimal montant, CellStyle styleMontant) {
        Row r = sheet.createRow(row);
        Cell cl = r.createCell(0);
        cl.setCellValue(label);
        cl.setCellStyle(findLabelStyle(styleMontant));

        Cell cv = r.createCell(1);
        if (montant != null) {
            cv.setCellValue(montant.doubleValue());
        }
        cv.setCellStyle(styleMontant);
        return row + 1;
    }

    private CellStyle findLabelStyle(CellStyle ref) {
        // Les styles de labels sont tous identiques visuellement,
        // on réutilise celui fourni dans le pack
        return ref;  // simplification ; voir creerStyles pour la source
    }

    // ==================================================================
    //  Construction des styles (centralisée)
    // ==================================================================

    private StylePack creerStyles(XSSFWorkbook wb) {
        CreationHelper helper = wb.getCreationHelper();
        String fmtMontant = "#,##0 \"FCFA\"";

        StylePack s = new StylePack();

        // ----- Titre principal -----
        s.titrePrincipal = baseStyle(wb);
        XSSFFont fontTitre = fontBold(wb, 14);
        fontTitre.setColor(new XSSFColor(RGB_RTS_PRIMARY, null));
        s.titrePrincipal.setFont(fontTitre);
        s.titrePrincipal.setAlignment(HorizontalAlignment.CENTER);
        s.titrePrincipal.setVerticalAlignment(VerticalAlignment.CENTER);

        // ----- Sous-titre -----
        s.sousTitre = baseStyle(wb);
        XSSFFont fontSous = fontBold(wb, 11);
        fontSous.setColor(new XSSFColor(RGB_RTS_PRIMARY_DARK, null));
        s.sousTitre.setFont(fontSous);
        s.sousTitre.setAlignment(HorizontalAlignment.CENTER);

        // ----- Titre de section (bandeau bleu) -----
        s.sectionTitre = baseStyle(wb);
        XSSFFont fontSection = fontBold(wb, 11);
        fontSection.setColor(IndexedColors.WHITE.getIndex());
        s.sectionTitre.setFont(fontSection);
        s.sectionTitre.setAlignment(HorizontalAlignment.LEFT);
        s.sectionTitre.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.sectionTitre, RGB_RTS_PRIMARY);
        applyBorder(s.sectionTitre);

        // ----- Label récap -----
        s.labelRecap = baseStyle(wb);
        XSSFFont fontLabel = fontBold(wb, 10);
        fontLabel.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
        s.labelRecap.setFont(fontLabel);
        s.labelRecap.setAlignment(HorizontalAlignment.LEFT);
        s.labelRecap.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(s.labelRecap);

        // ----- Valeur récap -----
        s.valeurRecap = baseStyle(wb);
        s.valeurRecap.setFont(fontNormal(wb, 10));
        s.valeurRecap.setAlignment(HorizontalAlignment.RIGHT);
        s.valeurRecap.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorder(s.valeurRecap);

        // ----- Montant récap (neutre) -----
        s.montantRecap = baseStyle(wb);
        s.montantRecap.setFont(fontNormal(wb, 10));
        s.montantRecap.setAlignment(HorizontalAlignment.RIGHT);
        s.montantRecap.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        applyBorder(s.montantRecap);

        // ----- Montant important (gras) -----
        s.montantImportant = baseStyle(wb);
        s.montantImportant.setFont(fontBold(wb, 11));
        s.montantImportant.setAlignment(HorizontalAlignment.RIGHT);
        s.montantImportant.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        applyBorder(s.montantImportant);

        // ----- Montant vert (entrée) -----
        s.montantVert = baseStyle(wb);
        XSSFFont fontVert = fontNormal(wb, 10);
        fontVert.setColor(IndexedColors.GREEN.getIndex());
        fontVert.setBold(true);
        s.montantVert.setFont(fontVert);
        s.montantVert.setAlignment(HorizontalAlignment.RIGHT);
        s.montantVert.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        applyBorder(s.montantVert);

        // ----- Montant rouge (sortie) -----
        s.montantRouge = baseStyle(wb);
        XSSFFont fontRouge = fontNormal(wb, 10);
        fontRouge.setColor(IndexedColors.DARK_RED.getIndex());
        fontRouge.setBold(true);
        s.montantRouge.setFont(fontRouge);
        s.montantRouge.setAlignment(HorizontalAlignment.RIGHT);
        s.montantRouge.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        applyBorder(s.montantRouge);

        s.montantVertImportant = cloneStyle(wb, s.montantVert);
        s.montantVertImportant.setFont(fontBold(wb, 11, IndexedColors.GREEN.getIndex()));
        setBackgroundColor(s.montantVertImportant, RGB_SUCCESS_BG);

        s.montantRougeImportant = cloneStyle(wb, s.montantRouge);
        s.montantRougeImportant.setFont(fontBold(wb, 11, IndexedColors.DARK_RED.getIndex()));
        setBackgroundColor(s.montantRougeImportant, RGB_DANGER_BG);

        // ----- En-tête tableau opérations -----
        s.entete = baseStyle(wb);
        XSSFFont fontEntete = fontBold(wb, 11);
        fontEntete.setColor(IndexedColors.WHITE.getIndex());
        s.entete.setFont(fontEntete);
        s.entete.setAlignment(HorizontalAlignment.CENTER);
        s.entete.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.entete, RGB_RTS_PRIMARY);
        applyBorder(s.entete);

        // ----- Cellule standard -----
        s.cellule = baseStyle(wb);
        s.cellule.setFont(fontNormal(wb, 10));
        s.cellule.setVerticalAlignment(VerticalAlignment.CENTER);
        s.cellule.setWrapText(true);
        applyBorder(s.cellule);

        // ----- Cellule alternée (ligne paire) -----
        s.celluleAlt = cloneStyle(wb, s.cellule);
        setBackgroundColor(s.celluleAlt, RGB_ROW_ALT);

        // ----- Cellule monospace (pour les numéros de reçu) -----
        s.celluleMonospace = cloneStyle(wb, s.cellule);
        XSSFFont fontMono = wb.createFont();
        fontMono.setFontName("Consolas");
        fontMono.setFontHeightInPoints((short) 9);
        s.celluleMonospace.setFont(fontMono);

        // ----- Badges type -----
        s.typeEntree = baseStyle(wb);
        XSSFFont fontTypeEntree = fontBold(wb, 10, IndexedColors.GREEN.getIndex());
        s.typeEntree.setFont(fontTypeEntree);
        s.typeEntree.setAlignment(HorizontalAlignment.CENTER);
        setBackgroundColor(s.typeEntree, RGB_SUCCESS_BG);
        applyBorder(s.typeEntree);

        s.typeSortie = baseStyle(wb);
        XSSFFont fontTypeSortie = fontBold(wb, 10, IndexedColors.DARK_RED.getIndex());
        s.typeSortie.setFont(fontTypeSortie);
        s.typeSortie.setAlignment(HorizontalAlignment.CENTER);
        setBackgroundColor(s.typeSortie, RGB_DANGER_BG);
        applyBorder(s.typeSortie);

        // ----- Cellule annulée -----
        s.cellAnnulee = baseStyle(wb);
        XSSFFont fontAnnul = fontBold(wb, 10, IndexedColors.WHITE.getIndex());
        s.cellAnnulee.setFont(fontAnnul);
        s.cellAnnulee.setAlignment(HorizontalAlignment.CENTER);
        setBackgroundColor(s.cellAnnulee, new byte[]{(byte) 0xC6, 0x28, 0x28});
        applyBorder(s.cellAnnulee);

        // ----- Totaux -----
        s.totalLabel = baseStyle(wb);
        XSSFFont fontTotalLab = fontBold(wb, 11);
        s.totalLabel.setFont(fontTotalLab);
        s.totalLabel.setAlignment(HorizontalAlignment.RIGHT);
        setBackgroundColor(s.totalLabel, RGB_RTS_ACCENT);
        applyBorder(s.totalLabel);

        s.totalValeur = baseStyle(wb);
        s.totalValeur.setFont(fontBold(wb, 12));
        s.totalValeur.setAlignment(HorizontalAlignment.RIGHT);
        s.totalValeur.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        setBackgroundColor(s.totalValeur, RGB_RTS_ACCENT);
        applyBorder(s.totalValeur);

        // ----- Pied de page -----
        s.pieds = baseStyle(wb);
        XSSFFont fontPied = wb.createFont();
        fontPied.setItalic(true);
        fontPied.setFontHeightInPoints((short) 9);
        fontPied.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.pieds.setFont(fontPied);
        s.pieds.setAlignment(HorizontalAlignment.CENTER);

        return s;
    }

    // ==================================================================
    //  Primitives de style
    // ==================================================================

    private XSSFCellStyle baseStyle(XSSFWorkbook wb) {
        return wb.createCellStyle();
    }

    private XSSFCellStyle cloneStyle(XSSFWorkbook wb, XSSFCellStyle src) {
        XSSFCellStyle clone = wb.createCellStyle();
        clone.cloneStyleFrom(src);
        return clone;
    }

    private XSSFFont fontNormal(Workbook wb, int size) {
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) size);
        return (XSSFFont) f;
    }

    private XSSFFont fontBold(Workbook wb, int size) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) size);
        return (XSSFFont) f;
    }

    private XSSFFont fontBold(Workbook wb, int size, short color) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) size);
        f.setColor(color);
        return (XSSFFont) f;
    }

    private void setBackgroundColor(CellStyle style, byte[] rgb) {
        ((XSSFCellStyle) style).setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private void applyBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    // ==================================================================
    //  Pack de styles (structure interne)
    // ==================================================================

    private static class StylePack {
        XSSFCellStyle titrePrincipal;
        XSSFCellStyle sousTitre;
        XSSFCellStyle sectionTitre;
        XSSFCellStyle labelRecap;
        XSSFCellStyle valeurRecap;
        XSSFCellStyle montantRecap;
        XSSFCellStyle montantImportant;
        XSSFCellStyle montantVert;
        XSSFCellStyle montantRouge;
        XSSFCellStyle montantVertImportant;
        XSSFCellStyle montantRougeImportant;
        XSSFCellStyle entete;
        XSSFCellStyle cellule;
        XSSFCellStyle celluleAlt;
        XSSFCellStyle celluleMonospace;
        XSSFCellStyle typeEntree;
        XSSFCellStyle typeSortie;
        XSSFCellStyle cellAnnulee;
        XSSFCellStyle totalLabel;
        XSSFCellStyle totalValeur;
        XSSFCellStyle pieds;
    }
}
