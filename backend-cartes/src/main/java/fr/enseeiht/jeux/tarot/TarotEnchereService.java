package fr.enseeiht.jeux.tarot;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Enchere;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.repository.EnchereRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service gérant la phase d'enchères au Tarot (incluant l'appel au Roi et l'écart).
 */
@Service
public class TarotEnchereService {

    private final EnchereRepository enchereRepository;
    private final TarotScoringService scoringService;

    // Hiérarchie des enchères Tarot
    private static final List<String> ENCHERES_ORDRE =
            List.of("PETITE", "GARDE", "GARDE_SANS", "GARDE_CONTRE");

    public TarotEnchereService(EnchereRepository enchereRepository, TarotScoringService scoringService) {
        this.enchereRepository = enchereRepository;
        this.scoringService = scoringService;
    }

    /**
     * Traite l'enchère d'un joueur et modifie l'état de la partie en conséquence.
     * Retourne true si les enchères sont terminées et que le jeu doit démarrer, false sinon.
     */
    public boolean traiterEnchere(Partie partie, Joueur joueurActif, String typeBid) {
        if (!"EN_ENCHERE".equals(partie.getStatut()) || partie.getPhaseJeu() != null) {
            throw new BusinessException("La partie n'est pas en phase d'enchères Tarot.");
        }

        if (typeBid == null || typeBid.isBlank()) {
            throw new BusinessException("typeBid requis.");
        }
        String bid = typeBid.toUpperCase().trim();
        int nbJoueurs = partie.getNbJoueursRequis();

        if ("PASSE".equals(bid)) {
            enregistrerEnchere(partie, joueurActif, true, "PASSE");
            partie.setPassesConsecutives(partie.getPassesConsecutives() + 1);

            if (partie.getPassesConsecutives() >= nbJoueurs) {
                // Tous les joueurs ont passé, la boucle principale gérera le redémarrage.
                return false;
            }

            // Passer au joueur suivant
            partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % nbJoueurs);

            // Si une enchère a été faite et que tous les autres ont passé
            if (partie.getEnchereType() != null) {
                List<Enchere> encheresMaj = enchereRepository.findByPartie_IdOrderByIdAsc(partie.getId());
                return doitTerminerEncheres(encheresMaj, nbJoueurs);
            }
            return false;
        } else {
            // Valider le type d'enchère
            if (!ENCHERES_ORDRE.contains(bid)) {
                throw new BusinessException("Enchère invalide. Valeurs : PETITE, GARDE, GARDE_SANS, GARDE_CONTRE.");
            }

            // Doit surenchérir sur l'enchère actuelle
            String enchereActuelle = partie.getEnchereType();
            if (enchereActuelle != null) {
                int niveauActuel = ENCHERES_ORDRE.indexOf(enchereActuelle);
                int niveauNouveau = ENCHERES_ORDRE.indexOf(bid);
                if (niveauNouveau <= niveauActuel) {
                    throw new BusinessException("Vous devez enchérir plus haut que " + enchereActuelle + ".");
                }
            }

            enregistrerEnchere(partie, joueurActif, false, bid);

            partie.setEnchereType(bid);
            partie.setPreneurId(joueurActif.getId());
            partie.setPassesConsecutives(0);

            // Passer au joueur suivant
            partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % nbJoueurs);

