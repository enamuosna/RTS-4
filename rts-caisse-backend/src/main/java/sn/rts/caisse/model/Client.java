package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Client ou tiers concerné par une opération (annonceur, partenaire, fournisseur...).
 * Optionnel pour les opérations internes.
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String raisonSociale;

    /** NINEA / RCCM pour les entreprises, CNI pour les particuliers. */
    @Column(length = 50)
    private String identifiantFiscal;

    @Column(length = 20)
    private String telephone;

    @Email
    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String adresse;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;
}
