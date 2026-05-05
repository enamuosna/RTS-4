package sn.rts.caisse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration de l'intégration WhatsApp Business Cloud API (Meta).
 *
 * <h2>Variables d'environnement attendues</h2>
 * <ul>
 *   <li>{@code WHATSAPP_ENABLED} — {@code true} en prod, {@code false} en dev</li>
 *   <li>{@code WHATSAPP_API_VERSION} — ex. {@code v18.0}</li>
 *   <li>{@code WHATSAPP_PHONE_NUMBER_ID} — ID du numéro WhatsApp RTS chez Meta</li>
 *   <li>{@code WHATSAPP_ACCESS_TOKEN} — Bearer token permanent (Meta)</li>
 * </ul>
 *
 * <p>Les valeurs sont mappées depuis {@code application.properties}, qui
 * lui-même prend ses valeurs des variables d'environnement (voir snippet
 * {@code application-whatsapp.properties}).</p>
 */
@Component
@ConfigurationProperties(prefix = "rts.whatsapp")
@Getter
@Setter
public class WhatsAppProperties {

    /** Active/désactive l'envoi WhatsApp (à passer à {@code true} en prod). */
    private boolean enabled = false;

    /** Version de l'API Graph Meta (ex. {@code v18.0}). */
    private String apiVersion = "v18.0";

    /** Identifiant Meta du numéro WhatsApp Business RTS. */
    private String phoneNumberId;

    /** Bearer token permanent (jamais commité, source : variable d'environnement). */
    private String accessToken;

    /** Indicatif pays par défaut quand le numéro client est sans préfixe. */
    private String defaultCountryPrefix = "221";

    public String getBaseUrl() {
        return "https://graph.facebook.com/" + apiVersion;
    }

    public String getMediaUploadUrl() {
        return getBaseUrl() + "/" + phoneNumberId + "/media";
    }

    public String getMessagesUrl() {
        return getBaseUrl() + "/" + phoneNumberId + "/messages";
    }
}