            List<Enchere> toutesEncheres = enchereRepository.findByPartie_IdOrderByIdAsc(partie.getId());
            return "GARDE_CONTRE".equals(bid) || doitTerminerEncheres(toutesEncheres, nbJoueurs);
        }
    }

    private void enregistrerEnchere(Partie partie, Joueur joueur, boolean passe, String bid) {
        Enchere e = new Enchere();
        e.setPartie(partie);
        e.setPreneur(joueur);
        e.setPasse(passe);
        e.setTypeBid(bid);
        enchereRepository.save(e);
    }

    public boolean doitTerminerEncheres(List<Enchere> encheres, int nbJoueurs) {
        if (encheres.isEmpty()) return false;
        int passesDepuisDerniere = 0;
        for (int i = encheres.size() - 1; i >= 0; i--) {
            if (encheres.get(i).isPasse()) passesDepuisDerniere++;
            else break;
        }
        return passesDepuisDerniere >= nbJoueurs - 1;
    }

    /**
     * Initialise le jeu après qu'une enchère ait été gagnée.
     */
    public void initialiserJeuApresEnchere(Partie partie, List<Joueur> joueurs) {
        String enchereType = partie.getEnchereType();
        int mult = scoringService.multiplicateurPourType(enchereType);
        partie.setMultiplicateur(mult);

        // Assigner les équipes : preneur = 1, défenseurs = 2
        for (Joueur j : joueurs) {
            j.setEquipe(j.getId().equals(partie.getPreneurId()) ? 1 : 2);
        }

        // C'est le premier joueur de la donne (rotation) qui ouvre le premier pli, pas le preneur
        int nbJoueursPartie = partie.getNbJoueursRequis();
        partie.setTourJoueurIndex((partie.getDonneActuelle() - 1) % nbJoueursPartie);

        // Déterminer la prochaine phase
        boolean cinqJoueurs = partie.getNbJoueursRequis() == 5;
        if (cinqJoueurs) {
            // En 5j : le preneur doit d'abord appeler un Roi avant d'accéder au chien
            partie.setPhaseJeu("APPEL_ROI");
        } else if ("GARDE_CONTRE".equals(enchereType)) {
            // Chien directement aux défenseurs (invisible), commencer le jeu
            partie.setStatut("EN_JEU");
            partie.setPhaseJeu("JEU");
            partie.setNumPliCourant(1);
        } else if ("GARDE_SANS".equals(enchereType)) {
            // Le preneur voit le chien mais ne l'écarte pas
            partie.setPhaseJeu("CHIEN_VU");
        } else {
            // PETITE ou GARDE : preneur prend le chien et écarte
            partie.setPhaseJeu("CHIEN");
        }
    }

    /**
     * Le preneur appelle un Roi (5 joueurs).
     */
    public void appelerRoi(Partie partie, Joueur preneur, String couleur) {
        if (!"EN_ENCHERE".equals(partie.getStatut()) || !"APPEL_ROI".equals(partie.getPhaseJeu())) {
            throw new BusinessException("La partie n'est pas en phase d'appel du Roi.");
        }

        if (couleur == null || couleur.isBlank()) {
            throw new BusinessException("Couleur requise (Coeur, Carreau, Trefle ou Pique).");
        }
        String[] couleursValides = {"Coeur", "Carreau", "Trefle", "Pique"};
        boolean couleurOk = false;
        for (String c : couleursValides) if (c.equals(couleur)) { couleurOk = true; break; }
        if (!couleurOk) throw new BusinessException("Couleur invalide : " + couleur);

        partie.setAppelRoi(couleur);

        String enchereType = partie.getEnchereType();
        if ("GARDE_CONTRE".equals(enchereType)) {
            partie.setStatut("EN_JEU");
            partie.setPhaseJeu("JEU");
            partie.setNumPliCourant(1);
        } else if ("GARDE_SANS".equals(enchereType)) {
            partie.setPhaseJeu("CHIEN_VU");
        } else {
            partie.setPhaseJeu("CHIEN");
        }
    }

    /**
     * Le preneur écarte des cartes après avoir pris le chien (PETITE/GARDE).
     */
    public void ecarterCartes(Partie partie, Joueur preneur, List<Long> carteIds) {
        String phase = partie.getPhaseJeu();
        if (!"CHIEN".equals(phase) && !"CHIEN_VU".equals(phase)) {
            throw new BusinessException("La partie n'est pas en phase chien/écart.");
        }

        int tailleChien = partie.getChien().size();

        if ("CHIEN".equals(phase)) {
            // Intégrer le chien dans la main du preneur
            if (preneur.getCartesEnMain().isEmpty() || !partie.getChien().isEmpty()) {
                preneur.getCartesEnMain().addAll(partie.getChien());
                partie.getChien().clear();
            }

            // Valider l'écart
            if (carteIds == null || carteIds.size() != tailleChien) {
                throw new BusinessException("Vous devez écarter exactement " + tailleChien + " cartes.");
            }

            List<Carte> main = new ArrayList<>(preneur.getCartesEnMain());
            List<Carte> aEcarter = new ArrayList<>();
            for (Long cid : carteIds) {
                Carte c = main.stream().filter(cc -> cc.getId().equals(cid)).findFirst()
                        .orElseThrow(() -> new BusinessException("Carte #" + cid + " non trouvée dans votre main."));
                aEcarter.add(c);
            }

            // Règles d'écart : pas de bouts, pas de Rois
            for (Carte c : aEcarter) {
                if (scoringService.isBout(c)) {
                    throw new BusinessException("Impossible d'écarter un bout (" + c.getValeur() + ").");
                }
                if ("Roi".equals(c.getValeur())) {
                    throw new BusinessException("Impossible d'écarter un Roi.");
                }
            }

            // Effectuer l'écart
            preneur.getCartesEnMain().removeAll(aEcarter);
            partie.getEcartes().addAll(aEcarter);
        }
        // Pour CHIEN_VU (GARDE_SANS) : pas d'écart, le chien reste pour les défenseurs

        // Passer au jeu
        partie.setStatut("EN_JEU");
        partie.setPhaseJeu("JEU");
        partie.setNumPliCourant(1);
    }
}
