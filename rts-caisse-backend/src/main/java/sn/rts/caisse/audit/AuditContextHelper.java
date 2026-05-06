package sn.rts.caisse.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import sn.rts.caisse.model.Utilisateur;

/**
 * Utilitaires pour extraire le contexte d'exécution courant (utilisateur
 * Spring Security, requête HTTP) sans créer de couplage fort entre les
 * services métier et l'infrastructure web.
 *
 * <p>Toutes les méthodes sont <b>tolérantes au null</b> : si on est dans
 * un thread qui n'a pas de contexte web (tâches planifiées, tests…),
 * elles renvoient {@code null} ou une chaîne vide plutôt que de planter.</p>
 */
@Slf4j
@UtilityClass
public class AuditContextHelper {

    /**
     * Renvoie l'utilisateur authentifié du thread courant ou {@code null}
     * s'il n'y en a pas (endpoint public, traitement asynchrone…).
     */
    public Utilisateur currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Utilisateur u) {
            return u;
        }
        return null;
    }

    /**
     * Renvoie la requête HTTP courante ou {@code null} si on est hors
     * d'un contexte servlet (thread async, scheduler…).
     */
    public HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    /**
     * Adresse IP de l'appelant en tenant compte des headers de proxy
     * (nginx, load balancer…). Renvoie {@code null} si indisponible.
     *
     * <p>Ordre de priorité :
     * <ol>
     *   <li>{@code X-Forwarded-For} (premier IP de la liste)</li>
     *   <li>{@code X-Real-IP}</li>
     *   <li>{@link HttpServletRequest#getRemoteAddr()}</li>
     * </ol>
     */
    public String currentIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;

        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Header peut contenir "client, proxy1, proxy2" -> on prend le 1er
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return req.getRemoteAddr();
    }

    /** User-Agent brut, tronqué à 500 caractères pour rester dans la colonne. */
    public String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String ua = req.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 500 ? ua.substring(0, 500) : ua;
    }

    /** Méthode HTTP (GET, POST…) ou null. */
    public String currentMethod() {
        HttpServletRequest req = currentRequest();
        return req == null ? null : req.getMethod();
    }

    /** Path de la requête (sans query string), tronqué à 255. */
    public String currentPath() {
        HttpServletRequest req = currentRequest();
        if (req == null) return null;
        String uri = req.getRequestURI();
        if (uri == null) return null;
        return uri.length() > 255 ? uri.substring(0, 255) : uri;
    }
}
