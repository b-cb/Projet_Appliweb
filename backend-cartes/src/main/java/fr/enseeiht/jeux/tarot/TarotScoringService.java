package fr.enseeiht.jeux.tarot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import fr.enseeiht.jeux.modele.Carte;

// Scoring Tarot — points stockés ×2 pour éviter les demi-points (total jeu = 91 pts → 182 ×2)
@Component
public class TarotScoringService {

    // Les trois bouts
    public static final Set<String> BOUTS_VALEURS = Set.of("1", "21", "Excuse");

    // Points x2 par type de carte
    private static final int BOUT_X2     = 9;
    private static final int ROI_X2      = 9;
    private static final int DAME_X2     = 7;
    private static final int CAVALIER_X2 = 5;
    private static final int VALET_X2    = 3;
    private static final int AUTRE_X2    = 1;

    // Seuils de victoire selon le nombre de bouts capturés par le preneur
    private static final int[] SEUILS = {56, 51, 41, 36};

    // Multiplicateurs selon le type d'enchère
    private static final Map<String, Integer> MULTIPLICATEURS = new HashMap<>();
    static {
        MULTIPLICATEURS.put("PETITE",       1);
        MULTIPLICATEURS.put("GARDE",        2);
        MULTIPLICATEURS.put("GARDE_SANS",   4);
        MULTIPLICATEURS.put("GARDE_CONTRE", 6);
    }



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


    public int calculerPointsX2(List<Carte> cartes) {
        int total = 0;
        for (Carte c : cartes) total += carteVautX2(c);
        return total;
    }


    public boolean isBout(Carte c) {
        return "Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur());
    }


    public int compterBouts(List<Carte> cartes) {
        return (int) cartes.stream()
                .filter(c -> "Atout".equals(c.getCouleur()) && BOUTS_VALEURS.contains(c.getValeur()))
                .count();
    }



    public int seuilPourBouts(int bouts) {
        int idx = Math.min(bouts, 3);
        return SEUILS[idx];
    }


    public int multiplicateurPourType(String enchereType) {
        return MULTIPLICATEURS.getOrDefault(enchereType, 1);
    }


    // résultat = ±round((25 + |pts_preneur - seuil|) × multiplicateur), bonus ±10×mult si Petit au bout
    public int calculerScore(int pointsPreneurX2, int bouts, String enchereType,
                             boolean petitAuBoutPreneur, boolean petitAuBoutDefense) {
        int seuil = seuilPourBouts(bouts);
        int mult  = multiplicateurPourType(enchereType);

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



    public int poigneeBonus(String poigneeDeclaree) {
        if (poigneeDeclaree == null) return 0;
        return switch (poigneeDeclaree) {
            case "SIMPLE" -> 20;
            case "DOUBLE" -> 30;
            case "TRIPLE" -> 40;
            default       -> 0;
        };
    }


    public int nbAtouttsPourPoignee(int nbJoueurs, String poigneeType) {
        return switch (nbJoueurs) {
            case 3  -> switch (poigneeType) { case "SIMPLE" -> 13; case "DOUBLE" -> 15; default -> 18; };
            case 5  -> switch (poigneeType) { case "SIMPLE" -> 8;  case "DOUBLE" -> 10; default -> 13; };
            default -> switch (poigneeType) { case "SIMPLE" -> 10; case "DOUBLE" -> 13; default -> 15; };
        };
    }
}
