package sn.rts.caisse.dto.auth;

import sn.rts.caisse.model.Role;

public record AuthResponse(
        String token,
        String type,
        long expiresInMs,
        Long utilisateurId,
        String matricule,
        String login,
        String nomComplet,
        Role role
) {
    public static AuthResponse bearer(String token,
                                      long expiresInMs,
                                      Long id,
                                      String matricule,
                                      String login,
                                      String nomComplet,
                                      Role role) {
        return new AuthResponse(token, "Bearer", expiresInMs,
                id, matricule, login, nomComplet, role);
    }
}
