package sn.rts.caisse.exception;

/** Entité demandée introuvable en base. Donne un HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String ressource, Object id) {
        return new ResourceNotFoundException(ressource + " introuvable (id = " + id + ")");
    }
}
