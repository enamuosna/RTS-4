package sn.rts.caisse.dto;

import sn.rts.caisse.model.Role;
import sn.rts.caisse.model.Utilisateur;

public record UtilisateurDTO(
        Long id,
        String matricule,
        String login,
        String prenom,
        String nom,
        String email,
        String telephone,
        Role role,
        boolean actif
) {
    public static UtilisateurDTO from(Utilisateur u) {
        return new UtilisateurDTO(
                u.getId(),
                u.getMatricule(),
                u.getLogin(),
                u.getPrenom(),
                u.getNom(),
                u.getEmail(),
                u.getTelephone(),
                u.getRole(),
                u.isActif()
        );
    }
}
