package sn.rts.caisse.model;

/**
 * Rôles applicatifs. Chaque rôle détermine les endpoints accessibles
 * via Spring Security (voir SecurityConfig).
 */
public enum Role {

    /** Administration globale : utilisateurs, caisses, paramétrage. */
    ADMIN,

    /** Supervision : dashboards, clôtures, validation des écarts. */
    SUPERVISEUR,

    /** Agent de guichet : saisie des encaissements / décaissements. */
    CAISSIER
}
