package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import sn.rts.caisse.audit.AuditAction;
import sn.rts.caisse.audit.AuditService;
import sn.rts.caisse.dto.auth.AuthResponse;
import sn.rts.caisse.dto.auth.LoginRequest;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.security.JwtService;

/**
 * Authentifie un utilisateur et émet un JWT signé.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UtilisateurRepository utilisateurRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.login(), request.motDePasse()));

            Utilisateur utilisateur = utilisateurRepository.findByLogin(request.login())
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "Utilisateur introuvable après authentification."));

            String token = jwtService.generateToken(utilisateur);

            // ⬇ AUDIT
            auditService.logForUser(
                    AuditAction.LOGIN_SUCCESS,
                    utilisateur,
                    "Utilisateur", utilisateur.getId(), utilisateur.getLogin(),
                    true, null,
                    "Rôle: " + utilisateur.getRole());

            log.info("Connexion réussie : {} ({})",
                    utilisateur.getLogin(), utilisateur.getRole());

            return AuthResponse.bearer(token, jwtService.getExpirationMs(),
                    utilisateur.getId(), utilisateur.getMatricule(),
                    utilisateur.getLogin(), utilisateur.getNomComplet(),
                    utilisateur.getRole());

        } catch (org.springframework.security.core.AuthenticationException e) {
            // ⬇ AUDIT — login refusé
            auditService.logLoginFailed(request.login(), e.getMessage());
            throw e;
        }
    }
}
