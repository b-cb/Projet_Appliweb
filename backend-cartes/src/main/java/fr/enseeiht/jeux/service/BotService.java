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

// Joue automatiquement les coups des bots. S'auto-chaîne jusqu'au prochain joueur humain.
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

    @Async
    public void jouerSiTourDuBot(Long partieId) {
        try { Thread.sleep(900); } catch (InterruptedException ignored) { return; }

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

        boolean aJoue = false;
        try {
            if ("EN_ENCHERE".equals(statut)) {
                jouerEnchere(partieId, botUserId, partie, botPseudo);
                aJoue = true;
            } else if ("EN_JEU".equals(statut)) {
                Joueur joueurFrais = joueurRepository.findById(joueurActif.getId()).orElse(null);
                if (joueurFrais != null) {
                    jouerCarte(partieId, botUserId, joueurFrais, botPseudo);
                    aJoue = true;
                }
            }
        } catch (Exception e) {
            log.error("Bot {} dans partie {} : {}", botPseudo, partieId, e.getMessage());
        }

        // enchaîner si le joueur suivant est aussi un bot
        if (aJoue) jouerSiTourDuBot(partieId);
    }

    private void jouerEnchere(Long partieId, Long botUserId, Partie partie, String botPseudo) {
        try {
            jeuService.encherir(partieId, botUserId, null, null, true);
        } catch (Exception e) {
            log.error("{} ne peut pas passer dans partie {} : {}", botPseudo, partieId, e.getMessage());
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
