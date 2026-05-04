package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.CategorieOperation;
import sn.rts.caisse.model.TypeOperation;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategorieOperationRepository extends JpaRepository<CategorieOperation, Long> {

    Optional<CategorieOperation> findByCode(String code);

    boolean existsByCode(String code);

    List<CategorieOperation> findByTypeOperationAndActifTrue(TypeOperation type);

    List<CategorieOperation> findByActifTrue();
}
