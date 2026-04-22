package fr.enseeiht.jeux.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.repository.JoueurRepository;
import fr.enseeiht.jeux.repository.PartieRepository;

/**
 * Joue automatiquement les coups des bots en mode Tarot.
 *
 * Stratégie MVP simplifiée :
 *   Enchères : toujours passer
 *   Écart    : écarter les N cartes ayant le moins de valeur (jamais bouts ni rois)
 *   Jeu      : jouer la première carte valide (essai séquentiel)
 */
@Service
public class TarotBotService {

    private static final Logger log = LoggerFactory.getLogger(TarotBotService.class);

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final TarotService tarotService;
    private final TarotScoringService scoringService;

    public TarotBotService(PartieRepository partieRepository,
                           JoueurRepository joueurRepository,
                           @Lazy TarotService tarotService,
                           TarotScoringService scoringService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.tarotService = tarotService;
        this.scoringService = scoringService;
    }

    @Async
    public void jouerSiTourDuBot(Long partieId) {
        try { Thread.sleep(1200); } catch (InterruptedException ignored) { return; }

        Partie partie = partieRepository.findById(partieId).orElse(null);
        if (partie == null) return;

        String statut = partie.getStatut();
        if ("TERMINEE".equals(statut) || "OUVERTE".equals(statut)) return;

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur joueurActif = joueurs.stream()
                .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                .findFirst().orElse(null);

        if (joueurActif == null || !joueurActif.getUtilisateur().isBot()) return;

        String botPseudo = joueurActif.getUtilisateur().getPseudo();
        Long botUserId = joueurActif.getUtilisateur().getId();

        try {
            String phase = partie.getPhaseJeu();

            if ("EN_ENCHERE".equals(statut) && phase == null) {
                // Phase d'enchères : toujours passer
                log.info("Tarot {} : {} passe", partieId, botPseudo);
                tarotService.enchirirTarot(partieId, botUserId, "PASSE");

            } else if ("EN_ENCHERE".equals(statut) && "APPEL_ROI".equals(phase)) {
                // 5j : seul le preneur appelle — vérifier que ce bot est le preneur
                if (joueurActif.getId().equals(partie.getPreneurId())) {
                    // Appeler une couleur au hasard parmi celles que le bot ne détient pas en Roi
                    String[] couleurs = {"Coeur", "Carreau", "Trefle", "Pique"};
                    Joueur joueurFrais = joueurRepository.findById(joueurActif.getId()).orElse(null);
                    String choix = "Coeur"; // fallback
                    if (joueurFrais != null) {
                        for (String c : couleurs) {
                            final String cFinal = c;
                            boolean detientRoi = joueurFrais.getCartesEnMain().stream()
                                    .anyMatch(carte -> "Roi".equals(carte.getValeur()) && cFinal.equals(carte.getCouleur()));
                            if (!detientRoi) { choix = c; break; }
                        }
                    }
                    log.info("Tarot {} : {} appelle le Roi de {}", partieId, botPseudo, choix);
                    tarotService.appelerRoi(partieId, botUserId, choix);
                }

            } else if ("EN_ENCHERE".equals(statut) && "CHIEN".equals(phase)) {
                // Le preneur doit écarter — un bot peut aussi être le preneur (rare)
                if (joueurActif.getId().equals(partie.getPreneurId())) {
                    ecarterAutomatique(partieId, botUserId, joueurActif, partie);
                }

            } else if ("EN_ENCHERE".equals(statut) && "CHIEN_VU".equals(phase)) {
                // GARDE_SANS : confirmer sans écart
                if (joueurActif.getId().equals(partie.getPreneurId())) {
                    tarotService.ecarterCartes(partieId, botUserId, Collections.emptyList());
                }

            } else if ("EN_JEU".equals(statut)) {
                Joueur joueurFrais = joueurRepository.findById(joueurActif.getId()).orElse(null);
                if (joueurFrais == null) return;
                jouerCarteTarot(partieId, botUserId, joueurFrais, botPseudo);
            }

        } catch (Exception e) {
            log.error("TarotBot {} : exception pour {} : {}", partieId, botPseudo, e.getMessage());
        }
    }

    private void ecarterAutomatique(Long partieId, Long botUserId, Joueur joueur, Partie partie) {
        List<Carte> main = new ArrayList<>(joueur.getCartesEnMain());
        // Ajouter le chien dans la main
        main.addAll(partie.getChien());

        int tailleChien = partie.getChien().size();

        // Trier par valeur croissante (les moins importants d'abord)
        // Ne pas écarter les bouts ni les rois ni les atouts si possible
        List<Carte> candidats = main.stream()
                .filter(c -> !scoringService.isBout(c))
                .filter(c -> !"Roi".equals(c.getValeur()))
                .filter(c -> !"Atout".equals(c.getCouleur()))
                .sorted(Comparator.comparingInt(c -> scoringService.carteVautX2(c)))
                .collect(Collectors.toList());

        List<Long> aEcarter = new ArrayList<>();
        for (Carte c : candidats) {
            if (aEcarter.size() >= tailleChien) break;
            aEcarter.add(c.getId());
        }

        // Si pas assez, prendre des atouts non-bouts
        if (aEcarter.size() < tailleChien) {
            for (Carte c : main) {
                if (aEcarter.size() >= tailleChien) break;
                if (!aEcarter.contains(c.getId()) && "Atout".equals(c.getCouleur()) && !scoringService.isBout(c)) {
                    aEcarter.add(c.getId());
                }
            }
        }

        try {
            tarotService.ecarterCartes(partieId, botUserId, aEcarter);
        } catch (Exception e) {
            log.error("TarotBot écart échoué : {}", e.getMessage());
        }
    }

    private void jouerCarteTarot(Long partieId, Long botUserId, Joueur joueur, String botPseudo) {
        List<Carte> main = new ArrayList<>(joueur.getCartesEnMain());
        if (main.isEmpty()) return;

        for (Carte c : main) {
            try {
                tarotService.jouerCarte(partieId, botUserId, c.getId());
                //log.info("Tarot {} : {} joue {} {}", partieId, botPseudo, c.getValeur(), c.getCouleur());
                return;
            } catch (Exception e) {
                log.debug("Tarot {} : {} {} rejetée — {}", partieId, c.getValeur(), c.getCouleur(), e.getMessage());
            }
        }

        log.error("TarotBot {} BLOQUÉ — aucune carte jouable pour {}", partieId, botPseudo);
    }
}
