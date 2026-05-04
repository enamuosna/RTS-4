package sn.rts.caisse.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import sn.rts.caisse.model.Role;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.UtilisateurRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = AbstractTestcontainersIT.Initializer.class)
@DisplayName("Tests d'intégration - AuthController")
class AuthControllerIT extends AbstractTestcontainersIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String LOGIN_TEST = "test-auth-user";
    private static final String PASSWORD_TEST = "TestPassword@2026";

    @BeforeEach
    void setUp() {
        utilisateurRepository.findByLogin(LOGIN_TEST).ifPresent(utilisateurRepository::delete);

        utilisateurRepository.save(Utilisateur.builder()
                .matricule("AUTH-TEST-001")
                .login(LOGIN_TEST)
                .motDePasse(passwordEncoder.encode(PASSWORD_TEST))
                .prenom("Auth").nom("Tester")
                .role(Role.CAISSIER)
                .actif(true)
                .build());
    }

    @Test
    @DisplayName("POST /api/auth/login avec identifiants valides → 200 + JWT")
    void login_valide_retourneToken() throws Exception {
        String payload = """
                { "login": "%s", "motDePasse": "%s" }
                """.formatted(LOGIN_TEST, PASSWORD_TEST);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.login").value(LOGIN_TEST))
                .andExpect(jsonPath("$.role").value("CAISSIER"));
    }

    @Test
    @DisplayName("POST /api/auth/login avec mauvais mot de passe → 401")
    void login_mauvaisMotDePasse_retourne401() throws Exception {
        String payload = """
                { "login": "%s", "motDePasse": "wrong-password" }
                """.formatted(LOGIN_TEST);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login sans corps → 400")
    void login_corpsVide_retourne400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
