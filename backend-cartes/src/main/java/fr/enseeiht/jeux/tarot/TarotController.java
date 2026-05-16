package fr.enseeiht.jeux.tarot;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Endpoints REST pour le Tarot. Tous sous /api/partie/{id}/tarot/
@RestController
@RequestMapping("/api/partie/{id}/tarot")
public class TarotController {

    private final TarotService    tarotService;
    private final TarotBotService tarotBotService;

    public TarotController(TarotService tarotService, TarotBotService tarotBotService) {
        this.tarotService    = tarotService;
        this.tarotBotService = tarotBotService;
    }

    @GetMapping("/etat")
    public ResponseEntity<EtatTarotDTO> getEtat(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(tarotService.getEtatJeuTarot(id, utilisateurId));
    }

    // Body : { "typeBid": "PASSE" | "PETITE" | "GARDE" | "GARDE_SANS" | "GARDE_CONTRE" }
    @PostMapping("/encherir")
    public ResponseEntity<EtatTarotDTO> encherir(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        EtatTarotDTO etat = tarotService.encherirTarot(id, utilisateurId, body.get("typeBid"));
        tarotBotService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }

    // Body : { "carteIds": [1, 2, 3, 4, 5, 6] } — vide pour GARDE_SANS
    @PostMapping("/ecarter")
    public ResponseEntity<EtatTarotDTO> ecarter(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("carteIds");
        List<Long> carteIds = (rawIds != null)
                ? rawIds.stream().map(Number::longValue).toList()
                : List.of();
        EtatTarotDTO etat = tarotService.ecarterCartes(id, utilisateurId, carteIds);
        tarotBotService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }

    // Body : { "carteId": 42 }
    @PostMapping("/jouer")
    public ResponseEntity<EtatTarotDTO> jouer(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        Long carteId = ((Number) body.get("carteId")).longValue();
        EtatTarotDTO etat = tarotService.jouerCarte(id, utilisateurId, carteId);
        tarotBotService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }

    // Body : { "couleur": "Coeur" | "Carreau" | "Trefle" | "Pique" }
    @PostMapping("/appeler-roi")
    public ResponseEntity<EtatTarotDTO> appelerRoi(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        EtatTarotDTO etat = tarotService.appelerRoi(id, utilisateurId, body.get("couleur"));
        tarotBotService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }

    // Body : { "type": "SIMPLE" | "DOUBLE" | "TRIPLE" }
    @PostMapping("/poignee")
    public ResponseEntity<EtatTarotDTO> declarePoignee(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(tarotService.declarePoignee(id, utilisateurId, body.get("type")));
    }

    @PostMapping("/petit-sec")
    public ResponseEntity<EtatTarotDTO> signalerPetitSec(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        EtatTarotDTO etat = tarotService.signalerPetitSec(id, utilisateurId);
        tarotBotService.jouerSiTourDuBot(id);
        return ResponseEntity.ok(etat);
    }
}
