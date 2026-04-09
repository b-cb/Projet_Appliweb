package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.UtilisateurDTO;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.repository.UtilisateurRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepository;

    public UtilisateurController(UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    @GetMapping("/utilisateur/{id}")
    public ResponseEntity<UtilisateurDTO> getUtilisateur(@PathVariable Long id) {
        return utilisateurRepository.findById(id)
                .map(u -> ResponseEntity.ok(UtilisateurDTO.fromEntity(u)))
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur #" + id + " introuvable."));
    }

    @GetMapping("/utilisateurs")
    public List<UtilisateurDTO> getUtilisateurs() {
        return utilisateurRepository.findAll().stream()
                .map(UtilisateurDTO::fromEntity)
                .toList();
    }
}
