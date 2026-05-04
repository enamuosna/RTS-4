package sn.rts.caisse.model;

/**
 * État logique d'une caisse physique.
 */
public enum StatutCaisse {
    /** La caisse n'a pas encore été ouverte pour la journée. */
    FERMEE,

    /** La caisse est ouverte : des opérations peuvent être saisies. */
    OUVERTE,

    /** Suspendue par l'administration (audit, incident). */
    SUSPENDUE
}
