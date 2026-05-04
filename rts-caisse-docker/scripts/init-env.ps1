package sn.rts.caisse.guichet.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.util.Config;
import sn.rts.caisse.guichet.util.Session;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Client HTTP centralisé. Utilise {@link java.net.http.HttpClient} natif
 * (Java 11+) et Jackson pour (dé)sérialiser. Injecte automatiquement
 * l'en-tête Authorization: Bearer &lt;token&gt; depuis {@link Session}.
 *
 * <h2>Important : HTTP/1.1 force</h2>
 * Le client est explicitement configuré en HTTP/1.1. Sans ça, Java 11+
 * tente HTTP/2 cleartext (h2c) avec un header {@code Upgrade: h2c} que
 * la plupart des reverse proxies (nginx en particulier) rejettent par
 * un 501 Not Implemented. Toujours garder cette ligne.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private static final ApiClient INSTANCE = new ApiClient();
    public static ApiClient getInstance() { return INSTANCE; }

    /** Client HTTP partage par toute l'application. CRITIQUE : HTTP_1_1 force. */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ==================================================================
    //  Ping de connectivite
    // ==================================================================

    /**
     * Resultat d'un ping de connectivite serveur.
     */
    public record PingResult(boolean ok, String message) {
        public boolean ok() {
            return new PingResult(true, "Le serveur répond correctement.").ok();
        }
        public static PingResult ko(String message) {
            return new PingResult(false, message);
        }
    }

    /**
     * Teste la disponibilite du serveur via {@code GET /actuator/health}
     * (sur la base URL serveur, hors {@code /api}).
     * <p>
     * Methode bloquante : appeler depuis un thread de fond
     * (typiquement {@code AsyncRunner.run(...)}).
     *
     * @return {@link PingResult#ok()} si HTTP 200 + body contient {@code "UP"},
     *         sinon {@link PingResult#ko(String)} avec message explicite.
     */
    public PingResult ping() {
        String url = Config.getInstance().getServerBaseUrl() + "/actuator/health";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();
            String body = resp.body() == null ? "" : resp.body();

            if (status == 200 && body.contains("\"UP\"")) {
                return PingResult.ok();
            }
            if (status == 200) {
                return PingResult.ko("Réponse inattendue du serveur (statut UP non confirmé).");
            }
            if (status == 503) {
                return PingResult.ko("Service temporairement indisponible (backend en cours de démarrage ?)");
            }
            return PingResult.ko("HTTP " + status + " sur " + url);

        } catch (java.net.http.HttpConnectTimeoutException e) {
            return PingResult.ko("Délai de connexion dépassé (5s).");
        } catch (java.net.http.HttpTimeoutException e) {
            return PingResult.ko("Le serveur ne répond pas dans le délai imparti.");
        } catch (java.net.ConnectException e) {
            return PingResult.ko("Serveur injoignable (host/port incorrect ?).");
        } catch (java.net.UnknownHostException e) {
            return PingResult.ko("Hôte inconnu : vérifiez l'URL.");
        } catch (Exception e) {
            log.warn("Ping {} : {}", url, e.toString());
            return PingResult.ko("Erreur réseau : " + e.getMessage());
        }
    }

    // ==================================================================
    //  Verbes HTTP
    // ==================================================================

    public <T> T get(String path, Class<T> responseType) {
        return get(path, (TypeReference<T>) null, responseType);
    }

    public <T> T get(String path, TypeReference<T> typeRef) {
        return get(path, typeRef, null);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        HttpRequest req = requestBuilder(path)
                .POST(bodyOf(body))
                .header("Content-Type", "application/json")
                .build();
        return send(req, responseType, null);
    }

    public <T> T put(String path, Object body, Class<T> responseType) {
        HttpRequest req = requestBuilder(path)
                .PUT(bodyOf(body))
                .header("Content-Type", "application/json")
                .build();
        return send(req, responseType, null);
    }

    public <T> T patch(String path, Object body, Class<T> responseType) {
        HttpRequest req = requestBuilder(path)
                .method("PATCH", body == null ? BodyPublishers.noBody() : bodyOf(body))
                .header("Content-Type", "application/json")
                .build();
        return send(req, responseType, null);
    }

    public void delete(String path) {
        HttpRequest req = requestBuilder(path).DELETE().build();
        send(req, Void.class, null);
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private HttpRequest.Builder requestBuilder(String path) {
        URI uri = URI.create(Config.getInstance().getApiUrl() + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        String token = Session.getInstance().getToken();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpRequest.BodyPublisher bodyOf(Object body) {
        try {
            String json = body == null ? "" : mapper.writeValueAsString(body);
            return BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiException("Sérialisation impossible : " + e.getMessage());
        }
    }

    private <T> T get(String path, TypeReference<T> typeRef, Class<T> responseClass) {
        HttpRequest req = requestBuilder(path).GET().build();
        return send(req, responseClass, typeRef);
    }

    @SuppressWarnings("unchecked")
    private <T> T send(HttpRequest req, Class<T> responseClass, TypeReference<T> typeRef) {
        try {
            HttpResponse<String> response = http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();

            if (status >= 200 && status < 300) {
                if (responseClass == Void.class || body == null || body.isBlank()) {
                    return null;
                }
                if (typeRef != null) {
                    return mapper.readValue(body, typeRef);
                }
                return mapper.readValue(body, responseClass);
            }

            // Erreur : essaie de lire le message renvoye par le GlobalExceptionHandler
            String message = extractErrorMessage(body, status);
            log.warn("API {} {} → {}: {}", req.method(), req.uri(), status, message);
            throw new ApiException(status, message);

        } catch (ApiException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new ApiException(0, "Serveur injoignable à " + Config.getInstance().getApiUrl());
        } catch (Exception e) {
            log.error("Erreur appel API {} {}", req.method(), req.uri(), e);
            throw new ApiException("Erreur réseau : " + e.getMessage());
        }
    }

    private String extractErrorMessage(String body, int status) {
        if (body == null || body.isBlank()) {
            return httpStatusLabel(status);
        }
        try {
            Map<String, Object> parsed = mapper.readValue(body, new TypeReference<>() {});
            Object msg = parsed.get("message");
            return msg != null ? msg.toString() : httpStatusLabel(status);
        } catch (Exception ex) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
    }

    private String httpStatusLabel(int status) {
        return switch (status) {
            case 401 -> "Session invalide ou expirée.";
            case 403 -> "Accès refusé.";
            case 404 -> "Ressource introuvable.";
            case 500 -> "Erreur interne du serveur.";
            case 503 -> "Service temporairement indisponible.";
            default  -> "Erreur HTTP " + status;
        };
    }

    /** Utilitaire pour encoder un parametre de query string. */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
package sn.rts.caisse.guichet.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sn.rts.caisse.guichet.api.ApiClient;
import sn.rts.caisse.guichet.api.ApiClient.PingResult;
import sn.rts.caisse.guichet.util.AsyncRunner;
import sn.rts.caisse.guichet.util.Config;

import java.util.prefs.Preferences;

/**
 * Controleur de l'ecran "Connexion au serveur" :
 *   - saisie / validation / persistance de l'URL serveur
 *   - test de connectivite asynchrone via {@link ApiClient#ping()}
 *   - feedback visuel (succes / echec / en cours)
 *   - bouton "Continuer" debloque uniquement apres un test reussi
 *   - case "Tester automatiquement au demarrage" memorisee
 *
 * <h2>FXML attendu (fx:id)</h2>
 * <ul>
 *   <li>{@code urlField}        : TextField - URL du serveur</li>
 *   <li>{@code testButton}      : Button    - "Tester la connexion"</li>
 *   <li>{@code retestButton}    : Button    - icone refresh (optionnel)</li>
 *   <li>{@code continueButton}  : Button    - "Continuer vers la connexion"</li>
 *   <li>{@code statusPanel}     : Region    - panneau resultat (rouge/vert/bleu)</li>
 *   <li>{@code statusTitle}     : Label     - "Connecte" / "Connexion impossible" / ...</li>
 *   <li>{@code statusMessage}   : Label     - detail (URL testee, message d'erreur)</li>
 *   <li>{@code statusIndicator} : Region    - pastille coloree (optionnel)</li>
 *   <li>{@code autoTestCheck}   : CheckBox  - "Tester automatiquement au demarrage"</li>
 * </ul>
 *
 * <h2>Classes CSS appliquees</h2>
 * Le panneau {@code statusPanel} recoit successivement, selon l'etat :
 *   {@code status--idle}, {@code status--testing}, {@code status--success},
 *   {@code status--error}. A styler dans le CSS de l'application.
 */
public class ServeurController {

    private static final Logger log = LoggerFactory.getLogger(ServeurController.class);

    /** Cle pour memoriser la preference "auto-test" entre les sessions. */
    private static final String PREF_AUTOTEST = "autoTest";
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ServeurController.class);

    // ===== FXML =====
    @FXML private TextField urlField;
    @FXML private Button    testButton;
    @FXML private Button    retestButton;
    @FXML private Button    continueButton;
    @FXML private Region    statusPanel;
    @FXML private Label     statusTitle;
    @FXML private Label     statusMessage;
    @FXML private Region    statusIndicator;
    @FXML private CheckBox  autoTestCheck;

    /** Callback invoque quand l'utilisateur clique sur "Continuer". */
    private Runnable onConnectionConfirmed;

    // ==================================================================
    //  Initialisation
    // ==================================================================

    @FXML
    public void initialize() {
        // 1. Pre-remplit le champ avec l'URL deja connue
        urlField.setText(Config.getInstance().getApiUrl());

        // 2. Charge l'etat de la case "auto-test" depuis les preferences
        boolean autoTest = PREFS.getBoolean(PREF_AUTOTEST, true);
        if (autoTestCheck != null) {
            autoTestCheck.setSelected(autoTest);
            autoTestCheck.selectedProperty().addListener((obs, oldV, newV) ->
                    PREFS.putBoolean(PREF_AUTOTEST, newV));
        }

        // 3. Etat initial
        showIdle();
        continueButton.setDisable(true);

        // 4. Permet de tester avec la touche Entree depuis le champ URL
        urlField.setOnAction(e -> testConnection());

        // 5. Lance le test automatiquement si la case est cochee
        if (autoTest) {
            // runLater : laisse le temps a la scene d'etre affichee
            Platform.runLater(this::testConnection);
        }
    }

    // ==================================================================
    //  Actions FXML
    // ==================================================================

    @FXML
    public void testConnection() {
        // Etape 1 : valider et persister l'URL avant le ping
        String saisi = urlField.getText();
        String normalise;
        try {
            normalise = Config.normalize(saisi);
        } catch (IllegalArgumentException ex) {
            showError("URL invalide", ex.getMessage());
            return;
        }
        urlField.setText(normalise);
        Config.getInstance().setApiUrl(normalise);

        // Etape 2 : ping asynchrone (UI non bloquee)
        showTesting();
        AsyncRunner.run(
                () -> ApiClient.getInstance().ping(),
                this::onPingResult,
                this::onPingError
        );
    }

    @FXML
    public void continueToLogin() {
        if (onConnectionConfirmed != null) {
            onConnectionConfirmed.run();
        } else {
            log.warn("Aucun callback 'onConnectionConfirmed' n'est defini.");
        }
    }

    // ==================================================================
    //  Callbacks ping
    // ==================================================================

    private void onPingResult(PingResult result) {
        if (PingResult.ok()) {
            log.info("Ping OK : {}", Config.getInstance().getServerBaseUrl());
            showSuccess(result.message());
        } else {
            log.warn("Ping KO : {}", result.message());
            showError("Connexion impossible", result.message());
        }
    }

    private void onPingError(Throwable t) {
        log.error("Erreur inattendue durant le ping", t);
        showError("Erreur inattendue", t.getMessage() == null ? t.toString() : t.getMessage());
    }

    // ==================================================================
    //  Etats visuels
    // ==================================================================

    private void showIdle() {
        if (statusPanel != null) {
            statusPanel.setVisible(false);
            statusPanel.setManaged(false);
        }
        setTestButtonsDisabled(false);
    }

    private void showTesting() {
        applyStatus("status--testing", "Test en cours…",
                "Vérification de " + Config.getInstance().getServerBaseUrl());
        setTestButtonsDisabled(true);
        continueButton.setDisable(true);
    }

    private void showSuccess(String detail) {
        applyStatus("status--success", "Connecté", detail);
        setTestButtonsDisabled(false);
        continueButton.setDisable(false);
    }

    private void showError(String title, String message) {
        applyStatus("status--error", title, message);
        setTestButtonsDisabled(false);
        continueButton.setDisable(true);
    }

    /** Applique une classe CSS exclusive (status--testing/success/error) au panneau. */
    private void applyStatus(String cssClass, String title, String message) {
        if (statusPanel != null) {
            statusPanel.setVisible(true);
            statusPanel.setManaged(true);
            // Retire les anciennes classes d'etat puis ajoute la nouvelle
            statusPanel.getStyleClass().removeAll(
                    "status--idle", "status--testing", "status--success", "status--error");
            statusPanel.getStyleClass().add(cssClass);
        }
        if (statusIndicator != null) {
            statusIndicator.getStyleClass().removeAll(
                    "indicator--idle", "indicator--testing",
                    "indicator--success", "indicator--error");
            statusIndicator.getStyleClass().add(cssClass.replace("status--", "indicator--"));
        }
        if (statusTitle != null)   statusTitle.setText(title);
        if (statusMessage != null) statusMessage.setText(message);
    }

    private void setTestButtonsDisabled(boolean disabled) {
        if (testButton != null)   testButton.setDisable(disabled);
        if (retestButton != null) retestButton.setDisable(disabled);
    }

    // ==================================================================
    //  API publique pour le launcher / scene manager
    // ==================================================================

    /**
     * Branche le callback execute lorsque l'utilisateur clique sur
     * "Continuer vers la connexion" apres un test reussi.
     * Typiquement : changer la scene vers l'ecran de login.
     */
    public void setOnConnectionConfirmed(Runnable callback) {
        this.onConnectionConfirmed = callback;
    }
}
package sn.rts.caisse.guichet.util;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wrapper simple pour exécuter un appel API sur un thread dédié et
 * recevoir le résultat (ou l'erreur) sur le thread JavaFX Application.
 * <p>
 * Cela évite de geler l'UI pendant les 50-500 ms d'un appel HTTP.
 */
public final class AsyncRunner {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "rts-api-worker");
        t.setDaemon(true);
        return t;
    });

    private AsyncRunner() {}

    /**
     * Exécute {@code supplier} en tâche de fond, appelle {@code onSuccess}
     * sur le FX thread en cas de succès, {@code onError} en cas d'exception.
     */
    public static <T> void run(Supplier<T> supplier,
                               Consumer<T> onSuccess,
                               Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override protected T call() { return supplier.get(); }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> onSuccess.accept(task.getValue())));
        task.setOnFailed(e -> Platform.runLater(() -> onError.accept(task.getException())));
        EXECUTOR.submit(task);
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
package sn.rts.caisse.guichet.util;

