package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.modele.Carte;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Calcule les scores du Tarot français.
 *
 * Système de demi-points (stockés ×2 pour rester en entiers) :
 *   Bout (Petit/Monde/Excuse) = 4.5 pts → 9 ×2
 *   Roi                       = 4.5 pts → 9 ×2
 *   Dame                      = 3.5 pts → 7 ×2
 *   Cavalier                  = 2.5 pts → 5 ×2
 *   Valet                     = 1.5 pts → 3 ×2
 *   Toute autre carte         = 0.5 pts → 1 ×2
 *
 * Total du jeu = 91 pts → 182 ×2
 */
@Component
public class TarotScoringService {

    // Les trois bouts (oudlers)
    public static final Set<String> BOUTS_VALEURS = Set.of("1", "21", "Excuse");

    // Points ×2 par type de carte
    private static final int BOUT_X2 = 9;
    private static final int ROI_X2 = 9;
    private static final int DAME_X2 = 7;
    private static final int CAVALIER_X2 = 5;
    private static final int VALET_X2 = 3;
    private static final int AUTRE_X2 = 1;

    // Seuils de victoire selon le nombre de bouts capturés par le preneur
    private static final int[] SEUILS = {56, 51, 41, 36}; // index = nb de bouts (0, 1, 2, 3)

    // Multiplicateurs selon le type d'enchère
    private static final java.util.Map<String, Integer> MULTIPLICATEURS = new java.util.HashMap<>();
    static {
        MULTIPLICATEURS.put("PETITE",       1);
        MULTIPLICATEURS.put("GARDE",        2);
        MULTIPLICATEURS.put("GARDE_SANS",   4);
        MULTIPLICATEURS.put("GARDE_CONTRE", 6);
    }

    /**
     * Valeur ×2 d'une carte.
     */
    public int carteVautX2(Carte c) {
        if ("Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur())) return BOUT_X2;
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
     * Nombre de bouts dans une liste de cartes.
     */
    public int compterBouts(List<Carte> cartes) {
        return (int) cartes.stream()
                .filter(c -> "Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur()))
                .count();
    }

    /**
     * Seuil de victoire (en points entiers) selon le nombre de bouts capturés.
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

    /**
     * Calcule le score final de la partie pour le preneur.
     *
     * Formule :
     *   écart = points_preneur - seuil  (en demi-points)
     *   résultat = round((25 + |écart|) × multiplicateur)
     *   Bonus Petit au bout : +10 × multiplicateur (pour l'équipe qui le réalise)
     *
     * @param pointsPreneurX2 points ×2 du preneur (tricks + écartes)
     * @param bouts           nombre de bouts capturés par le preneur
     * @param enchereType     "PETITE"|"GARDE"|"GARDE_SANS"|"GARDE_CONTRE"
     * @param petitAuBout     true si le preneur a réalisé le Petit au bout
     * @return score du preneur (positif = preneur gagne, négatif = preneur perd)
     */
    public int calculerScore(int pointsPreneurX2, int bouts, String enchereType, boolean petitAuBout) {
        int seuil = seuilPourBouts(bouts);
        int mult = multiplicateurPourType(enchereType);

        // Travailler en demi-points ×2 pour éviter les flottants
        // seuil ×2 pour comparer avec pointsPreneurX2
        int ecartX2 = pointsPreneurX2 - (seuil * 2);
        boolean rempli = ecartX2 >= 0;

        // résultat_brut = (25 + |écart|) × mult
        // En ×2 : résultat_brutX2 = (50 + |écartX2|) × mult
        // resultat = résultat_brutX2 / 2 (arrondi à l'entier le plus proche)
        int resultatBrutX2 = (50 + Math.abs(ecartX2)) * mult;
        int resultat = (resultatBrutX2 + 1) / 2; // ceiling (prefer rounding up)

        // Bonus Petit au bout
        if (petitAuBout) {
            resultat += 10 * mult;
        }

        return rempli ? resultat : -resultat;
    }

    /**
     * Retourne true si la carte est un bout (oudler).
     */
    public boolean isBout(Carte c) {
        return "Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur());
    }

    /**
     * Bonus de Poignée (en points entiers, avant multiplication).
     * Si le preneur remplit son contrat, il gagne le bonus ; sinon les défenseurs le gagnent.
     * SIMPLE = 20 pts, DOUBLE = 30 pts, TRIPLE = 40 pts
     *
     * @param poigneeDeclaree "SIMPLE"|"DOUBLE"|"TRIPLE"|null
     * @return bonus en points entiers (0 si null)
     */
    public int poigneeBonus(String poigneeDeclaree) {
        if (poigneeDeclaree == null) return 0;
        return switch (poigneeDeclaree) {
            case "SIMPLE" -> 20;
            case "DOUBLE" -> 30;
            case "TRIPLE" -> 40;
            default -> 0;
        };
    }

    /**
     * Nombre d'atouts (hors Excuse) requis pour déclarer une poignée selon le nombre de joueurs.
     * nbJoueurs=3: Simple=13, Double=15, Triple=18
     * nbJoueurs=4: Simple=10, Double=13, Triple=15
     * nbJoueurs=5: Simple=8,  Double=10, Triple=13
     */
    public int nbAtouttsPourPoignee(int nbJoueurs, String poigneeType) {
        return switch (nbJoueurs) {
            case 3 -> switch (poigneeType) { case "SIMPLE" -> 13; case "DOUBLE" -> 15; default -> 18; };
            case 5 -> switch (poigneeType) { case "SIMPLE" -> 8;  case "DOUBLE" -> 10; default -> 13; };
            default -> switch (poigneeType) { case "SIMPLE" -> 10; case "DOUBLE" -> 13; default -> 15; };
        };
    }
}
