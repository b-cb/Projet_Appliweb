package fr.enseeiht.jeux.tarot;

import fr.enseeiht.jeux.modele.Carte;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Calcule les scores du Tarot français.
 *
 * Système de demi-points (stockés ×2 pour rester en entiers) :
 *   Bout (Petit/Monde/Excuse) = 4,5 pts → 9 ×2
 *   Roi                       = 4,5 pts → 9 ×2
 *   Dame                      = 3,5 pts → 7 ×2
 *   Cavalier                  = 2,5 pts → 5 ×2
 *   Valet                     = 1,5 pts → 3 ×2
 *   Toute autre carte         = 0,5 pt  → 1 ×2
 *
 * Total du jeu = 91 pts → 182 ×2
 */
@Component
public class TarotScoringService {

    // Les trois bouts (oudlers)
    public static final Set<String> BOUTS_VALEURS = Set.of("1", "21", "Excuse");

    // Points ×2 par type de carte
    private static final int BOUT_X2     = 9;
    private static final int ROI_X2      = 9;
    private static final int DAME_X2     = 7;
    private static final int CAVALIER_X2 = 5;
    private static final int VALET_X2    = 3;
    private static final int AUTRE_X2    = 1;

    // Seuils de victoire selon le nombre de bouts capturés par le preneur
    // index 0 = 0 bout, index 1 = 1 bout, etc.
    private static final int[] SEUILS = {56, 51, 41, 36};

    // Multiplicateurs selon le type d'enchère
    private static final Map<String, Integer> MULTIPLICATEURS = new HashMap<>();
    static {
        MULTIPLICATEURS.put("PETITE",       1);
        MULTIPLICATEURS.put("GARDE",        2);
        MULTIPLICATEURS.put("GARDE_SANS",   4);
        MULTIPLICATEURS.put("GARDE_CONTRE", 6);
    }

    // =========================================================
    // VALEUR DES CARTES
    // =========================================================

    /**
     * Valeur ×2 d'une carte (pour éviter les flottants).
     */
    public int carteVautX2(Carte c) {
        if ("Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur())) {
            return BOUT_X2;
        }
        return switch (c.getValeur()) {
            case "Roi"      -> ROI_X2;
            case "Dame"     -> DAME_X2;
            case "Cavalier" -> CAVALIER_X2;
            case "Valet"    -> VALET_X2;
            default         -> AUTRE_X2;
        };
    }

    /**
     * Somme des valeurs ×2 d'une liste de cartes.
     */
    public int calculerPointsX2(List<Carte> cartes) {
        int total = 0;
        for (Carte c : cartes) total += carteVautX2(c);
        return total;
    }

    /**
     * Retourne true si la carte est un bout (oudler).
     */
    public boolean isBout(Carte c) {
        return "Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur());
    }

    /**
     * Nombre de bouts dans une liste de cartes.
     */
    public int compterBouts(List<Carte> cartes) {
        return (int) cartes.stream()
                .filter(c -> "Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur()))
                .count();
    }

    // =========================================================
    // SEUILS ET MULTIPLICATEURS
    // =========================================================

    /**
     * Seuil de victoire (en points entiers) selon le nombre de bouts capturés.
     * 0 bout → 56 pts, 1 → 51, 2 → 41, 3 → 36
     */
    public int seuilPourBouts(int bouts) {
        int idx = Math.min(bouts, 3);
        return SEUILS[idx];
    }

    /**
     * Multiplicateur selon le type d'enchère.
     */
    public int multiplicateurPourType(String enchereType) {
        return MULTIPLICATEURS.getOrDefault(enchereType, 1);
    }

    // =========================================================
    // CALCUL DU SCORE FINAL
    // =========================================================

    /**
     * Calcule le score final de la partie pour le preneur.
     *
     * Formule :
     *   écart = points_preneur - seuil  (en demi-points)
     *   résultat = round((25 + |écart|) × multiplicateur)
     *   Bonus Petit au bout : +10 × multiplicateur (pour l'équipe qui réalise)
     *
     * @param pointsPreneurX2    points ×2 du preneur (plis + écartes)
     * @param bouts              nombre de bouts capturés par le preneur
     * @param enchereType        "PETITE" | "GARDE" | "GARDE_SANS" | "GARDE_CONTRE"
     * @param petitAuBoutPreneur true si le preneur a réalisé le Petit au bout
     * @param petitAuBoutDefense true si la défense a réalisé le Petit au bout
     * @return score du preneur (positif = victoire, négatif = défaite)
     */
    public int calculerScore(int pointsPreneurX2, int bouts, String enchereType,
                             boolean petitAuBoutPreneur, boolean petitAuBoutDefense) {
        int seuil = seuilPourBouts(bouts);
        int mult  = multiplicateurPourType(enchereType);

        // Travailler en ×2 pour éviter les flottants
        int ecartX2       = pointsPreneurX2 - (seuil * 2);
        boolean rempli    = ecartX2 >= 0;
        int resultatBrutX2 = (50 + Math.abs(ecartX2)) * mult;
        int resultat      = (resultatBrutX2 + 1) / 2; // arrondi supérieur

        if (rempli) {
            if (petitAuBoutPreneur) resultat += 10 * mult;
            if (petitAuBoutDefense) resultat -= 10 * mult;
            return resultat;
        } else {
            if (petitAuBoutPreneur) resultat -= 10 * mult;
            if (petitAuBoutDefense) resultat += 10 * mult;
            return -resultat;
        }
    }

    // =========================================================
    // POIGNÉE
    // =========================================================

    /**
     * Bonus de Poignée en points entiers.
     * Le bonus va au camp gagnant, quel que soit le déclarant.
     * SIMPLE = 20 pts, DOUBLE = 30 pts, TRIPLE = 40 pts
     */
    public int poigneeBonus(String poigneeDeclaree) {
        if (poigneeDeclaree == null) return 0;
        return switch (poigneeDeclaree) {
            case "SIMPLE" -> 20;
            case "DOUBLE" -> 30;
            case "TRIPLE" -> 40;
            default       -> 0;
        };
    }

    /**
     * Nombre d'atouts (hors Excuse) requis pour déclarer une poignée.
     *
     * 3 joueurs : Simple=13, Double=15, Triple=18
     * 4 joueurs : Simple=10, Double=13, Triple=15
     * 5 joueurs : Simple=8,  Double=10, Triple=13
     */
    public int nbAtouttsPourPoignee(int nbJoueurs, String poigneeType) {
        return switch (nbJoueurs) {
            case 3  -> switch (poigneeType) { case "SIMPLE" -> 13; case "DOUBLE" -> 15; default -> 18; };
            case 5  -> switch (poigneeType) { case "SIMPLE" -> 8;  case "DOUBLE" -> 10; default -> 13; };
            default -> switch (poigneeType) { case "SIMPLE" -> 10; case "DOUBLE" -> 13; default -> 15; };
        };
    }
}