import sn.rts.caisse.guichet.model.Dto.AuthResponse;
import sn.rts.caisse.guichet.model.Dto.CaisseDTO;

/**
 * Singleton en mémoire conservant l'état de la session : utilisateur connecté,
 * JWT et caisse actuellement sélectionnée par le caissier.
 */
public final class Session {

    private static final Session INSTANCE = new Session();

    public static Session getInstance() { return INSTANCE; }

    private Session() {}

    private AuthResponse auth;
    private CaisseDTO caisseActive;

    public void setAuth(AuthResponse auth) {
        this.auth = auth;
    }

    public AuthResponse getAuth() {
        return auth;
    }

    public String getToken() {
        return auth != null ? auth.token : null;
    }

    public boolean isAuthenticated() {
        return auth != null && auth.token != null;
    }

    public CaisseDTO getCaisseActive() { return caisseActive; }

    public void setCaisseActive(CaisseDTO caisse) { this.caisseActive = caisse; }

    public void clear() {
        this.auth = null;
        this.caisseActive = null;
    }
}
package sn.rts.caisse.guichet.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Méthodes statiques utilitaires : formatage, boîtes de dialogue.
 */
public final class Ui {

    private static final Locale FR_SN = Locale.of("fr", "SN");
    private static final NumberFormat CURRENCY = NumberFormat.getNumberInstance(FR_SN);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_TIME_FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm:ss");

