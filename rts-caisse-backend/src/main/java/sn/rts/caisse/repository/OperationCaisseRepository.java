package sn.rts.caisse.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.OperationCaisse;
import sn.rts.caisse.model.TypeOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OperationCaisseRepository extends JpaRepository<OperationCaisse, Long> {

    Optional<OperationCaisse> findByNumeroRecu(String numeroRecu);

    Page<OperationCaisse> findByCaisseId(Long caisseId, Pageable pageable);

    List<OperationCaisse> findByCaisseIdAndDateOperationBetweenAndAnnuleeFalse(
            Long caisseId, LocalDateTime debut, LocalDateTime fin);

    List<OperationCaisse> findByDateOperationBetween(LocalDateTime debut, LocalDateTime fin);

    List<OperationCaisse> findByJournalId(Long journalId);

    /**
     * Retourne la séquence (5 derniers chiffres) la plus haute parmi
     * tous les numéros de reçu commençant par le préfixe donné.
     *
     * Préfixe attendu : "RTS-{année}-{codeCaisse}-"
     * Numéro complet : "RTS-2026-CAISSE_PRINCIPALE-00042"
     *                                                ↑↑↑↑↑ ces 5 chiffres
     */
    @Query(value = """
        SELECT COALESCE(MAX(CAST(SUBSTRING(numero_recu FROM LENGTH(:prefixe) + 1) AS INTEGER)), 0)
        FROM operation_caisse
        WHERE numero_recu LIKE :prefixe || '%'
        """, nativeQuery = true)
    Optional<Integer> findMaxSequenceForPrefix(@Param("prefixe") String prefixe);


    /**
     * Somme des montants d'un type d'opération sur une période, pour une caisse donnée,
     * en excluant les opérations annulées. Renvoie 0 si aucune opération.
     */
    @Query("""
            SELECT COALESCE(SUM(o.montant), 0)
            FROM OperationCaisse o
            WHERE o.caisse.id = :caisseId
              AND o.typeOperation = :type
              AND o.dateOperation BETWEEN :debut AND :fin
              AND o.annulee = false
            """)
    BigDecimal sommerMontants(@Param("caisseId") Long caisseId,
                              @Param("type") TypeOperation type,
                              @Param("debut") LocalDateTime debut,
                              @Param("fin") LocalDateTime fin);

    /**
     * Compte les opérations non rattachées à un journal de clôture pour une caisse
     * sur une journée donnée (utilisé pour savoir s'il reste des opérations à clôturer).
     */
    long countByCaisseIdAndDateOperationBetweenAndJournalIsNullAndAnnuleeFalse(
            Long caisseId, LocalDateTime debut, LocalDateTime fin);
}
