package sn.rts.caisse.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import sn.rts.caisse.repository.UtilisateurRepository;

/**
 * Pont entre Spring Security et notre entité {@link sn.rts.caisse.model.Utilisateur}.
 * Le {@code login} sert d'identifiant d'authentification.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return utilisateurRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur introuvable : " + login));
    }
}
