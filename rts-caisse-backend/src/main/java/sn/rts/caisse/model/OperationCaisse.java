package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Opération de caisse unitaire : une entrée ou une sortie d'argent.
 * <p>
 * Chaque opération est rattachée à une {@link Caisse}, enregistrée par
 * un {@link Utilisateur} caissier, et classée dans une {@link CategorieOperation}.
 * Un numéro de reçu unique est généré automatiquement par le service.
 */
@Entity
@Table(name = "operations_caisse",
        uniqueConstraints = @UniqueConstraint(name = "uk_operation_numero_recu", columnNames = "numero_recu"),
        indexes = {
                @Index(name = "idx_operation_date", columnList = "date_operation"),
                @Index(name = "idx_operation_caisse", columnList = "caisse_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationCaisse extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_recu", nullable = false, length = 50)
    private String numeroRecu;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TypeOperation typeOperation;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 255)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false, length = 20)
    private ModePaiement modePaiement;

    /** Référence externe : n° chèque, n° transaction Wave/OM, n° bordereau... */
    @Column(length = 100)
    private String reference;

    @Column(name = "date_operation", nullable = false)
    private LocalDateTime dateOperation;

    // ---------- Relations ----------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caisse_id", nullable = false)
    private Caisse caisse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caissier_id", nullable = false)
    private Utilisateur caissier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieOperation categorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    /** Rattache l'opération à une journée de caisse clôturée (null si pas encore clôturée). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id")
    private JournalCaisse journal;

    /** Marque l'opération comme annulée (jamais supprimée physiquement : traçabilité). */
    @Column(nullable = false)
    @Builder.Default
    private boolean annulee = false;

    @Column(length = 255)
    private String motifAnnulation;
}
