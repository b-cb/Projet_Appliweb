package fr.enseeiht.jeux.tarot;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Pli;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service gérant la validation des règles de pose de carte au Tarot.
 */
@Service
public class TarotReglesService {

    // Ordre force des atouts (1 = le Petit, 21 = le Monde ; Excuse est hors classement)
    private static final List<String> ORDRE_TRUMP =
            List.of("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21");

    /**
     * Vérifie les règles de suivi au Tarot :
     * 1. Obligation de suivre la couleur demandée
     * 2. Si n'a pas la couleur : obligation de couper avec un atout
     * 3. Si joue atout : obligation de monter
     * 4. L'Excuse peut être jouée à tout moment (valeur "Excuse", couleur "Atout")
     */
    public void verifierReglesTarot(Joueur joueur, Carte carteJouee, Pli pli) {
        // L'Excuse est toujours jouable
        if ("Excuse".equals(carteJouee.getValeur()) && "Atout".equals(carteJouee.getCouleur())) return;

        // Premier à jouer dans ce pli → tout est permis
        if (pli.getCartesJouees().isEmpty()) return;

        List<Carte> main = joueur.getCartesEnMain();
        Carte premiereCarteDuPli = pli.getCartesJouees().get(0);

        // Si la première carte est l'Excuse, la couleur demandée est la 2e carte
        String couleurDemandee = premiereCarteDuPli.getCouleur();
        if ("Excuse".equals(premiereCarteDuPli.getValeur())) {
            if (pli.getCartesJouees().size() < 2) return; // seule l'Excuse jouée → tout permis
            couleurDemandee = pli.getCartesJouees().get(1).getCouleur();
        }

        final String couleurDemandeeFinale = couleurDemandee;
        boolean joueAtout = "Atout".equals(carteJouee.getCouleur());
        boolean couleurDemandeeEstAtout = "Atout".equals(couleurDemandee);

        boolean possedeColoreDemandee = main.stream()
                .filter(c -> !("Excuse".equals(c.getValeur()) && "Atout".equals(c.getCouleur())))
                .anyMatch(c -> c.getCouleur().equals(couleurDemandeeFinale));

        boolean possedeAtout = main.stream()
                .filter(c -> !("Excuse".equals(c.getValeur()) && "Atout".equals(c.getCouleur())))
                .anyMatch(c -> "Atout".equals(c.getCouleur()));

        if (couleurDemandeeEstAtout) {
            // La couleur demandée est l'atout → doit jouer atout et monter
            if (!joueAtout) {
                if (possedeAtout) {
                    throw new BusinessException("Vous devez jouer un atout.");
                }
                // N'a pas d'atout → défausse libre
                return;
            }
            // Joue atout → doit monter si possible
            verifierMonteeAtout(main, carteJouee, pli);

        } else {
            // La couleur demandée est une couleur normale
            if (!carteJouee.getCouleur().equals(couleurDemandee) && !joueAtout) {
                if (possedeColoreDemandee) {
                    throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
                }
                if (possedeAtout) {
                    throw new BusinessException("Vous devez couper avec un atout.");
                }
                // N'a pas la couleur ni d'atout → défausse libre
                return;
            }

            if (!carteJouee.getCouleur().equals(couleurDemandee) && joueAtout) {
                // Ne peut couper qu'en l'absence de la couleur demandée
                if (possedeColoreDemandee) {
                    throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
                }
                // Coupe avec atout → doit monter si possible
                verifierMonteeAtout(main, carteJouee, pli);
            }
        }
    }

    private void verifierMonteeAtout(List<Carte> main, Carte carteJouee, Pli pli) {
        // Trouver le plus fort atout déjà joué dans ce pli
        Optional<Carte> plusFortAtout = pli.getCartesJouees().stream()
                .filter(c -> "Atout".equals(c.getCouleur()) && !("Excuse".equals(c.getValeur())))
                .max(Comparator.comparingInt(c -> ORDRE_TRUMP.indexOf(c.getValeur())));

        if (plusFortAtout.isEmpty()) return; // pas encore d'atout dans le pli

        int rangJoue = ORDRE_TRUMP.indexOf(carteJouee.getValeur());
        int rangMax = ORDRE_TRUMP.indexOf(plusFortAtout.get().getValeur());

        boolean peutMonter = main.stream()
                .filter(c -> "Atout".equals(c.getCouleur()) && !("Excuse".equals(c.getValeur())))
                .anyMatch(c -> ORDRE_TRUMP.indexOf(c.getValeur()) > rangMax);

        if (peutMonter && rangJoue <= rangMax) {
            throw new BusinessException("Vous devez monter à l'atout (jouer un atout plus fort).");
        }
    }
}
