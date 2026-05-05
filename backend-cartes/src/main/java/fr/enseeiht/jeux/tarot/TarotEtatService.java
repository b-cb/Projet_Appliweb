package fr.enseeiht.jeux.tarot;

import fr.enseeiht.jeux.dto.CarteDTO;
import fr.enseeiht.jeux.dto.EnchereDTO;
import fr.enseeiht.jeux.dto.EvenementJeuDTO;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.modele.Pli;
import fr.enseeiht.jeux.repository.EnchereRepository;
import fr.enseeiht.jeux.repository.JoueurRepository;
import fr.enseeiht.jeux.repository.PartieRepository;
import fr.enseeiht.jeux.repository.PliRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service gérant la construction de l'état (DTO) pour le Tarot.
 */
@Service
@Transactional
public class TarotEtatService {

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final EnchereRepository enchereRepository;
    private final PliRepository pliRepository;
    private final TarotScoringService scoringService;

    // Ordre force des cartes de couleur (1 = As tarot, Roi le plus fort)
    private static final List<String> ORDRE_SUIT =
            List.of("1","2","3","4","5","6","7","8","9","10","Valet","Cavalier","Dame","Roi");

    public TarotEtatService(PartieRepository partieRepository,
                            JoueurRepository joueurRepository,
                            EnchereRepository enchereRepository,
                            PliRepository pliRepository,
                            TarotScoringService scoringService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
        this.scoringService = scoringService;
    }

    public EtatTarotDTO getEtatJeuTarot(Long partieId, Long utilisateurId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        int nbJoueurs = partie.getNbJoueursRequis();

        Joueur monJoueur = joueurs.stream()
                .filter(j -> j.getUtilisateur().getId().equals(utilisateurId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        EtatTarotDTO dto = new EtatTarotDTO();
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
            List<EtatTarotDTO.CartePliDTO> pliCourant = new ArrayList<>();
            for (int i = 0; i < cartesJouees.size(); i++) {
                int idx = (ouvreurIndex + i) % nbJoueurs;
                final int idxFinal = idx;
                Joueur j = joueurs.stream().filter(jj -> jj.getPosition() == idxFinal).findFirst().orElse(null);
                if (j != null) {
                    pliCourant.add(new EtatTarotDTO.CartePliDTO(
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
                List<EtatTarotDTO.CartePliDTO> dernierPliList = new ArrayList<>();
                for (int i = 0; i < dpCartes.size(); i++) {
                    int idx = (dpOuvreur + i) % nbJoueurs;
                    final int idxFinal = idx;
                    Joueur j = joueurs.stream().filter(jj -> jj.getPosition() == idxFinal).findFirst().orElse(null);
                    if (j != null) {
                        dernierPliList.add(new EtatTarotDTO.CartePliDTO(
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

        // Petit sec
        dto.setPetitSecDetecte(partie.isPetitSecDetecte());
        if (partie.isPetitSecDetecte()) {
            // Indiquer si C'EST ce joueur qui a le petit sec
            boolean monPetitSec = monJoueur.getCartesEnMain().stream()
                    .anyMatch(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()))
                    && monJoueur.getCartesEnMain().stream()
                            .filter(c -> "Atout".equals(c.getCouleur()) && !"Excuse".equals(c.getValeur()))
                            .count() == 1;
            dto.setMonPetitEstSec(monPetitSec);
        }

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

    public List<Carte> collecterCartesPreneur(Partie partie, List<Joueur> joueurs) {
        List<Carte> c = new ArrayList<>();
        // Les plis gagnés par l'équipe 1
        List<Pli> plisEquipe1 = pliRepository.findByPartie_Id(partie.getId()).stream()
                .filter(p -> p.getGagnantEquipe() == 1)
                .collect(Collectors.toList());
        for (Pli p : plisEquipe1) c.addAll(p.getCartesJouees());

        // Les écarts (si l'enchère n'est pas GARDE_CONTRE où le chien va en défense)
        if (!"GARDE_CONTRE".equals(partie.getEnchereType())) {
            c.addAll(partie.getEcartes());
        }
        return c;
    }

    public int correctionExcuseX2(Partie partie, List<Joueur> joueurs) {
        int correction = 0;
        List<Pli> tousPlis = pliRepository.findByPartie_Id(partie.getId());

        // Trouver qui a joué l'Excuse
        Pli pliExcuse = null;
        Joueur joueurExcuse = null;

        for (Pli p : tousPlis) {
            for (int i = 0; i < p.getCartesJouees().size(); i++) {
                if ("Excuse".equals(p.getCartesJouees().get(i).getValeur())) {
                    pliExcuse = p;
                    int idxJoueur = (p.getJoueurOuvreurIndex() + i) % partie.getNbJoueursRequis();
                    joueurExcuse = joueurs.stream().filter(j -> j.getPosition() == idxJoueur).findFirst().orElse(null);
                    break;
                }
            }
            if (pliExcuse != null) break;
        }

        if (pliExcuse != null && joueurExcuse != null) {
            int eqGagnantPli = pliExcuse.getGagnantEquipe();
            int eqJoueurExcuse = joueurExcuse.getEquipe();

            // Règle : l'Excuse reste acquise au camp qui la joue
            if (eqJoueurExcuse == 1 && eqGagnantPli != 1) {
                // Le preneur a joué l'Excuse mais a perdu le pli → il garde les 4,5 pts de l'Excuse (+9 ×2)
                // mais on lui "enlève" 0,5 pt (+1 ×2) car il doit donner une carte basse à l'adversaire
                correction += (9 - 1); // +8 demi-pts
            } else if (eqJoueurExcuse != 1 && eqGagnantPli == 1) {
                // La défense a joué l'Excuse et le preneur a gagné le pli → le preneur ne prend pas les 4,5 pts (-9 ×2)
                // mais reçoit 0,5 pt (+1 ×2) en échange
                correction -= (9 - 1); // -8 demi-pts
            }
        }
        return correction;
    }

    public EtatTarotDTO.ResultatTarotDTO buildResultatTarot(Partie partie, List<Joueur> joueurs) {
        EtatTarotDTO.ResultatTarotDTO r = new EtatTarotDTO.ResultatTarotDTO();
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
        int maxPlis = (partie.getNbJoueursRequis() == 5) ? 15 : (partie.getNbJoueursRequis() == 4 ? 18 : 24);
        Pli dernierPli = plis.stream().filter(p -> p.getNumTour() == maxPlis).findFirst().orElse(null);
        if (dernierPli != null && dernierPli.getCartesJouees().stream().anyMatch(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()))) {
            if (dernierPli.getGagnantEquipe() != 1) petitAuBoutDefense = true;
        }

        int score = scoringService.calculerScore(
                pointsX2, bouts, partie.getEnchereType(), partie.isPetitAuBoutPreneur(), petitAuBoutDefense
        ); 
        r.setPointsPreneurX2(pointsX2);
        r.setBoutsPreneur(bouts);
        r.setSeuil(seuil);
        r.setContratRempli(score > 0);
        r.setScorePartie(Math.abs(score));
        r.setPetitAuBout(partie.isPetitAuBoutPreneur());
        r.setGagnantEquipe(score > 0 ? 1 : 2);

        return r;
    }

    private int ordreCarte(Carte c) {
        if ("Atout".equals(c.getCouleur())) {
            if ("Excuse".equals(c.getValeur())) return 100;
            try {
                return Integer.parseInt(c.getValeur());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        int idx = ORDRE_SUIT.indexOf(c.getValeur());
        return idx >= 0 ? idx : -1;
    }
}
