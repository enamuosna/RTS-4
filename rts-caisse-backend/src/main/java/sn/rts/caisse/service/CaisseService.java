package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.CaisseDTO;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Caisse;
import sn.rts.caisse.model.StatutCaisse;
import sn.rts.caisse.model.Utilisateur;
import sn.rts.caisse.repository.CaisseRepository;
import sn.rts.caisse.repository.UtilisateurRepository;
import sn.rts.caisse.model.Utilisateur;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CaisseService {

    private final CaisseRepository caisseRepository;
    private final UtilisateurRepository utilisateurRepository;

    public CaisseDTO creer(CaisseDTO dto) {
        if (caisseRepository.existsByCode(dto.code())) {
            throw new BusinessException("Code de caisse déjà utilisé : " + dto.code());
        }
        Caisse caisse = Caisse.builder()
                .code(dto.code())
                .libelle(dto.libelle())
                .emplacement(dto.emplacement())
                .statut(StatutCaisse.FERMEE)
                .soldeCourant(BigDecimal.ZERO)
                .build();
        return CaisseDTO.from(caisseRepository.save(caisse));
    }

    public CaisseDTO modifier(Long id, CaisseDTO dto) {
        Caisse caisse = trouver(id);
        caisse.setLibelle(dto.libelle());
        caisse.setEmplacement(dto.emplacement());
        return CaisseDTO.from(caisseRepository.save(caisse));
    }

    public CaisseDTO affecterCaissier(Long caisseId, Long caissierId) {
        Caisse caisse = trouver(caisseId);
        Utilisateur caissier = utilisateurRepository.findById(caissierId)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", caissierId));
        caisse.setCaissier(caissier);
        return CaisseDTO.from(caisseRepository.save(caisse));
    }

    public CaisseDTO suspendre(Long id, boolean suspendre) {
        Caisse caisse = trouver(id);
        caisse.setStatut(suspendre ? StatutCaisse.SUSPENDUE : StatutCaisse.FERMEE);
        return CaisseDTO.from(caisseRepository.save(caisse));
    }

    @Transactional(readOnly = true)
    public List<CaisseDTO> lister() {
        return caisseRepository.findAll().stream()
                .map(CaisseDTO::from)
                .toList();
    }

    /**
     * Renvoie uniquement les caisses affectées au caissier dont le login
     * est passé en paramètre. Utilisé pour l'écran de sélection de caisse
     * du guichet : un caissier ne doit voir que sa propre caisse.
     *
     * <p><b>Comportement :</b></p>
     * <ul>
     *   <li>Si le caissier n'a aucune caisse affectée → liste vide.</li>
     *   <li>Si le caissier en a plusieurs → toutes sont retournées.</li>
     *   <li>Si le login est introuvable → exception
     *       {@link ResourceNotFoundException}.</li>
     * </ul>
     *
     * @param loginCaissier login extrait du JWT
     *                      (ex. {@code authentication.getName()})
     * @return caisses affectées à ce caissier
     */
    @Transactional(readOnly = true)
    public List<CaisseDTO> listerPourCaissier(String loginCaissier) {
        Utilisateur caissier = utilisateurRepository.findByLogin(loginCaissier)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "Utilisateur", loginCaissier));

        return caisseRepository.findByCaissierId(caissier.getId()).stream()
                .map(CaisseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CaisseDTO obtenir(Long id) {
        return CaisseDTO.from(trouver(id));
    }

    public Caisse trouver(Long id) {
        return caisseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Caisse", id));
    }
}
