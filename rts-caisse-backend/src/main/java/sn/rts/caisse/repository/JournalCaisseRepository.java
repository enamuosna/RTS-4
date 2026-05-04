package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.JournalCaisse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalCaisseRepository extends JpaRepository<JournalCaisse, Long> {

    /** Journal d'une caisse pour une date donnée (une seule ouverture possible par jour). */
    Optional<JournalCaisse> findByCaisseIdAndDateJournal(Long caisseId, LocalDate date);

    /** Journal ouvert (non clôturé) pour une caisse. */
    Optional<JournalCaisse> findByCaisseIdAndClotureFalse(Long caisseId);

    List<JournalCaisse> findByDateJournal(LocalDate date);

    List<JournalCaisse> findByCaisseIdOrderByDateJournalDesc(Long caisseId);
}
