package fr.enseeiht.jeux.coinche;

import fr.enseeiht.jeux.dto.*;
import fr.enseeiht.jeux.coinche.EtatCoincheDTO;
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
public class CoincheService {

    // Valeurs des cartes à l'atout (Belote coinchée normale)
    private static final Map<String, Integer> POINTS_ATOUT = new LinkedHashMap<>();
    // Valeurs des cartes hors atout (Belote coinchée normale)
    private static final Map<String, Integer> POINTS_NORMAL = new LinkedHashMap<>();
    // Valeurs en mode Sans-atout (As=19, total = 4×38 + 10 dernier pli = 162)
    private static final Map<String, Integer> POINTS_SANS_ATOUT = new LinkedHashMap<>();
    // Valeurs en mode Tout-atout (total = 4×38 + 10 dernier pli = 162)
    private static final Map<String, Integer> POINTS_TOUT_ATOUT = new LinkedHashMap<>();
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
    private final CoincheBotService botService;
    private final CarteRepository carteRepository;

    private final CoincheEtatService coincheEtatService;
    private final CoincheEnchereService coincheEnchereService;
    private final CoincheReglesService coincheReglesService;

    public CoincheService(PartieRepository partieRepository,
            JoueurRepository joueurRepository,
            UtilisateurRepository utilisateurRepository,
            EnchereRepository enchereRepository,
            PliRepository pliRepository,
            SimpMessagingTemplate messagingTemplate,
            @Lazy CoincheBotService botService,
            CarteRepository carteRepository,
            CoincheEtatService coincheEtatService,
            CoincheEnchereService coincheEnchereService,
            CoincheReglesService coincheReglesService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
        this.messagingTemplate = messagingTemplate;
        this.botService = botService;
        this.carteRepository = carteRepository;
        this.coincheEtatService = coincheEtatService;
        this.coincheEnchereService = coincheEnchereService;
        this.coincheReglesService = coincheReglesService;
    }

    /**
     * Pousse l'état courant du jeu à tous les joueurs de la partie via WebSocket.
     * On envoie un EvenementJeuDTO avec l'état vu par chaque joueur (sa propre
     * main).
     */
    private void pushEtatATous(Long partieId, List<Joueur> joueurs, EvenementJeuDTO.Type type) {
        for (Joueur j : joueurs) {
            EtatCoincheDTO etat = getEtatJeu(partieId, j.getUtilisateur().getId());
            // Topic personnel par joueur pour que chacun reçoive uniquement sa propre main
            messagingTemplate.convertAndSend(
                    "/topic/partie/" + partieId + "/joueur/" + j.getUtilisateur().getId(),
                    EvenementJeuDTO.of(type, etat));
        }
    }

    // =========================================================
    // ÉTAT DU JEU
    // =========================================================

    public EtatCoincheDTO getEtatJeu(Long partieId, Long utilisateurId) {
        return coincheEtatService.getEtatJeu(partieId, utilisateurId);
    }

    // =========================================================
    // ENCHÈRES
    // =========================================================

    public EtatCoincheDTO encherir(Long partieId, Long utilisateurId, Integer contrat, String couleur, boolean passe) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur joueurActif = joueurs.stream().filter(j -> j.getPosition() == partie.getTourJoueurIndex()).findFirst()
                .orElseThrow(() -> new BusinessException("Joueur actif introuvable."));
        if (!joueurActif.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Ce n'est pas votre tour d'enchérir.");
        }

        boolean finEncheres = coincheEnchereService.traiterEnchere(partie, joueurActif, contrat, couleur, passe, joueurs);
        if (finEncheres) {
            coincheEnchereService.demarrerJeuDepuisEnchere(partie);
        }
        
