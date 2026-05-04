package sn.rts.caisse.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sn.rts.caisse.repository.OperationCaisseRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Générateur de numéros de reçu unique par jour et par caisse.
 *
 * Format : RTS-{année}-{codeCaisse}-{séquence sur 5 chiffres}
 *
 * Robustesse :
 *  - Le compteur est initialisé depuis la base au premier appel pour
 *    une combinaison (date, caisse) donnée. Évite les collisions au
 *    redémarrage du container.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NumeroRecuGenerator {

    private static final Map<String, AtomicInteger> COMPTEURS = new ConcurrentHashMap<>();

    private final OperationCaisseRepository operationRepository;

    public synchronized String generer(String codeCaisse) {
        LocalDate aujourdHui = LocalDate.now();
        String cle = aujourdHui + "::" + codeCaisse;

        AtomicInteger compteur = COMPTEURS.computeIfAbsent(cle, k -> {
            // Première fois pour cette (date, caisse) : on initialise
            // depuis le plus haut numéro déjà en base, pour éviter
            // toute collision après un redémarrage du container.
            int dernier = trouverDernierNumeroEnBase(aujourdHui, codeCaisse);
            log.info("Initialisation compteur reçu - cle={}, dernier={}", k, dernier);
            return new AtomicInteger(dernier);
        });

        int numero = compteur.incrementAndGet();
        return String.format("RTS-%d-%s-%05d",
                aujourdHui.getYear(), codeCaisse, numero);
    }

    /**
     * Cherche en base le numéro de séquence le plus haut déjà utilisé
     * pour la combinaison (date, caisse). Retourne 0 si aucun.
     */
    private int trouverDernierNumeroEnBase(LocalDate date, String codeCaisse) {
        String prefixe = String.format("RTS-%d-%s-", date.getYear(), codeCaisse);
        try {
            return operationRepository
                    .findMaxSequenceForPrefix(prefixe)
                    .orElse(0);
        } catch (Exception e) {
            log.warn("Impossible de lire la dernière séquence en base, démarrage à 0", e);
            return 0;
        }
    }

    /** Réservé aux tests unitaires. */
    public static void reset() {
        COMPTEURS.clear();
    }
}
