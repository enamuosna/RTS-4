package sn.rts.caisse.dto;

/**
 * Réponse renvoyée après un appel à
 * {@code POST /api/operations/{id}/whatsapp}.
 *
 * @param envoye         {@code true} si Meta a confirmé la prise en compte
 * @param messageId      identifiant {@code wamid.XXX} retourné par Meta
 * @param destinataire   numéro effectivement utilisé (E.164 sans le +)
 * @param messageErreur  null si succès, sinon raison de l'échec
 */
public record EnvoiWhatsAppResponse(
        boolean envoye,
        String messageId,
        String destinataire,
        String messageErreur
) {
    public static EnvoiWhatsAppResponse succes(String messageId, String destinataire) {
        return new EnvoiWhatsAppResponse(true, messageId, destinataire, null);
    }

    public static EnvoiWhatsAppResponse echec(String destinataire, String raison) {
        return new EnvoiWhatsAppResponse(false, null, destinataire, raison);
    }
}
