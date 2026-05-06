package sn.rts.caisse.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sn.rts.caisse.audit.AuditContextHelper;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.model.Utilisateur;

/**
 * Politique d'<b>administrateur général</b> (« super admin »).
 *
 * <p>Tous les utilisateurs avec le rôle {@code ADMIN} ne sont pas équivalents :
 * un seul d'entre eux — l'<i>administrateur général</i> — détient des
 * privilèges supplémentaires comme la modification des identifiants
 * (login + mot de passe) d'autres utilisateurs.</p>
 *
 * <p>L'identification se fait par <b>login</b>, configurable via la propriété :
 * <pre>
 *   rts.security.super-admin-login = admin
 * </pre>
 * Valeur par défaut : {@code admin}. Si tu changes le login en base, mets
 * aussi à jour cette propriété.</p>
 *
 * <p>Note : on ne se base PAS sur le matricule ni sur l'ID, car le matricule
 * peut être modifié et l'ID dépend de l'ordre de création.</p>
 */
@Slf4j
@Component
public class SuperAdminPolicy {

    @Value("${rts.security.super-admin-login:admin}")
    private String superAdminLogin;

    /** {@code true} si l'utilisateur passé est l'administrateur général. */
    public boolean isSuperAdmin(Utilisateur user) {
        return user != null
                && user.getLogin() != null
                && user.getLogin().equalsIgnoreCase(superAdminLogin);
    }

    /** {@code true} si l'utilisateur Spring Security courant est super admin. */
    public boolean isCurrentUserSuperAdmin() {
        return isSuperAdmin(AuditContextHelper.currentUser());
    }

    /**
     * Lève une {@link BusinessException} si l'utilisateur courant n'est pas
     * super admin. À utiliser en garde au début d'une méthode sensible.
     *
     * @param messageInterdit message qui apparaîtra dans la réponse HTTP
     */
    public void requireSuperAdmin(String messageInterdit) {
        if (!isCurrentUserSuperAdmin()) {
            Utilisateur courant = AuditContextHelper.currentUser();
            log.warn("Tentative d'action super-admin par un utilisateur non habilité : {}",
                    courant != null ? courant.getLogin() : "anonyme");
            throw new BusinessException(messageInterdit);
        }
    }

    /** Login configuré pour le super admin (lecture seule). */
    public String getSuperAdminLogin() {
        return superAdminLogin;
    }
}
