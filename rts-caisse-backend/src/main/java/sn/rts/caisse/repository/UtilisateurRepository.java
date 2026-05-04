package sn.rts.caisse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sn.rts.caisse.model.Role;
import sn.rts.caisse.model.Utilisateur;

import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByLogin(String login);

    Optional<Utilisateur> findByMatricule(String matricule);

    boolean existsByLogin(String login);

    boolean existsByMatricule(String matricule);

    List<Utilisateur> findByRole(Role role);

    List<Utilisateur> findByActifTrue();
}
