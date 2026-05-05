package fr.enseeiht.jeux.coinche;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Pli;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service gérant la validation des règles de pose de carte à la Coinche.
 */
@Service
public class CoincheReglesService {

    // Ordre normal d'une couleur (pas d'atout)
    private static final List<String> ORDRE_NORMAL =
            List.of("7","8","9","Valet","Dame","Roi","10","As");

    // Ordre d'une couleur d'atout
    private static final List<String> ORDRE_ATOUT =
            List.of("7","8","Dame","Roi","10","As","9","Valet");

    /**
     * Vérifie les règles de suivi (couleur demandée, obligation de couper, de monter).
     * Gère les trois modes : coinche normale, Sans-atout, Tout-atout.
     */
    public void verifierReglesCouleur(Joueur joueur, Carte carteJouee, Pli pli, String atout, List<Joueur> joueurs) {
        if (pli.getCartesJouees().isEmpty())
            return; // premier à jouer dans ce pli, tout est permis

        Carte premiereCarteJouee = pli.getCartesJouees().get(0);
        String couleurDemandee = premiereCarteJouee.getCouleur();
        List<Carte> main = joueur.getCartesEnMain();
        boolean possedeColoreDemandee = main.stream().anyMatch(c -> c.getCouleur().equals(couleurDemandee));

        // --- Mode Sans-atout : uniquement l'obligation de suivre la couleur ---
        if ("Sans-atout".equals(atout)) {
            if (!carteJouee.getCouleur().equals(couleurDemandee) && possedeColoreDemandee) {
                throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
            }
            return; // pas d'atout → pas de coupe ni de montée inter-couleurs
        }

        // --- Mode Tout-atout : chaque couleur est son propre atout ---
        if ("Tout-atout".equals(atout)) {
            if (!carteJouee.getCouleur().equals(couleurDemandee)) {
                if (possedeColoreDemandee) {
                    throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
                }
                return; // n'a pas la couleur → peut défausser librement
            }
            // Joue la couleur demandée → obligation de monter
            Optional<Carte> plusFort = pli.getCartesJouees().stream()
                    .filter(c -> c.getCouleur().equals(couleurDemandee))
                    .max(Comparator.comparingInt(c -> ORDRE_ATOUT.indexOf(c.getValeur())));
            if (plusFort.isPresent()) {
                boolean peutMonter = main.stream()
                        .filter(c -> c.getCouleur().equals(couleurDemandee))
                        .anyMatch(c -> ORDRE_ATOUT.indexOf(c.getValeur()) > ORDRE_ATOUT
                                .indexOf(plusFort.get().getValeur()));
                if (peutMonter && ORDRE_ATOUT.indexOf(carteJouee.getValeur()) <= ORDRE_ATOUT
                        .indexOf(plusFort.get().getValeur())) {
                    throw new BusinessException("Vous devez monter (jouer plus fort dans la couleur).");
                }
            }
            return;
        }

        // --- Mode coinche normal (atout = couleur) ---
        boolean possedeAtout = main.stream().anyMatch(c -> c.getCouleur().equals(atout));

        // Déterminer l'équipe du joueur courant et du maître en cours
        int equipeJoueur = joueur.getEquipe();
        boolean partenaireEstMaitre = estPartenaireLeGagnantActuel(
                pli.getCartesJouees(), pli.getJoueurOuvreurIndex(), equipeJoueur, joueurs, atout);

        if (!carteJouee.getCouleur().equals(couleurDemandee)) {
            if (possedeColoreDemandee) {
                throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
            }
            // N'a pas la couleur demandée → obligation de couper SAUF si le partenaire est maître
            if (!couleurDemandee.equals(atout) && possedeAtout
                    && !carteJouee.getCouleur().equals(atout)
                    && !partenaireEstMaitre) {
                throw new BusinessException("Vous devez couper avec un atout.");
            }
        }

        // Obligation de monter à l'atout si on joue atout
        if (carteJouee.getCouleur().equals(atout) && couleurDemandee.equals(atout)) {
            Optional<Carte> plusFortAtoutJoue = pli.getCartesJouees().stream()
                    .filter(c -> c.getCouleur().equals(atout))
                    .max(Comparator.comparingInt(c -> ORDRE_ATOUT.indexOf(c.getValeur())));

            if (plusFortAtoutJoue.isPresent()) {
                boolean peutMonter = main.stream()
                        .filter(c -> c.getCouleur().equals(atout))
                        .anyMatch(c -> ORDRE_ATOUT.indexOf(c.getValeur()) > ORDRE_ATOUT
                                .indexOf(plusFortAtoutJoue.get().getValeur()));
                // Obligation de monter SAUF si le partenaire est déjà le plus fort à l'atout
                if (peutMonter && !partenaireEstMaitre
                        && ORDRE_ATOUT.indexOf(carteJouee.getValeur()) <= ORDRE_ATOUT
                                .indexOf(plusFortAtoutJoue.get().getValeur())) {
                    throw new BusinessException("Vous devez monter à l'atout (jouer un atout plus fort).");
                }
            }
        }
    }

    /**
     * Retourne true si c'est le partenaire du joueur courant qui est actuellement maître du pli.
     */
    private boolean estPartenaireLeGagnantActuel(List<Carte> cartesJouees, int ouvreurIndex,
            int equipeJoueur, List<Joueur> joueurs, String atout) {
        if (cartesJouees.isEmpty())
            return false;

        // Calculer le gagnant actuel des cartes déjà jouées
        String couleurOuverte = cartesJouees.get(0).getCouleur();
        int indexGagnant = ouvreurIndex;
        Carte meilleureCarteCouleurOuverte = cartesJouees.get(0);
        Carte meilleureAtout = null;

        for (int i = 0; i < cartesJouees.size(); i++) {
            Carte c = cartesJouees.get(i);
            int idx = (ouvreurIndex + i) % 4;
            if (c.getCouleur().equals(atout)) {
                if (meilleureAtout == null
                        || ORDRE_ATOUT.indexOf(c.getValeur()) > ORDRE_ATOUT.indexOf(meilleureAtout.getValeur())) {
                    meilleureAtout = c;
                    indexGagnant = idx;
                }
            } else if (meilleureAtout == null && c.getCouleur().equals(couleurOuverte)) {
                if (ORDRE_NORMAL.indexOf(c.getValeur()) > ORDRE_NORMAL
                        .indexOf(meilleureCarteCouleurOuverte.getValeur())) {
                    meilleureCarteCouleurOuverte = c;
                    indexGagnant = idx;
                }
            }
        }

        final int gagnantIndex = indexGagnant;
        Joueur gagnantActuel = joueurs.stream()
                .filter(j -> j.getPosition() == gagnantIndex)
                .findFirst().orElse(null);

        if (gagnantActuel == null)
            return false;
        return gagnantActuel.getEquipe() == equipeJoueur;
    }
}
