package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
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

    public AuthResponse login(LoginRequest request) {
        // Vérification login + mot de passe via Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.login(), request.motDePasse()));

        Utilisateur utilisateur = utilisateurRepository.findByLogin(request.login())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur introuvable après authentification."));

        String token = jwtService.generateToken(utilisateur);
        log.info("Connexion réussie : {} ({})", utilisateur.getLogin(), utilisateur.getRole());

        return AuthResponse.bearer(
                token,
                jwtService.getExpirationMs(),
                utilisateur.getId(),
                utilisateur.getMatricule(),
                utilisateur.getLogin(),
                utilisateur.getNomComplet(),
                utilisateur.getRole());
    }
}
