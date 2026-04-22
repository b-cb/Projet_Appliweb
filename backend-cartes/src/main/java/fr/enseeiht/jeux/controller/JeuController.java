package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.EtatJeuDTO;
import fr.enseeiht.jeux.service.JeuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/partie")
public class JeuController {

    private final JeuService jeuService;

    public JeuController(JeuService jeuService) {
        this.jeuService = jeuService;
    }

    /**
     * GET /api/partie/{id}/etat?utilisateurId=X
     * Retourne l'état complet du jeu pour cet utilisateur (sa main, le pli courant, le tour, etc.)
     */
    @GetMapping("/{id}/etat")
    public ResponseEntity<EtatJeuDTO> getEtat(
            @PathVariable Long id,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(jeuService.getEtatJeu(id, utilisateurId));
    }

    /**
     * POST /api/partie/{id}/encherir
     * Body JSON : { "passe": true }
     *          ou { "passe": false, "contrat": 80, "couleur": "Coeur" }
     */
    @PostMapping("/{id}/encherir")
    public ResponseEntity<EtatJeuDTO> encherir(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {

        boolean passe = Boolean.TRUE.equals(body.get("passe"));
        Integer contrat = body.get("contrat") != null ? ((Number) body.get("contrat")).intValue() : null;
        String couleur = (String) body.get("couleur");

        return ResponseEntity.ok(jeuService.encherir(id, utilisateurId, contrat, couleur, passe));
    }

    /**
     * POST /api/partie/{id}/jouer
     * Body JSON : { "carteId": 42 }
     */
    @PostMapping("/{id}/jouer")
    public ResponseEntity<EtatJeuDTO> jouerCarte(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, Object> body) {

        Long carteId = ((Number) body.get("carteId")).longValue();
        return ResponseEntity.ok(jeuService.jouerCarte(id, utilisateurId, carteId));
    }

    /**
     * POST /api/partie/{id}/coincher?utilisateurId=X
     * POST /api/partie/{id}/coincher?utilisateurId=X&surcoinche=true
     */
    @PostMapping("/{id}/coincher")
    public ResponseEntity<EtatJeuDTO> coincher(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestParam(defaultValue = "false") boolean surcoinche) {
        return ResponseEntity.ok(jeuService.coincher(id, utilisateurId, surcoinche));
    }
}
