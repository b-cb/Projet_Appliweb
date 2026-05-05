package fr.enseeiht.jeux.coinche;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.Enchere;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.repository.EnchereRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service gérant la phase d'enchères à la Coinche.
 */
@Service
public class CoincheEnchereService {

    private final EnchereRepository enchereRepository;

    public CoincheEnchereService(EnchereRepository enchereRepository) {
        this.enchereRepository = enchereRepository;
    }

    /**
     * Traite l'enchère d'un joueur et modifie l'état de la partie en conséquence.
     * Retourne true si les enchères sont terminées et que le jeu doit démarrer, false sinon.
     */
    public boolean traiterEnchere(Partie partie, Joueur joueurActif, Integer contrat, String couleur, boolean passe, List<Joueur> joueurs) {
        if (!"EN_ENCHERE".equals(partie.getStatut())) {
            throw new BusinessException("La partie n'est pas en phase d'enchères.");
        }

        // --- Cas : la donne est coinchée (enchères classiques interdites) ---
        if (partie.getCoinche() == 1) {
            if (!passe) {
                throw new BusinessException("La donne est coinchée : vous ne pouvez que passer ou surcoincher.");
            }
            // Seule l'équipe du preneur peut parler après une coinche
            Joueur preneur = joueurs.stream()
                    .filter(j -> j.getId().equals(partie.getPreneurId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Preneur introuvable."));
            if (joueurActif.getEquipe() != preneur.getEquipe()) {
                throw new BusinessException("Après une coinche, seule l'équipe du preneur peut passer ou surcoincher.");
            }

            // Enregistrer le passe
            enregistrerEnchere(partie, joueurActif, true, 0, null);
            partie.setPassesConsecutives(partie.getPassesConsecutives() + 1);

            // Après 2 passes de l'équipe preneure → fin des enchères
            if (partie.getPassesConsecutives() >= 2) {
                return true;
            } else {
                // Passer à l'autre joueur de l'équipe du preneur
                partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 2) % 4);
                return false;
            }
        }

        // --- Enchères normales (coinche == 0) ---
        if (passe) {
            enregistrerEnchere(partie, joueurActif, true, 0, null);
            partie.setPassesConsecutives(partie.getPassesConsecutives() + 1);

            // Si 4 passes consécutives sans contrat → relancer la donne (reset)
            if (partie.getPassesConsecutives() >= 4) {
                throw new BusinessException("Quatre passes consécutives : la donne est annulée. Redémarrez la partie.");
            }
        } else {
            // Valider l'enchère
            if (contrat == null || contrat < 80 || contrat > 160 || contrat % 10 != 0) {
                throw new BusinessException("Contrat invalide. Valeur entre 80 et 160, multiple de 10.");
            }
            if (couleur == null || couleur.isBlank()) {
                throw new BusinessException("La couleur de l'atout est obligatoire.");
            }
            String[] couleursValides = { "Coeur", "Carreau", "Trefle", "Pique", "Sans-atout", "Tout-atout" };
            if (Arrays.stream(couleursValides).noneMatch(c -> c.equalsIgnoreCase(couleur))) {
                throw new BusinessException(
                        "Couleur invalide. Valeurs acceptées : Coeur, Carreau, Trefle, Pique, Sans-atout, Tout-atout.");
            }

            // Vérifier que le contrat surenchérit sur le précédent
            if (partie.getContratValeur() > 0 && contrat <= partie.getContratValeur()) {
                throw new BusinessException(
                        "Le contrat doit être supérieur au contrat précédent (" + partie.getContratValeur() + ").");
            }

            String atoutClean = capitalise(couleur);
            enregistrerEnchere(partie, joueurActif, false, contrat, atoutClean);

            partie.setContratValeur(contrat);
            partie.setContratCouleur(atoutClean);
            partie.setPreneurId(joueurActif.getId());
            partie.setPassesConsecutives(0);
        }

        // Passer au joueur suivant
        partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % 4);

        // Vérifier si on doit passer en EN_JEU :
        // Condition : il y a un contrat ET les 3 joueurs suivants ont tous passé
        List<Enchere> toutesEncheres = enchereRepository.findByPartie_IdOrderByIdAsc(partie.getId());
        return partie.getContratValeur() > 0 && doitCommencerJeu(toutesEncheres);
    }

    /**
     * Traite l'action de coinche ou surcoinche.
     * Retourne true si les enchères sont terminées suite à une surcoinche.
     */
    public boolean traiterCoinche(Partie partie, Joueur monJoueur, boolean surcoinche, List<Joueur> joueurs) {
        if (!"EN_ENCHERE".equals(partie.getStatut())) {
            throw new BusinessException("La coinche n'est possible que pendant les enchères.");
        }
        if (partie.getContratValeur() <= 0) {
            throw new BusinessException("Il n'y a pas encore de contrat à coincher.");
        }

        // Trouver le preneur et son équipe
        Joueur preneur = joueurs.stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Preneur introuvable."));
        int equipePreneur = preneur.getEquipe();
        int equipeJoueur = monJoueur.getEquipe();

        if (surcoinche) {
            // --- SURCOINCHE ---
            if (partie.getCoinche() != 1) {
                throw new BusinessException("La surcoinche n'est possible qu'après une coinche.");
            }
            if (equipeJoueur != equipePreneur) {
                throw new BusinessException("Seule l'équipe du preneur peut surcoincher.");
            }
            partie.setCoinche(2);
            partie.setEnchereType("SURCOINCHE");

            // La surcoinche met fin immédiatement aux enchères
            return true;
        } else {
            // --- COINCHE ---
            if (partie.getCoinche() != 0) {
                throw new BusinessException("La donne est déjà coinchée ou surcoinchée.");
            }
            if (equipeJoueur == equipePreneur) {
                throw new BusinessException("Seuls les adversaires du preneur peuvent coincher.");
            }
            partie.setCoinche(1);
            partie.setEnchereType("COINCHE");
            // Remettre le compteur de passes à 0 pour compter les 2 passes de l'équipe preneure
            partie.setPassesConsecutives(0);

            // La parole revient au preneur lui-même
            partie.setTourJoueurIndex(preneur.getPosition());
            return false;
        }
    }

    private void enregistrerEnchere(Partie partie, Joueur joueur, boolean passe, Integer contrat, String couleur) {
        Enchere e = new Enchere();
        e.setPartie(partie);
        e.setPreneur(joueur);
        e.setPasse(passe);
        e.setContrat(contrat);
        e.setCouleur(couleur);
        enchereRepository.save(e);
    }

    public void demarrerJeuDepuisEnchere(Partie partie) {
        partie.setStatut("EN_JEU");
        partie.setAtout(partie.getContratCouleur());
        partie.setNumPliCourant(1);
        // Le premier joueur de la donne (rotation) ouvre le premier pli
        partie.setTourJoueurIndex((partie.getDonneActuelle() - 1) % 4);
    }

    private boolean doitCommencerJeu(List<Enchere> encheres) {
        if (encheres.size() < 4)
            return false;
        int passesDepuisDernierContrat = 0;
        for (int i = encheres.size() - 1; i >= 0; i--) {
            if (encheres.get(i).isPasse()) {
                passesDepuisDernierContrat++;
            } else {
                break;
            }
        }
        return passesDepuisDernierContrat >= 3;
    }

    private String capitalise(String s) {
        if (s == null || s.isBlank())
            return s;
        if (s.equalsIgnoreCase("Sans-atout"))
            return "Sans-atout";
        if (s.equalsIgnoreCase("Tout-atout"))
            return "Tout-atout";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
