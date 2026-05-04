package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Catégorie comptable d'une opération. Ex :
 *   - ENTREE :  "Recette publicitaire", "Vente d'archives", "Cession de droits"
 *   - SORTIE  : "Frais de mission", "Achat consommables", "Remboursement"
 */
@Entity
@Table(name = "categories_operation",
        uniqueConstraints = @UniqueConstraint(name = "uk_categorie_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorieOperation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TypeOperation typeOperation;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;
}
