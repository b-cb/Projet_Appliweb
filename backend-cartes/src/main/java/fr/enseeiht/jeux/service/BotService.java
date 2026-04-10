package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Joue automatiquement pour les bots.
 *
 * Chaque appel à jouerSiTourDuBot() joue UN SEUL coup si c'est le tour d'un bot.
 * Le chaînage entre bots consécutifs est assuré par les hooks afterCommit
 * dans JeuService (qui rappellent jouerSiTourDuBot après chaque transaction).
 */
@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final JeuService jeuService;

    public BotService(PartieRepository partieRepository,
                      JoueurRepository joueurRepository,
                      @Lazy JeuService jeuService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.jeuService = jeuService;
    }

    /**
     * Joue un seul coup si le joueur actif est un bot.
     * Appelé en @Async (nouveau thread) depuis les hooks afterCommit de JeuService.
     */
    @Async
    public void jouerSiTourDuBot(Long partieId) {
        try { Thread.sleep(1200); } catch (InterruptedException ignored) { return; }

        // Lecture fraîche depuis la BDD (pas de cache Hibernate)
        Partie partie = partieRepository.findById(partieId).orElse(null);
        if (partie == null) return;

        String statut = partie.getStatut();
        if ("TERMINEE".equals(statut) || "OUVERTE".equals(statut)) return;

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur joueurActif = joueurs.stream()
                .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                .findFirst().orElse(null);

        if (joueurActif == null || !joueurActif.getUtilisateur().isBot()) {
            log.debug("Partie {} : tour du joueur {} (pas un bot), arrêt", partieId,
                    joueurActif != null ? joueurActif.getUtilisateur().getPseudo() : "null");
            return;
        }

        String botPseudo = joueurActif.getUtilisateur().getPseudo();
        Long botUserId = joueurActif.getUtilisateur().getId();
        log.info("Partie {} : {} (userId={}) joue — statut={}", partieId, botPseudo, botUserId, statut);

        try {
            if ("EN_ENCHERE".equals(statut)) {
                jouerEnchere(partieId, botUserId, partie, botPseudo);
            } else if ("EN_JEU".equals(statut)) {
                // Recharger le joueur pour avoir la main à jour
                Joueur joueurFrais = joueurRepository.findById(joueurActif.getId()).orElse(null);
                if (joueurFrais == null) {
                    log.error("Partie {} : joueur {} introuvable après rechargement", partieId, botPseudo);
                    return;
                }
                jouerCarte(partieId, botUserId, joueurFrais, botPseudo);
            }
        } catch (Exception e) {
            log.error("Partie {} : exception inattendue pour {} : {}", partieId, botPseudo, e.getMessage(), e);
        }
        // Le prochain bot sera déclenché par le hook afterCommit de JeuService
    }

    private void jouerEnchere(Long partieId, Long botUserId, Partie partie, String botPseudo) {
        try {
            if (partie.getContratValeur() == 0) {
                log.info("{} enchérit 80 Coeur dans partie {}", botPseudo, partieId);
                jeuService.encherir(partieId, botUserId, 80, "Coeur", false);
            } else {
                log.info("{} passe dans partie {}", botPseudo, partieId);
                jeuService.encherir(partieId, botUserId, null, null, true);
            }
        } catch (Exception e) {
            log.error("{} ne peut pas enchérir dans partie {} : {}", botPseudo, partieId, e.getMessage());
        }
    }

    private void jouerCarte(Long partieId, Long botUserId, Joueur joueur, String botPseudo) {
        List<Carte> main = new ArrayList<>(joueur.getCartesEnMain());
        if (main.isEmpty()) {
            log.error("{} a une main vide dans partie {}", botPseudo, partieId);
            return;
        }

        log.info("{} tente de jouer dans partie {} — {} cartes en main : {}",
                botPseudo, partieId, main.size(), descriptionMain(main));

        // Essayer chaque carte de la main
        for (Carte carte : main) {
            try {
                jeuService.jouerCarte(partieId, botUserId, carte.getId());
                log.info("{} a joué {} de {} dans partie {}", botPseudo, carte.getValeur(), carte.getCouleur(), partieId);
                return;
            } catch (Exception e) {
                log.debug("{} : {} de {} rejetée — {}", botPseudo, carte.getValeur(), carte.getCouleur(), e.getMessage());
            }
        }

        log.error("{} BLOQUÉ dans partie {} — aucune des {} cartes n'est jouable !", botPseudo, partieId, main.size());
    }

    private String descriptionMain(List<Carte> main) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < main.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(main.get(i).getValeur()).append(" ").append(main.get(i).getCouleur());
        }
        sb.append("]");
        return sb.toString();
    }
}
