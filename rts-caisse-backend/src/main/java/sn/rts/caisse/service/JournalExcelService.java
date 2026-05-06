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
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.JournalCaisse;
import sn.rts.caisse.model.ModePaiement;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.repository.JournalCaisseRepository;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Génère l'export Excel (.xlsx) d'un journal de caisse — <b>version 2</b>.
 *
 * <h2>Améliorations v2</h2>
 * <ul>
 *   <li><b>Onglet Récapitulatif</b> enrichi avec une section "PÉRIODE"
 *       (année, mois, date), une section "STATISTIQUES" (nb d'opérations
 *       par type, nb d'annulées) et deux sections de répartition
 *       (par mode de paiement et par catégorie) avec pourcentages.</li>
 *   <li><b>Onglet Opérations</b> :
 *       <ul>
 *         <li>dates au format Excel natif {@code dd/MM/yyyy HH:mm}
 *             (plus de microsecondes parasites);</li>
 *         <li>colonne "Annulée" affiche "NON" (gris) au lieu d'être vide,
 *             ou "OUI" (rouge) si l'opération est annulée;</li>
 *         <li>nouvelle colonne "Motif annulation";</li>
 *         <li>lignes annulées : fond rouge clair + montant barré.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Mise en forme professionnelle : charte RTS (bleu primaire, doré accent),
 * en-têtes gras centrés fond bleu, lignes alternées pour lisibilité,
 * formats monétaires FCFA, bordures, gel de la ligne d'en-tête,
 * autofilter activé pour tri/filtrage côté Excel.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalExcelService {

    // ----- Palette RTS (reprise du thème web) -----
    private static final byte[] RGB_RTS_PRIMARY      = {0x0A, 0x4D, (byte) 0x8C};
    private static final byte[] RGB_RTS_PRIMARY_DARK = {0x07, 0x3A, 0x6B};
    private static final byte[] RGB_RTS_ACCENT       = {(byte) 0xE8, (byte) 0xA3, 0x17};
    private static final byte[] RGB_ROW_ALT          = {(byte) 0xF5, (byte) 0xF5, (byte) 0xF7};
    private static final byte[] RGB_SUCCESS_BG       = {(byte) 0xE8, (byte) 0xF5, (byte) 0xE9};
    private static final byte[] RGB_DANGER_BG        = {(byte) 0xFF, (byte) 0xEB, (byte) 0xEE};
    private static final byte[] RGB_NEUTRAL_BG       = {(byte) 0xEC, (byte) 0xEF, (byte) 0xF1};

    private static final DateTimeFormatter FMT_DATE_LISIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATETIME_LISIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale LOCALE_FR = Locale.FRENCH;

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

            ecrireOngletRecap(workbook, journal, operations, styles);
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
    //  Onglet 1 : Récapitulatif (enrichi)
    // ==================================================================

    private void ecrireOngletRecap(XSSFWorkbook workbook,
                                   JournalCaisse j,
                                   List<OperationCaisse> operations,
                                   StylePack s) {
        XSSFSheet sheet = workbook.createSheet("Récapitulatif");
        sheet.setDefaultColumnWidth(22);
        sheet.setColumnWidth(0, 9500);  // ~31 caractères : labels
        sheet.setColumnWidth(1, 7000);  // ~23 caractères : valeurs
        sheet.setColumnWidth(2, 4500);  // ~15 caractères : compléments (%)

        // ----- Ligne 0 : Titre RTS -----
        Row r0 = sheet.createRow(0);
        r0.setHeightInPoints(34);
        Cell titre = r0.createCell(0);
        titre.setCellValue("RADIODIFFUSION TÉLÉVISION SÉNÉGALAISE");
        titre.setCellStyle(s.titrePrincipal);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

        // ----- Ligne 1 : Sous-titre -----
        Row r1 = sheet.createRow(1);
        r1.setHeightInPoints(22);
        Cell sousTitre = r1.createCell(0);
        sousTitre.setCellValue("Journal de caisse — Récapitulatif");
        sousTitre.setCellStyle(s.sousTitre);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));

        int row = 3;

        // ----- Section : période (année / mois / date) -----
        row = ecrireSection(sheet, row, "PÉRIODE", s);
        int annee = j.getDateJournal().getYear();
        Month mois = j.getDateJournal().getMonth();
        String moisFr = capitalize(mois.getDisplayName(TextStyle.FULL, LOCALE_FR));
        row = ecrireLigne(sheet, row, "Année", String.valueOf(annee), s);
        row = ecrireLigne(sheet, row, "Mois",
                moisFr + " " + annee, s);
        row = ecrireLigne(sheet, row, "Date du journal",
                j.getDateJournal().format(FMT_DATE_LISIBLE), s);

        row++;

        // ----- Section : identification -----
        row = ecrireSection(sheet, row, "IDENTIFICATION", s);
        row = ecrireLigne(sheet, row, "Caisse", j.getCaisse().getCode() + " — "
                + j.getCaisse().getLibelle(), s);
        row = ecrireLigne(sheet, row, "Caissier", j.getCaissier().getNomComplet()
                + " (" + j.getCaissier().getMatricule() + ")", s);
        row = ecrireLigneDateTime(sheet, row, "Ouvert le", j.getOuvertLe(), s);
        row = ecrireLigneDateTime(sheet, row, "Clôturé le", j.getClotureLe(), s);

        row++;

        // ----- Section : totaux financiers -----
        row = ecrireSection(sheet, row, "TOTAUX FINANCIERS", s);
        row = ecrireLigneMontant(sheet, row, "Fond d'ouverture",
                j.getFondOuverture(), s.montantRecap);
        row = ecrireLigneMontant(sheet, row, "Total des entrées",
                j.getTotalEntrees(), s.montantVert);
        row = ecrireLigneMontant(sheet, row, "Total des sorties",
                j.getTotalSorties(), s.montantRouge);
        row = ecrireLigneMontant(sheet, row, "Solde théorique",
                j.getSoldeTheorique(), s.montantImportant);
        row = ecrireLigneMontant(sheet, row, "Solde réel compté",
                j.getSoldeReel(), s.montantImportant);

        // ----- Écart (coloré selon signe) -----
        BigDecimal ecart = j.getEcart();
        CellStyle styleEcart;
        String libEcart;
        if (ecart == null) {
            styleEcart = s.montantImportant;
            libEcart = "Écart";
        } else if (ecart.signum() == 0) {
            styleEcart = s.montantVertImportant;
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

        // ----- Section : statistiques d'opérations -----
        long nbTotal    = operations.size();
        long nbEntrees  = operations.stream()
                .filter(o -> o.getTypeOperation() == TypeOperation.ENTREE).count();
        long nbSorties  = operations.stream()
                .filter(o -> o.getTypeOperation() == TypeOperation.SORTIE).count();
        long nbAnnulees = operations.stream().filter(OperationCaisse::isAnnulee).count();
        long nbActives  = nbTotal - nbAnnulees;

        row = ecrireSection(sheet, row, "STATISTIQUES OPÉRATIONS", s);
        row = ecrireLigne(sheet, row, "Total opérations", String.valueOf(nbTotal), s);
        row = ecrireLigne(sheet, row, "  • Entrées", String.valueOf(nbEntrees), s);
        row = ecrireLigne(sheet, row, "  • Sorties", String.valueOf(nbSorties), s);
        row = ecrireLigne(sheet, row, "Opérations actives", String.valueOf(nbActives), s);
        row = ecrireLigne(sheet, row, "Opérations annulées",
                String.valueOf(nbAnnulees),
                nbAnnulees > 0 ? s : s);

        row++;

        // ----- Section : répartition par mode de paiement -----
        row = ecrireSection(sheet, row, "RÉPARTITION PAR MODE DE PAIEMENT", s);
        row = ecrireRepartitionEntete(sheet, row, "Mode", s);
        Map<ModePaiement, BigDecimal> parMode = new LinkedHashMap<>();
        Map<ModePaiement, Long> nbParMode    = new LinkedHashMap<>();
        BigDecimal grandTotalNonAnnule = BigDecimal.ZERO;
        for (OperationCaisse op : operations) {
            if (op.isAnnulee() || op.getMontant() == null) continue;
            ModePaiement mp = op.getModePaiement();
            parMode.merge(mp, op.getMontant(), BigDecimal::add);
            nbParMode.merge(mp, 1L, Long::sum);
            grandTotalNonAnnule = grandTotalNonAnnule.add(op.getMontant());
        }
        if (parMode.isEmpty()) {
            row = ecrireLigne(sheet, row, "Aucune opération active", "—", s);
        } else {
            for (Map.Entry<ModePaiement, BigDecimal> e : parMode.entrySet()) {
                String label = e.getKey().name().replace('_', ' ')
                        + " (" + nbParMode.get(e.getKey()) + " op.)";
                String pct = pourcentage(e.getValue(), grandTotalNonAnnule);
                row = ecrireLigneRepartition(sheet, row, label, e.getValue(), pct, s);
            }
        }

        row++;

        // ----- Section : répartition par catégorie -----
        row = ecrireSection(sheet, row, "RÉPARTITION PAR CATÉGORIE", s);
        row = ecrireRepartitionEntete(sheet, row, "Catégorie", s);
        // LinkedHashMap pour conserver l'ordre d'apparition
        Map<String, BigDecimal> parCat   = new LinkedHashMap<>();
        Map<String, Long>       nbParCat = new LinkedHashMap<>();
        for (OperationCaisse op : operations) {
            if (op.isAnnulee() || op.getMontant() == null) continue;
            String cat = op.getCategorie() != null
                    ? op.getCategorie().getLibelle()
                    : "(non renseignée)";
            parCat.merge(cat, op.getMontant(), BigDecimal::add);
            nbParCat.merge(cat, 1L, Long::sum);
        }
        if (parCat.isEmpty()) {
            row = ecrireLigne(sheet, row, "Aucune opération active", "—", s);
        } else {
            for (Map.Entry<String, BigDecimal> e : parCat.entrySet()) {
                String label = e.getKey() + " (" + nbParCat.get(e.getKey()) + " op.)";
                String pct = pourcentage(e.getValue(), grandTotalNonAnnule);
                row = ecrireLigneRepartition(sheet, row, label, e.getValue(), pct, s);
            }
        }

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
                + LocalDateTime.now().format(DateTimeFormatter
                .ofPattern("dd/MM/yyyy 'à' HH:mm")));
        cf.setCellStyle(s.pieds);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 2));
    }

    // ==================================================================
    //  Onglet 2 : Opérations (enrichi)
    // ==================================================================

    private void ecrireOngletOperations(XSSFWorkbook workbook,
                                        List<OperationCaisse> operations,
                                        StylePack s) {
        XSSFSheet sheet = workbook.createSheet("Opérations");

        // Largeurs : Date(20) | N°(20) | Type(10) | Cat(24) | Mode(14)
        //         | Client(22) | Motif(40) | Réf(18) | Montant(14)
        //         | Annulée(10) | Motif annulation(28)
        int[] widths = {4800, 5400, 2800, 6400, 4000,
                        6400, 10000, 4800, 4000,
                        2800, 7200};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }

        // ----- Ligne 0 : en-têtes -----
        Row header = sheet.createRow(0);
        header.setHeightInPoints(28);
        String[] entetes = {
                "Date & heure", "N° Reçu", "Type", "Catégorie",
                "Mode paiement", "Client", "Motif", "Référence",
                "Montant (FCFA)", "Annulée", "Motif annulation"
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
            row.setHeightInPoints(20);

            boolean annulee = op.isAnnulee();
            boolean pair    = rowIdx % 2 == 0;
            CellStyle base  = annulee
                    ? s.celluleAnnuleeBg
                    : (pair ? s.celluleAlt : s.cellule);

            // Date & heure (Excel natif, pas de microsecondes)
            Cell c0 = row.createCell(0);
            if (op.getDateOperation() != null) {
                c0.setCellValue(java.sql.Timestamp.valueOf(op.getDateOperation()));
                c0.setCellStyle(annulee ? s.dateCellAnnulee : s.dateCell);
            } else {
                c0.setCellValue("");
                c0.setCellStyle(base);
            }

            // Numéro de reçu (monospace)
            Cell c1 = row.createCell(1);
            c1.setCellValue(op.getNumeroRecu());
            c1.setCellStyle(annulee ? s.celluleMonoAnnulee : s.celluleMonospace);

            // Type (badge ENTRÉE / SORTIE)
            Cell c2 = row.createCell(2);
            boolean entree = op.getTypeOperation() == TypeOperation.ENTREE;
            c2.setCellValue(entree ? "ENTRÉE" : "SORTIE");
            c2.setCellStyle(entree ? s.typeEntree : s.typeSortie);

            // Catégorie
            Cell c3 = row.createCell(3);
            c3.setCellValue(op.getCategorie() != null
                    ? op.getCategorie().getLibelle() : "");
            c3.setCellStyle(base);

            // Mode paiement
            Cell c4 = row.createCell(4);
            c4.setCellValue(op.getModePaiement() != null
                    ? op.getModePaiement().name().replace('_', ' ') : "");
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

            // Montant (numérique, formaté FCFA, barré si annulé)
            Cell c8 = row.createCell(8);
            if (op.getMontant() != null) {
                c8.setCellValue(op.getMontant().doubleValue());
            }
            CellStyle styleMontant;
            if (annulee) {
                styleMontant = s.montantAnnule;
            } else {
                styleMontant = entree ? s.montantVert : s.montantRouge;
            }
            c8.setCellStyle(styleMontant);

            // Annulée (OUI / NON)
            Cell c9 = row.createCell(9);
            c9.setCellValue(annulee ? "OUI" : "NON");
            c9.setCellStyle(annulee ? s.cellAnnulee : s.cellNonAnnulee);

            // Motif annulation
            Cell c10 = row.createCell(10);
            c10.setCellValue(annulee && op.getMotifAnnulation() != null
                    ? op.getMotifAnnulation()
                    : "");
            c10.setCellStyle(base);

            rowIdx++;
        }

        // ----- Ligne de totaux (somme des opérations non annulées) -----
        if (!operations.isEmpty()) {
            rowIdx++;
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.setHeightInPoints(24);

            Cell totalLabel = totalRow.createCell(7);
            totalLabel.setCellValue("TOTAL (non annulé)");
            totalLabel.setCellStyle(s.totalLabel);

            int derniereDonnee = operations.size() + 1;  // ligne Excel = index+1
            Cell totalValue = totalRow.createCell(8);
            // SUMIF sur la colonne "Annulée" (J) = "NON"
            totalValue.setCellFormula(
                    "SUMIF(J2:J" + derniereDonnee + ",\"NON\",I2:I" + derniereDonnee + ")");
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
        // Cellules de remplissage pour étendre le bandeau bleu sur 3 colonnes
        Cell c1 = r.createCell(1);
        c1.setCellStyle(s.sectionTitre);
        Cell c2 = r.createCell(2);
        c2.setCellStyle(s.sectionTitre);
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 2));
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

        // Colonne 2 : vide mais bordée pour cohérence visuelle
        Cell c3 = r.createCell(2);
        c3.setCellStyle(s.valeurRecap);
        return row + 1;
    }

    private int ecrireLigneDateTime(XSSFSheet sheet, int row, String label,
                                    LocalDateTime dt, StylePack s) {
        return ecrireLigne(sheet, row, label,
                dt == null ? "—" : dt.format(FMT_DATETIME_LISIBLE), s);
    }

    private int ecrireLigneMontant(XSSFSheet sheet, int row, String label,
                                   BigDecimal montant, CellStyle styleMontant) {
        Row r = sheet.createRow(row);
        Cell cl = r.createCell(0);
        cl.setCellValue(label);
        cl.setCellStyle(styleMontant);  // label hérite du style pour cohérence

        Cell cv = r.createCell(1);
        if (montant != null) {
            cv.setCellValue(montant.doubleValue());
        }
        cv.setCellStyle(styleMontant);

        Cell c3 = r.createCell(2);
        c3.setCellStyle(styleMontant);
        return row + 1;
    }

    /** En-tête de tableau de répartition (3 colonnes : libellé / montant / %). */
    private int ecrireRepartitionEntete(XSSFSheet sheet, int row,
                                        String libCol1, StylePack s) {
        Row r = sheet.createRow(row);
        r.setHeightInPoints(20);
        Cell c0 = r.createCell(0);
        c0.setCellValue(libCol1);
        c0.setCellStyle(s.entete);

        Cell c1 = r.createCell(1);
        c1.setCellValue("Total");
        c1.setCellStyle(s.entete);

        Cell c2 = r.createCell(2);
        c2.setCellValue("Part");
        c2.setCellStyle(s.entete);
        return row + 1;
    }

    private int ecrireLigneRepartition(XSSFSheet sheet, int row,
                                       String libelle, BigDecimal montant,
                                       String pourcentage, StylePack s) {
        Row r = sheet.createRow(row);
        Cell cl = r.createCell(0);
        cl.setCellValue(libelle);
        cl.setCellStyle(s.labelRecap);

        Cell cv = r.createCell(1);
        if (montant != null) {
            cv.setCellValue(montant.doubleValue());
        }
        cv.setCellStyle(s.montantRecap);

        Cell cp = r.createCell(2);
        cp.setCellValue(pourcentage);
        cp.setCellStyle(s.pourcentageRecap);
        return row + 1;
    }

    private static String pourcentage(BigDecimal partie, BigDecimal total) {
        if (total == null || total.signum() == 0 || partie == null) return "—";
        BigDecimal pct = partie.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
        return pct.toPlainString() + " %";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ==================================================================
    //  Construction des styles (centralisée)
    // ==================================================================

    private StylePack creerStyles(XSSFWorkbook wb) {
        CreationHelper helper = wb.getCreationHelper();
        String fmtMontant = "#,##0 \"FCFA\"";
        String fmtDateTime = "dd/mm/yyyy hh:mm";

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

        // ----- Pourcentage récap (centré, gris) -----
        s.pourcentageRecap = baseStyle(wb);
        XSSFFont fontPct = fontNormal(wb, 10);
        fontPct.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
        s.pourcentageRecap.setFont(fontPct);
        s.pourcentageRecap.setAlignment(HorizontalAlignment.CENTER);
        s.pourcentageRecap.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.pourcentageRecap, RGB_NEUTRAL_BG);
        applyBorder(s.pourcentageRecap);

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
        XSSFFont fontVert = fontBold(wb, 10, IndexedColors.GREEN.getIndex());
        s.montantVert.setFont(fontVert);
        s.montantVert.setAlignment(HorizontalAlignment.RIGHT);
        s.montantVert.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        applyBorder(s.montantVert);

        // ----- Montant rouge (sortie) -----
        s.montantRouge = baseStyle(wb);
        XSSFFont fontRouge = fontBold(wb, 10, IndexedColors.DARK_RED.getIndex());
        s.montantRouge.setFont(fontRouge);
        s.montantRouge.setAlignment(HorizontalAlignment.RIGHT);
        s.montantRouge.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        applyBorder(s.montantRouge);

        // ----- Montant annulé (gris, barré) -----
        s.montantAnnule = baseStyle(wb);
        XSSFFont fontAnnule = wb.createFont();
        fontAnnule.setFontHeightInPoints((short) 10);
        fontAnnule.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        fontAnnule.setStrikeout(true);
        s.montantAnnule.setFont(fontAnnule);
        s.montantAnnule.setAlignment(HorizontalAlignment.RIGHT);
        s.montantAnnule.setDataFormat(helper.createDataFormat().getFormat(fmtMontant));
        setBackgroundColor(s.montantAnnule, RGB_DANGER_BG);
        applyBorder(s.montantAnnule);

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

        // ----- Cellule pour ligne d'opération annulée -----
        s.celluleAnnuleeBg = cloneStyle(wb, s.cellule);
        setBackgroundColor(s.celluleAnnuleeBg, RGB_DANGER_BG);

        // ----- Cellule monospace (numéros de reçu) -----
        s.celluleMonospace = cloneStyle(wb, s.cellule);
        XSSFFont fontMono = wb.createFont();
        fontMono.setFontName("Consolas");
        fontMono.setFontHeightInPoints((short) 9);
        s.celluleMonospace.setFont(fontMono);

        // ----- Cellule monospace pour ligne annulée -----
        s.celluleMonoAnnulee = cloneStyle(wb, s.celluleAnnuleeBg);
        XSSFFont fontMonoAnnule = wb.createFont();
        fontMonoAnnule.setFontName("Consolas");
        fontMonoAnnule.setFontHeightInPoints((short) 9);
        fontMonoAnnule.setStrikeout(true);
        fontMonoAnnule.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.celluleMonoAnnulee.setFont(fontMonoAnnule);

        // ----- Cellule date (format Excel natif) -----
        s.dateCell = cloneStyle(wb, s.cellule);
        s.dateCell.setDataFormat(helper.createDataFormat().getFormat(fmtDateTime));
        s.dateCell.setAlignment(HorizontalAlignment.CENTER);

        s.dateCellAnnulee = cloneStyle(wb, s.celluleAnnuleeBg);
        s.dateCellAnnulee.setDataFormat(helper.createDataFormat().getFormat(fmtDateTime));
        s.dateCellAnnulee.setAlignment(HorizontalAlignment.CENTER);
        XSSFFont fontDateAnn = wb.createFont();
        fontDateAnn.setFontHeightInPoints((short) 10);
        fontDateAnn.setStrikeout(true);
        fontDateAnn.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.dateCellAnnulee.setFont(fontDateAnn);

        // ----- Badges type -----
        s.typeEntree = baseStyle(wb);
        XSSFFont fontTypeEntree = fontBold(wb, 10, IndexedColors.GREEN.getIndex());
        s.typeEntree.setFont(fontTypeEntree);
        s.typeEntree.setAlignment(HorizontalAlignment.CENTER);
        s.typeEntree.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.typeEntree, RGB_SUCCESS_BG);
        applyBorder(s.typeEntree);

        s.typeSortie = baseStyle(wb);
        XSSFFont fontTypeSortie = fontBold(wb, 10, IndexedColors.DARK_RED.getIndex());
        s.typeSortie.setFont(fontTypeSortie);
        s.typeSortie.setAlignment(HorizontalAlignment.CENTER);
        s.typeSortie.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.typeSortie, RGB_DANGER_BG);
        applyBorder(s.typeSortie);

        // ----- Cellule "Annulée" : OUI (rouge) -----
        s.cellAnnulee = baseStyle(wb);
        XSSFFont fontAnnul = fontBold(wb, 10, IndexedColors.WHITE.getIndex());
        s.cellAnnulee.setFont(fontAnnul);
        s.cellAnnulee.setAlignment(HorizontalAlignment.CENTER);
        s.cellAnnulee.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.cellAnnulee, new byte[]{(byte) 0xC6, 0x28, 0x28});
        applyBorder(s.cellAnnulee);

        // ----- Cellule "Annulée" : NON (gris neutre) -----
        s.cellNonAnnulee = baseStyle(wb);
        XSSFFont fontNon = fontNormal(wb, 10);
        fontNon.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.cellNonAnnulee.setFont(fontNon);
        s.cellNonAnnulee.setAlignment(HorizontalAlignment.CENTER);
        s.cellNonAnnulee.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.cellNonAnnulee, RGB_NEUTRAL_BG);
        applyBorder(s.cellNonAnnulee);

        // ----- Totaux -----
        s.totalLabel = baseStyle(wb);
        XSSFFont fontTotalLab = fontBold(wb, 11);
        s.totalLabel.setFont(fontTotalLab);
        s.totalLabel.setAlignment(HorizontalAlignment.RIGHT);
        s.totalLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        setBackgroundColor(s.totalLabel, RGB_RTS_ACCENT);
        applyBorder(s.totalLabel);

        s.totalValeur = baseStyle(wb);
        s.totalValeur.setFont(fontBold(wb, 12));
        s.totalValeur.setAlignment(HorizontalAlignment.RIGHT);
        s.totalValeur.setVerticalAlignment(VerticalAlignment.CENTER);
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
        XSSFCellStyle pourcentageRecap;
        XSSFCellStyle montantRecap;
        XSSFCellStyle montantImportant;
        XSSFCellStyle montantVert;
        XSSFCellStyle montantRouge;
        XSSFCellStyle montantAnnule;
        XSSFCellStyle montantVertImportant;
        XSSFCellStyle montantRougeImportant;
        XSSFCellStyle entete;
        XSSFCellStyle cellule;
        XSSFCellStyle celluleAlt;
        XSSFCellStyle celluleAnnuleeBg;
        XSSFCellStyle celluleMonospace;
        XSSFCellStyle celluleMonoAnnulee;
        XSSFCellStyle dateCell;
        XSSFCellStyle dateCellAnnulee;
        XSSFCellStyle typeEntree;
        XSSFCellStyle typeSortie;
        XSSFCellStyle cellAnnulee;
        XSSFCellStyle cellNonAnnulee;
        XSSFCellStyle totalLabel;
        XSSFCellStyle totalValeur;
        XSSFCellStyle pieds;
    }
}
