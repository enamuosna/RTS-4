package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditContextHelper;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.UtilisateurDTO;
import sn.rts.caisse.dto.auth.RegisterRequest;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.security.SuperAdminPolicy;

import java.util.List;
import java.util.Objects;

/**
 * Service de gestion des utilisateurs (administration RTS).
 *
 * <h2>Politique d'autorisation</h2>
 * <ul>
 *   <li><b>Tous les ADMIN</b> peuvent : lister, voir, créer, désactiver,
 *       réactiver, supprimer (logiquement) des utilisateurs.</li>
 *   <li><b>Seul l'administrateur général</b> ({@link SuperAdminPolicy}) peut :
 *       <ul>
 *         <li>modifier le <b>login</b> d'un utilisateur ;</li>
 *         <li>réinitialiser le <b>mot de passe</b> d'un autre utilisateur.</li>
 *       </ul>
 *   </li>
 *   <li><b>Tout utilisateur authentifié</b> peut changer son propre mot de
 *       passe (self-service).</li>
 * </ul>
 *
 * <p>Toutes les actions sensibles sont tracées dans {@code audit_logs} via
 * {@link AuditService}, en succès comme en échec.</p>
 *
 * <p>⚠ Aucun mot de passe (clair ou hashé) n'est jamais écrit dans
 * {@code audit_logs}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SuperAdminPolicy superAdminPolicy;

    // ------------------------------------------------------------------
    //  Création
    // ------------------------------------------------------------------

    public UtilisateurDTO creer(RegisterRequest request) {
        try {
            if (utilisateurRepository.existsByLogin(request.login())) {
                throw new BusinessException("Login déjà utilisé : " + request.login());
            }
            if (utilisateurRepository.existsByMatricule(request.matricule())) {
                throw new BusinessException("Matricule déjà utilisé : " + request.matricule());
            }

            Utilisateur u = Utilisateur.builder()
                    .matricule(request.matricule())
                    .login(request.login())
                    .motDePasse(passwordEncoder.encode(request.motDePasse()))
                    .prenom(request.prenom())
                    .nom(request.nom())
                    .email(request.email())
                    .telephone(request.telephone())
                    .role(request.role())
                    .actif(true)
                    .build();

            Utilisateur saved = utilisateurRepository.save(u);
            log.info("Utilisateur créé : login={} matricule={} role={}",
                    saved.getLogin(), saved.getMatricule(), saved.getRole());

            auditService.logSuccess(
                    AuditAction.CREER_UTILISATEUR,
                    "Utilisateur",
                    saved.getId(),
                    saved.getLogin() + " (" + saved.getMatricule() + ")",
                    "Login=" + saved.getLogin()
                            + " Matricule=" + saved.getMatricule()
                            + " Role=" + saved.getRole()
                            + " Nom=" + saved.getNomComplet()
                            + (saved.getEmail() != null ? " Email=" + saved.getEmail() : "")
                            + (saved.getTelephone() != null ? " Tel=" + saved.getTelephone() : ""));

            return UtilisateurDTO.from(saved);

        } catch (BusinessException e) {
            auditService.logFailure(
                    AuditAction.CREER_UTILISATEUR,
                    "Utilisateur",
                    null,
                    request != null
                            ? "login=" + request.login()
                                    + " matricule=" + request.matricule()
                                    + " role=" + request.role()
                            : null,
                    e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Lecture
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UtilisateurDTO> lister() {
        return utilisateurRepository.findAll().stream()
                .map(UtilisateurDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UtilisateurDTO obtenir(Long id) {
        return UtilisateurDTO.from(trouver(id));
    }

    // ------------------------------------------------------------------
    //  Activation / désactivation
    // ------------------------------------------------------------------

    public UtilisateurDTO activer(Long id, boolean actif) {
        AuditAction action = actif
                ? AuditAction.REACTIVER_UTILISATEUR
                : AuditAction.DESACTIVER_UTILISATEUR;
        try {
            Utilisateur u = trouver(id);

            // Protection : on ne peut pas désactiver le super admin
            if (!actif && superAdminPolicy.isSuperAdmin(u)) {
                throw new BusinessException(
                        "L'administrateur général ne peut pas être désactivé.");
            }

            boolean ancienEtat = u.isActif();
            u.setActif(actif);
            Utilisateur saved = utilisateurRepository.save(u);

            log.info("Utilisateur {} : login={} ancien={} nouveau={}",
                    actif ? "réactivé" : "désactivé",
                    saved.getLogin(), ancienEtat, actif);

            auditService.logSuccess(
                    action,
                    "Utilisateur",
                    saved.getId(),
                    saved.getLogin() + " (" + saved.getMatricule() + ")",
                    "Login=" + saved.getLogin()
                            + " Role=" + saved.getRole()
                            + " AncienEtat=" + (ancienEtat ? "actif" : "inactif")
                            + " NouvelEtat=" + (actif ? "actif" : "inactif"));

            return UtilisateurDTO.from(saved);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(action, "Utilisateur", id, null, e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Modification du LOGIN — RÉSERVÉE AU SUPER ADMIN
    // ------------------------------------------------------------------

    /**
     * Change le login d'un utilisateur. <b>Réservé à l'administrateur
     * général</b> (les autres ADMIN n'ont pas ce droit).
     *
     * @throws BusinessException si l'appelant n'est pas super admin,
     *         si le nouveau login est déjà utilisé, ou si on tente de
     *         renommer le super admin lui-même.
     */
    public UtilisateurDTO modifierLogin(Long id, String nouveauLogin) {
        try {
            superAdminPolicy.requireSuperAdmin(
                    "Seul l'administrateur général (login '"
                            + superAdminPolicy.getSuperAdminLogin()
                            + "') peut modifier le login d'un utilisateur.");

            if (nouveauLogin == null || nouveauLogin.isBlank()) {
                throw new BusinessException("Le nouveau login ne peut pas être vide.");
            }
            String loginNorm = nouveauLogin.trim();

            Utilisateur u = trouver(id);

            // Protection : on ne peut pas renommer le super admin lui-même
            if (superAdminPolicy.isSuperAdmin(u)) {
                throw new BusinessException(
                        "Le login de l'administrateur général ne peut pas être modifié "
                                + "(il est référencé dans la configuration).");
            }

            String ancienLogin = u.getLogin();
            if (loginNorm.equals(ancienLogin)) {
                // Pas de changement → on ne fait rien (et on n'audite pas)
                return UtilisateurDTO.from(u);
            }

            if (utilisateurRepository.existsByLogin(loginNorm)) {
                throw new BusinessException("Login déjà utilisé : " + loginNorm);
            }

            u.setLogin(loginNorm);
            Utilisateur saved = utilisateurRepository.save(u);

            log.info("Login modifié pour l'utilisateur #{} : '{}' → '{}'",
                    id, ancienLogin, loginNorm);

            auditService.logSuccess(
                    AuditAction.MODIFIER_UTILISATEUR,
                    "Utilisateur",
                    saved.getId(),
                    saved.getLogin() + " (" + saved.getMatricule() + ")",
                    "Champ=login"
                            + " Matricule=" + saved.getMatricule()
                            + " AncienLogin=" + ancienLogin
                            + " NouveauLogin=" + loginNorm);

            return UtilisateurDTO.from(saved);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(
                    AuditAction.MODIFIER_UTILISATEUR,
                    "Utilisateur",
                    id,
                    "tentative=modifierLogin nouveau=" + nouveauLogin,
                    e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Changement de mot de passe
    // ------------------------------------------------------------------

    /**
     * Change le mot de passe d'un utilisateur.
     *
     * <p><b>Règles :</b></p>
     * <ul>
     *   <li>Tout utilisateur authentifié peut changer <b>son propre</b>
     *       mot de passe → action {@link AuditAction#CHANGER_MOT_DE_PASSE}.</li>
     *   <li>Pour réinitialiser le mot de passe d'un <b>autre</b> utilisateur,
     *       il faut être l'administrateur général →
     *       action {@link AuditAction#REINITIALISER_MOT_DE_PASSE}.
     *       Les autres ADMIN ne peuvent pas le faire.</li>
     * </ul>
     */
    public void changerMotDePasse(Long id, String nouveau) {
        AuditAction action = determinerActionMotDePasse(id);
        try {
            Utilisateur courant = AuditContextHelper.currentUser();
            boolean estSelf = courant != null && Objects.equals(courant.getId(), id);

            // Pour modifier le mot de passe d'un AUTRE utilisateur, super admin requis
            if (!estSelf) {
                superAdminPolicy.requireSuperAdmin(
                        "Seul l'administrateur général (login '"
                                + superAdminPolicy.getSuperAdminLogin()
                                + "') peut réinitialiser le mot de passe d'un autre utilisateur.");
            }

            Utilisateur u = trouver(id);

            if (nouveau == null || nouveau.isBlank()) {
                throw new BusinessException("Le nouveau mot de passe ne peut pas être vide.");
            }

            u.setMotDePasse(passwordEncoder.encode(nouveau));
            utilisateurRepository.save(u);

            log.info("Mot de passe changé pour l'utilisateur {} (action={})",
                    u.getLogin(), action);

            // ⚠ Aucun mot de passe (clair ou hashé) ne va dans audit_logs
            auditService.logSuccess(
                    action,
                    "Utilisateur",
                    u.getId(),
                    u.getLogin() + " (" + u.getMatricule() + ")",
                    "Login=" + u.getLogin()
                            + " Role=" + u.getRole()
                            + " LongueurNouveauMdp=" + nouveau.length()
                            + " Self=" + estSelf);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(
                    action,
                    "Utilisateur",
                    id,
                    null,
                    e.getMessage());
            throw e;
        }
    }

    /**
     * Détermine si le changement de mot de passe est self-service
     * (CHANGER_MOT_DE_PASSE) ou administratif (REINITIALISER_MOT_DE_PASSE).
     */
    private AuditAction determinerActionMotDePasse(Long idCible) {
        Utilisateur courant = AuditContextHelper.currentUser();
        if (courant != null && Objects.equals(courant.getId(), idCible)) {
            return AuditAction.CHANGER_MOT_DE_PASSE;
        }
        return AuditAction.REINITIALISER_MOT_DE_PASSE;
    }

    // ------------------------------------------------------------------
    //  Suppression (logique = désactivation)
    // ------------------------------------------------------------------

    public void supprimer(Long id) {
        try {
            Utilisateur u = trouver(id);

            // Protection : on ne peut pas supprimer le super admin
            if (superAdminPolicy.isSuperAdmin(u)) {
                throw new BusinessException(
                        "L'administrateur général ne peut pas être supprimé.");
            }

            boolean etaitActif = u.isActif();
            u.setActif(false);
            utilisateurRepository.save(u);

            log.info("Utilisateur supprimé (désactivé) : login={}", u.getLogin());

            auditService.logSuccess(
                    AuditAction.DESACTIVER_UTILISATEUR,
                    "Utilisateur",
                    u.getId(),
                    u.getLogin() + " (" + u.getMatricule() + ")",
                    "Login=" + u.getLogin()
                            + " Role=" + u.getRole()
                            + " Action=suppression(désactivation logique)"
                            + " EtaitActif=" + etaitActif);

        } catch (BusinessException | ResourceNotFoundException e) {
            auditService.logFailure(
                    AuditAction.DESACTIVER_UTILISATEUR,
                    "Utilisateur",
                    id,
                    "tentative=suppression",
                    e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    //  Helper privé
    // ------------------------------------------------------------------

    private Utilisateur trouver(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
