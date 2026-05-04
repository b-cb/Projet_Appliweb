package fr.enseeiht.jeux.tarot;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour le jeu de Tarot.
 *
 * Tous les endpoints sont sous : /api/partie/{id}/tarot/...
 *
 * GET  /api/partie/{id}/tarot/etat            ?utilisateurId=X
 * POST /api/partie/{id}/tarot/encherir        ?utilisateurId=X  { typeBid: "GARDE" }
 * POST /api/partie/{id}/tarot/ecarter         ?utilisateurId=X  { carteIds: [1,2,3,4,5,6] }
 * POST /api/partie/{id}/tarot/jouer           ?utilisateurId=X  { carteId: 42 }
 * POST /api/partie/{id}/tarot/appeler-roi     ?utilisateurId=X  { couleur: "Coeur" }
 * POST /api/partie/{id}/tarot/poignee         ?utilisateurId=X  { type: "SIMPLE" }
 * POST /api/partie/{id}/tarot/petit-sec       ?utilisateurId=X
 */
@RestController
@RequestMapping("/api/partie/{id}/tarot")
public class TarotController {

    private final TarotService tarotService;

    public TarotController(TarotService tarotService) {
        this.tarotService = tarotService;
    }

    // =========================================================
    // ÉTAT DU JEU
    // =========================================================

    /** Retourne l'état complet du jeu pour un utilisateur donné. */
    @GetMapping("/etat")
    public ResponseEntity<EtatTarotDTO> getEtat(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(tarotService.getEtatJeuTarot(id, utilisateurId));
    }

    // =========================================================
    // ENCHÈRES
    // =========================================================

    /**
     * Enchérir ou passer.
     * Body : { "typeBid": "PASSE" | "PETITE" | "GARDE" | "GARDE_SANS" | "GARDE_CONTRE" }
     */
    @PostMapping("/encherir")
    public ResponseEntity<EtatTarotDTO> encherir(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        String typeBid = body.get("typeBid");
        return ResponseEntity.ok(tarotService.enchirirTarot(id, utilisateurId, typeBid));
    }

    // =========================================================
    // CHIEN / ÉCART
    // =========================================================

    /**
     * Écarter des cartes (PETITE/GARDE) ou confirmer la vue du chien (GARDE_SANS).
     * Body : { "carteIds": [1, 2, 3, 4, 5, 6] }  — vide pour GARDE_SANS
     */
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
        return ResponseEntity.ok(tarotService.ecarterCartes(id, utilisateurId, carteIds));
    }

    // =========================================================
    // JEU
    // =========================================================

    /**
     * Jouer une carte.
     * Body : { "carteId": 42 }
     */
    @PostMapping("/jouer")
    public ResponseEntity<EtatTarotDTO> jouer(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        Long carteId = ((Number) body.get("carteId")).longValue();
        return ResponseEntity.ok(tarotService.jouerCarte(id, utilisateurId, carteId));
    }

    // =========================================================
    // 5 JOUEURS — APPEL DU ROI
    // =========================================================

    /**
     * Appeler un Roi (5 joueurs uniquement, phase APPEL_ROI).
     * Body : { "couleur": "Coeur" | "Carreau" | "Trefle" | "Pique" }
     */
    @PostMapping("/appeler-roi")
    public ResponseEntity<EtatTarotDTO> appelerRoi(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        String couleur = body.get("couleur");
        return ResponseEntity.ok(tarotService.appelerRoi(id, utilisateurId, couleur));
    }

    // =========================================================
    // DÉCLARATIONS SPÉCIALES
    // =========================================================

    /**
     * Déclarer une Poignée avant le premier pli.
     * Body : { "type": "SIMPLE" | "DOUBLE" | "TRIPLE" }
     */
    @PostMapping("/poignee")
    public ResponseEntity<EtatTarotDTO> declarePoignee(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {
        String type = body.get("type");
        return ResponseEntity.ok(tarotService.declarePoignee(id, utilisateurId, type));
    }

    /**
     * Signaler un Petit sec (Atout 1 = seul atout en main) → annule et redistribue la donne.
     * Disponible uniquement avant les enchères (statut EN_ENCHERE, phase null).
     */
    @PostMapping("/petit-sec")
    public ResponseEntity<EtatTarotDTO> signalerPetitSec(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(tarotService.signalerPetitSec(id, utilisateurId));
    }
}
