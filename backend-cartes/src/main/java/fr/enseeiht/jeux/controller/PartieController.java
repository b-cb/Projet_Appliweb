package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.JoueurDTO;
import fr.enseeiht.jeux.dto.PartieDTO;
import fr.enseeiht.jeux.service.PartieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PartieController {

    private final PartieService partieService;

    public PartieController(PartieService partieService) {
        this.partieService = partieService;
    }

    @PostMapping("/partie/creer")
    public ResponseEntity<PartieDTO> creerPartie() {
        return ResponseEntity.ok(PartieDTO.fromEntity(partieService.creerPartie()));
    }

    @GetMapping("/parties")
    public List<PartieDTO> getParties() {
        return partieService.listerParties().stream()
                .map(PartieDTO::fromEntity)
                .toList();
    }

    @GetMapping("/partie/{id}")
    public ResponseEntity<PartieDTO> getPartie(@PathVariable Long id) {
        return ResponseEntity.ok(PartieDTO.fromEntity(partieService.getPartie(id)));
    }

    @PostMapping("/partie/{partieId}/rejoindre")
    public ResponseEntity<JoueurDTO> rejoindrePartie(
            @PathVariable Long partieId,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(JoueurDTO.fromEntity(
                partieService.rejoindrePartie(partieId, utilisateurId)));
    }

    @PostMapping("/partie/{partieId}/demarrer")
    public ResponseEntity<PartieDTO> demarrerPartie(@PathVariable Long partieId) {
        return ResponseEntity.ok(PartieDTO.fromEntity(partieService.demarrerPartie(partieId)));
    }

    @GetMapping("/partie/{partieId}/joueurs")
    public List<JoueurDTO> getJoueurs(@PathVariable Long partieId) {
        return partieService.getJoueurs(partieId).stream()
                .map(JoueurDTO::fromEntity)
                .toList();
    }
}
