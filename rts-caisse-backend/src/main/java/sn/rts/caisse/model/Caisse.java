package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

/**
 * Caisse physique (un guichet, un poste de paiement).
 * Exemples : "Guichet Accueil RTS Triangle Sud", "Caisse Régie Publicitaire".
 */
@Entity
@Table(name = "caisses",
        uniqueConstraints = @UniqueConstraint(name = "uk_caisse_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caisse extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court unique (ex : "CAI-01"). */
    @NotBlank
    @Column(nullable = false, length = 20)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String libelle;

    /** Localisation physique : siège RTS, annexe régionale, etc. */
    @Column(length = 150)
    private String emplacement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutCaisse statut = StatutCaisse.FERMEE;

    /**
     * Solde théorique courant (fond de caisse + mouvements du jour).
     * Mis à jour transactionnellement par le service.
     */
    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal soldeCourant = BigDecimal.ZERO;

    /** Agent actuellement affecté à cette caisse (peut être null si fermée). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caissier_id")
    private Utilisateur caissier;
}
