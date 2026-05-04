package sn.rts.caisse.exception;

/**
 * Règle métier violée : caisse fermée, solde insuffisant, journal déjà clôturé,
 * etc. Donne un HTTP 400/409.
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
