package sn.rts.caisse.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Journal de caisse : clôture d'une journée pour une caisse donnée.
 * <p>
 * Contient le fond de caisse d'ouverture, les totaux d'entrées / sorties,
 * le solde théorique calculé et le solde réel compté par le caissier.
 * L'écart permet de détecter les anomalies.
 */
@Entity
@Table(name = "journaux_caisse",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_journal_caisse_date",
                columnNames = {"caisse_id", "date_journal"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalCaisse extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_journal", nullable = false)
    private LocalDate dateJournal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caisse_id", nullable = false)
    private Caisse caisse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caissier_id", nullable = false)
    private Utilisateur caissier;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fondOuverture = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalEntrees = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSorties = BigDecimal.ZERO;

    /** fondOuverture + totalEntrees - totalSorties */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal soldeTheorique = BigDecimal.ZERO;

    /** Solde réellement compté en fin de journée (saisi par le caissier). */
    @Column(precision = 15, scale = 2)
    private BigDecimal soldeReel;

    /** soldeReel - soldeTheorique (positif = excédent, négatif = manquant). */
    @Column(precision = 15, scale = 2)
    private BigDecimal ecart;

    @Column(length = 500)
    private String commentaire;

    @Column(name = "ouvert_le", nullable = false)
    private LocalDateTime ouvertLe;

    @Column(name = "cloture_le")
    private LocalDateTime clotureLe;

    /** Validation par un superviseur (audit). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valide_par_id")
    private Utilisateur valideePar;

    @Column(nullable = false)
    @Builder.Default
    private boolean cloture = false;
}
