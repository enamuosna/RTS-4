package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import sn.rts.caisse.dto.ClientDTO;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Client;
import sn.rts.caisse.repository.ClientRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientDTO creer(ClientDTO dto) {
        Client c = Client.builder()
                .raisonSociale(dto.raisonSociale())
                .identifiantFiscal(dto.identifiantFiscal())
                .telephone(dto.telephone())
                .email(dto.email())
                .adresse(dto.adresse())
                .actif(true)
                .build();
        return ClientDTO.from(clientRepository.save(c));
    }

    public ClientDTO modifier(Long id, ClientDTO dto) {
        Client c = trouver(id);
        c.setRaisonSociale(dto.raisonSociale());
        c.setIdentifiantFiscal(dto.identifiantFiscal());
        c.setTelephone(dto.telephone());
        c.setEmail(dto.email());
        c.setAdresse(dto.adresse());
        c.setActif(dto.actif());
        return ClientDTO.from(clientRepository.save(c));
    }

    @Transactional(readOnly = true)
    public List<ClientDTO> lister(String terme) {
        List<Client> items = StringUtils.hasText(terme)
                ? clientRepository.rechercher(terme)
                : clientRepository.findByActifTrue();
        return items.stream().map(ClientDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientDTO obtenir(Long id) {
        return ClientDTO.from(trouver(id));
    }

    public Client trouver(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Client", id));
    }
}
