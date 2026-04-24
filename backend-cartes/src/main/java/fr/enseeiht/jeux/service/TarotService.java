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

    public TarotService(PartieRepository partieRepository,
                        JoueurRepository joueurRepository,
                        UtilisateurRepository utilisateurRepository,
                        EnchereRepository enchereRepository,
                        PliRepository pliRepository,
                        TarotScoringService scoringService,
                        SimpMessagingTemplate messagingTemplate,
                        @Lazy TarotBotService tarotBotService,
                        fr.enseeiht.jeux.repository.CarteRepository carteRepository) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
        this.scoringService = scoringService;
        this.messagingTemplate = messagingTemplate;
        this.tarotBotService = tarotBotService;
        this.carteRepository = carteRepository;
    }

    // =========================================================
    // ÉTAT DU JEU
    // =========================================================

    public EtatJeuTarotDTO getEtatJeuTarot(Long partieId, Long utilisateurId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        int nbJoueurs = partie.getNbJoueursRequis();

        Joueur monJoueur = joueurs.stream()
                .filter(j -> j.getUtilisateur().getId().equals(utilisateurId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        EtatJeuTarotDTO dto = new EtatJeuTarotDTO();
        dto.setPartieId(partieId);
        dto.setStatut(partie.getStatut());
        dto.setPhaseJeu(partie.getPhaseJeu());
        dto.setEnchereType(partie.getEnchereType());
        dto.setMultiplicateur(partie.getMultiplicateur());
        dto.setScoreA(partie.getScoreA());
        dto.setScoreB(partie.getScoreB());
        dto.setNumPliCourant(partie.getNumPliCourant());
        dto.setMonJoueurId(monJoueur.getId());
        dto.setMonEquipe(monJoueur.getEquipe());
        dto.setEstPreneur(monJoueur.getId().equals(partie.getPreneurId()));

        // 5 joueurs : partenaire et appel du roi
        if (partie.getNbJoueursRequis() == 5) {
            dto.setAppelRoi(partie.getAppelRoi()); // visible par tous une fois appelé
            if (partie.getPartenaireId() != null) {
                boolean estPartenaire = monJoueur.getId().equals(partie.getPartenaireId());
                dto.setEstPartenaire(estPartenaire);
                joueurs.stream()
                        .filter(j -> j.getId().equals(partie.getPartenaireId()))
                        .findFirst()
                        .ifPresent(p -> dto.setPseudoPartenaire(p.getUtilisateur().getPseudo()));
            }
        }

        // Ma main (en phase CHIEN le preneur voit aussi les cartes du chien pour préparer l'écart)
        List<Carte> mainAffichee = new ArrayList<>(monJoueur.getCartesEnMain());
        if ("CHIEN".equals(partie.getPhaseJeu()) && monJoueur.getId().equals(partie.getPreneurId())) {
            mainAffichee.addAll(partie.getChien());
        }
        dto.setMaMain(mainAffichee.stream()
                .sorted(Comparator.comparing(Carte::getCouleur).thenComparing(c -> ordreCarte(c)))
                .map(CarteDTO::fromEntity)
                .collect(Collectors.toList()));

        // Le chien est visible par TOUS les joueurs en phases CHIEN et CHIEN_VU
        if ("CHIEN".equals(partie.getPhaseJeu()) || "CHIEN_VU".equals(partie.getPhaseJeu())) {
            dto.setChien(partie.getChien().stream().map(CarteDTO::fromEntity).collect(Collectors.toList()));
        }

        // Joueur dont c'est le tour
        if (!joueurs.isEmpty()) {
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
            List<EtatJeuTarotDTO.CartePliDTO> pliCourant = new ArrayList<>();
            for (int i = 0; i < cartesJouees.size(); i++) {
                int idx = (ouvreurIndex + i) % nbJoueurs;
                final int idxFinal = idx;
                Joueur j = joueurs.stream().filter(jj -> jj.getPosition() == idxFinal).findFirst().orElse(null);
                if (j != null) {
                    pliCourant.add(new EtatJeuTarotDTO.CartePliDTO(
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

        // Dernier pli terminé
        if (partie.getNumPliCourant() > 1 || "TERMINEE".equals(partie.getStatut())) {
            int numDernier = "TERMINEE".equals(partie.getStatut())
                    ? partie.getNumPliCourant() : partie.getNumPliCourant() - 1;
            Optional<Pli> dernierOpt = pliRepository.findByPartie_IdAndNumTour(partieId, numDernier);
            if (dernierOpt.isPresent()) {
                Pli dp = dernierOpt.get();
                List<Carte> dpCartes = dp.getCartesJouees();
                int dpOuvreur = dp.getJoueurOuvreurIndex();
                List<EtatJeuTarotDTO.CartePliDTO> dernierPliList = new ArrayList<>();
                for (int i = 0; i < dpCartes.size(); i++) {
                    int idx = (dpOuvreur + i) % nbJoueurs;
                    final int idxFinal = idx;
                    Joueur j = joueurs.stream().filter(jj -> jj.getPosition() == idxFinal).findFirst().orElse(null);
                    if (j != null) {
                        dernierPliList.add(new EtatJeuTarotDTO.CartePliDTO(
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

        // Historique des enchères (avec typeBid)
        dto.setEncheres(enchereRepository.findByPartie_IdOrderByIdAsc(partieId).stream()
                .map(EnchereDTO::fromEntity)
                .collect(Collectors.toList()));

        // Progression du score preneur
        if (partie.getPreneurId() != null && "EN_JEU".equals(partie.getStatut())) {
            List<Carte> cartesPreneur = collecterCartesPreneur(partie, joueurs);
            int pointsX2 = scoringService.calculerPointsX2(cartesPreneur)
                         + correctionExcuseX2(partie, joueurs);
            int bouts = scoringService.compterBouts(cartesPreneur);
            dto.setPointsPreneurX2(pointsX2);
            dto.setBoutsPreneur(bouts);
            dto.setSeuilCourant(scoringService.seuilPourBouts(bouts));
        }

        // Poignée
        dto.setPoigneeDeclaree(partie.getPoigneeDeclaree());

        // Scores individuels : map joueurId → scorePartie pour le frontend
        java.util.Map<Long, Integer> scoresMap = new java.util.HashMap<>();
        for (Joueur j : joueurs) {
            scoresMap.put(j.getId(), j.getScorePartie());
        }
        dto.setScoresJoueurs(scoresMap);

        // Résultat si terminée
        if ("TERMINEE".equals(partie.getStatut())) {
            dto.setResultat(buildResultatTarot(partie, joueurs));
        }

        return dto;
    }

    // =========================================================
    // ENCHÈRES TAROT
    // =========================================================

    public EtatJeuTarotDTO enchirirTarot(Long partieId, Long utilisateurId, String typeBid) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"EN_ENCHERE".equals(partie.getStatut()) || partie.getPhaseJeu() != null) {
            throw new BusinessException("La partie n'est pas en phase d'enchères Tarot.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        int nbJoueurs = partie.getNbJoueursRequis();

        Joueur joueurActif = joueurs.stream()
                .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                .findFirst()
                .orElseThrow(() -> new BusinessException("Joueur actif introuvable."));

        if (!joueurActif.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Ce n'est pas votre tour d'enchérir.");
        }

        if (typeBid == null || typeBid.isBlank()) {
            throw new BusinessException("typeBid requis.");
        }
        String bid = typeBid.toUpperCase().trim();

        if ("PASSE".equals(bid)) {
            // Enregistrer le passe
            Enchere e = new Enchere();
            e.setPartie(partie);
            e.setPreneur(joueurActif);
            e.setPasse(true);
            e.setTypeBid("PASSE");
            enchereRepository.save(e);

            partie.setPassesConsecutives(partie.getPassesConsecutives() + 1);

            // Si tous les joueurs ont passé sans contrat → redémarrer la donne
            if (partie.getPassesConsecutives() >= nbJoueurs) {
                redemarrerDonneTarot(partie, joueurs);
                partieRepository.save(partie);
                List<Joueur> joueursActualises = joueurRepository.findByPartie_Id(partieId);
                pushEtatTarotATous(partieId, joueursActualises, EvenementJeuDTO.Type.ENCHERE);
                return getEtatJeuTarot(partieId, utilisateurId);
            }

            // Passer au joueur suivant
            partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % nbJoueurs);

            // Si une enchère a été faite et que tous les autres ont passé → lancer le jeu
            if (partie.getEnchereType() != null) {
                List<Enchere> encheresMaj = enchereRepository.findByPartie_IdOrderByIdAsc(partieId);
                if (doitTerminerEncheres(encheresMaj, nbJoueurs)) {
                    lancerJeuTarot(partie, joueurs, partie.getEnchereType());
                }
            }
        } else {
            // Valider le type d'enchère
            if (!ENCHERES_ORDRE.contains(bid)) {
                throw new BusinessException("Enchère invalide. Valeurs : PETITE, GARDE, GARDE_SANS, GARDE_CONTRE.");
            }

            // Doit surenchérir sur l'enchère actuelle (si une existe)
            String enchereActuelle = partie.getEnchereType();
            if (enchereActuelle != null) {
                int niveauActuel = ENCHERES_ORDRE.indexOf(enchereActuelle);
                int niveauNouveau = ENCHERES_ORDRE.indexOf(bid);
                if (niveauNouveau <= niveauActuel) {
                    throw new BusinessException("Vous devez enchérir plus haut que " + enchereActuelle + ".");
                }
            }

            // Enregistrer l'enchère
            Enchere e = new Enchere();
            e.setPartie(partie);
            e.setPreneur(joueurActif);
            e.setPasse(false);
            e.setTypeBid(bid);
            enchereRepository.save(e);

            partie.setEnchereType(bid);
            partie.setPreneurId(joueurActif.getId());
            partie.setPassesConsecutives(0);

            // Passer au joueur suivant
            partie.setTourJoueurIndex((partie.getTourJoueurIndex() + 1) % nbJoueurs);

            // Vérifier si tous les autres ont eu leur chance (N-1 joueurs ont passé après cette enchère)
            // Ou si c'est GARDE_CONTRE (personne ne peut surenchérir par convention)
            List<Enchere> toutesEncheres = enchereRepository.findByPartie_IdOrderByIdAsc(partieId);
            if ("GARDE_CONTRE".equals(bid) || doitTerminerEncheres(toutesEncheres, nbJoueurs)) {
                // L'enchère est gagnée — initialiser le jeu
                lancerJeuTarot(partie, joueurs, bid);
            }
        }

        partieRepository.save(partie);

        // Push WebSocket
        List<Joueur> joueursActualises = joueurRepository.findByPartie_Id(partieId);
        pushEtatTarotATous(partieId, joueursActualises, EvenementJeuDTO.Type.ENCHERE);

        // Déclencher bot
        final Long partieIdFinal = partieId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal); }
        });

        return getEtatJeuTarot(partieId, utilisateurId);
    }

    /**
     * Retourne true si, après la dernière enchère réelle, N-1 joueurs ont passé.
     */
    private boolean doitTerminerEncheres(List<Enchere> encheres, int nbJoueurs) {
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
    private void lancerJeuTarot(Partie partie, List<Joueur> joueurs, String enchereType) {
        int mult = scoringService.multiplicateurPourType(enchereType);
        partie.setMultiplicateur(mult);

        // Assigner les équipes : preneur = 1, défenseurs = 2
        for (Joueur j : joueurs) {
            j.setEquipe(j.getId().equals(partie.getPreneurId()) ? 1 : 2);
            joueurRepository.save(j);
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

    // =========================================================
    // PHASE APPEL ROI (5 joueurs uniquement)
    // =========================================================

    /**
     * Le preneur appelle un Roi d'une couleur qu'il ne détient pas (règle 5j).
     * Après l'appel, on passe à la phase suivante selon le type d'enchère.
     *
     * @param couleur "Coeur"|"Carreau"|"Trefle"|"Pique"
     */
    public EtatJeuTarotDTO appelerRoi(Long partieId, Long utilisateurId, String couleur) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"EN_ENCHERE".equals(partie.getStatut()) || !"APPEL_ROI".equals(partie.getPhaseJeu())) {
            throw new BusinessException("La partie n'est pas en phase d'appel du Roi.");
        }

        // Vérifier que c'est le preneur qui appelle
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur preneur = joueurs.stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst().orElseThrow(() -> new BusinessException("Preneur introuvable."));

        if (!preneur.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Seul le preneur peut appeler un Roi.");
        }

        if (couleur == null || couleur.isBlank()) {
            throw new BusinessException("Couleur requise (Coeur, Carreau, Trefle ou Pique).");
        }
        String[] couleursValides = {"Coeur", "Carreau", "Trefle", "Pique"};
        boolean couleurOk = false;
        for (String c : couleursValides) if (c.equals(couleur)) { couleurOk = true; break; }
        if (!couleurOk) throw new BusinessException("Couleur invalide : " + couleur);

        partie.setAppelRoi(couleur);

        // Vérifier si le preneur détient lui-même le Roi appelé
        // Dans ce cas, il joue seul (partenaireId reste null)
        // Note : si le preneur détient lui-même le Roi appelé, il joue seul (partenaireId reste null).

        // Passer à la phase suivante selon l'enchère
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

        partieRepository.save(partie);

        List<Joueur> joueursAct = joueurRepository.findByPartie_Id(partieId);
        pushEtatTarotATous(partieId, joueursAct, EvenementJeuDTO.Type.ENCHERE);

        // Déclencher bot si nécessaire (chien/ecart)
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
    public EtatJeuTarotDTO ecarterCartes(Long partieId, Long utilisateurId, List<Long> carteIds) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        String phase = partie.getPhaseJeu();
        if (!"CHIEN".equals(phase) && !"CHIEN_VU".equals(phase)) {
            throw new BusinessException("La partie n'est pas en phase chien/écart.");
        }

        if (!partie.getPreneurId().equals(getJoueurId(utilisateurId, partieId))) {
            throw new BusinessException("Seul le preneur peut écarter.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur preneur = joueurs.stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Preneur introuvable."));

        int tailleChien = partie.getChien().size(); // 6 pour 4j, 3 pour 5j (non implémenté)

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

            // Règles d'écart : pas de bouts (Petit/Monde/Excuse), pas de Rois
            // Les atouts non-bouts sont autorisés (règle officielle FFT)
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

        joueurRepository.save(preneur);
        partieRepository.save(partie);

        List<Joueur> joueursActualises = joueurRepository.findByPartie_Id(partieId);
        pushEtatTarotATous(partieId, joueursActualises, EvenementJeuDTO.Type.CARTE_JOUEE);

        final Long partieIdFinal = partieId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { tarotBotService.jouerSiTourDuBot(partieIdFinal); }
        });

        return getEtatJeuTarot(partieId, utilisateurId);
    }

    // =========================================================
    // JOUER UNE CARTE
    // =========================================================

    public EtatJeuTarotDTO jouerCarte(Long partieId, Long utilisateurId, Long carteId) {
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
        verifierReglesTarot(joueurActif, carteJouee, pli);

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
    private void verifierReglesTarot(Joueur joueur, Carte carteJouee, Pli pli) {
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
        List<Carte> cartesPreneur = collecterCartesPreneur(partie, joueurs);

        int pointsPreneurX2 = scoringService.calculerPointsX2(cartesPreneur)
                            + correctionExcuseX2(partie, joueurs);
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
    private List<Carte> collecterCartesPreneur(Partie partie, List<Joueur> joueurs) {
        List<Carte> cartes = new ArrayList<>();
        int nbJoueurs = partie.getNbJoueursRequis();
        int maxPlis = nombreMaxPlis(nbJoueurs);

        List<Pli> plis = pliRepository.findByPartie_IdOrderByNumTourAsc(partieId(partie));
        for (Pli pli : plis) {
            List<Carte> cartesDuPli = new ArrayList<>(pli.getCartesJouees());
            int ouvreur = pli.getJoueurOuvreurIndex();
            boolean dernierPli = pli.getNumTour() == maxPlis;

            // Détecter l'Excuse dans ce pli et l'équipe qui l'a jouée
            Carte excuseDuPli = null;
            int equipeExcuse = -1;
            for (int i = 0; i < cartesDuPli.size(); i++) {
                Carte c = cartesDuPli.get(i);
                if ("Excuse".equals(c.getValeur()) && "Atout".equals(c.getCouleur())) {
                    final int posFinal = (ouvreur + i) % nbJoueurs;
                    Joueur joueurExcuse = joueurs.stream()
                            .filter(j -> j.getPosition() == posFinal)
                            .findFirst().orElse(null);
                    if (joueurExcuse != null) equipeExcuse = joueurExcuse.getEquipe();
                    excuseDuPli = c;
                    break;
                }
            }

            if (pli.getGagnantEquipe() == 1) {
                // Pli gagné par le preneur — toutes les cartes lui reviennent…
                cartes.addAll(cartesDuPli);
                // … sauf l'Excuse si elle a été jouée par la défense (retourne à l'équipe 2)
                if (excuseDuPli != null && equipeExcuse == 2) {
                    cartes.remove(excuseDuPli);
                }
            } else {
                // Pli gagné par les défenseurs — rien pour le preneur…
                // … sauf l'Excuse s'il l'a jouée, SAUF au dernier pli (exception officielle FFT)
                if (excuseDuPli != null && equipeExcuse == 1 && !dernierPli) {
                    cartes.add(excuseDuPli);
                }
            }
        }

        // Écartes comptent pour le preneur (PETITE/GARDE uniquement)
        String enchereType = partie.getEnchereType();
        if ("PETITE".equals(enchereType) || "GARDE".equals(enchereType)) {
            cartes.addAll(partie.getEcartes());
        }

        // GARDE_SANS / GARDE_CONTRE : le chien s'ajoute aux défenseurs (ne compte pas pour le preneur)

        return cartes;
    }

    private Long partieId(Partie partie) {
        return partie.getId();
    }

    // =========================================================
    // WEBSOCKET
    // =========================================================

    private void pushEtatTarotATous(Long partieId, List<Joueur> joueurs, EvenementJeuDTO.Type type) {
        for (Joueur j : joueurs) {
            EtatJeuTarotDTO etat = getEtatJeuTarot(partieId, j.getUtilisateur().getId());
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
    private int ordreCarte(Carte c) {
        if ("Atout".equals(c.getCouleur())) {
            if ("Excuse".equals(c.getValeur())) return -1;
            return ORDRE_TRUMP.indexOf(c.getValeur());
        }
        return ORDRE_SUIT.indexOf(c.getValeur());
    }

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
    private int correctionExcuseX2(Partie partie, List<Joueur> joueurs) {
        int nbJoueurs = partie.getNbJoueursRequis();
        int maxPlis   = nombreMaxPlis(nbJoueurs);
        int correction = 0;

        List<Pli> plis = pliRepository.findByPartie_IdOrderByNumTourAsc(partieId(partie));
        for (Pli pli : plis) {
            List<Carte> cartesDuPli = pli.getCartesJouees();
            int ouvreur    = pli.getJoueurOuvreurIndex();
            boolean dernier = pli.getNumTour() == maxPlis;

            for (int i = 0; i < cartesDuPli.size(); i++) {
                Carte c = cartesDuPli.get(i);
                if (!("Excuse".equals(c.getValeur()) && "Atout".equals(c.getCouleur()))) continue;

                final int posFinal = (ouvreur + i) % nbJoueurs;
                Joueur joueurExcuse = joueurs.stream()
                        .filter(j -> j.getPosition() == posFinal)
                        .findFirst().orElse(null);
                if (joueurExcuse == null) break;

                int equipeExcuse   = joueurExcuse.getEquipe();
                int equipeGagnante = pli.getGagnantEquipe();

                // Compensation uniquement quand l'Excuse change de camp (hors exception dernier pli)
                if (equipeExcuse != equipeGagnante && !dernier) {
                    if (equipeExcuse == 1) {
                        // Preneur a joué l'Excuse, défense a gagné → preneur cède 0,5 à défense
                        correction -= 1;
                    } else {
                        // Défenseur a joué l'Excuse, preneur a gagné → défense cède 0,5 au preneur
                        correction += 1;
                    }
                }
                break; // une seule Excuse possible par pli
            }
        }
        return correction;
    }

    private EtatJeuTarotDTO.ResultatTarotDTO buildResultatTarot(Partie partie, List<Joueur> joueurs) {
        EtatJeuTarotDTO.ResultatTarotDTO r = new EtatJeuTarotDTO.ResultatTarotDTO();
        r.setEnchereType(partie.getEnchereType());
        r.setMultiplicateur(partie.getMultiplicateur());

        // Trouver le pseudo du preneur
        joueurs.stream()
                .filter(j -> j.getId().equals(partie.getPreneurId()))
                .findFirst()
                .ifPresent(j -> r.setPseudoPreneur(j.getUtilisateur().getPseudo()));

        // 5j : pseudo du partenaire (null si solo ou 3j/4j)
        if (partie.getPartenaireId() != null) {
            joueurs.stream()
                    .filter(j -> j.getId().equals(partie.getPartenaireId()))
                    .findFirst()
                    .ifPresent(j -> r.setPseudoPartenaire(j.getUtilisateur().getPseudo()));
        }

        // Recalculer les stats depuis les plis
        List<Carte> cartesPreneur = collecterCartesPreneur(partie, joueurs);
        int pointsX2 = scoringService.calculerPointsX2(cartesPreneur)
                     + correctionExcuseX2(partie, joueurs);
        int bouts = scoringService.compterBouts(cartesPreneur);
        int seuil = scoringService.seuilPourBouts(bouts);
        boolean petitAuBoutDefense = false;
        List<Pli> plis = pliRepository.findByPartie_IdOrderByNumTourAsc(partie.getId());
        int maxPlis = nombreMaxPlis(partie.getNbJoueursRequis());
        Pli dernierPli = plis.stream().filter(p -> p.getNumTour() == maxPlis).findFirst().orElse(null);
        if (dernierPli != null && dernierPli.getCartesJouees().stream().anyMatch(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()))) {
            if (dernierPli.getGagnantEquipe() != 1) petitAuBoutDefense = true;
        }

        int score = scoringService.calculerScore(
                pointsX2, bouts, partie.getEnchereType(), partie.isPetitAuBoutPreneur(), petitAuBoutDefense
        ); r.setPointsPreneurX2(pointsX2);
        r.setBoutsPreneur(bouts);
        r.setSeuil(seuil);
        r.setContratRempli(score > 0);
        r.setScorePartie(Math.abs(score));
        r.setPetitAuBout(partie.isPetitAuBoutPreneur());
        r.setGagnantEquipe(score > 0 ? 1 : 2);

        return r;
    }

    // =========================================================
    // POIGNÉE
    // =========================================================

    /**
     * Le preneur déclare une Poignée avant de jouer sa première carte.
     * "SIMPLE"|"DOUBLE"|"TRIPLE"
     */
    public EtatJeuTarotDTO declarePoignee(Long partieId, Long utilisateurId, String typePoignee) {
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
        // Le premier joueur tourne d'une position à chaque donne (3j, 4j ou 5j)
        int premierJoueur = (partie.getDonneActuelle() - 1) % nbJoueurs;
        partie.setTourJoueurIndex(premierJoueur);
        // Ne PAS réinitialiser : donneActuelle, maxDonnes, maxPoints, scoreGlobalA/B
        partieRepository.save(partie);
    }
}
