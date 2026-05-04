package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.ClientDTO;
import sn.rts.caisse.service.ClientService;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Annonceurs, partenaires, tiers")
public class ClientController {

    private final ClientService service;

    @GetMapping
    @Operation(summary = "Lister / rechercher les clients (paramètre q optionnel)")
    public ResponseEntity<List<ClientDTO>> lister(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.lister(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping
    public ResponseEntity<ClientDTO> creer(@Valid @RequestBody ClientDTO dto) {
        return ResponseEntity.ok(service.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientDTO> modifier(@PathVariable Long id,
                                              @Valid @RequestBody ClientDTO dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }
}
