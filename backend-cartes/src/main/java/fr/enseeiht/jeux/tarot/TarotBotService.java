package fr.enseeiht.jeux.tarot;

import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Joue automatiquement les coups des bots en mode Tarot.
 *
 * Stratégie MVP :
 *   Enchères : toujours passer
 *   Écart    : écarter les cartes de moindre valeur (pas les bouts ni les rois)
 *   Jeu      : jouer la première carte valide (essai séquentiel)
 */
@Service
public class TarotBotService {

    private static final Logger log = LoggerFactory.getLogger(TarotBotService.class);

    private final PartieRepository       partieRepository;
    private final JoueurRepository       joueurRepository;
    private final TarotService           tarotService;
    private final TarotScoringService    scoringService;

    public TarotBotService(PartieRepository partieRepository,
                           JoueurRepository joueurRepository,
                           @Lazy TarotService tarotService,
                           TarotScoringService scoringService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.tarotService     = tarotService;
        this.scoringService   = scoringService;
    }

    // =========================================================
    // POINT D'ENTRÉE PRINCIPAL
    // =========================================================

    /**
     * Déclenché après chaque action humaine.
     * Si le joueur actif est un bot, joue automatiquement son coup après un délai.
     */
    @Async
    public void jouerSiTourDuBot(Long partieId) {
        // Délai pour rendre le bot moins "instantané"
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

        String botPseudo  = joueurActif.getUtilisateur().getPseudo();
        Long   botUserId  = joueurActif.getUtilisateur().getId();

        try {
            jouerCoupBot(partie, joueurs, joueurActif, partieId, botPseudo, botUserId);
        } catch (Exception e) {
            log.error("TarotBot {} : exception pour {} : {}", partieId, botPseudo, e.getMessage());
        }
    }

    // =========================================================
    // LOGIQUE DE JEU DU BOT
    // =========================================================

    /**
     * Choisit et exécute l'action du bot selon la phase de jeu actuelle.
     */
    private void jouerCoupBot(Partie partie, List<Joueur> joueurs, Joueur joueurActif,
                               Long partieId, String botPseudo, Long botUserId) {
        String statut = partie.getStatut();
        String phase  = partie.getPhaseJeu();

        if ("EN_ENCHERE".equals(statut) && phase == null) {
            // Phase d'enchères : le bot passe toujours
            log.info("Tarot {} : {} passe", partieId, botPseudo);
            tarotService.enchirirTarot(partieId, botUserId, "PASSE");

        } else if ("EN_ENCHERE".equals(statut) && "APPEL_ROI".equals(phase)) {
            // 5 joueurs : appel du Roi (seul le preneur joue cette phase)
            if (joueurActif.getId().equals(partie.getPreneurId())) {
                String couleur = choisirRoiAAppeler(joueurActif);
                log.info("Tarot {} : {} appelle le Roi de {}", partieId, botPseudo, couleur);
                tarotService.appelerRoi(partieId, botUserId, couleur);
            }

        } else if ("EN_ENCHERE".equals(statut) && "CHIEN".equals(phase)) {
            // Phase d'écart : uniquement pour le preneur
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
    }

    // =========================================================
    // ÉCART AUTOMATIQUE
    // =========================================================

    /**
     * Choisit les cartes à écarter : priorité aux cartes de couleur de faible valeur,
     * jamais de bouts ni de rois.
     */
    private void ecarterAutomatique(Long partieId, Long botUserId, Joueur joueur, Partie partie) {
        List<Carte> main = new ArrayList<>(joueur.getCartesEnMain());
        main.addAll(partie.getChien()); // intégrer le chien dans la main

        int tailleChien = partie.getChien().size();

        // Candidats : cartes de couleur (pas atouts), pas bouts, pas rois — triées par valeur croissante
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

        // Si pas assez de cartes de couleur, compléter avec des atouts non-bouts
        if (aEcarter.size() < tailleChien) {
            for (Carte c : main) {
                if (aEcarter.size() >= tailleChien) break;
                boolean estAtoutNonBout = "Atout".equals(c.getCouleur()) && !scoringService.isBout(c);
                if (!aEcarter.contains(c.getId()) && estAtoutNonBout) {
                    aEcarter.add(c.getId());
                }
            }
        }

        try {
            tarotService.ecarterCartes(partieId, botUserId, aEcarter);
        } catch (Exception e) {
            log.error("TarotBot : écart échoué — {}", e.getMessage());
        }
    }

    // =========================================================
    // JEU D'UNE CARTE
    // =========================================================

    /**
     * Essaie de jouer chaque carte de la main jusqu'à en trouver une valide.
     */
    private void jouerCarteTarot(Long partieId, Long botUserId, Joueur joueur, String botPseudo) {
        List<Carte> main = new ArrayList<>(joueur.getCartesEnMain());
        if (main.isEmpty()) return;

        for (Carte c : main) {
            try {
                tarotService.jouerCarte(partieId, botUserId, c.getId());
                log.info("Tarot {} : {} joue {} {}", partieId, botPseudo, c.getValeur(), c.getCouleur());
                return;
            } catch (Exception e) {
                log.debug("Tarot {} : {} {} rejetée — {}", partieId, c.getValeur(), c.getCouleur(), e.getMessage());
            }
        }

        log.error("TarotBot {} BLOQUÉ — aucune carte jouable pour {}", partieId, botPseudo);
    }

    // =========================================================
    // APPEL DU ROI (5 joueurs)
    // =========================================================

    /**
     * Choisit le Roi d'une couleur que le bot ne détient pas.
     */
    private String choisirRoiAAppeler(Joueur joueur) {
        String[] couleurs = {"Coeur", "Carreau", "Trefle", "Pique"};
        Joueur joueurFrais = joueurRepository.findById(joueur.getId()).orElse(null);
        if (joueurFrais == null) return "Coeur";

        for (String couleur : couleurs) {
            final String c = couleur;
            boolean detientLe = joueurFrais.getCartesEnMain().stream()
                    .anyMatch(carte -> "Roi".equals(carte.getValeur()) && c.equals(carte.getCouleur()));
            if (!detientLe) return couleur;
        }
        return "Coeur"; // fallback (bot détient tous les rois — possible mais très rare)
    }
}
