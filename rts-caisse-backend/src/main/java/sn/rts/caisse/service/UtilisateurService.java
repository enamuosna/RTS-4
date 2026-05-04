package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.UtilisateurDTO;
import sn.rts.caisse.dto.auth.RegisterRequest;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.UtilisateurRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public UtilisateurDTO creer(RegisterRequest request) {
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

        return UtilisateurDTO.from(utilisateurRepository.save(u));
    }

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

    public UtilisateurDTO activer(Long id, boolean actif) {
        Utilisateur u = trouver(id);
        u.setActif(actif);
        return UtilisateurDTO.from(utilisateurRepository.save(u));
    }

    public void changerMotDePasse(Long id, String nouveau) {
        Utilisateur u = trouver(id);
        u.setMotDePasse(passwordEncoder.encode(nouveau));
        utilisateurRepository.save(u);
    }

    public void supprimer(Long id) {
        Utilisateur u = trouver(id);
        // On préfère désactiver plutôt que supprimer (audit).
        u.setActif(false);
        utilisateurRepository.save(u);
    }

    private Utilisateur trouver(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
