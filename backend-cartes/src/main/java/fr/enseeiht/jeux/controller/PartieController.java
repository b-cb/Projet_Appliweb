package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.JoueurDTO;
import fr.enseeiht.jeux.dto.PartieDTO;
import fr.enseeiht.jeux.service.PartieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PartieController {

    private final PartieService partieService;

    public PartieController(PartieService partieService) {
        this.partieService = partieService;
    }

    @PostMapping("/partie/creer")
    public ResponseEntity<PartieDTO> creerPartie(
            @RequestParam(defaultValue = "false") boolean avecBots,
            @RequestParam(required = false) Long utilisateurId,
            @RequestBody(required = false) Map<String, Object> body) {
        String typeJeu = body != null ? (String) body.getOrDefault("typeJeu", "COINCHE") : "COINCHE";
        int nbJoueurs = body != null && body.containsKey("nbJoueurs")
                ? ((Number) body.get("nbJoueurs")).intValue() : 4;

        if (avecBots && utilisateurId != null) {
            if ("TAROT".equals(typeJeu)) {
                return ResponseEntity.ok(PartieDTO.fromEntity(
                        partieService.creerEtDemarrerTarotAvecBots(utilisateurId, nbJoueurs)));
            }
            return ResponseEntity.ok(PartieDTO.fromEntity(
                    partieService.creerEtDemarrerAvecBots(utilisateurId)));
        }
        return ResponseEntity.ok(PartieDTO.fromEntity(partieService.creerPartie(typeJeu, nbJoueurs)));
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

    @DeleteMapping("/partie/{partieId}")
    public ResponseEntity<Void> supprimerPartie(
            @PathVariable Long partieId,
            @RequestParam Long utilisateurId) {
        partieService.supprimerPartie(partieId, utilisateurId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/partie/{partieId}/joueurs")
    public List<JoueurDTO> getJoueurs(@PathVariable Long partieId) {
        return partieService.getJoueurs(partieId).stream()
                .map(JoueurDTO::fromEntity)
                .toList();
    }

    @PostMapping("/partie/{partieId}/remplir-bots")
    public ResponseEntity<PartieDTO> remplirAvecBots(
            @PathVariable Long partieId,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(PartieDTO.fromEntity(
                partieService.remplirAvecBots(partieId, utilisateurId)));
    }

    @DeleteMapping("/partie/{partieId}/retirer-bots")
    public ResponseEntity<PartieDTO> retirerBots(
            @PathVariable Long partieId,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(PartieDTO.fromEntity(
                partieService.retirerBots(partieId, utilisateurId)));
    }
}
