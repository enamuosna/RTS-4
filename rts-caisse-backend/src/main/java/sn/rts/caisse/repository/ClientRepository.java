package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.Client;

import java.util.List;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByActifTrue();

    @Query("""
            SELECT c FROM Client c
            WHERE LOWER(c.raisonSociale) LIKE LOWER(CONCAT('%', :terme, '%'))
               OR LOWER(COALESCE(c.identifiantFiscal, '')) LIKE LOWER(CONCAT('%', :terme, '%'))
               OR LOWER(COALESCE(c.telephone, '')) LIKE LOWER(CONCAT('%', :terme, '%'))
            """)
    List<Client> rechercher(@Param("terme") String terme);
}
