package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.dto.*;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class JeuService {

    // Valeurs des cartes à l'atout (Belote coinchée)
    private static final Map<String, Integer> POINTS_ATOUT = new LinkedHashMap<>();
    // Valeurs des cartes hors atout
    private static final Map<String, Integer> POINTS_NORMAL = new LinkedHashMap<>();
    // Ordre des cartes à l'atout (force croissante)
    private static final List<String> ORDRE_ATOUT = List.of("7", "8", "Dame", "Roi", "10", "As", "9", "Valet");
    // Ordre des cartes hors atout (force croissante)
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
    }

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EnchereRepository enchereRepository;
    private final PliRepository pliRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final BotService botService;

    public JeuService(PartieRepository partieRepository,
                      JoueurRepository joueurRepository,
                      UtilisateurRepository utilisateurRepository,
                      EnchereRepository enchereRepository,
                      PliRepository pliRepository,
                      SimpMessagingTemplate messagingTemplate,
                      @Lazy BotService botService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
        this.messagingTemplate = messagingTemplate;
        this.botService = botService;
    }

    /**
     * Pousse l'état courant du jeu à tous les joueurs de la partie via WebSocket.
     * On envoie un EvenementJeuDTO avec l'état vu par chaque joueur (sa propre main).
     */
    private void pushEtatATous(Long partieId, List<Joueur> joueurs, EvenementJeuDTO.Type type) {
        for (Joueur j : joueurs) {
            EtatJeuDTO etat = getEtatJeu(partieId, j.getUtilisateur().getId());
            // Topic personnel par joueur pour que chacun reçoive uniquement sa propre main
            messagingTemplate.convertAndSend(
                    "/topic/partie/" + partieId + "/joueur/" + j.getUtilisateur().getId(),
                    EvenementJeuDTO.of(type, etat)
            );
        }
    }

    // =========================================================
    // ÉTAT DU JEU
    // =========================================================

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

        // Ma main
        dto.setMaMain(monJoueur.getCartesEnMain().stream()
                .map(CarteDTO::fromEntity)
                .collect(Collectors.toList()));

        // Joueur dont c'est le tour
        if (joueurs.size() == 4) {
            Joueur joueurTour = joueurs.stream()
                    .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                    .findFirst().orElse(null);
            if (joueurTour != null) {
                dto.setTourJoueurId(joueurTour.getId());
                dto.setTourPseudo(joueurTour.getUtilisateur().getPseudo());
            }
        }

        // Pli courant
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
                            j.getEquipe()
                    ));
                }
            }
            dto.setPliCourant(pliCourant);
        } else {
            dto.setPliCourant(new ArrayList<>());
        }

        // Dernier pli terminé (pli précédent)
        if (partie.getNumPliCourant() > 1 || "TERMINEE".equals(partie.getStatut())) {
            int numDernier = "TERMINEE".equals(partie.getStatut())
                    ? partie.getNumPliCourant() : partie.getNumPliCourant() - 1;
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
                                j.getEquipe()
                        ));
                    }
                }
                dto.setDernierPli(dernierPliList);
                dto.setDernierPliGagnantEquipe(dp.getGagnantEquipe());
            }
        }

        // Historique des enchères
        dto.setEncheres(enchereRepository.findByPartie_IdOrderByIdAsc(partieId).stream()
                .map(EnchereDTO::fromEntity)
                .collect(Collectors.toList()));

        // Résultat si terminée
        if ("TERMINEE".equals(partie.getStatut())) {
            dto.setResultat(buildResultat(partie, joueurs));
        }

        return dto;
    }

    // =========================================================
    // ENCHÈRES
    // =========================================================

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

        if (passe) {
            // Enregistrer le passe
            Enchere e = new Enchere();
            e.setPartie(partie);
            e.setPreneur(joueurActif);
            e.setPasse(true);
            e.setContrat(0);
            enchereRepository.save(e);

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
            String[] couleursValides = {"Coeur", "Carreau", "Trefle", "Pique"};
            if (Arrays.stream(couleursValides).noneMatch(c -> c.equalsIgnoreCase(couleur))) {
                throw new BusinessException("Couleur invalide. Valeurs acceptées : Coeur, Carreau, Trefle, Pique.");
            }

            // Vérifier que le contrat surenchérit sur le précédent
            if (partie.getContratValeur() > 0 && contrat <= partie.getContratValeur()) {
                throw new BusinessException("Le contrat doit être supérieur au contrat précédent (" + partie.getContratValeur() + ").");
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

        // Passer au joueur suivant
        partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % 4);

        // Vérifier si on doit passer en EN_JEU :
        // Condition : il y a un contrat ET les 3 joueurs suivants ont tous passé
        // (simplification : 3 passes consécutives après un contrat)
        List<Enchere> toutesEncheres = enchereRepository.findByPartie_IdOrderByIdAsc(partieId);
        if (partie.getContratValeur() > 0 && doitCommencerJeu(toutesEncheres)) {
            partie.setStatut("EN_JEU");
            partie.setAtout(partie.getContratCouleur());
            partie.setNumPliCourant(1);
            // Le joueur qui a pris le contrat ouvre le premier pli
            Joueur preneur = joueurs.stream()
                    .filter(j -> j.getId().equals(partie.getPreneurId()))
                    .findFirst().orElse(joueurs.get(0));
            partie.setTourJoueurIndex(preneur.getPosition());
        }

        partieRepository.save(partie);

        // Push WebSocket : notifier tous les joueurs du nouvel état
        EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut())
                ? EvenementJeuDTO.Type.CARTE_JOUEE
                : EvenementJeuDTO.Type.ENCHERE;
        pushEtatATous(partieId, joueurs, typeEvt);

        // Déclencher le bot après le commit (évite la lecture de données pas encore commitées)
        final Long partieIdFinal = partieId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { botService.jouerSiTourDuBot(partieIdFinal); }
        });

        return getEtatJeu(partieId, utilisateurId);
    }

    /**
     * Retourne true si, depuis la dernière enchère réelle, il y a eu 3 passes consécutives.
     */
    private boolean doitCommencerJeu(List<Enchere> encheres) {
        if (encheres.size() < 4) return false;
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

    // =========================================================
    // JOUER UNE CARTE
    // =========================================================

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

        // Vérifier que la carte est dans la main du joueur
        Carte carteJouee = joueurActif.getCartesEnMain().stream()
                .filter(c -> c.getId().equals(carteId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Cette carte n'est pas dans votre main."));

        // Récupérer ou créer le pli courant
        Pli pli = pliRepository.findByPartie_IdAndNumTour(partieId, partie.getNumPliCourant())
                .orElseGet(() -> {
                    Pli nouveau = new Pli();
                    nouveau.setPartie(partie);
                    nouveau.setNumTour(partie.getNumPliCourant());
                    nouveau.setJoueurOuvreurIndex(partie.getTourJoueurIndex());
                    return pliRepository.save(nouveau);
                });

        // Vérifier les règles de suivi de couleur
        verifierReglesCouleur(joueurActif, carteJouee, pli, partie.getAtout());

        // Jouer la carte
        pli.getCartesJouees().add(carteJouee);
        joueurActif.getCartesEnMain().remove(carteJouee);
        pliRepository.save(pli);
        joueurRepository.save(joueurActif);

        // Passer au joueur suivant
        partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % 4);

        // Si le pli est complet (4 cartes)
        boolean pliComplet = pli.getCartesJouees().size() == 4;

        if (pliComplet) {
            // Push intermédiaire AVANT de terminer le pli : les 4 cartes sont visibles
            partieRepository.save(partie);
            pushEtatATous(partieId, joueurs, EvenementJeuDTO.Type.CARTE_JOUEE);

            terminerPli(partie, pli, joueurs);
        }

        partieRepository.save(partie);

        // Push final (nouveau pli ou fin de partie)
        if (pliComplet) {
            EvenementJeuDTO.Type typeEvt = "TERMINEE".equals(partie.getStatut())
                    ? EvenementJeuDTO.Type.PARTIE_TERMINEE
                    : EvenementJeuDTO.Type.PLI_TERMINE;
            pushEtatATous(partieId, joueurs, typeEvt);
        } else {
            pushEtatATous(partieId, joueurs, EvenementJeuDTO.Type.CARTE_JOUEE);
        }

        // Déclencher le bot après le commit (évite la lecture de données pas encore commitées)
        if (!"TERMINEE".equals(partie.getStatut())) {
            final Long partieIdFinal = partieId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { botService.jouerSiTourDuBot(partieIdFinal); }
            });
        }

        return getEtatJeu(partieId, utilisateurId);
    }

    /**
     * Vérifie les règles de suivi (couleur demandée, obligation de couper, de monter).
     */
    private void verifierReglesCouleur(Joueur joueur, Carte carteJouee, Pli pli, String atout) {
        if (pli.getCartesJouees().isEmpty()) return; // premier à jouer dans ce pli, tout est permis

        Carte premiereCarteJouee = pli.getCartesJouees().get(0);
        String couleurDemandee = premiereCarteJouee.getCouleur();
        List<Carte> main = joueur.getCartesEnMain();

        boolean possedeColoreDemandee = main.stream().anyMatch(c -> c.getCouleur().equals(couleurDemandee));
        boolean possedeAtout = main.stream().anyMatch(c -> c.getCouleur().equals(atout));

        if (!carteJouee.getCouleur().equals(couleurDemandee)) {
            if (possedeColoreDemandee) {
                throw new BusinessException("Vous devez suivre la couleur demandée (" + couleurDemandee + ").");
            }
            // N'a pas la couleur demandée → obligation de couper (sauf si c'est l'atout qui est demandé)
            if (!couleurDemandee.equals(atout) && possedeAtout && !carteJouee.getCouleur().equals(atout)) {
                throw new BusinessException("Vous devez couper avec un atout.");
            }
        }

        // Obligation de monter à l'atout si on joue atout
        if (carteJouee.getCouleur().equals(atout) && couleurDemandee.equals(atout)) {
            // Trouver l'atout le plus fort déjà joué dans ce pli
            Optional<Carte> plusFortAtoutJoue = pli.getCartesJouees().stream()
                    .filter(c -> c.getCouleur().equals(atout))
                    .max(Comparator.comparingInt(c -> ORDRE_ATOUT.indexOf(c.getValeur())));

            if (plusFortAtoutJoue.isPresent()) {
                boolean peutMonter = main.stream()
                        .filter(c -> c.getCouleur().equals(atout))
                        .anyMatch(c -> ORDRE_ATOUT.indexOf(c.getValeur()) > ORDRE_ATOUT.indexOf(plusFortAtoutJoue.get().getValeur()));
                if (peutMonter && ORDRE_ATOUT.indexOf(carteJouee.getValeur()) <= ORDRE_ATOUT.indexOf(plusFortAtoutJoue.get().getValeur())) {
                    throw new BusinessException("Vous devez monter à l'atout (jouer un atout plus fort).");
                }
            }
        }
    }

    /**
     * Évalue le pli, attribue les points, met à jour les scores, gère la fin de partie.
     */
    private void terminerPli(Partie partie, Pli pli, List<Joueur> joueurs) {
        String atout = partie.getAtout();
        List<Carte> cartes = pli.getCartesJouees();
        int ouvreurIndex = pli.getJoueurOuvreurIndex();

        // Déterminer le gagnant du pli
        int indexGagnant = ouvreurIndex; // l'ouvreur gagne par défaut
        Carte meilleureCarteAtout = null;
        Carte meilleureCarteCouleurOuverte = cartes.get(0); // carte de la couleur demandée
        String couleurOuverte = cartes.get(0).getCouleur();

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
                if (ORDRE_NORMAL.indexOf(c.getValeur()) > ORDRE_NORMAL.indexOf(meilleureCarteCouleurOuverte.getValeur())) {
                    meilleureCarteCouleurOuverte = c;
                    indexGagnant = joueurIndex;
                }
            }
        }

        // Trouver l'équipe gagnante
        int indexGagnantFinal = indexGagnant;
        Joueur joueurGagnant = joueurs.stream()
                .filter(j -> j.getPosition() == indexGagnantFinal)
                .findFirst()
                .orElse(joueurs.get(0));
        int equipeGagnante = joueurGagnant.getEquipe();

        // Calculer les points du pli
        int points = 0;
        for (Carte c : cartes) {
            if (c.getCouleur().equals(atout)) {
                points += POINTS_ATOUT.getOrDefault(c.getValeur(), 0);
            } else {
                points += POINTS_NORMAL.getOrDefault(c.getValeur(), 0);
            }
        }

        // Dernier pli : +10 points
        boolean dernierPli = (partie.getNumPliCourant() == 8);
        if (dernierPli) points += 10;

        pli.setGagnantEquipe(equipeGagnante);
        pli.setPointsPli(points);
        pliRepository.save(pli);

        // Ajouter les points à l'équipe gagnante
        if (equipeGagnante == 1) {
            partie.setScoreA(partie.getScoreA() + points);
        } else {
            partie.setScoreB(partie.getScoreB() + points);
        }

        if (dernierPli) {
            // Fin de partie : calculer le résultat
            terminerPartie(partie, joueurs);
        } else {
            // Passer au pli suivant, le gagnant du pli ouvre le suivant
            partie.setNumPliCourant(partie.getNumPliCourant() + 1);
            partie.setTourJoueurIndex(indexGagnant);
        }
    }

    /**
     * Calcule le résultat final et met à jour les scoreGlobal des joueurs gagnants.
     */
    private void terminerPartie(Partie partie, List<Joueur> joueurs) {
        int contrat = partie.getContratValeur();
        Long preneurId = partie.getPreneurId();

        // Trouver l'équipe du preneur
        Joueur preneur = joueurs.stream()
                .filter(j -> j.getId().equals(preneurId))
                .findFirst()
                .orElse(joueurs.get(0));
        int equipePreneur = preneur.getEquipe();

        int scorePreneur = (equipePreneur == 1) ? partie.getScoreA() : partie.getScoreB();
        boolean contratRempli = scorePreneur >= contrat;

        if (!contratRempli) {
            // Chute : les défenseurs gagnent 160 + valeur contrat
            int bonusChute = 160 + contrat;
            if (equipePreneur == 1) {
                partie.setScoreA(0);
                partie.setScoreB(bonusChute);
            } else {
                partie.setScoreA(bonusChute);
                partie.setScoreB(0);
            }
        }
        // Si contrat rempli : les scores déjà calculés sont corrects

        partie.setStatut("TERMINEE");

        // Incrémenter scoreGlobal des gagnants
        int scoreA = partie.getScoreA();
        int scoreB = partie.getScoreB();
        int equipeGagnante = (scoreA > scoreB) ? 1 : 2;

        for (Joueur j : joueurs) {
            if (j.getEquipe() == equipeGagnante) {
                Utilisateur u = j.getUtilisateur();
                u.setScoreGlobal(u.getScoreGlobal() + 1);
                utilisateurRepository.save(u);
            }
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String capitalise(String s) {
        if (s == null || s.isBlank()) return s;
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
