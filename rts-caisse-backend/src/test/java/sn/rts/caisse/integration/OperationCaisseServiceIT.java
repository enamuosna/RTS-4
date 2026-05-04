package sn.rts.caisse.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.OperationCaisseRequest;
import sn.rts.caisse.dto.OperationCaisseResponse;
import sn.rts.caisse.dto.OuvertureCaisseRequest;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.model.*;
import sn.rts.caisse.repository.*;
import sn.rts.caisse.service.JournalCaisseService;
import sn.rts.caisse.service.OperationCaisseService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = AbstractTestcontainersIT.Initializer.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "logging.level.sn.rts.caisse=INFO"
})
@Transactional
@DisplayName("Tests d'intégration - OperationCaisseService")
class OperationCaisseServiceIT extends AbstractTestcontainersIT {

    @Autowired private OperationCaisseService operationService;
    @Autowired private JournalCaisseService journalService;
    @Autowired private CaisseRepository caisseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private CategorieOperationRepository categorieRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Utilisateur caissier;
    private Caisse caisse;
    private CategorieOperation categorieEntree;
    private CategorieOperation categorieSortie;

    @BeforeEach
    void setUp() {
        caissier = utilisateurRepository.save(Utilisateur.builder()
                .matricule("RTS-TEST-" + System.nanoTime())
                .login("caissier_it_" + System.nanoTime())
                .motDePasse(passwordEncoder.encode("password"))
                .prenom("Test")
                .nom("Caissier")
                .role(Role.CAISSIER)
                .actif(true)
                .build());

        caisse = caisseRepository.save(Caisse.builder()
                .code("TEST-" + System.nanoTime())
                .libelle("Caisse test intégration")
                .statut(StatutCaisse.FERMEE)
                .soldeCourant(BigDecimal.ZERO)
                .build());

        categorieEntree = categorieRepository.save(CategorieOperation.builder()
                .code("E-" + System.nanoTime())
                .libelle("Test entrée")
                .typeOperation(TypeOperation.ENTREE)
                .actif(true)
                .build());

        categorieSortie = categorieRepository.save(CategorieOperation.builder()
                .code("S-" + System.nanoTime())
                .libelle("Test sortie")
                .typeOperation(TypeOperation.SORTIE)
                .actif(true)
                .build());

        // Ouverture de la caisse (nécessaire pour pouvoir enregistrer)
        journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("100000")),
                caissier.getLogin());
    }

    @Test
    @DisplayName("Enregistrement d'une entrée : met à jour le solde et génère un numéro de reçu")
    void enregistrer_entree_incrementerSolde() {
        OperationCaisseRequest req = buildRequest(TypeOperation.ENTREE,
                categorieEntree.getId(), new BigDecimal("50000"));

        OperationCaisseResponse response = operationService.enregistrer(req, caissier.getLogin());

        assertThat(response.numeroRecu()).startsWith("RTS-");
        assertThat(response.montant()).isEqualByComparingTo("50000");
        assertThat(response.typeOperation()).isEqualTo(TypeOperation.ENTREE);
        assertThat(response.annulee()).isFalse();

        Caisse refreshed = caisseRepository.findById(caisse.getId()).orElseThrow();
        assertThat(refreshed.getSoldeCourant()).isEqualByComparingTo("150000"); // 100k + 50k
    }

    @Test
    @DisplayName("Enregistrement d'une sortie : décrémente le solde")
    void enregistrer_sortie_decrementerSolde() {
        OperationCaisseRequest req = buildRequest(TypeOperation.SORTIE,
                categorieSortie.getId(), new BigDecimal("40000"));

        operationService.enregistrer(req, caissier.getLogin());

        Caisse refreshed = caisseRepository.findById(caisse.getId()).orElseThrow();
        assertThat(refreshed.getSoldeCourant()).isEqualByComparingTo("60000"); // 100k - 40k
    }

    @Test
    @DisplayName("Une sortie supérieure au solde doit être rejetée (règle métier)")
    void enregistrer_sortie_soldeInsuffisant_rejete() {
        OperationCaisseRequest req = buildRequest(TypeOperation.SORTIE,
                categorieSortie.getId(), new BigDecimal("999999"));

        assertThatThrownBy(() -> operationService.enregistrer(req, caissier.getLogin()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Solde insuffisant");
    }

    @Test
    @DisplayName("Catégorie ENTREE utilisée pour une SORTIE : rejet")
    void enregistrer_categorieIncoherente_rejete() {
        OperationCaisseRequest req = buildRequest(TypeOperation.SORTIE,
                categorieEntree.getId(), new BigDecimal("10000"));

        assertThatThrownBy(() -> operationService.enregistrer(req, caissier.getLogin()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne correspond pas");
    }

    @Test
    @DisplayName("Annulation : contre-passation du solde + marqueur annulée")
    void annuler_contrePassation_soldeRevenuInitial() {
        OperationCaisseRequest req = buildRequest(TypeOperation.ENTREE,
                categorieEntree.getId(), new BigDecimal("70000"));
        OperationCaisseResponse op = operationService.enregistrer(req, caissier.getLogin());

        Caisse apres = caisseRepository.findById(caisse.getId()).orElseThrow();
        assertThat(apres.getSoldeCourant()).isEqualByComparingTo("170000");

        OperationCaisseResponse annulee = operationService.annuler(op.id(),
                "Erreur de saisie", caissier.getLogin());

        assertThat(annulee.annulee()).isTrue();
        assertThat(annulee.motifAnnulation()).contains("Erreur de saisie");

        Caisse refreshed = caisseRepository.findById(caisse.getId()).orElseThrow();
        assertThat(refreshed.getSoldeCourant()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Double annulation : rejetée")
    void annuler_deuxFois_rejete() {
        OperationCaisseRequest req = buildRequest(TypeOperation.ENTREE,
                categorieEntree.getId(), new BigDecimal("10000"));
        OperationCaisseResponse op = operationService.enregistrer(req, caissier.getLogin());

        operationService.annuler(op.id(), "première", caissier.getLogin());

        assertThatThrownBy(() -> operationService.annuler(op.id(), "seconde", caissier.getLogin()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà annulée");
    }

    // ------------------------------------------------------------------

    private OperationCaisseRequest buildRequest(TypeOperation type, Long categorieId, BigDecimal montant) {
        return new OperationCaisseRequest(
                caisse.getId(), categorieId, null,
                type, montant, ModePaiement.ESPECES,
                "Opération de test", "REF-TEST");
    }
}
