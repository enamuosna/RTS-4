package sn.rts.caisse.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.OperationCaisseRequest;
import sn.rts.caisse.dto.OperationCaisseResponse;
import sn.rts.caisse.dto.OuvertureCaisseRequest;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.*;
import sn.rts.caisse.repository.CaisseRepository;
import sn.rts.caisse.repository.CategorieOperationRepository;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.service.JournalCaisseService;
import sn.rts.caisse.service.OperationCaisseService;
import sn.rts.caisse.service.RecuPdfService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = AbstractTestcontainersIT.Initializer.class)
@Transactional
@DisplayName("Tests d'intégration - RecuPdfService")
class RecuPdfServiceIT extends AbstractTestcontainersIT {

    @Autowired private RecuPdfService pdfService;
    @Autowired private OperationCaisseService operationService;
    @Autowired private JournalCaisseService journalService;
    @Autowired private CaisseRepository caisseRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private CategorieOperationRepository categorieRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private OperationCaisseResponse operation;

    @BeforeEach
    void setUp() {
        long stamp = System.nanoTime();

        Utilisateur caissier = utilisateurRepository.save(Utilisateur.builder()
                .matricule("RTS-P-" + stamp)
                .login("caissier_pdf_" + stamp)
                .motDePasse(passwordEncoder.encode("pw"))
                .prenom("Aïssatou").nom("Diop")
                .role(Role.CAISSIER).actif(true).build());

        Caisse caisse = caisseRepository.save(Caisse.builder()
                .code("P-" + stamp)
                .libelle("Caisse PDF test")
                .statut(StatutCaisse.FERMEE)
                .soldeCourant(BigDecimal.ZERO).build());

        CategorieOperation cat = categorieRepository.save(CategorieOperation.builder()
                .code("PDF-" + stamp).libelle("Recette publicitaire")
                .typeOperation(TypeOperation.ENTREE).actif(true).build());

        journalService.ouvrir(caisse.getId(),
                new OuvertureCaisseRequest(new BigDecimal("100000")),
                caissier.getLogin());

        operation = operationService.enregistrer(new OperationCaisseRequest(
                caisse.getId(), cat.getId(), null,
                TypeOperation.ENTREE, new BigDecimal("150000"),
                ModePaiement.WAVE, "Spot publicitaire JT 20h", "TX-WAVE-1234"),
                caissier.getLogin());
    }

    @Test
    @DisplayName("Génère un PDF valide à partir d'une opération")
    void genererRecu_operationExistante_retournePdf() {
        byte[] pdf = pdfService.genererRecu(operation.id());

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(500);

        // Signature PDF : %PDF-
        assertThat(pdf[0]).isEqualTo((byte) '%');
        assertThat(pdf[1]).isEqualTo((byte) 'P');
        assertThat(pdf[2]).isEqualTo((byte) 'D');
        assertThat(pdf[3]).isEqualTo((byte) 'F');
    }

    @Test
    @DisplayName("Opération inexistante → ResourceNotFoundException")
    void genererRecu_operationInexistante_leveException() {
        assertThatThrownBy(() -> pdfService.genererRecu(999_999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
