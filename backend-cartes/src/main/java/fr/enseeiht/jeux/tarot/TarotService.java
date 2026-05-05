package fr.enseeiht.jeux.tarot;

import fr.enseeiht.jeux.dto.*;
import fr.enseeiht.jeux.tarot.EtatTarotDTO;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Logique de jeu pour le Tarot français (3/4 joueurs).
 *
 * State machine statut × phaseJeu :
 *   OUVERTE           → (demarrerPartie) → EN_ENCHERE / phaseJeu=null
 *   EN_ENCHERE / null → (enchirirTarot gagnée)
 *     PETITE/GARDE    → EN_ENCHERE / phaseJeu="CHIEN"
 *     GARDE_SANS      → EN_ENCHERE / phaseJeu="CHIEN_VU"  (vue uniquement, pas d'écart)
 *     GARDE_CONTRE    → EN_JEU    / phaseJeu="JEU"
 *   EN_ENCHERE / CHIEN     → (ecarterCartes) → EN_JEU / phaseJeu="JEU"
 *   EN_ENCHERE / CHIEN_VU  → (confirmerChien) → EN_JEU / phaseJeu="JEU"
 *   EN_JEU / JEU           → (jouerCarte × N_plis) → TERMINEE
 */
@Service
@Transactional
public class TarotService {

    // Ordre force des atouts (1 = le Petit, 21 = le Monde ; Excuse est hors classement)
    private static final List<String> ORDRE_TRUMP =
            List.of("1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21");

    // Ordre force des cartes de couleur (1 = As tarot, Roi le plus fort)
    private static final List<String> ORDRE_SUIT =
            List.of("1","2","3","4","5","6","7","8","9","10","Valet","Cavalier","Dame","Roi");

    // Hiérarchie des enchères Tarot
    private static final List<String> ENCHERES_ORDRE =
            List.of("PETITE", "GARDE", "GARDE_SANS", "GARDE_CONTRE");

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EnchereRepository enchereRepository;
    private final PliRepository pliRepository;
    private final TarotScoringService scoringService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TarotBotService tarotBotService;
    private final fr.enseeiht.jeux.repository.CarteRepository carteRepository;
    private final TarotReglesService tarotReglesService;
    private final TarotEtatService tarotEtatService;
    private final TarotEnchereService tarotEnchereService;

    public TarotService(PartieRepository partieRepository,
                        JoueurRepository joueurRepository,
                        UtilisateurRepository utilisateurRepository,
                        EnchereRepository enchereRepository,
                        PliRepository pliRepository,
                        TarotScoringService scoringService,
                        SimpMessagingTemplate messagingTemplate,
                        @Lazy TarotBotService tarotBotService,
                        fr.enseeiht.jeux.repository.CarteRepository carteRepository,
                        TarotReglesService tarotReglesService,
                        TarotEtatService tarotEtatService,
                        TarotEnchereService tarotEnchereService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
        this.scoringService = scoringService;
        this.messagingTemplate = messagingTemplate;
        this.tarotBotService = tarotBotService;
        this.carteRepository = carteRepository;
        this.tarotReglesService = tarotReglesService;
        this.tarotEtatService = tarotEtatService;
        this.tarotEnchereService = tarotEnchereService;
    }

    // =========================================================
    // ÉTAT DU JEU
    // =========================================================

    public EtatTarotDTO getEtatJeuTarot(Long partieId, Long utilisateurId) {
        return tarotEtatService.getEtatJeuTarot(partieId, utilisateurId);
    }

    // =========================================================
    // ENCHÈRES TAROT
    // =========================================================

    public EtatTarotDTO enchirirTarot(Long partieId, Long utilisateurId, String typeBid) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        int nbJoueurs = partie.getNbJoueursRequis();

        Joueur joueurActif = joueurs.stream()
                .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                .findFirst()
                .orElseThrow(() -> new BusinessException("Joueur actif introuvable."));

        if (!joueurActif.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Ce n'est pas votre tour d'enchérir.");
        }

        boolean encheresTerminees = tarotEnchereService.traiterEnchere(partie, joueurActif, typeBid);

        if ("PASSE".equals(typeBid.toUpperCase().trim()) && partie.getPassesConsecutives() >= nbJoueurs) {
            partie.setDonneActuelle(partie.getDonneActuelle() + 1);
            redemarrerDonneTarot(partie, joueurs);
            partieRepository.save(partie);
            pushEtatTarotATous(partieId, joueurRepository.findByPartie_Id(partieId), EvenementJeuDTO.Type.ENCHERE);
            final Long partieIdFinal2 = partieId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal2); }
            });
            return getEtatJeuTarot(partieId, utilisateurId);
        }

        if (encheresTerminees) {
            tarotEnchereService.initialiserJeuApresEnchere(partie, joueurs);
            for (Joueur j : joueurs) joueurRepository.save(j); // update équipes
        }

        partieRepository.save(partie);
        pushEtatTarotATous(partieId, joueurRepository.findByPartie_Id(partieId), EvenementJeuDTO.Type.ENCHERE);

        final Long partieIdFinal = partieId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal); }
        });

        return getEtatJeuTarot(partieId, utilisateurId);
    }

    /**
     * Retourne true si, après la dernière enchère réelle, N-1 joueurs ont passé.
     */

    /**
     * Initialise le jeu après qu'une enchère ait été gagnée.
     */

    // =========================================================
    // PHASE APPEL ROI (5 joueurs uniquement)
    // =========================================================

    /**
     * Le preneur appelle un Roi d'une couleur qu'il ne détient pas (règle 5j).
     * Après l'appel, on passe à la phase suivante selon le type d'enchère.
     *
     * @param couleur "Coeur"|"Carreau"|"Trefle"|"Pique"
     */
    public EtatTarotDTO appelerRoi(Long partieId, Long utilisateurId, String couleur) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        Joueur preneur = joueurRepository.findByPartie_Id(partieId).stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst().orElseThrow(() -> new BusinessException("Preneur introuvable."));

        if (!preneur.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Seul le preneur peut appeler un Roi.");
        }

        tarotEnchereService.appelerRoi(partie, preneur, couleur);
        partieRepository.save(partie);

        pushEtatTarotATous(partieId, joueurRepository.findByPartie_Id(partieId), EvenementJeuDTO.Type.ENCHERE);
        final Long partieIdFinal = partieId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal); }
        });
        return getEtatJeuTarot(partieId, utilisateurId);
    }

    // =========================================================
    // PHASE CHIEN / ÉCART
    // =========================================================

    /**
     * Le preneur écarte des cartes après avoir pris le chien (PETITE/GARDE).
     * Pour GARDE_SANS, appeler avec une liste vide (confirme la vue).
     */
    public EtatTarotDTO ecarterCartes(Long partieId, Long utilisateurId, List<Long> carteIds) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        Joueur preneur = joueurRepository.findByPartie_Id(partieId).stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst().orElseThrow(() -> new BusinessException("Preneur introuvable."));

        if (!preneur.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Seul le preneur peut écarter.");
        }

        tarotEnchereService.ecarterCartes(partie, preneur, carteIds);
        
        joueurRepository.save(preneur);
        partieRepository.save(partie);

        pushEtatTarotATous(partieId, joueurRepository.findByPartie_Id(partieId), EvenementJeuDTO.Type.CARTE_JOUEE);
        final Long partieIdFinal = partieId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal); }
        });
        return getEtatJeuTarot(partieId, utilisateurId);
    }

    // =========================================================
    // JOUER UNE CARTE
    // =========================================================

    public EtatTarotDTO jouerCarte(Long partieId, Long utilisateurId, Long carteId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"EN_JEU".equals(partie.getStatut())) {
            throw new BusinessException("La partie n'est pas en phase de jeu.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        int nbJoueurs = partie.getNbJoueursRequis();

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

        // Récupérer ou créer le pli courant
        Pli pli = pliRepository.findByPartie_IdAndNumTour(partieId, partie.getNumPliCourant())
                .orElseGet(() -> {
                    Pli nouveau = new Pli();
                    nouveau.setPartie(partie);
                    nouveau.setNumTour(partie.getNumPliCourant());
                    nouveau.setJoueurOuvreurIndex(partie.getTourJoueurIndex());
                    return pliRepository.save(nouveau);
                });

        // Vérifier les règles de jeu Tarot
        tarotReglesService.verifierReglesTarot(joueurActif, carteJouee, pli);

        // 5j : révéler le partenaire si le Roi appelé vient d'être joué
        if (partie.getAppelRoi() != null && partie.getPartenaireId() == null) {
            boolean estLeRoiAppele = "Roi".equals(carteJouee.getValeur())
                    && partie.getAppelRoi().equals(carteJouee.getCouleur());
            boolean pasLePreneur = !joueurActif.getId().equals(partie.getPreneurId());
            if (estLeRoiAppele && pasLePreneur) {
                partie.setPartenaireId(joueurActif.getId());
                joueurActif.setEquipe(1); // rejoint l'équipe du preneur
                joueurRepository.save(joueurActif);
            }
        }

        // Jouer la carte
        pli.getCartesJouees().add(carteJouee);
        joueurActif.getCartesEnMain().remove(carteJouee);
        pliRepository.save(pli);
        joueurRepository.save(joueurActif);

        // Passer au joueur suivant
        partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % nbJoueurs);

        boolean pliComplet = pli.getCartesJouees().size() == nbJoueurs;

        if (pliComplet) {
            partieRepository.save(partie);
            pushEtatTarotATous(partieId, joueurs, EvenementJeuDTO.Type.CARTE_JOUEE);
            terminerPliTarot(partie, pli, joueurs);
        }

        partieRepository.save(partie);

        if (pliComplet) {
            EvenementJeuDTO.Type type = "TERMINEE".equals(partie.getStatut())
                    ? EvenementJeuDTO.Type.PARTIE_TERMINEE
                    : EvenementJeuDTO.Type.PLI_TERMINE;
            pushEtatTarotATous(partieId, joueurs, type);
        } else {
            pushEtatTarotATous(partieId, joueurs, EvenementJeuDTO.Type.CARTE_JOUEE);
        }

        if (!"TERMINEE".equals(partie.getStatut())) {
            final Long partieIdFinal = partieId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal); }
            });
        }

        return getEtatJeuTarot(partieId, utilisateurId);
    }

    /**
     * Vérifie les règles de suivi au Tarot :
     * 1. Obligation de suivre la couleur demandée
     * 2. Si n'a pas la couleur : obligation de couper avec un atout
     * 3. Si joue atout : obligation de monter
     * 4. L'Excuse peut être jouée à tout moment (valeur "Excuse", couleur "Atout")
     */


    // =========================================================
    // TERMINER UN PLI
    // =========================================================

    private void terminerPliTarot(Partie partie, Pli pli, List<Joueur> joueurs) {
        List<Carte> cartes = pli.getCartesJouees();
        int ouvreurIndex = pli.getJoueurOuvreurIndex();
        int nbJoueurs = partie.getNbJoueursRequis();

        // Trouver le gagnant du pli
        int indexGagnant = determinerGagnantPli(cartes, ouvreurIndex, nbJoueurs);

        // Trouver le joueur correspondant
        final int indexGagnantFinal = indexGagnant;
        Joueur gagnant = joueurs.stream()
                .filter(j -> j.getPosition() == indexGagnantFinal)
                .findFirst().orElse(joueurs.get(0));

        pli.setGagnantEquipe(gagnant.getEquipe());

        // Calculer les points du pli (×2)
        int pointsPliX2 = cartes.stream().mapToInt(scoringService::carteVautX2).sum();

        // Dernier pli : bonus +10 pts → +20 ×2
        boolean dernierPli = pli.getNumTour() == nombreMaxPlis(nbJoueurs);
        if (dernierPli) pointsPliX2 += 20;

        // Vérifier Petit au bout (Atout "1" joué lors du dernier pli, et gagnant est le preneur ou un défenseur)
        if (dernierPli) {
            boolean petitJoue = cartes.stream()
                    .anyMatch(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()));
            if (petitJoue) {
                boolean petitCotePreneur = gagnant.getId().equals(partie.getPreneurId());
                partie.setPetitAuBoutPreneur(petitCotePreneur);
            }
        }

        pli.setPointsPli(pointsPliX2);
        pliRepository.save(pli);

        // Mettre à jour les scores (scoreA = preneur ×2, scoreB = défenseurs ×2)
        if (gagnant.getEquipe() == 1) {
            partie.setScoreA(partie.getScoreA() + pointsPliX2);
        } else {
            partie.setScoreB(partie.getScoreB() + pointsPliX2);
        }

        // Prochain pli ou fin de partie
        if (pli.getNumTour() >= nombreMaxPlis(nbJoueurs)) {
            terminerPartieTarot(partie, joueurs);
        } else {
            partie.setNumPliCourant(partie.getNumPliCourant() + 1);
            // Le gagnant du pli ouvre le suivant
            partie.setTourJoueurIndex(indexGagnant);
        }
    }

    /**
     * Détermine l'index de position du gagnant du pli.
     */
    private int determinerGagnantPli(List<Carte> cartes, int ouvreurIndex, int nbJoueurs) {
        // Retirer l'Excuse du calcul du gagnant (elle ne gagne jamais)
        Carte premierCarteEffective = null;
        String couleurOuverte = null;
        int indexOuvreurEffectif = ouvreurIndex;

        // Trouver la couleur demandée (en ignorant l'Excuse si elle ouvre)
        for (int i = 0; i < cartes.size(); i++) {
            Carte c = cartes.get(i);
            if (!("Excuse".equals(c.getValeur()) && "Atout".equals(c.getCouleur()))) {
                premierCarteEffective = c;
                couleurOuverte = c.getCouleur();
                indexOuvreurEffectif = (ouvreurIndex + i) % nbJoueurs;
                break;
            }
        }

        if (premierCarteEffective == null) {
            // Que des Excuses (impossible normalement)
            return ouvreurIndex;
        }

        // Chercher le plus fort atout, sinon la plus forte carte de la couleur ouverte
        int indexGagnant = indexOuvreurEffectif;
        Carte meilleureAtout = null;
        Carte meilleureCouleurOuverte = premierCarteEffective;

        for (int i = 0; i < cartes.size(); i++) {
            Carte c = cartes.get(i);
            int idx = (ouvreurIndex + i) % nbJoueurs;

            // L'Excuse ne gagne pas
            if ("Excuse".equals(c.getValeur()) && "Atout".equals(c.getCouleur())) continue;

            if ("Atout".equals(c.getCouleur())) {
                if (meilleureAtout == null ||
                        ORDRE_TRUMP.indexOf(c.getValeur()) > ORDRE_TRUMP.indexOf(meilleureAtout.getValeur())) {
                    meilleureAtout = c;
                    indexGagnant = idx;
                }
            } else if (meilleureAtout == null && c.getCouleur().equals(couleurOuverte)) {
                if (ORDRE_SUIT.indexOf(c.getValeur()) > ORDRE_SUIT.indexOf(meilleureCouleurOuverte.getValeur())) {
                    meilleureCouleurOuverte = c;
                    indexGagnant = idx;
                }
            }
        }

        return indexGagnant;
    }

    private int nombreMaxPlis(int nbJoueurs) {
        // 3j : 24 cartes / 3 = 8 plis ; 4j : 18 cartes / 4 ≈ not integer... wait
        // 4j : (78 - 6 chien) / 4 = 72/4 = 18 cartes par joueur → 18 plis
        // Wait no, 4 players × 18 cards = 72, + 6 chien = 78 ✓
        // But 18 cards per player → 18 plis of 4 cards
        // 3j : 3 × 24 = 72, + 6 chien = 78 → 24 plis of 3 cards
        return switch (nbJoueurs) {
            case 3 -> 24;   // 3 × 24 cartes = 72 + 6 chien = 78
            case 4 -> 18;   // 4 × 18 cartes = 72 + 6 chien = 78
            case 5 -> 15;   // 5 × 15 cartes = 75 + 3 chien = 78
            default -> 18;
        };
    }

    // =========================================================
    // FIN DE PARTIE
    // =========================================================

    private void terminerPartieTarot(Partie partie, List<Joueur> joueurs) {
        int nbJoueurs = partie.getNbJoueursRequis();
        int maxPlis = nombreMaxPlis(nbJoueurs);

        // Collecter toutes les cartes du preneur (tricks + écartes sauf GARDE_SANS/GARDE_CONTRE)
        List<Carte> cartesPreneur = tarotEtatService.collecterCartesPreneur(partie, joueurs);

        int pointsPreneurX2 = scoringService.calculerPointsX2(cartesPreneur)
                            + tarotEtatService.correctionExcuseX2(partie, joueurs);
        int bouts = scoringService.compterBouts(cartesPreneur);
        
        // Détecter qui a fait le Petit au bout (le dernier pli)
        boolean petitAuBoutPreneur = false;
        boolean petitAuBoutDefense = false;
        List<Pli> plis = pliRepository.findByPartie_IdOrderByNumTourAsc(partie.getId());
        Pli dernierPli = plis.stream().filter(p -> p.getNumTour() == maxPlis).findFirst().orElse(null);
        if (dernierPli != null && dernierPli.getCartesJouees().stream().anyMatch(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()))) {
            if (dernierPli.getGagnantEquipe() == 1) petitAuBoutPreneur = true;
            else petitAuBoutDefense = true;
            if (petitAuBoutPreneur) partie.setPetitAuBoutPreneur(true); // store for backward compatibility
        }

        int score = scoringService.calculerScore(
                pointsPreneurX2, bouts, partie.getEnchereType(), petitAuBoutPreneur, petitAuBoutDefense);

        // Bonus Poignée (s'ajoute au camp gagnant, peu importe qui a déclaré)
        int bonusPoignee = scoringService.poigneeBonus(partie.getPoigneeDeclaree());
        if (bonusPoignee > 0) {
            score = score >= 0 ? score + bonusPoignee : score - bonusPoignee;
        }

        boolean rempli = score > 0;
        int absScore = Math.abs(score);
        boolean cinqJoueurs = partie.getNbJoueursRequis() == 5;
        boolean jeuSolo5j = cinqJoueurs && partie.getPartenaireId() == null;

        // Accumuler dans les scores globaux de la partie
        // scoreGlobalA = preneur (ou équipe attaque), scoreGlobalB = défenseurs
        int deltaPreneur = rempli ? absScore : -absScore;
        partie.setScoreGlobalA(partie.getScoreGlobalA() + deltaPreneur);
        partie.setScoreGlobalB(partie.getScoreGlobalB() - deltaPreneur);

        // Mettre à jour les scores globaux des utilisateurs
        for (Joueur j : joueurs) {
            int delta;
            boolean estPreneur = j.getId().equals(partie.getPreneurId());
            boolean estPartenaire = cinqJoueurs && j.getId().equals(partie.getPartenaireId());

            if (cinqJoueurs) {
                if (estPreneur) {
                    int facteur = jeuSolo5j ? 4 : 2;
                    delta = rempli ? absScore * facteur : -absScore * facteur;
                } else if (estPartenaire) {
                    delta = rempli ? absScore : -absScore;
                } else {
                    delta = rempli ? -absScore : absScore;
                }
            } else {
                int nbDefenseurs = partie.getNbJoueursRequis() - 1;
                if (estPreneur) {
                    delta = rempli ? absScore * nbDefenseurs : -absScore * nbDefenseurs;
                } else {
                    delta = rempli ? -absScore : absScore;
                }
            }

            j.setScorePartie(j.getScorePartie() + delta);
            joueurRepository.save(j);

            Utilisateur u = j.getUtilisateur();
            u.setScoreGlobal(u.getScoreGlobal() + delta);
            utilisateurRepository.save(u);
        }

        // Stocker les scores de la donne
        partie.setScoreA(pointsPreneurX2);
        partie.setScoreB(182 - pointsPreneurX2);

        // Vérifier la condition de fin de partie multi-manche
        boolean partieTerminee = false;
        if (partie.getMaxDonnes() > 0 && partie.getDonneActuelle() >= partie.getMaxDonnes()) {
            partieTerminee = true;
        } else if (partie.getMaxPoints() == 0 && partie.getMaxDonnes() == 0) {
            // Comportement legacy : 1 seule donne
            partieTerminee = true;
        }
        // Pour maxPoints en Tarot : le scoreGlobal est la somme des deltas (peut être négatif)
        // On utilise simplement le nombre de donnes comme condition principale

        if (partieTerminee) {
            partie.setStatut("TERMINEE");
        } else {
            partie.setDonneActuelle(partie.getDonneActuelle() + 1);
            redemarrerDonneTarot(partie, joueurs);
        }
    }

    /**
     * Collecte les cartes comptant pour le preneur selon le type d'enchère.
     * - PETITE/GARDE : plis gagnés par l'équipe 1 + écartes
     * - GARDE_SANS   : plis gagnés par l'équipe 1 (chien aux défenseurs)
     * - GARDE_CONTRE : plis gagnés par l'équipe 1 (chien aux défenseurs)
     *
     * Règle de l'Excuse : elle reste toujours dans les points de l'équipe qui l'a jouée,
     * quelle que soit l'équipe gagnante du pli.
     * Exception : si l'équipe 1 (attaque) joue l'Excuse au DERNIER pli, elle va à la défense.
     */

    private Long partieId(Partie partie) {
        return partie.getId();
    }

    // =========================================================
    // WEBSOCKET
    // =========================================================

    private void pushEtatTarotATous(Long partieId, List<Joueur> joueurs, EvenementJeuDTO.Type type) {
        for (Joueur j : joueurs) {
            EtatTarotDTO etat = getEtatJeuTarot(partieId, j.getUtilisateur().getId());
            messagingTemplate.convertAndSend(
                    "/topic/partie/" + partieId + "/joueur/" + j.getUtilisateur().getId(),
                    EvenementJeuDTO.of(type, etat)
            );
        }
    }

    // =========================================================
    // UTILITAIRES
    // =========================================================

    private Long getJoueurId(Long utilisateurId, Long partieId) {
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        return joueurs.stream()
                .filter(j -> j.getUtilisateur().getId().equals(utilisateurId))
                .map(Joueur::getId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));
    }

    /** Ordre de tri d'une carte pour afficher la main de façon lisible. */

    /**
     * Correction de ±0,5 pt (±1 en ×2) liée à la règle de compensation de l'Excuse.
     *
     * Quand l'Excuse est jouée par une équipe qui ne gagne pas le pli, elle revient à
     * l'équipe qui l'a jouée. Mais l'équipe gagnante se retrouve avec une carte en moins
     * dans le pli (déficit de 0,5 pt). Par compensation, l'équipe qui a joué l'Excuse
     * lui cède une petite carte (0,5 pt) tirée de ses plis.
     *
     * On modélise cela comme un ajustement de ±1 (×2) sur le score du preneur :
     *   +1  → défenseur a joué l'Excuse, preneur a gagné le pli → preneur reçoit +0,5
     *   −1  → preneur a joué l'Excuse, défenseur a gagné le pli → preneur cède −0,5
     *
     * Exception : dernier pli + preneur joue l'Excuse → l'Excuse va déjà à la défense,
     * pas de compensation supplémentaire.
     */


    // =========================================================
    // PETIT SEC
    // =========================================================

    /**
     * Permet à un joueur de signaler qu'il a le Petit sec (Atout 1 = seul atout en main)
     * avant le début des enchères. Dans ce cas, la donne est annulée et redistribuée.
     * Cette action n'est disponible que si :
     *  - La partie est en statut EN_ENCHERE sans phase (début de donne)
     *  - petitSecDetecte est vrai
     *  - Le joueur qui signale a bien le Petit comme seul atout
     */
    public EtatTarotDTO signalerPetitSec(Long partieId, Long utilisateurId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"EN_ENCHERE".equals(partie.getStatut()) || partie.getPhaseJeu() != null) {
            throw new BusinessException("Le Petit sec ne peut être signalé qu'avant les enchères.");
        }
        if (!partie.isPetitSecDetecte()) {
            throw new BusinessException("Aucun Petit sec détecté dans cette donne.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur monJoueur = joueurs.stream()
                .filter(j -> j.getUtilisateur().getId().equals(utilisateurId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        if (!hasPetitSec(monJoueur)) {
            throw new BusinessException("Vous n'avez pas de Petit sec.");
        }

        // Annuler la donne et redistribuer
        partie.setDonneActuelle(partie.getDonneActuelle() + 1);
        redemarrerDonneTarot(partie, joueurs);
        partieRepository.save(partie);

        List<Joueur> joueursActualises = joueurRepository.findByPartie_Id(partieId);
        pushEtatTarotATous(partieId, joueursActualises, EvenementJeuDTO.Type.ENCHERE);

        final Long partieIdFinal = partieId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal); }
        });

        return getEtatJeuTarot(partieId, utilisateurId);
    }

    /**
     * Retourne true si le joueur a le Petit (Atout 1) comme seul atout non-Excuse en main.
     */
    private boolean hasPetitSec(Joueur joueur) {
        List<Carte> main = joueur.getCartesEnMain();
        boolean aPetit = main.stream().anyMatch(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()));
        if (!aPetit) return false;
        long nbAtouts = main.stream()
                .filter(c -> "Atout".equals(c.getCouleur()) && !"Excuse".equals(c.getValeur()))
                .count();
        return nbAtouts == 1;
    }

    // =========================================================
    // POIGNÉE
    // =========================================================

    /**
     * Le preneur déclare une Poignée avant de jouer sa première carte.
     * "SIMPLE"|"DOUBLE"|"TRIPLE"
     */
    public EtatTarotDTO declarePoignee(Long partieId, Long utilisateurId, String typePoignee) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new fr.enseeiht.jeux.exception.ResourceNotFoundException("Partie introuvable."));

        if (!"EN_JEU".equals(partie.getStatut()) || !"JEU".equals(partie.getPhaseJeu())) {
            throw new fr.enseeiht.jeux.exception.BusinessException("Poignée déclarable uniquement au début de la phase de jeu.");
        }
        if (partie.getNumPliCourant() > 1) {
            throw new fr.enseeiht.jeux.exception.BusinessException("Poignée déclarable avant le premier pli uniquement.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur preneur = joueurs.stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst()
                .orElseThrow(() -> new fr.enseeiht.jeux.exception.BusinessException("Preneur introuvable."));

        if (!preneur.getUtilisateur().getId().equals(utilisateurId)) {
            throw new fr.enseeiht.jeux.exception.BusinessException("Seul le preneur peut déclarer une Poignée.");
        }

        long nbAtouts = preneur.getCartesEnMain().stream()
                .filter(c -> "Atout".equals(c.getCouleur()) && !"Excuse".equals(c.getValeur()))
                .count();

        int requis = scoringService.nbAtouttsPourPoignee(partie.getNbJoueursRequis(), typePoignee);
        if (nbAtouts < requis) {
            throw new fr.enseeiht.jeux.exception.BusinessException(
                    "Poignée " + typePoignee + " requiert " + requis + " atouts, vous en avez " + nbAtouts + ".");
        }

        partie.setPoigneeDeclaree(typePoignee);
        partieRepository.save(partie);

        List<Joueur> joueursAct = joueurRepository.findByPartie_Id(partieId);
        pushEtatTarotATous(partieId, joueursAct, EvenementJeuDTO.Type.CARTE_JOUEE);
        return getEtatJeuTarot(partieId, utilisateurId);
    }

    // =========================================================
    // REDÉMARRAGE D'UNE NOUVELLE DONNE
    // =========================================================

    /**
     * Réinitialise la donne : vide mains/plis/enchères, redistribue 78 cartes.
     * Conserve les scores globaux et le numéro de donne.
     */
    void redemarrerDonneTarot(Partie partie, List<Joueur> joueurs) {
        Long partieId = partie.getId();
        int nbJoueurs = partie.getNbJoueursRequis();

        for (Joueur j : joueurs) {
            j.getCartesEnMain().clear();
            joueurRepository.save(j);
        }

        pliRepository.deleteAll(pliRepository.findByPartie_Id(partieId));
        enchereRepository.deleteAll(enchereRepository.findByPartie_IdOrderByIdAsc(partieId));

        // Générer 78 cartes Tarot
        List<Carte> paquet = new ArrayList<>();
        String[] valeursCouleur = {"1","2","3","4","5","6","7","8","9","10","Valet","Cavalier","Dame","Roi"};
        String[] couleurs = {"Coeur","Carreau","Trefle","Pique"};
        for (String couleur : couleurs) {
            for (String val : valeursCouleur) {
                Carte c = new Carte(); c.setValeur(val); c.setCouleur(couleur);
                paquet.add(carteRepository.save(c));
            }
        }
        for (int i = 1; i <= 21; i++) {
            Carte c = new Carte(); c.setValeur(String.valueOf(i)); c.setCouleur("Atout");
            paquet.add(carteRepository.save(c));
        }
        Carte excuse = new Carte(); excuse.setValeur("Excuse"); excuse.setCouleur("Atout");
        paquet.add(carteRepository.save(excuse));
        Collections.shuffle(paquet);

        int tailleChien = (nbJoueurs == 5) ? 3 : 6;
        int cartesParJoueur = (paquet.size() - tailleChien) / nbJoueurs;
        joueurs.sort(Comparator.comparingInt(Joueur::getPosition));
        for (int i = 0; i < nbJoueurs; i++) {
            Joueur j = joueurs.get(i);
            j.setCartesEnMain(new ArrayList<>(paquet.subList(i * cartesParJoueur, (i + 1) * cartesParJoueur)));
            j.setEquipe(0);
            joueurRepository.save(j);
        }

        partie.getChien().clear();
        partie.getChien().addAll(paquet.subList(nbJoueurs * cartesParJoueur, paquet.size()));
        partie.getEcartes().clear();

        // Réinitialiser l'état de la donne
        partie.setStatut("EN_ENCHERE");
        partie.setPhaseJeu(null);
        partie.setEnchereType(null);
        partie.setMultiplicateur(1);
        partie.setPreneurId(null);
        partie.setPartenaireId(null);
        partie.setAppelRoi(null);
        partie.setPassesConsecutives(0);
        partie.setNumPliCourant(0);
        partie.setScoreA(0);
        partie.setScoreB(0);
        partie.setPoigneeDeclaree(null);
        partie.setPetitAuBoutPreneur(false);
        partie.setPetitSecDetecte(false);
        // Le premier joueur tourne d'une position à chaque donne (3j, 4j ou 5j)
        int premierJoueur = (partie.getDonneActuelle() - 1) % nbJoueurs;
        partie.setTourJoueurIndex(premierJoueur);
        // Ne PAS réinitialiser : donneActuelle, maxDonnes, maxPoints, scoreGlobalA/B

        // Détecter si un joueur a le Petit sec après la distribution
        boolean petitSecTrouve = false;
        for (Joueur j : joueurs) {
            // Récharger le joueur depuis la BDD pour avoir sa main à jour
            Joueur jFrais = joueurRepository.findById(j.getId()).orElse(j);
            if (hasPetitSec(jFrais)) {
                petitSecTrouve = true;
                break;
            }
        }
        partie.setPetitSecDetecte(petitSecTrouve);

        partieRepository.save(partie);
    }
}
