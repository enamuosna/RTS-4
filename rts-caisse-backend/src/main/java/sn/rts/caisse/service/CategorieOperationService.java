package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.CategorieOperationDTO;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.CategorieOperation;
import sn.rts.caisse.model.TypeOperation;
import sn.rts.caisse.repository.CategorieOperationRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategorieOperationService {

    private final CategorieOperationRepository repository;

    public CategorieOperationDTO creer(CategorieOperationDTO dto) {
        if (repository.existsByCode(dto.code())) {
            throw new BusinessException("Code déjà utilisé : " + dto.code());
        }
        CategorieOperation c = CategorieOperation.builder()
                .code(dto.code())
                .libelle(dto.libelle())
                .typeOperation(dto.typeOperation())
                .actif(true)
                .build();
        return CategorieOperationDTO.from(repository.save(c));
    }

    public CategorieOperationDTO modifier(Long id, CategorieOperationDTO dto) {
        CategorieOperation c = trouver(id);
        c.setLibelle(dto.libelle());
        c.setTypeOperation(dto.typeOperation());
        c.setActif(dto.actif());
        return CategorieOperationDTO.from(repository.save(c));
    }

    @Transactional(readOnly = true)
    public List<CategorieOperationDTO> lister(TypeOperation type) {
        List<CategorieOperation> items = (type == null)
                ? repository.findByActifTrue()
                : repository.findByTypeOperationAndActifTrue(type);
        return items.stream().map(CategorieOperationDTO::from).toList();
    }

    public CategorieOperation trouver(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Catégorie", id));
    }
}
