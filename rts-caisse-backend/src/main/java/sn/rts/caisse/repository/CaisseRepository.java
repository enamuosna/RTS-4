package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.StatutCaisse;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaisseRepository extends JpaRepository<Caisse, Long> {

    Optional<Caisse> findByCode(String code);

    boolean existsByCode(String code);

    List<Caisse> findByStatut(StatutCaisse statut);

    List<Caisse> findByCaissierId(Long caissierId);
}