    static {
        CURRENCY.setMinimumFractionDigits(0);
        CURRENCY.setMaximumFractionDigits(0);
    }

    private Ui() {}

    // ==================================================================
    //  Formatage
    // ==================================================================

    public static String formatMontant(BigDecimal montant) {
        if (montant == null) return "—";
        return CURRENCY.format(montant) + " FCFA";
    }

    public static String formatMontant(double montant) {
        return CURRENCY.format(montant) + " FCFA";
    }

    public static String formatDateTime(LocalDateTime date) {
        return date == null ? "—" : date.format(DATE_TIME);
    }

    public static String formatDateTimeFull(LocalDateTime date) {
        return date == null ? "—" : date.format(DATE_TIME_FULL);
    }

    // ==================================================================
    //  Boîtes de dialogue
    // ==================================================================

    public static void info(String titre, String message) {
        showAlert(Alert.AlertType.INFORMATION, titre, message);
    }

    public static void erreur(String titre, String message) {
        showAlert(Alert.AlertType.ERROR, titre, message);
    }

    public static boolean confirmer(String titre, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("RTS Caisse - " + titre);
        alert.setHeaderText(titre);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static Optional<String> demanderTexte(String titre, String message, String defaut) {
        TextInputDialog dialog = new TextInputDialog(defaut == null ? "" : defaut);
        dialog.setTitle("RTS Caisse - " + titre);
        dialog.setHeaderText(titre);
        dialog.setContentText(message);
        return dialog.showAndWait();
    }

    private static void showAlert(Alert.AlertType type, String titre, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("RTS Caisse - " + titre);
        alert.setHeaderText(titre);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Parse un montant saisi par l'utilisateur (accepte "1 500" ou "1500.00"). */
    public static BigDecimal parseMontant(String input) {
        if (input == null || input.isBlank()) return null;
        String cleaned = input.replace(" ", "").replace(",", ".");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Button?>
<?import javafx.scene.control.CheckBox?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ProgressIndicator?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.StackPane?>
<?import javafx.scene.layout.VBox?>
<?import javafx.scene.shape.SVGPath?>

<!--
    Écran de connexion au serveur RTS.
    Design modernisé : carte centrale avec ombre douce, icônes SVG vectorielles,
    bloc de statut animé, indicateur de chargement pendant le test ping.
-->
<StackPane styleClass="login-pane"
           xmlns="http://javafx.com/javafx/21"
           xmlns:fx="http://javafx.com/fxml/1"
           fx:controller="sn.rts.caisse.guichet.controller.ServeurController">

    <VBox styleClass="login-card" spacing="14" maxWidth="500" maxHeight="-Infinity" alignment="CENTER">

        <!-- ======================== Logo ======================== -->
        <StackPane styleClass="login-logo">
            <!-- Icône serveur (SVG Lucide) -->
            <SVGPath styleClass="icon, icon-on-primary"
                     content="M2 4 a2 2 0 0 1 2-2 h16 a2 2 0 0 1 2 2 v4 a2 2 0 0 1-2 2 H4 a2 2 0 0 1-2-2 z M2 16 a2 2 0 0 1 2-2 h16 a2 2 0 0 1 2 2 v4 a2 2 0 0 1-2 2 H4 a2 2 0 0 1-2-2 z M6 6 h.01 M6 18 h.01"
                     scaleX="1.6" scaleY="1.6"
                     style="-fx-fill: -rts-primary-dark; -fx-stroke: -rts-primary-dark;"/>
        </StackPane>

        <Label styleClass="login-title" text="Connexion au serveur"/>
        <Label styleClass="login-subtitle"
               text="Indiquez l'URL du serveur RTS Caisse avant de vous authentifier."/>

        <!-- ===================== Formulaire ===================== -->
        <VBox spacing="14" maxWidth="440">
            <VBox.margin>
                <Insets top="16"/>
            </VBox.margin>

            <!-- URL serveur avec icône intégrée -->
            <VBox spacing="6">
                <Label styleClass="field-label" text="URL DU SERVEUR"/>

                <HBox styleClass="text-field-with-icon" alignment="CENTER_LEFT" spacing="10">
                    <SVGPath styleClass="icon, icon-muted"
                             content="M12 2 L2 7 v7 c0 5.55 3.84 10.74 10 12 6.16-1.26 10-6.45 10-12 V7 z"
                             scaleX="0.85" scaleY="0.85"/>
                    <TextField fx:id="urlField"
                               promptText="http://serveur-rts:9090/api"
                               HBox.hgrow="ALWAYS"/>
                </HBox>

                <Label styleClass="login-subtitle" wrapText="true"
                       text="Format : http://hôte:port ou http://hôte:port/api"/>
            </VBox>

            <!-- ============== Bloc statut ============== -->
            <HBox fx:id="statusBox" styleClass="status-box, status-idle"
                  spacing="12" alignment="CENTER_LEFT">

                <!-- Stack qui contient soit l'icône, soit le spinner -->
                <StackPane minWidth="28" maxWidth="28" minHeight="28" maxHeight="28" alignment="CENTER">
                    <!-- Icône SVG d'état (cercle plein/vide selon le statut) -->
                    <SVGPath fx:id="statusIcon"
                             styleClass="status-icon-svg"
                             content="M12 22 c5.523 0 10-4.477 10-10 S17.523 2 12 2 2 6.477 2 12 s4.477 10 10 10 z"
                             scaleX="0.85" scaleY="0.85"/>

                    <!-- Spinner affiché uniquement pendant un test -->
                    <ProgressIndicator fx:id="statusSpinner"
                                       styleClass="loading-spinner"
                                       prefWidth="22" prefHeight="22"
                                       visible="false" managed="false"/>
                </StackPane>

                <VBox spacing="2" HBox.hgrow="ALWAYS">
                    <Label fx:id="statusMessage" styleClass="status-message"
                           text="Non testé"/>
                    <Label fx:id="statusDetail" styleClass="status-detail"
                           text="Cliquez sur « Tester la connexion » pour vérifier."
                           wrapText="true"/>
                </VBox>
            </HBox>

            <!-- ============== Boutons ============== -->
            <HBox spacing="10">
                <Button fx:id="testerButton"
                        styleClass="button, button-secondary"
                        prefHeight="42"
                        HBox.hgrow="ALWAYS"
                        maxWidth="Infinity"
                        onAction="#onTester">
                    <graphic>
                        <HBox spacing="8" alignment="CENTER">
                            <SVGPath styleClass="icon"
                                     content="M5 12.55 a11 11 0 0 1 14.08 0 M1.42 9 a16 16 0 0 1 21.16 0 M8.53 16.11 a6 6 0 0 1 6.95 0 M12 20 h.01"
                                     scaleX="0.7" scaleY="0.7"
                                     style="-fx-fill: -rts-primary-dark;"/>
                            <Label fx:id="testerButtonLabel" text="Tester la connexion"
                                   style="-fx-text-fill: -rts-primary-dark; -fx-font-weight: 600;"/>
                        </HBox>
                    </graphic>
                </Button>

                <Button styleClass="button, button-icon"
                        onAction="#onReinitialiser">
                    <graphic>
                        <SVGPath styleClass="icon, icon-muted"
                                 content="M21 12 a9 9 0 0 1-15 6.7 L3 16 M3 12 a9 9 0 0 1 15-6.7 L21 8 M21 3 v5 h-5 M3 21 v-5 h5"
                                 scaleX="0.7" scaleY="0.7"/>
                    </graphic>
                </Button>
            </HBox>

            <!-- Bouton « Continuer » - principale action -->
            <Button fx:id="continuerButton"
                    styleClass="button, button-primary"
                    maxWidth="Infinity"
                    prefHeight="46"
                    onAction="#onContinuer"
                    defaultButton="true"
                    disable="true">
                <graphic>
                    <HBox spacing="10" alignment="CENTER">
                        <Label text="Continuer vers la connexion"
                               style="-fx-text-fill: white; -fx-font-weight: 600; -fx-font-size: 13.5px;"/>
                        <SVGPath styleClass="icon, icon-on-primary"
                                 content="M5 12 h14 M12 5 l7 7-7 7"
                                 scaleX="0.7" scaleY="0.7"/>
                    </HBox>
                </graphic>
            </Button>

            <HBox alignment="CENTER">
                <CheckBox fx:id="autoTestCheckBox"
                          text="Tester automatiquement au démarrage"
                          selected="true"/>
            </HBox>

            <Label styleClass="login-subtitle" alignment="CENTER" maxWidth="Infinity"
                   text="v1.0.0 — © RTS Sénégal"/>
        </VBox>

    </VBox>

</StackPane>
/* ============================================================
   RTS Caisse Guichet - Thème global v2 (modernisé)
   Police Inter recommandée. Fallback automatique sur les polices
   système. Palette enrichie, ombres douces, focus rings,
   transitions fluides.
   ============================================================ */

.root {
    -fx-font-family: "Inter", "Segoe UI Variable", "Segoe UI", "Roboto", "Helvetica Neue", system-ui, sans-serif;
    -fx-font-size: 13px;
    -fx-font-smoothing-type: lcd;

    /* ---------- Palette RTS modernisée ---------- */
    -rts-primary:        #0a4d8c;
    -rts-primary-light:  #1565c0;
    -rts-primary-dark:   #073a6b;
    -rts-primary-50:     #e8f1fa;
    -rts-primary-100:    #c5dcf0;

    -rts-accent:         #f5a623;
    -rts-accent-dark:    #d48806;
    -rts-accent-light:   #ffd66b;

    -rts-success:        #16a34a;
    -rts-success-light:  #dcfce7;
    -rts-success-dark:   #15803d;

    -rts-warning:        #ea580c;
    -rts-warning-light:  #ffedd5;

    -rts-danger:         #dc2626;
    -rts-danger-light:   #fee2e2;
    -rts-danger-dark:    #b91c1c;

    /* Échelle de gris néo (basée sur Tailwind slate) */
    -rts-gray-50:        #f8fafc;
    -rts-gray-100:       #f1f5f9;
    -rts-gray-200:       #e2e8f0;
    -rts-gray-300:       #cbd5e1;
    -rts-gray-400:       #94a3b8;
    -rts-gray-500:       #64748b;
    -rts-gray-600:       #475569;
    -rts-gray-700:       #334155;
    -rts-gray-800:       #1e293b;
    -rts-gray-900:       #0f172a;

    -fx-background-color: -rts-gray-100;
}

/* ============================================================
   ICÔNES SVG (Lucide-style, traits 2px)
   ============================================================ */

.icon {
    -fx-fill: -rts-gray-600;
    -fx-stroke: -rts-gray-600;
    -fx-stroke-width: 0;
}

/* Variantes de couleur applicables à tous les SVGPath stylés */
.icon-primary    { -fx-fill: -rts-primary;     -fx-stroke: -rts-primary; }
.icon-on-primary { -fx-fill: white;            -fx-stroke: white; }
.icon-accent     { -fx-fill: -rts-accent-dark; -fx-stroke: -rts-accent-dark; }
.icon-success    { -fx-fill: -rts-success;     -fx-stroke: -rts-success; }
.icon-warning    { -fx-fill: -rts-warning;     -fx-stroke: -rts-warning; }
.icon-danger     { -fx-fill: -rts-danger;      -fx-stroke: -rts-danger; }
.icon-muted      { -fx-fill: -rts-gray-400;    -fx-stroke: -rts-gray-400; }

/* Tailles standard d'icônes */
.icon-sm  { -fx-scale-x: 0.75; -fx-scale-y: 0.75; }
.icon-md  { -fx-scale-x: 1.0;  -fx-scale-y: 1.0; }
.icon-lg  { -fx-scale-x: 1.4;  -fx-scale-y: 1.4; }
.icon-xl  { -fx-scale-x: 1.8;  -fx-scale-y: 1.8; }

/* ============================================================
   ÉCRANS PLEIN-PAGE (login + serveur) — fond dégradé
   ============================================================ */

.login-pane {
    -fx-background-color:
        radial-gradient(center 20% 30%, radius 70%, rgba(255,255,255,0.10) 0%, transparent 60%),
        linear-gradient(from 0% 0% to 100% 100%, -rts-primary-dark 0%, -rts-primary 50%, #1976d2 100%);
}

/* Carte centrale */
.login-card {
    -fx-background-color: white;
    -fx-background-radius: 16;
    -fx-padding: 40 36 32 36;
    -fx-effect: dropshadow(gaussian, rgba(7, 58, 107, 0.35), 36, 0.15, 0, 12);
}

/* Logo central — pastille avec dégradé doré */
.login-logo {
    -fx-background-color: linear-gradient(to bottom right, -rts-accent-light 0%, -rts-accent 100%);
    -fx-background-radius: 18;
    -fx-min-width: 76; -fx-min-height: 76;
    -fx-max-width: 76; -fx-max-height: 76;
    -fx-alignment: center;
    -fx-effect: dropshadow(gaussian, rgba(213, 136, 6, 0.4), 16, 0.1, 0, 4);
}

.login-logo Label {
    -fx-text-fill: -rts-primary-dark;
    -fx-font-size: 24px;
    -fx-font-weight: 800;
}

.login-title {
    -fx-text-fill: -rts-gray-900;
    -fx-font-size: 24px;
    -fx-font-weight: 700;
}

.login-subtitle {
    -fx-text-fill: -rts-gray-500;
    -fx-font-size: 12.5px;
}

/* Lien discret sous le formulaire (« Changer de serveur ») */
.link-discreet {
    -fx-background-color: transparent;
    -fx-text-fill: -rts-primary;
    -fx-underline: false;
    -fx-cursor: hand;
    -fx-padding: 4 8;
    -fx-font-size: 12.5px;
}
.link-discreet:hover {
    -fx-underline: true;
    -fx-text-fill: -rts-primary-dark;
}

/* ============================================================
   HEADER / TOOLBAR
   ============================================================ */

.app-header {
    -fx-background-color: white;
    -fx-padding: 14 24 14 24;
    -fx-border-color: -rts-gray-200;
    -fx-border-width: 0 0 1 0;
    -fx-effect: dropshadow(gaussian, rgba(15, 23, 42, 0.06), 8, 0, 0, 2);
}

.app-title {
    -fx-text-fill: -rts-gray-900;
    -fx-font-size: 16px;
    -fx-font-weight: 700;
}

/* Logo header — petit pastille bleue */
.user-badge {
    -fx-background-color: linear-gradient(to bottom right, -rts-primary-light, -rts-primary-dark);
    -fx-text-fill: white;
    -fx-background-radius: 10;
    -fx-min-width: 38; -fx-min-height: 38;
    -fx-max-width: 38; -fx-max-height: 38;
    -fx-alignment: center;
    -fx-font-weight: 700;
    -fx-font-size: 12px;
    -fx-effect: dropshadow(gaussian, rgba(10, 77, 140, 0.25), 6, 0, 0, 2);
}

.user-name { -fx-font-weight: 600; -fx-font-size: 13px; -fx-text-fill: -rts-gray-900; }
.user-role { -fx-text-fill: -rts-gray-500; -fx-font-size: 11px; }

/* Avatar agent (bulle ronde avec initiales) */
.agent-avatar {
    -fx-background-color: linear-gradient(to bottom right, -rts-accent-light, -rts-accent);
    -fx-background-radius: 50%;
    -fx-min-width: 36; -fx-min-height: 36;
    -fx-max-width: 36; -fx-max-height: 36;
    -fx-alignment: center;
    -fx-text-fill: -rts-primary-dark;
    -fx-font-weight: 700;
    -fx-font-size: 13px;
}

/* ============================================================
   BOUTONS
   ============================================================ */

.button {
    -fx-background-radius: 8;
    -fx-padding: 9 18 9 18;
    -fx-cursor: hand;
    -fx-font-size: 13px;
    -fx-font-weight: 500;
}

.button:focused {
    -fx-effect: dropshadow(gaussian, rgba(10, 77, 140, 0.35), 0, 0, 0, 0), innershadow(gaussian, rgba(10, 77, 140, 0.5), 4, 0, 0, 0);
}

/* --- Primaire --- */
.button-primary {
    -fx-background-color: linear-gradient(to bottom, -rts-primary-light, -rts-primary);
    -fx-text-fill: white;
    -fx-font-weight: 600;
    -fx-effect: dropshadow(gaussian, rgba(10, 77, 140, 0.30), 8, 0.1, 0, 3);
}
.button-primary:hover {
    -fx-background-color: linear-gradient(to bottom, -rts-primary, -rts-primary-dark);
    -fx-effect: dropshadow(gaussian, rgba(10, 77, 140, 0.40), 12, 0.15, 0, 5);
}
.button-primary:pressed {
    -fx-background-color: -rts-primary-dark;
    -fx-effect: dropshadow(gaussian, rgba(10, 77, 140, 0.20), 4, 0, 0, 1);
}
.button-primary:disabled {
    -fx-background-color: -rts-gray-300;
    -fx-text-fill: -rts-gray-500;
    -fx-effect: null;
    -fx-opacity: 1.0;
}

/* --- Accent (doré) --- */
.button-accent {
    -fx-background-color: linear-gradient(to bottom, -rts-accent-light, -rts-accent);
    -fx-text-fill: -rts-primary-dark;
    -fx-font-weight: 700;
    -fx-effect: dropshadow(gaussian, rgba(213, 136, 6, 0.25), 6, 0.05, 0, 2);
}
.button-accent:hover {
    -fx-background-color: linear-gradient(to bottom, -rts-accent, -rts-accent-dark);
    -fx-effect: dropshadow(gaussian, rgba(213, 136, 6, 0.35), 10, 0.15, 0, 4);
}

/* --- Success / Danger --- */
.button-success {
    -fx-background-color: linear-gradient(to bottom, #22c55e, -rts-success);
    -fx-text-fill: white;
    -fx-font-weight: 600;
}
.button-success:hover { -fx-background-color: -rts-success-dark; }

.button-danger {
    -fx-background-color: linear-gradient(to bottom, #ef4444, -rts-danger);
    -fx-text-fill: white;
    -fx-font-weight: 600;
}
.button-danger:hover { -fx-background-color: -rts-danger-dark; }

/* --- Ghost (contour) --- */
.button-ghost {
    -fx-background-color: transparent;
    -fx-text-fill: -rts-gray-700;
    -fx-border-color: -rts-gray-300;
    -fx-border-radius: 8;
    -fx-border-width: 1;
}
.button-ghost:hover {
    -fx-background-color: -rts-gray-100;
    -fx-border-color: -rts-gray-400;
    -fx-text-fill: -rts-gray-900;
}

/* --- Secondaire (bleu clair) --- */
.button-secondary {
    -fx-background-color: -rts-primary-50;
    -fx-text-fill: -rts-primary-dark;
    -fx-border-color: -rts-primary-100;
    -fx-border-width: 1;
    -fx-border-radius: 8;
    -fx-background-radius: 8;
    -fx-font-weight: 600;
}
.button-secondary:hover {
    -fx-background-color: -rts-primary-100;
    -fx-border-color: -rts-primary-light;
}

/* Bouton icône carré */
.button-icon {
    -fx-background-color: transparent;
    -fx-border-color: -rts-gray-300;
    -fx-border-radius: 8;
    -fx-background-radius: 8;
    -fx-border-width: 1;
    -fx-padding: 0;
    -fx-min-width: 38; -fx-min-height: 38;
    -fx-max-width: 38; -fx-max-height: 38;
    -fx-alignment: center;
    -fx-cursor: hand;
}
.button-icon:hover {
    -fx-background-color: -rts-gray-100;
    -fx-border-color: -rts-gray-400;
}

/* ============================================================
   CARTES / PANNEAUX
   ============================================================ */

.card {
    -fx-background-color: white;
    -fx-background-radius: 12;
    -fx-padding: 22;
    -fx-effect: dropshadow(gaussian, rgba(15, 23, 42, 0.06), 12, 0.05, 0, 2);
}

.kpi-card {
    -fx-background-color: white;
    -fx-background-radius: 10;
    -fx-padding: 16 20 16 20;
    -fx-border-width: 0 0 0 4;
    -fx-border-color: -rts-primary;
    -fx-effect: dropshadow(gaussian, rgba(15, 23, 42, 0.05), 6, 0, 0, 2);
}
.kpi-card.entrees { -fx-border-color: -rts-success; }
.kpi-card.sorties { -fx-border-color: -rts-danger; }
.kpi-card.net     { -fx-border-color: -rts-accent; }

.kpi-value {
    -fx-font-size: 22px;
    -fx-font-weight: 700;
    -fx-text-fill: -rts-gray-900;
}
.kpi-label {
    -fx-font-size: 11px;
    -fx-text-fill: -rts-gray-500;
    -fx-font-weight: 600;
}

.amount-positive { -fx-text-fill: -rts-success; -fx-font-weight: 700; }
.amount-negative { -fx-text-fill: -rts-danger;  -fx-font-weight: 700; }

/* ============================================================
   CHAMPS DE SAISIE
   ============================================================ */

.text-field, .password-field, .combo-box, .text-area {
    -fx-background-radius: 8;
    -fx-border-radius: 8;
    -fx-border-color: -rts-gray-300;
    -fx-border-width: 1;
    -fx-padding: 9 12 9 12;
    -fx-font-size: 13px;
    -fx-prompt-text-fill: -rts-gray-400;
}

.text-field:hover, .password-field:hover, .combo-box:hover, .text-area:hover {
    -fx-border-color: -rts-gray-400;
}

.text-field:focused, .password-field:focused, .combo-box:focused, .text-area:focused {
    -fx-border-color: -rts-primary;
    -fx-border-width: 2;
    -fx-padding: 8 11 8 11; /* compense l'épaisseur supplémentaire */
    -fx-effect: dropshadow(gaussian, rgba(10, 77, 140, 0.15), 6, 0, 0, 0);
}

.text-area .content {
    -fx-background-radius: 6;
}

.field-label {
    -fx-text-fill: -rts-gray-700;
    -fx-font-size: 11.5px;
    -fx-font-weight: 700;
}

/* Champ avec icône à gauche : conteneur HBox styleClass="text-field-with-icon" */
.text-field-with-icon {
    -fx-background-color: white;
    -fx-background-radius: 8;
    -fx-border-radius: 8;
    -fx-border-color: -rts-gray-300;
    -fx-border-width: 1;
    -fx-padding: 0 12 0 12;
    -fx-spacing: 10;
    -fx-alignment: center-left;
}
.text-field-with-icon:hover {
    -fx-border-color: -rts-gray-400;
}
/* Quand le HBox parent reçoit la classe "field-focused" (gérée en Java)
   on affiche le focus ring complet. */
.text-field-with-icon.field-focused {
    -fx-border-color: -rts-primary;
    -fx-border-width: 2;
    -fx-padding: 0 11 0 11;
    -fx-effect: dropshadow(gaussian, rgba(10, 77, 140, 0.15), 6, 0, 0, 0);
}
.text-field-with-icon .text-field,
.text-field-with-icon .password-field {
    -fx-background-color: transparent;
    -fx-border-width: 0;
    -fx-padding: 10 0 10 0;
    -fx-effect: null;
}
.text-field-with-icon .text-field:focused,
.text-field-with-icon .password-field:focused {
    -fx-border-width: 0;
    -fx-padding: 10 0 10 0;
    -fx-effect: null;
}

/* ============================================================
   TABLEAU
   ============================================================ */

.table-view {
    -fx-background-color: white;
    -fx-background-radius: 10;
    -fx-border-color: -rts-gray-200;
    -fx-border-radius: 10;
}

.table-view .column-header-background {
    -fx-background-color: -rts-gray-50;
    -fx-background-radius: 10 10 0 0;
}
.table-view .column-header,
.table-view .filler {
    -fx-background-color: transparent;
    -fx-border-color: transparent -rts-gray-200 -rts-gray-200 transparent;
    -fx-border-width: 0 0 1 0;
}
.table-view .column-header .label {
    -fx-font-weight: 700;
    -fx-text-fill: -rts-gray-700;
    -fx-font-size: 11.5px;
}

.table-row-cell {
    -fx-border-color: transparent transparent -rts-gray-100 transparent;
    -fx-table-cell-border-color: transparent;
}
.table-row-cell:odd  { -fx-background-color: -rts-gray-50; }
.table-row-cell:even { -fx-background-color: white; }
.table-row-cell:hover {
    -fx-background-color: -rts-primary-50;
}
.table-row-cell:selected {
    -fx-background-color: -rts-primary-50;
    -fx-table-cell-border-color: transparent;
}
.table-row-cell:selected .text { -fx-fill: -rts-gray-900; }

/* ============================================================
   BADGES / STATUT
   ============================================================ */

.badge {
    -fx-background-radius: 12;
    -fx-padding: 3 10 3 10;
    -fx-font-size: 11px;
    -fx-font-weight: 700;
}
.badge-success { -fx-background-color: -rts-success-light; -fx-text-fill: -rts-success-dark; }
.badge-warning { -fx-background-color: -rts-warning-light; -fx-text-fill: -rts-warning; }
.badge-danger  { -fx-background-color: -rts-danger-light;  -fx-text-fill: -rts-danger-dark; }
.badge-info    { -fx-background-color: -rts-primary-50;    -fx-text-fill: -rts-primary-dark; }
.badge-neutral { -fx-background-color: -rts-gray-200;      -fx-text-fill: -rts-gray-700; }

/* ============================================================
   BANNIÈRE STATUT CAISSE
   ============================================================ */

.status-banner {
    -fx-background-color: -rts-primary;
    -fx-background-radius: 12;
    -fx-padding: 18 24 18 24;
    -fx-effect: dropshadow(gaussian, rgba(15, 23, 42, 0.1), 10, 0.05, 0, 3);
}
.status-banner.ouverte {
    -fx-background-color: linear-gradient(to right, -rts-success, #22c55e);
}
.status-banner.fermee  {
    -fx-background-color: linear-gradient(to right, -rts-warning, #f97316);
}

.status-banner .banner-title {
    -fx-text-fill: white;
    -fx-font-size: 11.5px;
    -fx-font-weight: 700;
    -fx-opacity: 0.85;
}
.status-banner .banner-value {
    -fx-text-fill: white;
    -fx-font-size: 26px;
    -fx-font-weight: 800;
}

/* ============================================================
   BLOC STATUT (page de connexion serveur)
   ============================================================ */

.status-box {
    -fx-background-radius: 10;
    -fx-border-radius: 10;
    -fx-border-width: 1;
    -fx-padding: 12 16 12 16;
}

.status-box .status-message {
    -fx-font-size: 13.5px;
    -fx-font-weight: 600;
}

.status-box .status-detail {
    -fx-font-size: 11.5px;
    -fx-text-fill: -rts-gray-500;
}

/* État "non testé" — neutre */
.status-idle {
    -fx-background-color: -rts-gray-50;
    -fx-border-color: -rts-gray-200;
}
.status-idle .status-message { -fx-text-fill: -rts-gray-700; }
.status-idle .status-icon-svg { -fx-fill: -rts-gray-400; -fx-stroke: -rts-gray-400; }

/* État "test en cours" — orange */
.status-pending {
    -fx-background-color: -rts-warning-light;
    -fx-border-color: #fdba74;
}
.status-pending .status-message { -fx-text-fill: #9a3412; }
.status-pending .status-icon-svg { -fx-fill: -rts-warning; -fx-stroke: -rts-warning; }

/* État "OK" — vert */
.status-ok {
    -fx-background-color: -rts-success-light;
    -fx-border-color: #86efac;
}
.status-ok .status-message { -fx-text-fill: -rts-success-dark; }
.status-ok .status-icon-svg { -fx-fill: -rts-success; -fx-stroke: -rts-success; }

/* État "KO" — rouge */
.status-ko {
    -fx-background-color: -rts-danger-light;
    -fx-border-color: #fca5a5;
}
.status-ko .status-message { -fx-text-fill: -rts-danger-dark; }
.status-ko .status-icon-svg { -fx-fill: -rts-danger; -fx-stroke: -rts-danger; }

/* Spinner de chargement (utilisé pendant le test serveur) */
.loading-spinner {
    -fx-progress-color: -rts-primary;
}

/* ============================================================
   MESSAGES / ALERTES INLINE
   ============================================================ */

.error-banner {
    -fx-background-color: -rts-danger-light;
    -fx-border-color: #fca5a5;
    -fx-border-width: 1;
    -fx-border-radius: 8;
    -fx-background-radius: 8;
    -fx-padding: 10 14 10 14;
    -fx-text-fill: -rts-danger-dark;
}

/* ============================================================
   SCROLLBARS — version slim et discrète
   ============================================================ */

.scroll-bar:vertical, .scroll-bar:horizontal {
    -fx-background-color: transparent;
}
.scroll-bar .thumb {
    -fx-background-color: -rts-gray-300;
    -fx-background-radius: 6;
}
.scroll-bar .thumb:hover {
    -fx-background-color: -rts-gray-400;
}
.scroll-bar .track,
.scroll-bar .track-background,
.scroll-bar .increment-button,
.scroll-bar .decrement-button {
    -fx-background-color: transparent;
    -fx-background-radius: 0;
}
.scroll-bar .increment-arrow,
.scroll-bar .decrement-arrow {
    -fx-shape: " ";
    -fx-padding: 0;
}

/* ============================================================
   LISTVIEW (sélection caisse)
   ============================================================ */

.list-view {
    -fx-background-color: white;
    -fx-background-radius: 8;
    -fx-border-color: -rts-gray-200;
    -fx-border-radius: 8;
    -fx-padding: 4;
}
.list-cell {
    -fx-background-color: transparent;
    -fx-padding: 12 14 12 14;
    -fx-background-radius: 6;
    -fx-text-fill: -rts-gray-800;
}
.list-cell:filled:hover {
    -fx-background-color: -rts-gray-100;
}
.list-cell:filled:selected {
    -fx-background-color: -rts-primary-50;
    -fx-text-fill: -rts-primary-dark;
}

/* ============================================================
   COMBO-BOX dropdown
   ============================================================ */

.combo-box-popup .list-view {
    -fx-background-radius: 8;
    -fx-effect: dropshadow(gaussian, rgba(15, 23, 42, 0.15), 12, 0.05, 0, 4);
}

/* ============================================================
   TOGGLE BUTTONS (Encaissement / Décaissement)
   ============================================================ */

.toggle-button {
    -fx-background-radius: 8;
    -fx-padding: 9 18 9 18;
    -fx-cursor: hand;
    -fx-font-weight: 600;
    -fx-opacity: 0.55;
}
.toggle-button:hover { -fx-opacity: 0.85; }
.toggle-button:selected { -fx-opacity: 1.0; }
