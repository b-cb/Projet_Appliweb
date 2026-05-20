package fr.enseeiht.jeux.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.enseeiht.jeux.dto.CarteDTO;
import fr.enseeiht.jeux.dto.EnchereDTO;
import fr.enseeiht.jeux.dto.EtatJeuDTO;
import fr.enseeiht.jeux.dto.EvenementJeuDTO;
import fr.enseeiht.jeux.dto.ResultatDTO;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Enchere;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.modele.Pli;
import fr.enseeiht.jeux.modele.Utilisateur;
import fr.enseeiht.jeux.repository.CarteRepository;
import fr.enseeiht.jeux.repository.EnchereRepository;
import fr.enseeiht.jeux.repository.JoueurRepository;
import fr.enseeiht.jeux.repository.PartieRepository;
import fr.enseeiht.jeux.repository.PliRepository;
import fr.enseeiht.jeux.repository.UtilisateurRepository;

@Service
@Transactional
public class JeuService {

    private static final Map<String, Integer> POINTS_ATOUT = new LinkedHashMap<>();
    private static final Map<String, Integer> POINTS_NORMAL = new LinkedHashMap<>();
    private static final Map<String, Integer> POINTS_SANS_ATOUT = new LinkedHashMap<>();
    private static final Map<String, Integer> POINTS_TOUT_ATOUT = new LinkedHashMap<>();
    private static final List<String> ORDRE_ATOUT = List.of("7", "8", "Dame", "Roi", "10", "As", "9", "Valet");
    private static final List<String> ORDRE_NORMAL = List.of("7", "8", "9", "Valet", "Dame", "Roi", "10", "As");

    static {
        POINTS_ATOUT.put("Valet", 20);
        POINTS_ATOUT.put("9", 14);
        POINTS_ATOUT.put("As", 11);
        POINTS_ATOUT.put("10", 10);
        POINTS_ATOUT.put("Roi", 4);
        POINTS_ATOUT.put("Dame", 3);
        POINTS_ATOUT.put("8", 0);
        POINTS_ATOUT.put("7", 0);

        POINTS_NORMAL.put("As", 11);
        POINTS_NORMAL.put("10", 10);
        POINTS_NORMAL.put("Roi", 4);
        POINTS_NORMAL.put("Dame", 3);
        POINTS_NORMAL.put("Valet", 2);
        POINTS_NORMAL.put("9", 0);
        POINTS_NORMAL.put("8", 0);
        POINTS_NORMAL.put("7", 0);

        // En Sans-atout l'As vaut 19
        POINTS_SANS_ATOUT.put("As", 19);
        POINTS_SANS_ATOUT.put("10", 10);
        POINTS_SANS_ATOUT.put("Roi", 4);
        POINTS_SANS_ATOUT.put("Dame", 3);
        POINTS_SANS_ATOUT.put("Valet", 2);
        POINTS_SANS_ATOUT.put("9", 0);
        POINTS_SANS_ATOUT.put("8", 0);
        POINTS_SANS_ATOUT.put("7", 0);

        // En Tout-atout échelle réduite
        POINTS_TOUT_ATOUT.put("Valet", 14);
        POINTS_TOUT_ATOUT.put("9", 9);
        POINTS_TOUT_ATOUT.put("As", 6);
        POINTS_TOUT_ATOUT.put("10", 5);
        POINTS_TOUT_ATOUT.put("Roi", 3);
        POINTS_TOUT_ATOUT.put("Dame", 1);
        POINTS_TOUT_ATOUT.put("8", 0);
        POINTS_TOUT_ATOUT.put("7", 0);
    }

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EnchereRepository enchereRepository;
    private final PliRepository pliRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CarteRepository carteRepository;

