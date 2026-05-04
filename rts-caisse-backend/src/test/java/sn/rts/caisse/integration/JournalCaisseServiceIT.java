package sn.rts.caisse.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.ClotureCaisseRequest;
import sn.rts.caisse.dto.JournalCaisseResponse;
import sn.rts.caisse.dto.OperationCaisseRequest;
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
@Transactional
@DisplayName("Tests d'intégration - JournalCaisseService")
class JournalCaisseServiceIT extends AbstractTestcontainersIT {

    @Autowired private JournalCaisseService journalService;
    @Autowired private OperationCaisseService operationService;
    @Autowired private CaisseRepository caisseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private CategorieOperationRepository categorieRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Utilisateur caissier;
    private Utilisateur superviseur;
    private Caisse caisse;
    private CategorieOperation catEntree;
    private CategorieOperation catSortie;

    @BeforeEach
    void setUp() {
        long stamp = System.nanoTime();

        caissier = utilisateurRepository.save(Utilisateur.builder()
                .matricule("M-C-" + stamp)
                .login("caissier_j_" + stamp)
                .motDePasse(passwordEncoder.encode("password"))
                .prenom("Jean").nom("Caissier")
                .role(Role.CAISSIER).actif(true).build());

        superviseur = utilisateurRepository.save(Utilisateur.builder()
                .matricule("M-S-" + stamp)
                .login("supervis_j_" + stamp)
                .motDePasse(passwordEncoder.encode("password"))
                .prenom("Marie").nom("Super")
                .role(Role.SUPERVISEUR).actif(true).build());

        caisse = caisseRepository.save(Caisse.builder()
                .code("C-" + stamp)
                .libelle("Caisse journal test")
                .statut(StatutCaisse.FERMEE)
                .soldeCourant(BigDecimal.ZERO).build());

        catEntree = categorieRepository.save(CategorieOperation.builder()
                .code("CE-" + stamp).libelle("Test entrée")
                .typeOperation(TypeOperation.ENTREE).actif(true).build());

        catSortie = categorieRepository.save(CategorieOperation.builder()
                .code("CS-" + stamp).libelle("Test sortie")
                .typeOperation(TypeOperation.SORTIE).actif(true).build());
    }

    @Test
    @DisplayName("Ouverture : la caisse passe OUVERTE et reçoit le fond")
    void ouvrir_caisse_devientOuverte() {
        JournalCaisseResponse journal = journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("50000")),
                caissier.getLogin());

        assertThat(journal.fondOuverture()).isEqualByComparingTo("50000");
        assertThat(journal.cloture()).isFalse();

        Caisse refreshed = caisseRepository.findById(caisse.getId()).orElseThrow();
        assertThat(refreshed.getStatut()).isEqualTo(StatutCaisse.OUVERTE);
        assertThat(refreshed.getSoldeCourant()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("Ouvrir une caisse déjà ouverte : rejet")
    void ouvrir_deuxFoisMemeJour_rejete() {
        journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("10000")),
                caissier.getLogin());

        assertThatThrownBy(() -> journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("5000")), caissier.getLogin()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Clôture sans écart : écart = 0")
    void cloturer_sansEcart() {
        // Fond 100k + entrée 50k - sortie 20k = solde théorique 130k
        journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("100000")),
                caissier.getLogin());

        enregistrer(TypeOperation.ENTREE, catEntree.getId(), "50000");
        enregistrer(TypeOperation.SORTIE, catSortie.getId(), "20000");

        JournalCaisseResponse cloture = journalService.cloturer(caisse.getId(),
                new ClotureCaisseRequest(new BigDecimal("130000"), "OK"),
                caissier.getLogin());

        assertThat(cloture.cloture()).isTrue();
        assertThat(cloture.totalEntrees()).isEqualByComparingTo("50000");
        assertThat(cloture.totalSorties()).isEqualByComparingTo("20000");
        assertThat(cloture.soldeTheorique()).isEqualByComparingTo("130000");
        assertThat(cloture.ecart()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Clôture avec manquant : écart négatif")
    void cloturer_avecManquant_ecartNegatif() {
        journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("100000")),
                caissier.getLogin());

        enregistrer(TypeOperation.ENTREE, catEntree.getId(), "25000");

        // Théorique attendu : 125 000, caissier compte 120 000 → manque 5 000
        JournalCaisseResponse cloture = journalService.cloturer(caisse.getId(),
                new ClotureCaisseRequest(new BigDecimal("120000"), "Manque 5 000"),
                caissier.getLogin());

        assertThat(cloture.ecart()).isEqualByComparingTo("-5000");
    }

    @Test
    @DisplayName("Clôture avec excédent : écart positif")
    void cloturer_avecExcedent_ecartPositif() {
        journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("50000")),
                caissier.getLogin());

        JournalCaisseResponse cloture = journalService.cloturer(caisse.getId(),
                new ClotureCaisseRequest(new BigDecimal("52000"), "Excédent"),
                caissier.getLogin());

        assertThat(cloture.ecart()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("Validation superviseur : renseigne validateur")
    void valider_parSuperviseur_renseignValidateur() {
        journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("10000")),
                caissier.getLogin());

        JournalCaisseResponse cloture = journalService.cloturer(caisse.getId(),
                new ClotureCaisseRequest(new BigDecimal("10000"), null),
                caissier.getLogin());

        JournalCaisseResponse validee = journalService.valider(cloture.id(),
                superviseur.getLogin());

        assertThat(validee.valideeParId()).isEqualTo(superviseur.getId());
    }

    // ------------------------------------------------------------------

    private void enregistrer(TypeOperation type, Long categorieId, String montant) {
        operationService.enregistrer(new OperationCaisseRequest(
                caisse.getId(), categorieId, null,
                type, new BigDecimal(montant), ModePaiement.ESPECES,
                "Test", null), caissier.getLogin());
    }
}