        partieRepository.save(partie);
        EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut()) ? EvenementJeuDTO.Type.CARTE_JOUEE : EvenementJeuDTO.Type.ENCHERE;
        pushEtatATous(partieId, joueurs, typeEvt);
        final Long pId = partieId;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override
            public void afterCommit() { botService.jouerSiTourDuBot(pId); }
        });
        return getEtatJeu(partieId, utilisateurId);
    }

    /**
     * Démarre la phase EN_JEU à partir de la phase d'enchères.
     * Applique le multiplicateur coinche/surcoinche et configure l'atout.
     */
    /**
     * Retourne true si, depuis la dernière enchère réelle, il y a eu 3 passes
     * consécutives.
     */
    // =========================================================
    // JOUER UNE CARTE
    // =========================================================

    public EtatCoincheDTO jouerCarte(Long partieId, Long utilisateurId, Long carteId) {
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
        coincheReglesService.verifierReglesCouleur(joueurActif, carteJouee, pli, partie.getAtout(), joueurs);

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

        // Déclencher le bot après le commit (évite la lecture de données pas encore
        // commitées)
        if (!"TERMINEE".equals(partie.getStatut())) {
            final Long partieIdFinal = partieId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    botService.jouerSiTourDuBot(partieIdFinal);
                }
            });
        }

        return getEtatJeu(partieId, utilisateurId);
    }

    /**
     * Vérifie les règles de suivi (couleur demandée, obligation de couper, de
     * monter).
     * Gère les trois modes : coinche normale, Sans-atout, Tout-atout.
     */
    /**
     * Retourne true si c'est le partenaire du joueur courant qui est actuellement
     * maître du pli.
     * Utilisé pour lever l'obligation de couper/monter.
     */
    /**
     * Évalue le pli, attribue les points, met à jour les scores, gère la fin de
     * partie.
     */
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
            // La couleur ouverte agit comme atout (ORDRE_ATOUT), pas de coupe
            // inter-couleurs
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

        // Trouver l'équipe gagnante
        int indexGagnantFinal = indexGagnant;
        Joueur joueurGagnant = joueurs.stream()
                .filter(j -> j.getPosition() == indexGagnantFinal)
                .findFirst()
                .orElse(joueurs.get(0));
        int equipeGagnante = joueurGagnant.getEquipe();

        // Calculer les points du pli selon le mode
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
     * Calcule le résultat de la donne, accumule les scores globaux.
     * Si la condition de fin de partie n'est pas atteinte, redémarre une nouvelle
     * donne.
     * Sinon marque TERMINEE et incrémente les scoreGlobal des gagnants.
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
            int bonusChute = 160 + contrat;
            if (equipePreneur == 1) {
                partie.setScoreA(0);
                partie.setScoreB(bonusChute);
            } else {
                partie.setScoreA(bonusChute);
                partie.setScoreB(0);
            }
        }

        // Appliquer le multiplicateur coinche/surcoinche
        int multCoinche = (partie.getCoinche() == 2) ? 4 : (partie.getCoinche() == 1) ? 2 : 1;
        if (multCoinche > 1) {
            partie.setScoreA(partie.getScoreA() * multCoinche);
            partie.setScoreB(partie.getScoreB() * multCoinche);
        }

        // Accumuler dans les scores globaux
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

    /**
     * Redistribue 32 nouvelles cartes pour une nouvelle donne Coinche.
     * Les joueurs, positions, équipes et scores globaux sont conservés.
     */
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

    // =========================================================
    // COINCHE / SURCOINCHE
    // =========================================================

    /**
     * Coinche : un adversaire du preneur double le contrat (×2).
     * - Condition : il y a un contrat en cours ET l'état est NORMAL (coinche == 0)
     * - Après coinche : la parole revient à l'équipe preneure (premier joueur du
     * preneur)
     * Les enchères classiques sont désormais interdites.
     *
     * Surcoinche : l'équipe du preneur quadruple le contrat (×4).
     * - Condition : le contrat est déjà coinché (coinche == 1) ET c'est l'équipe du
     * preneur qui joue
     * - Après surcoinche : fin immédiate des enchères → passage en EN_JEU
     */
    public EtatCoincheDTO coincher(Long partieId, Long utilisateurId, boolean surcoinche) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur monJoueur = joueurs.stream().filter(j -> j.getUtilisateur().getId().equals(utilisateurId)).findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        boolean finEncheres = coincheEnchereService.traiterCoinche(partie, monJoueur, surcoinche, joueurs);
        if (finEncheres) {
            coincheEnchereService.demarrerJeuDepuisEnchere(partie);
        }
        
        partieRepository.save(partie);
        EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut()) ? EvenementJeuDTO.Type.CARTE_JOUEE : EvenementJeuDTO.Type.ENCHERE;
        pushEtatATous(partieId, joueurs, typeEvt);
        final Long pId = partieId;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override
            public void afterCommit() { botService.jouerSiTourDuBot(pId); }
        });
        return getEtatJeu(partieId, utilisateurId);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    }