    public JeuService(PartieRepository partieRepository,
            JoueurRepository joueurRepository,
            UtilisateurRepository utilisateurRepository,
            EnchereRepository enchereRepository,
            PliRepository pliRepository,
            SimpMessagingTemplate messagingTemplate,
            CarteRepository carteRepository) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
        this.messagingTemplate = messagingTemplate;
        this.carteRepository = carteRepository;
    }


    private void pushEtatATous(Long partieId, List<Joueur> joueurs, EvenementJeuDTO.Type type) {
        for (Joueur j : joueurs) {
            EtatJeuDTO etat = getEtatJeu(partieId, j.getUtilisateur().getId());
            // Topic personnel par joueur pour que chacun reçoive uniquement sa propre main
            messagingTemplate.convertAndSend(
                    "/topic/partie/" + partieId + "/joueur/" + j.getUtilisateur().getId(),
                    EvenementJeuDTO.of(type, etat));
        }
    }


    public EtatJeuDTO getEtatJeu(Long partieId, Long utilisateurId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);

        Joueur monJoueur = joueurs.stream()
                .filter(j -> j.getUtilisateur().getId().equals(utilisateurId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        EtatJeuDTO dto = new EtatJeuDTO();
        dto.setPartieId(partieId);
        dto.setStatut(partie.getStatut());
        dto.setAtout(partie.getAtout());
        dto.setContratValeur(partie.getContratValeur());
        dto.setContratCouleur(partie.getContratCouleur());
        dto.setScoreA(partie.getScoreA());
        dto.setScoreB(partie.getScoreB());
        dto.setNumPliCourant(partie.getNumPliCourant());
        dto.setMonJoueurId(monJoueur.getId());
        dto.setMonEquipe(monJoueur.getEquipe());

        dto.setMaMain(monJoueur.getCartesEnMain().stream()
                .map(CarteDTO::fromEntity)
                .collect(Collectors.toList()));

        if (joueurs.size() == 4) {
            Joueur joueurTour = joueurs.stream()
                    .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                    .findFirst().orElse(null);
            if (joueurTour != null) {
                dto.setTourJoueurId(joueurTour.getId());
                dto.setTourPseudo(joueurTour.getUtilisateur().getPseudo());
            }
        }

        Optional<Pli> pliOpt = pliRepository.findByPartie_IdAndNumTour(partieId, partie.getNumPliCourant());
        if (pliOpt.isPresent()) {
            Pli pli = pliOpt.get();
            List<Carte> cartesJouees = pli.getCartesJouees();
            int ouvreurIndex = pli.getJoueurOuvreurIndex();
            List<EtatJeuDTO.CartePliDTO> pliCourant = new ArrayList<>();
            for (int i = 0; i < cartesJouees.size(); i++) {
                int idx = (ouvreurIndex + i) % 4;
                Joueur j = joueurs.stream()
                        .filter(jj -> jj.getPosition() == idx)
                        .findFirst().orElse(null);
                if (j != null) {
                    pliCourant.add(new EtatJeuDTO.CartePliDTO(
                            CarteDTO.fromEntity(cartesJouees.get(i)),
                            j.getUtilisateur().getPseudo(),
                            j.getEquipe()));
                }
            }
            dto.setPliCourant(pliCourant);
        } else {
            dto.setPliCourant(new ArrayList<>());
        }

        if (partie.getNumPliCourant() > 1 || "TERMINEE".equals(partie.getStatut())) {
            int numDernier = "TERMINEE".equals(partie.getStatut())
                    ? partie.getNumPliCourant()
                    : partie.getNumPliCourant() - 1;
            Optional<Pli> dernierOpt = pliRepository.findByPartie_IdAndNumTour(partieId, numDernier);
            if (dernierOpt.isPresent()) {
                Pli dp = dernierOpt.get();
                List<Carte> dpCartes = dp.getCartesJouees();
                int dpOuvreur = dp.getJoueurOuvreurIndex();
                List<EtatJeuDTO.CartePliDTO> dernierPliList = new ArrayList<>();
                for (int i = 0; i < dpCartes.size(); i++) {
                    int idx = (dpOuvreur + i) % 4;
                    final int idxFinal = idx;
                    Joueur j = joueurs.stream().filter(jj -> jj.getPosition() == idxFinal).findFirst().orElse(null);
                    if (j != null) {
                        dernierPliList.add(new EtatJeuDTO.CartePliDTO(
                                CarteDTO.fromEntity(dpCartes.get(i)),
                                j.getUtilisateur().getPseudo(),
                                j.getEquipe()));
                    }
                }
                dto.setDernierPli(dernierPliList);
                dto.setDernierPliGagnantEquipe(dp.getGagnantEquipe());
            }
        }

        dto.setEncheres(enchereRepository.findByPartie_IdOrderByIdAsc(partieId).stream()
                .map(EnchereDTO::fromEntity)
                .collect(Collectors.toList()));

        dto.setDonneActuelle(partie.getDonneActuelle());
        dto.setMaxDonnes(partie.getMaxDonnes());
        dto.setMaxPoints(partie.getMaxPoints());
        dto.setScoreGlobalA(partie.getScoreGlobalA());
        dto.setScoreGlobalB(partie.getScoreGlobalB());

        dto.setCoinche(partie.getCoinche());
        if (partie.getPreneurId() != null) {
            dto.setPreneurId(partie.getPreneurId());
            joueurs.stream()
                    .filter(j -> j.getId().equals(partie.getPreneurId()))
                    .findFirst()
                    .ifPresent(p -> dto.setPreneurEquipe(p.getEquipe()));
        }

        if ("TERMINEE".equals(partie.getStatut())) {
            dto.setResultat(buildResultat(partie, joueurs));
        }

        return dto;
    }


    public EtatJeuDTO encherir(Long partieId, Long utilisateurId, Integer contrat, String couleur, boolean passe) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"EN_ENCHERE".equals(partie.getStatut())) {
            throw new BusinessException("La partie n'est pas en phase d'enchères.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur joueurActif = joueurs.stream()
                .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                .findFirst()
                .orElseThrow(() -> new BusinessException("Joueur actif introuvable."));

        if (!joueurActif.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Ce n'est pas votre tour d'enchérir.");
        }

        // donne déjà coinchée : seul le passe ou la surcoinche sont autorisés
        if (partie.getCoinche() == 1) {
            if (!passe) {
                throw new BusinessException("La donne est coinchée : vous ne pouvez que passer ou surcoincher.");
            }
                Joueur preneur = joueurs.stream()
                    .filter(j -> j.getId().equals(partie.getPreneurId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Preneur introuvable."));
            if (joueurActif.getEquipe() != preneur.getEquipe()) {
                throw new BusinessException("Après une coinche, seule l'équipe du preneur peut passer ou surcoincher.");
            }

                Enchere e = new Enchere();
            e.setPartie(partie);
            e.setPreneur(joueurActif);
            e.setPasse(true);
            e.setContrat(0);
            enchereRepository.save(e);
            partie.setPassesConsecutives(partie.getPassesConsecutives() + 1);

                if (partie.getPassesConsecutives() >= 2) {
                demarrerJeuDepuisEnchere(partie);
            } else {
                partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 2) % 4);
            }

            partieRepository.save(partie);
            EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut())
                    ? EvenementJeuDTO.Type.CARTE_JOUEE
                    : EvenementJeuDTO.Type.ENCHERE;
            pushEtatATous(partieId, joueurs, typeEvt);
        return getEtatJeu(partieId, utilisateurId);
        }

        if (passe) {
                Enchere e = new Enchere();
            e.setPartie(partie);
            e.setPreneur(joueurActif);
            e.setPasse(true);
            e.setContrat(0);
            enchereRepository.save(e);

            partie.setPassesConsecutives(partie.getPassesConsecutives() + 1);

            // Si tout le monde a passé sans contrat → nouvelle donne automatique
            if (partie.getPassesConsecutives() >= 4) {
                partie.setDonneActuelle(partie.getDonneActuelle() + 1);
                redemarrerDonneCoinche(partie, joueurs);
                pushEtatATous(partieId, joueurs, EvenementJeuDTO.Type.ENCHERE);
                return getEtatJeu(partieId, utilisateurId);
            }
        } else {
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

                if (partie.getContratValeur() > 0 && contrat <= partie.getContratValeur()) {
                throw new BusinessException(
                        "Le contrat doit être supérieur au contrat précédent (" + partie.getContratValeur() + ").");
            }

            Enchere e = new Enchere();
            e.setPartie(partie);
            e.setPreneur(joueurActif);
            e.setPasse(false);
            e.setContrat(contrat);
            e.setCouleur(capitalise(couleur));
            enchereRepository.save(e);

            partie.setContratValeur(contrat);
            partie.setContratCouleur(capitalise(couleur));
            partie.setPreneurId(joueurActif.getId());
            partie.setPassesConsecutives(0);
        }

        partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % 4);

        List<Enchere> toutesEncheres = enchereRepository.findByPartie_IdOrderByIdAsc(partieId);
        if (partie.getContratValeur() > 0 && doitCommencerJeu(toutesEncheres)) {
            demarrerJeuDepuisEnchere(partie);
        }

        partieRepository.save(partie);

        EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut())
                ? EvenementJeuDTO.Type.CARTE_JOUEE
                : EvenementJeuDTO.Type.ENCHERE;
        pushEtatATous(partieId, joueurs, typeEvt);

        return getEtatJeu(partieId, utilisateurId);
    }


    private void demarrerJeuDepuisEnchere(Partie partie) {
        partie.setStatut("EN_JEU");
        partie.setAtout(partie.getContratCouleur());
        partie.setNumPliCourant(1);
        partie.setTourJoueurIndex((partie.getDonneActuelle() - 1) % 4);
    }

    // Vérifie s'il y a eu 3 passes consécutives.
    private boolean doitCommencerJeu(List<Enchere> encheres) {
        if (encheres.size() < 4)
            return false;
        // Compter les passes depuis la dernière enchère réelle
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


    public EtatJeuDTO jouerCarte(Long partieId, Long utilisateurId, Long carteId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"EN_JEU".equals(partie.getStatut())) {
            throw new BusinessException("La partie n'est pas en phase de jeu.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur joueurActif = joueurs.stream()
                .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                .findFirst()
                .orElseThrow(() -> new BusinessException("Joueur actif introuvable."));

        if (!joueurActif.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Ce n'est pas votre tour de jouer.");
        }

        Carte carteJouee = joueurActif.getCartesEnMain().stream()
                .filter(c -> c.getId().equals(carteId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Cette carte n'est pas dans votre main."));

        Pli pli = pliRepository.findByPartie_IdAndNumTour(partieId, partie.getNumPliCourant())
                .orElseGet(() -> {
                    Pli nouveau = new Pli();
                    nouveau.setPartie(partie);
                    nouveau.setNumTour(partie.getNumPliCourant());
                    nouveau.setJoueurOuvreurIndex(partie.getTourJoueurIndex());
                    return pliRepository.save(nouveau);
                });

        verifierReglesCouleur(joueurActif, carteJouee, pli, partie.getAtout(), joueurs);

        pli.getCartesJouees().add(carteJouee);
        joueurActif.getCartesEnMain().remove(carteJouee);
        pliRepository.save(pli);
        joueurRepository.save(joueurActif);

        partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % 4);

        boolean pliComplet = pli.getCartesJouees().size() == 4;

        if (pliComplet) {
            partieRepository.save(partie);
            pushEtatATous(partieId, joueurs, EvenementJeuDTO.Type.CARTE_JOUEE);

            terminerPli(partie, pli, joueurs);
        }

        partieRepository.save(partie);

        if (pliComplet) {
            EvenementJeuDTO.Type typeEvt = "TERMINEE".equals(partie.getStatut())
                    ? EvenementJeuDTO.Type.PARTIE_TERMINEE
                    : EvenementJeuDTO.Type.PLI_TERMINE;
            pushEtatATous(partieId, joueurs, typeEvt);
        } else {
            pushEtatATous(partieId, joueurs, EvenementJeuDTO.Type.CARTE_JOUEE);
        }

        if (!"TERMINEE".equals(partie.getStatut())) {
        }

        return getEtatJeu(partieId, utilisateurId);
    }

    // Vérifie les règles de suivi.
    private void verifierReglesCouleur(Joueur joueur, Carte carteJouee, Pli pli, String atout, List<Joueur> joueurs) {
        if (pli.getCartesJouees().isEmpty())
            return; // premier à jouer dans ce pli, tout est permis

        Carte premiereCarteJouee = pli.getCartesJouees().get(0);
        String couleurDemandee = premiereCarteJouee.getCouleur();
        List<Carte> main = joueur.getCartesEnMain();
        boolean possedeColoreDemandee = main.stream().anyMatch(c -> c.getCouleur().equals(couleurDemandee));

        // sans-atout : pas de coupe, juste le suivi de couleur
        if ("Sans-atout".equals(atout)) {
            if (!carteJouee.getCouleur().equals(couleurDemandee) && possedeColoreDemandee) {
                throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
            }
            return; // pas d'atout → pas de coupe ni de montée inter-couleurs
        }

        // tout-atout : chaque couleur se comporte comme un atout
        if ("Tout-atout".equals(atout)) {
            if (!carteJouee.getCouleur().equals(couleurDemandee)) {
                if (possedeColoreDemandee) {
                    throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
                }
                return; // n'a pas la couleur → peut défausser librement
            }
            // Joue la couleur demandée → obligation de monter.
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

        boolean possedeAtout = main.stream().anyMatch(c -> c.getCouleur().equals(atout));

        // Déterminer l'équipe du joueur courant et du maître en cours
        int equipeJoueur = joueur.getEquipe();
        boolean partenaireEstMaitre = estPartenaireLeGagnantActuel(
                pli.getCartesJouees(), pli.getJoueurOuvreurIndex(), equipeJoueur, joueurs, atout);

        if (!carteJouee.getCouleur().equals(couleurDemandee)) {
            if (possedeColoreDemandee) {
                throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
            }
            // Obligation de couper SAUF si le partenaire est maître.
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

    // Vérifie si le partenaire est maître du pli.
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
        // Le partenaire est maître si le gagnant est dans la même équipe.
        return gagnantActuel.getEquipe() == equipeJoueur;
    }

    // Termine le pli.
    private void terminerPli(Partie partie, Pli pli, List<Joueur> joueurs) {
        String atout = partie.getAtout();
        List<Carte> cartes = pli.getCartesJouees();
        int ouvreurIndex = pli.getJoueurOuvreurIndex();

        // Déterminer le gagnant du pli selon le mode
        String couleurOuverte = cartes.get(0).getCouleur();
        int indexGagnant = ouvreurIndex; // l'ouvreur gagne par défaut
        Carte meilleureCarteCouleurOuverte = cartes.get(0);

        if ("Sans-atout".equals(atout)) {
            // Pas d'atout : seule la couleur ouverte compte, comparée par ORDRE_NORMAL
            for (int i = 1; i < 4; i++) {
                Carte c = cartes.get(i);
                int joueurIndex = (ouvreurIndex + i) % 4;
                if (c.getCouleur().equals(couleurOuverte) &&
                        ORDRE_NORMAL.indexOf(c.getValeur()) > ORDRE_NORMAL
                                .indexOf(meilleureCarteCouleurOuverte.getValeur())) {
                    meilleureCarteCouleurOuverte = c;
                    indexGagnant = joueurIndex;
                }
            }
        } else if ("Tout-atout".equals(atout)) {
            // La couleur ouverte agit comme atout, pas de coupe inter-couleurs.
            for (int i = 1; i < 4; i++) {
                Carte c = cartes.get(i);
                int joueurIndex = (ouvreurIndex + i) % 4;
                if (c.getCouleur().equals(couleurOuverte) &&
                        ORDRE_ATOUT.indexOf(c.getValeur()) > ORDRE_ATOUT
                                .indexOf(meilleureCarteCouleurOuverte.getValeur())) {
                    meilleureCarteCouleurOuverte = c;
                    indexGagnant = joueurIndex;
                }
            }
        } else {
            // Mode coinche normal : atout bat la couleur ouverte
            Carte meilleureCarteAtout = null;
            for (int i = 0; i < 4; i++) {
                Carte c = cartes.get(i);
                int joueurIndex = (ouvreurIndex + i) % 4;
                if (c.getCouleur().equals(atout)) {
                    if (meilleureCarteAtout == null ||
                            ORDRE_ATOUT.indexOf(c.getValeur()) > ORDRE_ATOUT.indexOf(meilleureCarteAtout.getValeur())) {
                        meilleureCarteAtout = c;
                        indexGagnant = joueurIndex;
                    }
                } else if (meilleureCarteAtout == null && c.getCouleur().equals(couleurOuverte)) {
                    if (ORDRE_NORMAL.indexOf(c.getValeur()) > ORDRE_NORMAL
                            .indexOf(meilleureCarteCouleurOuverte.getValeur())) {
                        meilleureCarteCouleurOuverte = c;
                        indexGagnant = joueurIndex;
                    }
                }
            }
        }

        int indexGagnantFinal = indexGagnant;
        Joueur joueurGagnant = joueurs.stream()
                .filter(j -> j.getPosition() == indexGagnantFinal)
                .findFirst()
                .orElse(joueurs.get(0));
        int equipeGagnante = joueurGagnant.getEquipe();

        int points = 0;
        for (Carte c : cartes) {
            if ("Sans-atout".equals(atout)) {
                points += POINTS_SANS_ATOUT.getOrDefault(c.getValeur(), 0);
            } else if ("Tout-atout".equals(atout)) {
                points += POINTS_TOUT_ATOUT.getOrDefault(c.getValeur(), 0);
            } else {
                points += c.getCouleur().equals(atout)
                        ? POINTS_ATOUT.getOrDefault(c.getValeur(), 0)
                        : POINTS_NORMAL.getOrDefault(c.getValeur(), 0);
            }
        }

        // Dernier pli : +10 points
        boolean dernierPli = (partie.getNumPliCourant() == 8);
        if (dernierPli)
            points += 10;

        pli.setGagnantEquipe(equipeGagnante);
        pli.setPointsPli(points);
        pliRepository.save(pli);

        if (equipeGagnante == 1) {
            partie.setScoreA(partie.getScoreA() + points);
        } else {
            partie.setScoreB(partie.getScoreB() + points);
        }

        if (dernierPli) {
            terminerPartie(partie, joueurs);
        } else {
            partie.setNumPliCourant(partie.getNumPliCourant() + 1);
            partie.setTourJoueurIndex(indexGagnant);
        }
    }

    // Termine la partie.
    private void terminerPartie(Partie partie, List<Joueur> joueurs) {
        int contrat = partie.getContratValeur();
        Long preneurId = partie.getPreneurId();

        Joueur preneur = joueurs.stream()
                .filter(j -> j.getId().equals(preneurId))
                .findFirst()
                .orElse(joueurs.get(0));
        int equipePreneur = preneur.getEquipe();

        int scorePreneur = (equipePreneur == 1) ? partie.getScoreA() : partie.getScoreB();
        boolean contratRempli = scorePreneur >= contrat;

        if (!contratRempli) {
            int bonusChute = 160 + contrat;
            if (equipePreneur == 1) {
                partie.setScoreA(0);
                partie.setScoreB(bonusChute);
            } else {
                partie.setScoreA(bonusChute);
                partie.setScoreB(0);
            }
        }

        int multCoinche = (partie.getCoinche() == 2) ? 4 : (partie.getCoinche() == 1) ? 2 : 1;
        if (multCoinche > 1) {
            partie.setScoreA(partie.getScoreA() * multCoinche);
            partie.setScoreB(partie.getScoreB() * multCoinche);
        }

        partie.setScoreGlobalA(partie.getScoreGlobalA() + partie.getScoreA());
        partie.setScoreGlobalB(partie.getScoreGlobalB() + partie.getScoreB());

        // Vérifier la condition de fin de partie
        boolean partieTerminee = false;
        if (partie.getMaxDonnes() > 0 && partie.getDonneActuelle() >= partie.getMaxDonnes()) {
            partieTerminee = true;
        } else if (partie.getMaxPoints() > 0
                && (partie.getScoreGlobalA() >= partie.getMaxPoints()
                        || partie.getScoreGlobalB() >= partie.getMaxPoints())) {
            partieTerminee = true;
        } else if (partie.getMaxDonnes() == 0 && partie.getMaxPoints() == 0) {
            // Pas de condition configurée : comportement legacy (1 donne = fin de partie)
            partieTerminee = true;
        }

        if (partieTerminee) {
            partie.setStatut("TERMINEE");
            int sg_A = partie.getScoreGlobalA();
            int sg_B = partie.getScoreGlobalB();
            int equipeGagnante = (sg_A >= sg_B) ? 1 : 2;
            for (Joueur j : joueurs) {
                if (j.getEquipe() == equipeGagnante) {
                    Utilisateur u = j.getUtilisateur();
                    u.setScoreGlobal(u.getScoreGlobal() + 1);
                    utilisateurRepository.save(u);
                }
            }
        } else {
            // Redistribuer les cartes pour la nouvelle donne
            partie.setDonneActuelle(partie.getDonneActuelle() + 1);
            redemarrerDonneCoinche(partie, joueurs);
        }
    }


    private void redemarrerDonneCoinche(Partie partie, List<Joueur> joueurs) {
        Long partieId = partie.getId();

        // Vider les mains
        for (Joueur j : joueurs) {
            j.getCartesEnMain().clear();
            joueurRepository.save(j);
        }

        // Supprimer les plis et enchères de la donne précédente
        pliRepository.deleteAll(pliRepository.findByPartie_Id(partieId));
        enchereRepository.deleteAll(enchereRepository.findByPartie_IdOrderByIdAsc(partieId));

        // Nouveau jeu de 32 cartes
        String[] valeurs = { "7", "8", "9", "10", "Valet", "Dame", "Roi", "As" };
        String[] couleurs = { "Coeur", "Carreau", "Trefle", "Pique" };
        List<Carte> paquet = new ArrayList<>();
        for (String couleur : couleurs) {
            for (String valeur : valeurs) {
                Carte carte = new Carte();
                carte.setValeur(valeur);
                carte.setCouleur(couleur);
                paquet.add(carteRepository.save(carte));
            }
        }
        Collections.shuffle(paquet);

        joueurs.sort(Comparator.comparingInt(Joueur::getPosition));
        for (int i = 0; i < joueurs.size(); i++) {
            joueurs.get(i).setCartesEnMain(new ArrayList<>(paquet.subList(i * 8, (i + 1) * 8)));
            joueurRepository.save(joueurs.get(i));
        }

        // Réinitialiser l'état de la donne
        partie.setStatut("EN_ENCHERE");
        partie.setAtout(null);
        partie.setContratValeur(0);
        partie.setContratCouleur(null);
        partie.setPreneurId(null);
        partie.setPassesConsecutives(0);
        partie.setNumPliCourant(0);
        partie.setScoreA(0);
        partie.setScoreB(0);
        partie.setCoinche(0);
        // Le premier joueur tourne d'une position à chaque donne
        int premierJoueur = (partie.getDonneActuelle() - 1) % 4;
        partie.setTourJoueurIndex(premierJoueur);
        partieRepository.save(partie);
    }


    // Coinche ou surcoinche le contrat.
    public EtatJeuDTO coincher(Long partieId, Long utilisateurId, boolean surcoinche) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"EN_ENCHERE".equals(partie.getStatut())) {
            throw new BusinessException("La coinche n'est possible que pendant les enchères.");
        }
        if (partie.getContratValeur() <= 0) {
            throw new BusinessException("Il n'y a pas encore de contrat à coincher.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur monJoueur = joueurs.stream()
                .filter(j -> j.getUtilisateur().getId().equals(utilisateurId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        // Trouver le preneur et son équipe
        Joueur preneur = joueurs.stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Preneur introuvable."));
        int equipePreneur = preneur.getEquipe();
        int equipeJoueur = monJoueur.getEquipe();

        if (surcoinche) {
            if (partie.getCoinche() != 1) {
                throw new BusinessException("La surcoinche n'est possible qu'après une coinche.");
            }
            if (equipeJoueur != equipePreneur) {
                throw new BusinessException("Seule l'équipe du preneur peut surcoincher.");
            }
            // Application
            partie.setCoinche(2);
            partie.setEnchereType("SURCOINCHE");

            // La surcoinche met fin immédiatement aux enchères
            demarrerJeuDepuisEnchere(partie);

        } else {
            if (partie.getCoinche() != 0) {
                throw new BusinessException("La donne est déjà coinchée ou surcoinchée.");
            }
            if (equipeJoueur == equipePreneur) {
                throw new BusinessException("Seuls les adversaires du preneur peuvent coincher.");
            }
            // Application
            partie.setCoinche(1);
            partie.setEnchereType("COINCHE");
            // Remettre le compteur de passes à 0 pour l'équipe preneure.
            partie.setPassesConsecutives(0);

            // La parole revient au preneur, puis à son partenaire s'il passe.
            partie.setTourJoueurIndex(preneur.getPosition());
        }

        partieRepository.save(partie);
        EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut())
                ? EvenementJeuDTO.Type.CARTE_JOUEE
                : EvenementJeuDTO.Type.ENCHERE;
        pushEtatATous(partieId, joueurs, typeEvt);

        // Déclencher le bot si nécessaire après commit
        return getEtatJeu(partieId, utilisateurId);
    }


    private String capitalise(String s) {
        if (s == null || s.isBlank())
            return s;
        // Préserver la casse des modes spéciaux
        if (s.equalsIgnoreCase("Sans-atout"))
            return "Sans-atout";
        if (s.equalsIgnoreCase("Tout-atout"))
            return "Tout-atout";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private ResultatDTO buildResultat(Partie partie, List<Joueur> joueurs) {
        ResultatDTO r = new ResultatDTO();
        r.setScoreA(partie.getScoreA());
        r.setScoreB(partie.getScoreB());
        r.setContratValeur(partie.getContratValeur());
        r.setContratCouleur(partie.getContratCouleur());

        Long preneurId = partie.getPreneurId();
        if (preneurId != null) {
            joueurs.stream()
                    .filter(j -> j.getId().equals(preneurId))
                    .findFirst()
                    .ifPresent(preneur -> {
                        int equipePreneur = preneur.getEquipe();
                        int scorePreneur = (equipePreneur == 1) ? partie.getScoreA() : partie.getScoreB();
                        r.setContratRempli(scorePreneur >= partie.getContratValeur());
                        r.setPseudoPreneur(preneur.getUtilisateur().getPseudo());
                        r.setGagnantEquipe(partie.getScoreA() > partie.getScoreB() ? 1 : 2);
                    });
        }
        return r;
    }
}
