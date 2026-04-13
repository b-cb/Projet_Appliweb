package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.EtatJeuTarotDTO;
import fr.enseeiht.jeux.service.TarotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints REST pour le jeu de Tarot.
 *
 * POST /api/partie/{id}/tarot/encherir    { typeBid: "GARDE" }
 * POST /api/partie/{id}/tarot/ecarter     { carteIds: [1,2,3,4,5,6] }
 * POST /api/partie/{id}/tarot/jouer       { carteId: 42 }
 * GET  /api/partie/{id}/tarot/etat        ?utilisateurId=...
 */
@RestController
@RequestMapping("/api/partie/{id}/tarot")
public class TarotController {

    private final TarotService tarotService;

    public TarotController(TarotService tarotService) {
        this.tarotService = tarotService;
    }

    /** GET état du jeu Tarot pour un utilisateur */
    @GetMapping("/etat")
    public ResponseEntity<EtatJeuTarotDTO> getEtat(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(tarotService.getEtatJeuTarot(id, utilisateurId));
    }

    /** POST enchérir (PASSE, PETITE, GARDE, GARDE_SANS, GARDE_CONTRE) */
    @PostMapping("/encherir")
    public ResponseEntity<EtatJeuTarotDTO> encherir(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        String typeBid = body.get("typeBid");
        return ResponseEntity.ok(tarotService.enchirirTarot(id, utilisateurId, typeBid));
    }

    /** POST écarter des cartes (ou confirmer la vue pour GARDE_SANS) */
    @PostMapping("/ecarter")
    public ResponseEntity<EtatJeuTarotDTO> ecarter(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) body.get("carteIds");
        List<Long> carteIds = (rawIds != null)
                ? rawIds.stream().map(Number::longValue).toList()
                : List.of();
        return ResponseEntity.ok(tarotService.ecarterCartes(id, utilisateurId, carteIds));
    }

    /** POST jouer une carte */
    @PostMapping("/jouer")
    public ResponseEntity<EtatJeuTarotDTO> jouer(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        Long carteId = ((Number) body.get("carteId")).longValue();
        return ResponseEntity.ok(tarotService.jouerCarte(id, utilisateurId, carteId));
    }

    /** POST appeler un Roi (5 joueurs uniquement, phase APPEL_ROI) */
    @PostMapping("/appeler-roi")
    public ResponseEntity<EtatJeuTarotDTO> appelerRoi(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        String couleur = body.get("couleur");
        return ResponseEntity.ok(tarotService.appelerRoi(id, utilisateurId, couleur));
    }
}
