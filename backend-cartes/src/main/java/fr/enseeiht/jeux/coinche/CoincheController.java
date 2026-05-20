package fr.enseeiht.jeux.coinche;

import fr.enseeiht.jeux.dto.EtatJeuDTO;
import fr.enseeiht.jeux.service.BotService;
import fr.enseeiht.jeux.service.JeuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Endpoints REST pour la Coinche. Tous sous /api/partie/{id}/
@RestController
@RequestMapping("/api/partie")
public class CoincheController {

    private final JeuService jeuService;
    private final BotService botService;

    public CoincheController(JeuService jeuService, BotService botService) {
        this.jeuService = jeuService;
        this.botService = botService;
    }

    @GetMapping("/{id}/etat")
    public ResponseEntity<EtatJeuDTO> getEtat(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(jeuService.getEtatJeu(id, utilisateurId));
    }

    // Body : { "passe": true } ou { "passe": false, "contrat": 80, "couleur": "Coeur" }
    @PostMapping("/{id}/encherir")
    public ResponseEntity<EtatJeuDTO> encherir(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        boolean passe   = Boolean.TRUE.equals(body.get("passe"));
        Integer contrat = body.get("contrat") != null ? ((Number) body.get("contrat")).intValue() : null;
        String couleur  = (String) body.get("couleur");
        EtatJeuDTO etat = jeuService.encherir(id, utilisateurId, contrat, couleur, passe);
        botService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }

    // Body : { "carteId": 42 }
    @PostMapping("/{id}/jouer")
    public ResponseEntity<EtatJeuDTO> jouerCarte(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        Long carteId = ((Number) body.get("carteId")).longValue();
        EtatJeuDTO etat = jeuService.jouerCarte(id, utilisateurId, carteId);
        botService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }

    // surcoinche=true pour surcoincher
    @PostMapping("/{id}/coincher")
    public ResponseEntity<EtatJeuDTO> coincher(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestParam(defaultValue = "false") boolean surcoinche) {
        EtatJeuDTO etat = jeuService.coincher(id, utilisateurId, surcoinche);
        botService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }
}
