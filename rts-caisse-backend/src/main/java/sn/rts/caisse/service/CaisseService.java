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

    @Transactional(readOnly = true)
    public CaisseDTO obtenir(Long id) {
        return CaisseDTO.from(trouver(id));
    }

    public Caisse trouver(Long id) {
        return caisseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Caisse", id));
    }
}
