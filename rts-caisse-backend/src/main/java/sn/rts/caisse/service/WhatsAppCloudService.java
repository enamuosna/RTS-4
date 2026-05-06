package sn.rts.caisse.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import sn.rts.caisse.config.WhatsAppProperties;
import sn.rts.caisse.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsule les appels REST à l'API <b>WhatsApp Business Cloud</b> de Meta
 * pour l'envoi de reçus PDF.
 *
 * <h2>Workflow d'envoi d'un document</h2>
 * <ol>
 *   <li><b>Upload du PDF</b> via {@code POST /v18.0/{phone_number_id}/media}
 *       (multipart) → récupération d'un {@code media_id} valable 30 jours.</li>
 *   <li><b>Envoi du message</b> via {@code POST /v18.0/{phone_number_id}/messages}
 *       (JSON) avec le {@code media_id} et la légende → Meta livre le PDF
 *       sur le WhatsApp du destinataire.</li>
 * </ol>
 *
 * <h2>Quotas Meta</h2>
 * <ul>
 *   <li>Conversations gratuites entamées par RTS : <b>1 000 / mois</b>
 *       (au-delà : facturé selon tarif Meta).</li>
 *   <li>Réponses dans une fenêtre de 24h après un message du client : illimitées gratuites.</li>
 * </ul>
 *
 * <p><b>Note importante :</b> pour envoyer un message à un client qui n'a
 * jamais écrit à RTS sur WhatsApp, Meta exige un <i>template</i> approuvé.
 * Le code ci-dessous envoie un message libre type <i>document</i> qui
 * fonctionne tant que le client est dans la fenêtre de 24h. Si l'envoi
 * échoue avec une erreur Meta type "outside 24h window", il faudra créer
 * un template type {@code RECU_PAIEMENT} dans le dashboard Meta et adapter
 * ce service pour l'utiliser.</p>
 *
 * <h2>Audit</h2>
 * <p>Ce service ne s'audite pas lui-même : l'action {@code ENVOYER_WHATSAPP}
 * est tracée par {@code OperationCaisseService.envoyerWhatsApp()} qui dispose
 * du contexte métier (numéro de reçu, montant, opération…). Pour rester
 * cohérent avec ce schéma, on s'assure ici que les messages d'erreur
 * remontés à l'appelant sont concis et lisibles dans {@code audit_logs}
 * (extraction structurée de l'erreur Meta plutôt qu'un dump JSON brut).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppCloudService {

    private final WhatsAppProperties props;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================================================================
    //  API publique
    // ==================================================================

    /**
     * Envoie un document PDF par WhatsApp au numéro indiqué.
     *
     * @param numeroDestinataire numéro déjà normalisé (chiffres uniquement,
     *                           indicatif pays inclus, sans le {@code +})
     * @param pdfBytes           contenu binaire du PDF
     * @param nomFichierAffiche  nom de fichier qui apparaîtra côté client
     * @param legende            légende textuelle du document
     * @return {@code wamid} retourné par Meta
     * @throws BusinessException si l'envoi a échoué (le message est court
     *                           et lisible — il est destiné à finir dans
     *                           {@code audit_logs} et à être affiché à
     *                           l'utilisateur)
     */
    public String envoyerDocument(String numeroDestinataire,
                                  byte[] pdfBytes,
                                  String nomFichierAffiche,
                                  String legende) {

        if (!props.isEnabled()) {
            throw new BusinessException(
                    "L'intégration WhatsApp Cloud API est désactivée "
                            + "(rts.whatsapp.enabled=false).");
        }
        if (props.getPhoneNumberId() == null || props.getPhoneNumberId().isBlank()
                || props.getAccessToken() == null || props.getAccessToken().isBlank()) {
            throw new BusinessException(
                    "Configuration WhatsApp incomplète : phone-number-id "
                            + "ou access-token manquant.");
        }

        // Étape 1 : upload du média → récupération d'un media_id
        String mediaId = uploaderMedia(pdfBytes, nomFichierAffiche);
        log.info("PDF uploadé sur WhatsApp Cloud, media_id = {}", mediaId);

        // Étape 2 : envoi du message document
        String messageId = envoyerMessageDocument(numeroDestinataire, mediaId,
                nomFichierAffiche, legende);
        log.info("Message WhatsApp envoyé à {} : wamid = {}",
                numeroDestinataire, messageId);

        return messageId;
    }

    // ==================================================================
    //  Étape 1 — upload du média (multipart/form-data)
    // ==================================================================

    private String uploaderMedia(byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getAccessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // ByteArrayResource avec un nom de fichier explicite (Spring exige
        // getFilename() non-null pour la sérialisation multipart).
        ByteArrayResource pdfResource = new ByteArrayResource(pdfBytes) {
            @Override public String getFilename() { return filename; }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");
        body.add("type", "application/pdf");
        body.add("file", pdfResource);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<MediaUploadResponse> response = restTemplate.exchange(
                    props.getMediaUploadUrl(),
                    HttpMethod.POST,
                    request,
                    MediaUploadResponse.class);

            if (response.getBody() == null || response.getBody().id() == null) {
                throw new BusinessException(
                        "Réponse Meta inattendue lors de l'upload du PDF "
                                + "(pas de media_id).");
            }
            return response.getBody().id();

        } catch (HttpClientErrorException e) {
            String metaMessage = extraireMessageErreurMeta(e);
            log.error("Erreur Meta upload média : {} - {} (réponse complète : {})",
                    e.getStatusCode(), metaMessage, e.getResponseBodyAsString());
            throw new BusinessException(
                    "Échec upload PDF (" + e.getStatusCode().value() + ") : " + metaMessage);

        } catch (RestClientException e) {
            log.error("Erreur réseau upload média : {}", e.getMessage());
            throw new BusinessException(
                    "Impossible de joindre l'API WhatsApp Cloud : " + e.getMessage());
        }
    }

    // ==================================================================
    //  Étape 2 — envoi du message document (JSON)
    // ==================================================================

    private String envoyerMessageDocument(String numero, String mediaId,
                                          String filename, String legende) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", mediaId);
        document.put("filename", filename);
        if (legende != null && !legende.isBlank()) {
            document.put("caption", legende);
        }

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", numero,
                "type", "document",
                "document", document
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<MessageSendResponse> response = restTemplate.exchange(
                    props.getMessagesUrl(),
                    HttpMethod.POST,
                    request,
                    MessageSendResponse.class);

            MessageSendResponse responseBody = response.getBody();
            if (responseBody == null || responseBody.messages() == null
                    || responseBody.messages().isEmpty()) {
                throw new BusinessException(
                        "Réponse Meta inattendue lors de l'envoi du message.");
            }
            return responseBody.messages().get(0).id();

        } catch (HttpClientErrorException e) {
            String metaMessage = extraireMessageErreurMeta(e);
            log.error("Erreur Meta envoi message : {} - {} (réponse complète : {})",
                    e.getStatusCode(), metaMessage, e.getResponseBodyAsString());
            throw new BusinessException(
                    "Échec envoi WhatsApp (" + e.getStatusCode().value() + ") : " + metaMessage);

        } catch (RestClientException e) {
            log.error("Erreur réseau envoi message : {}", e.getMessage());
            throw new BusinessException(
                    "Impossible de joindre l'API WhatsApp Cloud : " + e.getMessage());
        }
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    /**
     * Extrait le message d'erreur "humain" depuis le corps de la réponse
     * d'erreur Meta, qui suit le format :
     * <pre>
     * {
     *   "error": {
     *     "message": "...",
     *     "type": "OAuthException",
     *     "code": 100,
     *     "error_subcode": 33,
     *     "fbtrace_id": "..."
     *   }
     * }
     * </pre>
     *
     * <p>Si le parsing échoue (ex. réponse non-JSON), on tronque la chaîne
     * brute à 200 caractères pour rester lisible dans les logs et l'audit.</p>
     */
    private String extraireMessageErreurMeta(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return e.getStatusText();
        }
        try {
            MetaErrorEnveloppe enveloppe = objectMapper.readValue(body, MetaErrorEnveloppe.class);
            if (enveloppe != null && enveloppe.error() != null
                    && enveloppe.error().message() != null
                    && !enveloppe.error().message().isBlank()) {

                Integer code = enveloppe.error().code();
                return code != null
                        ? enveloppe.error().message() + " (code Meta " + code + ")"
                        : enveloppe.error().message();
            }
        } catch (Exception parseEx) {
            log.debug("Impossible de parser la réponse d'erreur Meta : {}", parseEx.getMessage());
        }
        // Fallback : chaîne brute tronquée
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }

    // ==================================================================
    //  Records de désérialisation des réponses Meta
    // ==================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MediaUploadResponse(@JsonProperty("id") String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageSendResponse(
            @JsonProperty("messaging_product") String messagingProduct,
            @JsonProperty("contacts") List<Contact> contacts,
            @JsonProperty("messages") List<MessageRef> messages
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Contact(String input, @JsonProperty("wa_id") String waId) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record MessageRef(@JsonProperty("id") String id) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetaErrorEnveloppe(@JsonProperty("error") MetaError error) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record MetaError(
                @JsonProperty("message")       String  message,
                @JsonProperty("type")          String  type,
                @JsonProperty("code")          Integer code,
                @JsonProperty("error_subcode") Integer errorSubcode,
                @JsonProperty("fbtrace_id")    String  fbtraceId
        ) {}
    }
}
