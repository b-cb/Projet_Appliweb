package fr.enseeiht.jeux.coinche;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur REST pour le jeu de Coinche (Belote).
 *
 * Tous les endpoints sont sous : /api/partie/{id}/...
 *
 * GET  /api/partie/{id}/etat            ?utilisateurId=X
 * POST /api/partie/{id}/encherir        ?utilisateurId=X  { "passe": true }
 *                                                      ou { "passe": false, "contrat": 80, "couleur": "Coeur" }
 * POST /api/partie/{id}/jouer           ?utilisateurId=X  { "carteId": 42 }
 * POST /api/partie/{id}/coincher        ?utilisateurId=X  [&surcoinche=true]
 */
@RestController
@RequestMapping("/api/partie")
public class CoincheController {

    private final CoincheService coincheService;

    public CoincheController(CoincheService coincheService) {
        this.coincheService = coincheService;
    }

    // =========================================================
    // ÉTAT DU JEU
    // =========================================================

    /** Retourne l'état complet du jeu pour un utilisateur donné. */
    @GetMapping("/{id}/etat")
    public ResponseEntity<EtatCoincheDTO> getEtat(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(coincheService.getEtatJeu(id, utilisateurId));
    }

    // =========================================================
    // ENCHÈRES
    // =========================================================

    /**
     * Poser une enchère ou passer.
     * Body : { "passe": true }
     *     ou { "passe": false, "contrat": 80, "couleur": "Coeur" }
     */
    @PostMapping("/{id}/encherir")
    public ResponseEntity<EtatCoincheDTO> encherir(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        boolean passe   = Boolean.TRUE.equals(body.get("passe"));
        Integer contrat = body.get("contrat") != null ? ((Number) body.get("contrat")).intValue() : null;
        String couleur  = (String) body.get("couleur");
        return ResponseEntity.ok(coincheService.encherir(id, utilisateurId, contrat, couleur, passe));
    }

    // =========================================================
    // JEU
    // =========================================================

    /**
     * Jouer une carte.
     * Body : { "carteId": 42 }
     */
    @PostMapping("/{id}/jouer")
    public ResponseEntity<EtatCoincheDTO> jouerCarte(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {
        Long carteId = ((Number) body.get("carteId")).longValue();
        return ResponseEntity.ok(coincheService.jouerCarte(id, utilisateurId, carteId));
    }

    // =========================================================
    // COINCHE / SURCOINCHE
    // =========================================================

    /**
     * Coincher ou surcoincher le contrat adverse.
     * Paramètre optionnel : surcoinche=true pour surcoincher.
     */
    @PostMapping("/{id}/coincher")
    public ResponseEntity<EtatCoincheDTO> coincher(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestParam(defaultValue = "false") boolean surcoinche) {
        return ResponseEntity.ok(coincheService.coincher(id, utilisateurId, surcoinche));
    }
}
